#!/usr/bin/env python3

# Copyright (c)  2023  Xiaomi Corporation
# Author: Fangjun Kuang

from typing import Dict

import numpy as np
import onnx

import yaml


def load_cmvn():
    neg_mean = None
    inv_stddev = None

    with open("am.mvn") as f:
        for line in f:
            if not line.startswith("<LearnRateCoef>"):
                continue
            t = line.split()[3:-1]

            if neg_mean is None:
                neg_mean = ",".join(t)
            else:
                inv_stddev = ",".join(t)

    return neg_mean, inv_stddev


def get_vocab_size():
    with open("tokens.txt") as f:
        return len(f.readlines())


def add_meta_data(filename: str, meta_data: Dict[str, str]):
    """Add meta data to an ONNX model. It is changed in-place.

    Args:
      filename:
        Filename of the ONNX model to be changed.
      meta_data:
        Key-value pairs.
    """
    model = onnx.load(filename)
    for key, value in meta_data.items():
        meta = model.metadata_props.add()
        meta.key = key
        meta.value = str(value)

    onnx.save(model, filename)
    print(f"Updated {filename}")


def main():
    with open("config.yaml", "r") as stream:
        config = yaml.safe_load(stream)
    lfr_window_size = config["frontend_conf"]["lfr_m"]
    lfr_window_shift = config["frontend_conf"]["lfr_n"]
    encoder_output_size = config["encoder_conf"]["output_size"]
    decoder_num_blocks = config["decoder_conf"]["num_blocks"]
    decoder_kernel_size = config["decoder_conf"]["kernel_size"]
    cif_threshold = config["predictor_conf"]["threshold"]
    tail_threshold = config["predictor_conf"]["tail_threshold"]

    neg_mean, inv_stddev = load_cmvn()
    vocab_size = get_vocab_size()

    meta_data = {
        "lfr_window_size": str(lfr_window_size),  # 7
        "lfr_window_shift": str(lfr_window_shift),  # 6
        "neg_mean": neg_mean,
        "inv_stddev": inv_stddev,
        "encoder_output_size": encoder_output_size,  # 512, i.e.,  fsmn_layers
        "decoder_num_blocks": decoder_num_blocks,  # 16, i.e., fsmn_layers
        "decoder_kernel_size": decoder_kernel_size,  # 11, i.e., fsmn_orders+1
        "model_type": "paraformer",
        "version": "1",
        "model_author": "dengcunqin",
        "maintainer": "k2-fsa",
        "vocab_size": str(vocab_size),  # 8404
        "comment": "speech_paraformer-large_asr_nat-zh-cantonese-en-16k-vocab8501-online",
        "url": "https://modelscope.cn/models/dengcunqin/speech_paraformer-large_asr_nat-zh-cantonese-en-16k-vocab8501-online/summary",
    }
    print(meta_data)
    add_meta_data("encoder.onnx", meta_data)
    add_meta_data("encoder.int8.onnx", meta_data)


if __name__ == "__main__":
    main()
