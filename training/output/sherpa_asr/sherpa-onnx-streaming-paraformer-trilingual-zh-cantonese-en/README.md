# Introduction

Files in this directory are from
https://modelscope.cn/models/dengcunqin/speech_paraformer-large_asr_nat-zh-cantonese-en-16k-vocab8501-online/summary

First, we use the following Python code (using FunASR v0.8.8)
```python
from funasr_onnx.paraformer_online_bin import Paraformer

model_dir = "dengcunqin/speech_paraformer-large_asr_nat-zh-cantonese-en-16k-vocab8501-online"
chunk_size = [5, 10, 5]

model = Paraformer(model_dir, batch_size=1, quantize=True, chunk_size=chunk_size, intra_op_num_threads=4) #, revision='v1.0.0') # only support batch_size = 1
```

to get the following files:

  - model.onnx
  - model_quant.onnx
  - decoder.onnx
  - decoder_quant.onnx

Then we rename the following three files:

  - model.onnx -> encoder.onnx
  - model_quant.onnx -> encoder.int8.onnx
  - decoder_quant.onnx -> decoder.int8.onnx

Then we run

```bash
./generate-tokens.py
```
to get `tokens.txt`.

Finally, we run

```bash
./add-model-metadata.py
```

to add metadata to `encoder.onnx` and `encoder.int8.onnx`.


Now you can use the generated files in sherpa-onnx.

