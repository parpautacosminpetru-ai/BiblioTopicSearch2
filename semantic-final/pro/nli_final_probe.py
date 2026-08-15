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
print("MODEL_SHA", hashlib.sha256(open(model_path, "rb").read()).hexdigest())
so = ort.SessionOptions(); so.register_custom_ops_library(get_library_path())
tokenizer_session = ort.InferenceSession(tok_path, so, providers=["CPUExecutionProvider"])
classifier = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])

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
    packed = premise + " </s></s> " + hypothesis
    raw = tokenizer_session.run(None, {tokenizer_session.get_inputs()[0].name: np.asarray([packed], dtype=object)})
    ids = np.asarray(raw[0], dtype=np.int64).reshape(1, -1)
    mask = np.ones_like(ids, dtype=np.int64)
    logits = np.asarray(classifier.run(None, {"input_ids": ids, "attention_mask": mask})[0], dtype=np.float32)[0]
    exp = np.exp(logits - logits.max())
    probs = exp / exp.sum()
    print("NLI", premise, "||", hypothesis, "=> entailment,neutral,contradiction", [round(float(x), 6) for x in probs])
