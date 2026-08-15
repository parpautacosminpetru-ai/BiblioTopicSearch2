import hashlib
import inspect
import requests
import numpy as np
import onnx
import onnxruntime as ort
from huggingface_hub import hf_hub_download
from transformers import AutoTokenizer
from onnxruntime_extensions import gen_processing_models, get_library_path

REPO = "onnx-community/multilingual-MiniLMv2-L6-mnli-xnli-ONNX"
REV = "ca5daf3d11b6c4b3143b1f4602a2edfb64c3ad7e"
BASE_REPO = "MoritzLaurer/multilingual-MiniLMv2-L6-mnli-xnli"
SPM_SHA256 = "cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865"
SPM_PATH = "/tmp/sentencepiece.bpe.model"

url = f"https://huggingface.co/{BASE_REPO}/resolve/main/sentencepiece.bpe.model?download=true"
response = requests.get(url, timeout=120)
response.raise_for_status()
open(SPM_PATH, "wb").write(response.content)
assert hashlib.sha256(response.content).hexdigest() == SPM_SHA256

print("SIG", inspect.signature(gen_processing_models))
tok = AutoTokenizer.from_pretrained(REPO, revision=REV, use_fast=True)
tok.vocab_file = SPM_PATH
print("TOK", tok.__class__.__name__, tok.model_max_length, tok.vocab_file)
pre, _ = gen_processing_models(tok, pre_kwargs={}, opset=17)
tok_path = "/tmp/nli_fast_tokenizer.onnx"
onnx.save(pre, tok_path)
print("IN", [(x.name, x.type.tensor_type.elem_type, [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim]) for x in pre.graph.input])
print("OUT", [(x.name, x.type.tensor_type.elem_type, [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim]) for x in pre.graph.output])

model_path = hf_hub_download(REPO, filename="onnx/model_int8.onnx", revision=REV)
model_sha = hashlib.sha256(open(model_path, "rb").read()).hexdigest()
print("MODEL_SHA", model_sha)
assert model_sha == "55614f3c7da74184742eaa0006b978744437aa91de9ba4913db42f94d7844a8f"
so = ort.SessionOptions(); so.register_custom_ops_library(get_library_path())
tokenizer_session = ort.InferenceSession(tok_path, so, providers=["CPUExecutionProvider"])
classifier = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])


def onnx_pair_ids(premise, hypothesis):
    # Extensions returns flattened tokens plus ragged offsets in instance_indices.
    # Build the standard XLM-R pair sequence from independently tokenized strings;
    # never inject literal special-token text into the user's query/source.
    name = tokenizer_session.get_inputs()[0].name
    raw = tokenizer_session.run(None, {name: np.asarray([premise, hypothesis], dtype=object)})
    tokens = np.asarray(raw[0], dtype=np.int64).reshape(-1)
    offsets = np.asarray(raw[1], dtype=np.int64).reshape(-1)
    print("RAGGED", tokens.shape, offsets.tolist())
    if len(offsets) != 3 or offsets[0] != 0 or offsets[-1] != len(tokens):
        raise RuntimeError(f"Unexpected tokenizer ragged offsets: tokens={tokens.tolist()} offsets={offsets.tolist()}")
    a = tokens[offsets[0]:offsets[1]]
    b = tokens[offsets[1]:offsets[2]]
    if len(a) < 2 or len(b) < 2:
        raise RuntimeError(f"Unexpected tokenizer output: a={a.tolist()} b={b.tolist()} offsets={offsets.tolist()}")
    # Independent XLM-R sequences are <s> A </s> and <s> B </s>.
    # A pair is <s> A </s></s> B </s>.
    if a[0] != tok.cls_token_id or a[-1] != tok.sep_token_id or b[0] != tok.cls_token_id or b[-1] != tok.sep_token_id:
        raise RuntimeError(f"Unexpected XLM-R special tokens: a={a.tolist()} b={b.tolist()}")
    return np.concatenate([a, np.asarray([tok.sep_token_id], dtype=np.int64), b[1:]])


def probabilities(premise, hypothesis):
    ids = onnx_pair_ids(premise, hypothesis)
    expected = np.asarray(tok(premise, hypothesis, truncation=True, max_length=512)["input_ids"], dtype=np.int64)
    if len(ids) > 512:
        ids = ids[:512]
        ids[-1] = tok.sep_token_id
    expected = expected[:512]
    if not np.array_equal(ids, expected):
        raise AssertionError(f"PAIR_TOKEN_MISMATCH\nonnx={ids.tolist()}\nhf={expected.tolist()}")
    ids = ids.reshape(1, -1)
    mask = np.ones_like(ids, dtype=np.int64)
    logits = np.asarray(classifier.run(None, {"input_ids": ids, "attention_mask": mask})[0], dtype=np.float32)[0]
    exp = np.exp(logits - logits.max())
    return exp / exp.sum()


pairs = [
    ("Dumnezeu este bun și milostiv.", "Dumnezeu este bun"),
    ("Dumnezeu este bun și milostiv.", "Dumnezeu este rău"),
    ("Dumnezeu este bun și milostiv.", "Dumnezeu și iubire"),
    ("Nemulțumirile față de vânzarea indulgențelor au contribuit la izbucnirea Reformei protestante.", "cauzele Reformei protestante"),
    ("Nemulțumirile față de vânzarea indulgențelor au contribuit la izbucnirea Reformei protestante.", "consecințele Reformei protestante"),
    ("Nu avea voie să părăsească orașul.", "interdicție de deplasare"),
    ("Privea mereu drumul spre exterior și își amintea de vremurile când călătorea.", "interdicție de deplasare"),
    ("Dumnezeu iubește omul.", "iubirea lui Dumnezeu față de om"),
    ("Omul îl iubește pe Dumnezeu.", "iubirea lui Dumnezeu față de om"),
    ("Ion îl atacă pe Petru.", "Ion îl atacă pe Petru"),
    ("Petru îl atacă pe Ion.", "Ion îl atacă pe Petru"),
]

for premise, hypothesis in pairs:
    probs = probabilities(premise, hypothesis)
    print("NLI", premise, "||", hypothesis, "=> entailment,neutral,contradiction", [round(float(x), 6) for x in probs])

print("PAIR_TOKENIZATION_OK")
