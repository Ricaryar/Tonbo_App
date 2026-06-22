#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
合并 LoRA 权重到基础模型，生成完整的 Tonbo 自定义模型

用法:
  python merge_lora.py --lora-dir output/tonbo_lora
"""

import argparse
import json
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer


DEFAULT_BASE = "Qwen/Qwen2.5-0.5B-Instruct"


def main():
    parser = argparse.ArgumentParser(description="Merge LoRA weights into base model")
    parser.add_argument("--lora-dir", default="output/tonbo_lora", help="LoRA output from finetune_lora.py")
    parser.add_argument("--base-model", default=None, help="Override base model")
    parser.add_argument("--output", default="output/tonbo_merged", help="Merged model output")
    args = parser.parse_args()

    lora_dir = Path(args.lora_dir)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 从训练元数据读取基础模型
    base_model = args.base_model
    if base_model is None:
        meta_path = lora_dir / "training_meta.json"
        if meta_path.exists():
            with open(meta_path, encoding="utf-8") as f:
                meta = json.load(f)
            base_model = meta.get("base_model", DEFAULT_BASE)
        else:
            base_model = DEFAULT_BASE

    print(f"[1/4] Base model: {base_model}")
    print(f"[2/4] LoRA weights: {lora_dir}")

    tokenizer = AutoTokenizer.from_pretrained(str(lora_dir), trust_remote_code=True)
    base = AutoModelForCausalLM.from_pretrained(
        base_model,
        torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32,
        device_map="auto" if torch.cuda.is_available() else None,
        trust_remote_code=True,
    )

    print("[3/4] Merging LoRA weights...")
    model = PeftModel.from_pretrained(base, str(lora_dir))
    model = model.merge_and_unload()

    print(f"[4/4] Saving merged model to {output_dir}")
    model.save_pretrained(str(output_dir), safe_serialization=True)
    tokenizer.save_pretrained(str(output_dir))

    merged_meta = {
        "model_name": "Tonbo-Assistant-0.5B-Merged",
        "base_model": base_model,
        "lora_source": str(lora_dir),
        "description": "Fully merged Tonbo custom offline LLM",
    }
    with open(output_dir / "model_meta.json", "w", encoding="utf-8") as f:
        json.dump(merged_meta, f, indent=2, ensure_ascii=False)

    print("\nMerge complete!")
    print(f"  python export_to_gguf.py --merged-dir {output_dir}")


if __name__ == "__main__":
    main()
