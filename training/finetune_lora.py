#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Tonbo 离线 LLM LoRA 微调 — 训练你自己的视障助手模型

推荐基础模型: Qwen/Qwen2.5-0.5B-Instruct (中文优秀, 约500M参数, 手机可部署)

用法:
  # 有 NVIDIA GPU (推荐, 8GB+ 显存)
  python finetune_lora.py

  # 无 GPU (很慢, 仅测试用)
  python finetune_lora.py --cpu --epochs 1 --batch-size 1

  # Google Colab 免费 GPU (最佳方案, 见 Tonbo_LLM_Colab.ipynb)
"""

import argparse
import json
import sys
from pathlib import Path

import torch
from datasets import Dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
)
from trl import SFTTrainer, DataCollatorForCompletionOnlyLM


# Qwen2.5-0.5B: 中文/粤语表现好, 训练快, 导出后约300MB (Q4量化)
DEFAULT_MODEL = "Qwen/Qwen2.5-0.5B-Instruct"
LORA_RANK = 16
LORA_ALPHA = 32
LORA_TARGET_MODULES = ["q_proj", "v_proj", "k_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]

# Qwen chat template 中 assistant 回复的起始标记
QWEN_RESPONSE_TEMPLATE = "<|im_start|>assistant\n"


def load_jsonl_dataset(path: str) -> Dataset:
    records = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    if not records:
        raise ValueError(f"No training data found in {path}")
    return Dataset.from_list(records)


def format_chat(example, tokenizer):
    messages = example["messages"]
    text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=False)
    return {"text": text}


def check_environment(use_cpu: bool):
    has_cuda = torch.cuda.is_available()
    print("=" * 50)
    print("Tonbo Offline LLM Training")
    print("=" * 50)
    print(f"PyTorch: {torch.__version__}")
    print(f"CUDA available: {has_cuda}")
    if has_cuda:
        print(f"GPU: {torch.cuda.get_device_name(0)}")
        mem = torch.cuda.get_device_properties(0).total_memory / 1e9
        print(f"VRAM: {mem:.1f} GB")
    elif not use_cpu:
        print("\nWARNING: No GPU detected!")
        print("Training on CPU will be VERY slow (hours).")
        print("Recommended: Use Google Colab free GPU -> open Tonbo_LLM_Colab.ipynb")
        print("Or run: python finetune_lora.py --cpu")
        sys.exit(1)
    print("=" * 50)


def main():
    parser = argparse.ArgumentParser(description="Tonbo LoRA fine-tuning")
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--data", default="data/tonbo_assistant_train.jsonl")
    parser.add_argument("--output", default="output/tonbo_lora")
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--lr", type=float, default=2e-4)
    parser.add_argument("--cpu", action="store_true", help="Force CPU training (very slow)")
    parser.add_argument("--lora-rank", type=int, default=LORA_RANK)
    args = parser.parse_args()

    check_environment(args.cpu)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"\n[1/6] Loading model: {args.model}")
    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    use_quant = not args.cpu and torch.cuda.is_available()
    if use_quant:
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.float16,
            bnb_4bit_use_double_quant=True,
        )
        model = AutoModelForCausalLM.from_pretrained(
            args.model,
            quantization_config=bnb_config,
            device_map="auto",
            trust_remote_code=True,
        )
        model = prepare_model_for_kbit_training(model)
    else:
        model = AutoModelForCausalLM.from_pretrained(
            args.model,
            torch_dtype=torch.float32,
            trust_remote_code=True,
        )
        if not args.cpu and torch.cuda.is_available():
            model = model.cuda()

    print(f"[2/6] Configuring LoRA (rank={args.lora_rank})")
    lora_config = LoraConfig(
        r=args.lora_rank,
        lora_alpha=LORA_ALPHA,
        target_modules=LORA_TARGET_MODULES,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    print(f"[3/6] Loading training data: {args.data}")
    dataset = load_jsonl_dataset(args.data)
    print(f"       Training samples: {len(dataset)}")
    dataset = dataset.map(lambda x: format_chat(x, tokenizer))

    collator = DataCollatorForCompletionOnlyLM(
        response_template=QWEN_RESPONSE_TEMPLATE,
        tokenizer=tokenizer,
    )

    print("[4/6] Starting training...")
    training_args = TrainingArguments(
        output_dir=str(output_dir / "checkpoints"),
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=4 if not args.cpu else 8,
        learning_rate=args.lr,
        logging_steps=5,
        save_strategy="epoch",
        fp16=use_quant,
        optim="paged_adamw_8bit" if use_quant else "adamw_torch",
        report_to="none",
        remove_unused_columns=False,
        warmup_ratio=0.1,
    )

    trainer = SFTTrainer(
        model=model,
        args=training_args,
        train_dataset=dataset,
        dataset_text_field="text",
        data_collator=collator,
        max_seq_length=512,
    )
    trainer.train()

    print(f"[5/6] Saving LoRA weights to {output_dir}")
    model.save_pretrained(str(output_dir))
    tokenizer.save_pretrained(str(output_dir))

    meta = {
        "model_name": "Tonbo-Assistant-0.5B",
        "base_model": args.model,
        "lora_rank": args.lora_rank,
        "lora_target_modules": LORA_TARGET_MODULES,
        "training_samples": len(dataset),
        "epochs": args.epochs,
        "description": "Custom offline LLM for Tonbo visually impaired assistant app",
    }
    with open(output_dir / "training_meta.json", "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)

    print("[6/6] Training complete!")
    print("\nNext steps:")
    print(f"  1. Test model:  python test_model.py --lora-dir {output_dir}")
    print(f"  2. Export GGUF:  python export_to_gguf.py --lora-dir {output_dir}")
    print(f"  3. Deploy:       .\\deploy_offline_model.ps1")


if __name__ == "__main__":
    main()
