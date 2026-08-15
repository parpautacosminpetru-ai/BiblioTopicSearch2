import os
import numpy as np
import onnx
from transformers import AutoTokenizer
from onnxruntime_extensions import OrtPyFunction, gen_processing_models

REPO = "onnx-community/multilingual-MiniLMv2-L6-mnli-xnli-ONNX"
REV = "ca5daf3d11b6c4b3143b1f4602a2edfb64c3ad7e"
OUT = "/tmp/nli_tokenizer.onnx"

tokenizer = AutoTokenizer.from_pretrained(REPO, revision=REV)
print("tokenizer", tokenizer.__class__.__name__, "max", tokenizer.model_max_length)

premise = "Nu avea voie să părăsească orașul fără permisiune."
hypothesis = "restricție de deplasare"
packed = premise + " </s></s> " + hypothesis

pair = tokenizer(premise, hypothesis, add_special_tokens=True, truncation=True, return_tensors="np")
single = tokenizer(packed, add_special_tokens=True, truncation=True, return_tensors="np")
print("pair ids", pair["input_ids"].tolist())
print("packed ids", single["input_ids"].tolist())
print("PAIR_PACK_EQ", np.array_equal(pair["input_ids"], single["input_ids"]))
print("PAIR_MASK_EQ", np.array_equal(pair["attention_mask"], single["attention_mask"]))

pre, _ = gen_processing_models(tokenizer, pre_kwargs={}, opset=17)
onnx.save(pre, OUT)
print("PRE_INPUTS", [(x.name, [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim]) for x in pre.graph.input])
print("PRE_OUTPUTS", [(x.name, x.type.tensor_type.elem_type, [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim]) for x in pre.graph.output])
print("PRE_NODES", [(n.op_type, n.domain) for n in pre.graph.node])

fn = OrtPyFunction.from_model(pre)
result = fn(np.asarray([packed], dtype=object))
if not isinstance(result, tuple):
    result = (result,)
print("ORT_OUTPUT_COUNT", len(result))
for i, value in enumerate(result):
    arr = np.asarray(value)
    print("ORT_OUTPUT", i, arr.dtype, arr.shape, arr.tolist())

print("TOKENIZER_MODEL_BYTES", os.path.getsize(OUT))
