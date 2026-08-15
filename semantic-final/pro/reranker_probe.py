import hashlib
import numpy as np
import onnxruntime as ort
from huggingface_hub import hf_hub_download
from transformers import AutoTokenizer

REPO = "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1"
REV = "8008f0154ca2387468013ed439df09d70c122412"
MODEL = "onnx/model_qint8_arm64.onnx"

tok = AutoTokenizer.from_pretrained(REPO, revision=REV, use_fast=True)
model_path = hf_hub_download(REPO, filename=MODEL, revision=REV)
print("RERANK_MODEL_SHA", hashlib.sha256(open(model_path, "rb").read()).hexdigest())
print("RERANK_TOKENIZER", tok.__class__.__name__, tok.cls_token_id, tok.sep_token_id, tok.model_max_length)
session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
print("RERANK_IN", [(x.name, x.type, x.shape) for x in session.get_inputs()])
print("RERANK_OUT", [(x.name, x.type, x.shape) for x in session.get_outputs()])

pairs = [
    ("Dumnezeu", "Dumnezeu este bun și milostiv."),
    ("Dumnezeu este bun", "Dumnezeu este bun și milostiv."),
    ("bunătatea lui Dumnezeu", "Dumnezeu este bun și milostiv."),
    ("Dumnezeu este rău", "Dumnezeu este bun și milostiv."),
    ("cauzele Reformei protestante", "Nemulțumirile față de vânzarea indulgențelor au contribuit la izbucnirea Reformei protestante."),
    ("consecințele Reformei protestante", "Nemulțumirile față de vânzarea indulgențelor au contribuit la izbucnirea Reformei protestante."),
    ("interdicție de deplasare", "Nu avea voie să părăsească orașul."),
    ("interdicție de deplasare", "Privea mereu drumul spre exterior și își amintea de vremurile când călătorea."),
    ("iubirea lui Dumnezeu față de om", "Dumnezeu iubește omul."),
    ("iubirea lui Dumnezeu față de om", "Omul îl iubește pe Dumnezeu."),
    ("Ion îl atacă pe Petru", "Ion îl atacă pe Petru."),
    ("Ion îl atacă pe Petru", "Petru îl atacă pe Ion."),
]

for query, passage in pairs:
    batch = tok(query, passage, truncation=True, max_length=256, return_tensors="np")
    feeds = {}
    for inp in session.get_inputs():
        if inp.name in batch:
            feeds[inp.name] = np.asarray(batch[inp.name], dtype=np.int64)
    raw = np.asarray(session.run(None, feeds)[0], dtype=np.float32).reshape(-1)[0]
    sigmoid = 1.0 / (1.0 + np.exp(-raw))
    print("RERANK", query, "||", passage, "=>", round(float(raw), 6), round(float(sigmoid), 6))
