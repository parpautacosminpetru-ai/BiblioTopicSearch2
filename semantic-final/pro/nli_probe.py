import hashlib
import os
import requests
import numpy as np
import onnx
import onnxruntime as ort
from huggingface_hub import hf_hub_download
from transformers import AutoTokenizer, XLMRobertaTokenizer
from onnxruntime_extensions import gen_processing_models, get_library_path

ONNX_REPO = "onnx-community/multilingual-MiniLMv2-L6-mnli-xnli-ONNX"
ONNX_REV = "ca5daf3d11b6c4b3143b1f4602a2edfb64c3ad7e"
BASE_REPO = "MoritzLaurer/multilingual-MiniLMv2-L6-mnli-xnli"
SPM_SHA256 = "cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865"
SPM = "/tmp/sentencepiece.bpe.model"
OUT = "/tmp/nli_tokenizer.onnx"

url = f"https://huggingface.co/{BASE_REPO}/resolve/main/sentencepiece.bpe.model?download=true"
r = requests.get(url, timeout=120)
r.raise_for_status()
open(SPM, "wb").write(r.content)
sha = hashlib.sha256(r.content).hexdigest()
print("SPM_SHA", sha, "bytes", len(r.content))
assert sha == SPM_SHA256

model_path = hf_hub_download(ONNX_REPO, filename="onnx/model_int8.onnx", revision=ONNX_REV)
config_path = hf_hub_download(ONNX_REPO, filename="config.json", revision=ONNX_REV)
print("MODEL_BYTES", os.path.getsize(model_path))
print("CONFIG", open(config_path, encoding="utf-8").read())

fast = AutoTokenizer.from_pretrained(ONNX_REPO, revision=ONNX_REV)
slow = XLMRobertaTokenizer(vocab_file=SPM)
print("FAST", fast.__class__.__name__, fast.model_max_length)
print("SLOW", slow.__class__.__name__, slow.model_max_length)

pairs = [
    ("Nu avea voie să părăsească orașul fără permisiune.", "restricție de deplasare"),
    ("Nu avea voie să părăsească orașul fără permisiune.", "Nu putea pleca liber din oraș."),
    ("Se uita des pe fereastră și visa la alte locuri.", "restricție de deplasare"),
    ("Putea pleca oriunde dorea.", "Nu putea pleca liber."),
]

for premise, hypothesis in pairs:
    packed = premise + " </s></s> " + hypothesis
    pair = fast(premise, hypothesis, add_special_tokens=True, truncation=True, return_tensors="np")
    slow_single = slow(packed, add_special_tokens=True, truncation=True, return_tensors="np")
    print("TOKEN_EQ", np.array_equal(pair["input_ids"], slow_single["input_ids"]), premise, "||", hypothesis)
    if not np.array_equal(pair["input_ids"], slow_single["input_ids"]):
        print("FAST_IDS", pair["input_ids"].tolist())
        print("SLOW_IDS", slow_single["input_ids"].tolist())
        raise SystemExit("Tokenizer mismatch")

pre, _ = gen_processing_models(slow, pre_kwargs={}, opset=17)
onnx.save(pre, OUT)
print("PRE_INPUTS", [(x.name, x.type.tensor_type.elem_type, [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim]) for x in pre.graph.input])
print("PRE_OUTPUTS", [(x.name, x.type.tensor_type.elem_type, [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim]) for x in pre.graph.output])
print("PRE_NODES", [(n.op_type, n.domain) for n in pre.graph.node])
print("TOKENIZER_MODEL_BYTES", os.path.getsize(OUT))

so = ort.SessionOptions()
so.register_custom_ops_library(get_library_path())
tok = ort.InferenceSession(OUT, so, providers=["CPUExecutionProvider"])
cls = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
print("CLS_INPUTS", [(x.name, x.type, x.shape) for x in cls.get_inputs()])
print("CLS_OUTPUTS", [(x.name, x.type, x.shape) for x in cls.get_outputs()])

for premise, hypothesis in pairs:
    packed = premise + " </s></s> " + hypothesis
    tok_out = tok.run(None, {tok.get_inputs()[0].name: np.asarray([packed], dtype=object)})
    ids = np.asarray(tok_out[0], dtype=np.int64)
    mask = np.asarray(tok_out[1], dtype=np.int64) if len(tok_out) > 1 else np.ones_like(ids)
    feeds = {}
    for inp in cls.get_inputs():
        if inp.name == "input_ids": feeds[inp.name] = ids
        elif inp.name == "attention_mask": feeds[inp.name] = mask
        elif inp.name == "token_type_ids": feeds[inp.name] = np.zeros_like(ids)
    logits = np.asarray(cls.run(None, feeds)[0], dtype=np.float32)[0]
    e = np.exp(logits - logits.max())
    probs = e / e.sum()
    print("PROBS", premise, "||", hypothesis, probs.tolist())
