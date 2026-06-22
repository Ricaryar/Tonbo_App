#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试你训练好的 Tonbo 模型 — 训练完先跑这个验证效果

用法:
  python test_model.py --lora-dir output/tonbo_lora
  python test_model.py --merged-dir output/tonbo_merged
"""

import argparse
import json
import sys
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig


DEFAULT_BASE = "Qwen/Qwen2.5-0.5B-Instruct"

TEST_PROMPTS = [
    ("cantonese", "你好"),
    ("cantonese", "前面有咩障礙物"),
    ("cantonese", "我唔舒服，需要幫手"),
    ("cantonese", "香港有咩好去處"),
    ("mandarin", "帮我读一下这张纸"),
    ("mandarin", "我想找厕所"),
    ("english", "I need emergency help"),
    ("english", "What can you do"),
]


SYSTEM_PROMPTS = {
    "cantonese": "你是一個友善的語音助手，專為視障人士設計。請用廣東話回應，回答要簡潔自然，適合語音播報。",
    "mandarin": "你是一個友善的語音助手，專為視障人士設計。請用普通話回應，回答要簡潔自然，適合語音播報。",
    "english": "You are a friendly voice assistant designed for visually impaired users. Please respond in English, keep answers concise and natural, suitable for voice broadcast.",
}


def load_model(lora_dir: Path = None, merged_dir: Path = None):
    if merged_dir:
        print(f"Loading merged model: {merged_dir}")
        tokenizer = AutoTokenizer.from_pretrained(str(merged_dir), trust_remote_code=True)
        model = AutoModelForCausalLM.from_pretrained(
            str(merged_dir),
            torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32,
            device_map="auto" if torch.cuda.is_available() else None,
            trust_remote_code=True,
        )
    else:
        meta_path = lora_dir / "training_meta.json"
        base_model = DEFAULT_BASE
        if meta_path.exists():
            with open(meta_path, encoding="utf-8") as f:
                base_model = json.load(f).get("base_model", DEFAULT_BASE)

        print(f"Loading base: {base_model}")
        print(f"Loading LoRA: {lora_dir}")
        tokenizer = AutoTokenizer.from_pretrained(str(lora_dir), trust_remote_code=True)

        if torch.cuda.is_available():
            bnb = BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_compute_dtype=torch.float16)
            base = AutoModelForCausalLM.from_pretrained(
                base_model, quantization_config=bnb, device_map="auto", trust_remote_code=True
            )
        else:
            base = AutoModelForCausalLM.from_pretrained(base_model, trust_remote_code=True)

        model = PeftModel.from_pretrained(base, str(lora_dir))

    model.eval()
    return model, tokenizer


def generate(model, tokenizer, lang: str, user_input: str) -> str:
    messages = [
        {"role": "system", "content": SYSTEM_PROMPTS[lang]},
        {"role": "user", "content": user_input},
    ]
    text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    inputs = tokenizer(text, return_tensors="pt")
    if torch.cuda.is_available():
        inputs = {k: v.cuda() for k, v in inputs.items()}

    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=150,
            temperature=0.7,
            top_p=0.9,
            do_sample=True,
            pad_token_id=tokenizer.eos_token_id,
        )

    response = tokenizer.decode(outputs[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True)
    return response.strip()


def main():
    parser = argparse.ArgumentParser(description="Test trained Tonbo model")
    parser.add_argument("--lora-dir", default=None)
    parser.add_argument("--merged-dir", default=None)
    parser.add_argument("--prompt", default=None, help="Custom test prompt")
    parser.add_argument("--lang", default="cantonese", choices=["cantonese", "mandarin", "english"])
    args = parser.parse_args()

    if not args.lora_dir and not args.merged_dir:
        print("Specify --lora-dir or --merged-dir")
        sys.exit(1)

    model, tokenizer = load_model(
        lora_dir=Path(args.lora_dir) if args.lora_dir else None,
        merged_dir=Path(args.merged_dir) if args.merged_dir else None,
    )

    print("\n" + "=" * 50)
    print("Tonbo Model Test")
    print("=" * 50)

    if args.prompt:
        tests = [(args.lang, args.prompt)]
    else:
        tests = TEST_PROMPTS

    for lang, prompt in tests:
        response = generate(model, tokenizer, lang, prompt)
        print(f"\n[{lang}] User: {prompt}")
        print(f"       Assistant: {response}")

    print("\n" + "=" * 50)
    print("Test complete! If responses look good, run:")
    print("  python export_to_gguf.py --lora-dir output/tonbo_lora")


if __name__ == "__main__":
    main()
