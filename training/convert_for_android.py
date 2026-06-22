#!/usr/bin/env python3
"""
将 LoRA 微调权重转换为 Android MediaPipe 可用格式

用法:
  # 方式一：使用预转换的 Gemma-3 1B 基础模型（推荐，无需 GPU 转换）
  python convert_for_android.py --download-base

  # 方式二：转换自定义 LoRA 权重（需要 GPU）
  python convert_for_android.py --lora-dir output/tonbo_lora --lora-rank 8

输出:
  output/android/
    gemma3_1b.task          # 基础模型（约 700MB）
    tonbo_lora.tflite       # LoRA 权重（可选，需 GPU 推理）
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path


# HuggingFace 上已预转换的 Gemma-3 1B 4-bit 模型（MediaPipe 兼容）
GEMMA3_1B_TASK_URL = (
    "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/"
    "gemma3-1b-it-int4.task"
)


def download_base_model(output_dir: Path) -> Path:
    """下载预转换的 Gemma-3 1B .task 模型"""
    try:
        from huggingface_hub import hf_hub_download
    except ImportError:
        print("请先安装: pip install huggingface_hub")
        sys.exit(1)

    output_dir.mkdir(parents=True, exist_ok=True)
    dest = output_dir / "gemma3_1b.task"

    if dest.exists():
        print(f"基础模型已存在: {dest}")
        return dest

    print("正在下载 Gemma-3 1B 预转换模型（约 700MB）...")
    downloaded = hf_hub_download(
        repo_id="litert-community/Gemma3-1B-IT",
        filename="gemma3-1b-it-int4.task",
        local_dir=str(output_dir),
    )
    # hf_hub_download 可能返回不同路径，统一重命名
    src = Path(downloaded)
    if src != dest:
        shutil.move(str(src), str(dest))
    print(f"✅ 基础模型已保存: {dest}")
    return dest


def convert_lora(lora_dir: Path, output_dir: Path, lora_rank: int, base_model: str):
    """使用 MediaPipe converter 将 LoRA 转为 TFLite"""
    try:
        from mediapipe.tasks.python.genai import converter
    except ImportError:
        print("请先安装: pip install mediapipe")
        sys.exit(1)

    lora_ckpt = lora_dir / "adapter_model.safetensors"
    if not lora_ckpt.exists():
        print(f"找不到 LoRA 权重: {lora_ckpt}")
        print("请先运行 finetune_lora.py 完成训练")
        sys.exit(1)

    output_dir.mkdir(parents=True, exist_ok=True)
    lora_output = output_dir / "tonbo_lora.tflite"

    print(f"正在转换 LoRA 权重 (rank={lora_rank})...")
    print("注意: LoRA 转换需要 GPU backend，且仅支持 Gemma-2 2B / Phi-2 等特定模型")

    config = converter.ConversionConfig(
        input_ckpt=base_model,
        ckpt_format="safetensors",
        model_type="GEMMA_2B",
        backend="gpu",
        output_dir=str(output_dir),
        lora_ckpt=str(lora_ckpt),
        lora_rank=lora_rank,
        lora_output_tflite_file=str(lora_output),
    )
    converter.convert_checkpoint(config)
    print(f"✅ LoRA 已转换: {lora_output}")


def create_deploy_instructions(output_dir: Path, has_lora: bool):
    """生成部署说明文件"""
    instructions = {
        "base_model": str(output_dir / "gemma3_1b.task"),
        "lora_model": str(output_dir / "tonbo_lora.tflite") if has_lora else None,
        "deploy_steps": [
            "1. 将 gemma3_1b.task 复制到手机:",
            "   adb push output/android/gemma3_1b.task /data/local/tmp/llm/",
            "",
            "2. 在 App 设置中启用「离线 LLM」模式",
            "",
            "3. 首次启动会自动将模型复制到应用内部存储",
        ],
        "device_requirements": {
            "ram": "4GB+ 推荐",
            "storage": "约 1GB 可用空间",
            "recommended_devices": ["Pixel 8", "Samsung S23+", "小米 14 等旗舰机"],
        },
    }
    with open(output_dir / "deploy.json", "w", encoding="utf-8") as f:
        json.dump(instructions, f, indent=2, ensure_ascii=False)
    print(f"✅ 部署说明已保存: {output_dir / 'deploy.json'}")


def main():
    parser = argparse.ArgumentParser(description="Convert model for Android deployment")
    parser.add_argument("--download-base", action="store_true", help="Download pre-converted Gemma-3 1B model")
    parser.add_argument("--lora-dir", default=None, help="LoRA output directory from finetune_lora.py")
    parser.add_argument("--lora-rank", type=int, default=8, help="LoRA rank used during training")
    parser.add_argument("--base-model", default="google/gemma-2-2b-it", help="Base model for LoRA conversion")
    parser.add_argument("--output", default="output/android", help="Android output directory")
    args = parser.parse_args()

    output_dir = Path(args.output)

    if args.download_base or not args.lora_dir:
        download_base_model(output_dir)

    has_lora = False
    if args.lora_dir:
        convert_lora(Path(args.lora_dir), output_dir, args.lora_rank, args.base_model)
        has_lora = True

    create_deploy_instructions(output_dir, has_lora)

    print("\n📱 部署到 Android 设备:")
    print(f"   adb shell mkdir -p /data/local/tmp/llm/")
    print(f"   adb push {output_dir}/gemma3_1b.task /data/local/tmp/llm/")
    if has_lora:
        print(f"   adb push {output_dir}/tonbo_lora.tflite /data/local/tmp/llm/")


if __name__ == "__main__":
    main()
