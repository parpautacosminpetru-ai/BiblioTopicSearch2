import inspect
import numpy as np
import onnx
import onnxruntime as ort
from transformers import AutoTokenizer
from onnxruntime_extensions import gen_processing_models, get_library_path

REPO = "onnx-community/multilingual-MiniLMv2-L6-mnli-xnli-ONNX"
REV = "ca5daf3d11b6c4b3143b1f4602a2edfb64c3ad7e"

print("SIG", inspect.signature(gen_processing_models))
tok = AutoTokenizer.from_pretrained(REPO, revision=REV, use_fast=True)
print("TOK", tok.__class__.__name__, tok.model_max_length)

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
