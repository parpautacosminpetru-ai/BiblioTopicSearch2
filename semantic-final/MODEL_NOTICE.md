# Semantic model notice

The semantic detector downloads `sentence-transformers/distiluse-base-multilingual-cased-v2`
at build time from its pinned Hugging Face revision. The model is Apache-2.0 licensed.

Runtime pipeline used by the app:

1. multilingual cased DistilBERT ONNX (`onnx/model_qint8_arm64.onnx`),
2. attention-mask mean pooling (`1_Pooling`),
3. 768→512 dense projection with Tanh (`2_Dense/model.safetensors`),
4. cosine similarity for extractive semantic retrieval.

The model is used only to score text similarity. The application does not ask the model to
generate explanations, labels, or interpretations. Compression labels are copied from OCR
evidence; semantic categories and topic nodes are accepted only behind conservative direct-match
gates and remain anchored to the OCR span shown to the user.

Model repository: `sentence-transformers/distiluse-base-multilingual-cased-v2`
Pinned revision: `acc85025f9078147bcc1ce55a4ddb9ec5e0d87cf`
