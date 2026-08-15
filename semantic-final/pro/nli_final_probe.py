import hashlib
import inspect
import requests
import numpy as np
import onnx
import onnxruntime as ort
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

last_error = None
for kwargs in ({}, {"WITH_DEFAULT_INPUTS": True}):
    try:
        pre, _ = gen_processing_models(tok, pre_kwargs=kwargs, opset=17)
        path = "/tmp/nli_fast_tokenizer.onnx"
        onnx.save(pre, path)
        print("KW", kwargs)
        print("IN", [(x.name, x.type.tensor_type.elem_type, [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim]) for x in pre.graph.input])
        print("OUT", [(x.name, x.type.tensor_type.elem_type, [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim]) for x in pre.graph.output])
        print("NODES", [(n.op_type, n.domain) for n in pre.graph.node])
        so = ort.SessionOptions()
        so.register_custom_ops_library(get_library_path())
        session = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
        name = session.get_inputs()[0].name
        packed = "Dumnezeu este bun și milostiv. </s></s> Dumnezeu este rău"
        outputs = session.run(None, {name: np.asarray([packed], dtype=object)})
        for i, value in enumerate(outputs):
            arr = np.asarray(value)
            print("VAL", i, arr.dtype, arr.shape, arr.tolist())
        last_error = None
        break
    except Exception as exc:
        last_error = exc
        print("ERR", kwargs, repr(exc))

if last_error is not None:
    raise last_error
