#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
导出自训练模型为 GGUF 格式 — 部署到 Android 手机

用法:
  python export_to_gguf.py --lora-dir output/tonbo_lora
  python export_to_gguf.py --merged-dir output/tonbo_merged

输出:
  output/android/tonbo_assistant_q4.gguf  (~300MB, 可直接部署到手机)
"""

import argparse
import json
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path


DEFAULT_BASE = "Qwen/Qwen2.5-0.5B-Instruct"
LLAMA_CPP_REPO = "https://github.com/ggml-org/llama.cpp"
CONVERT_SCRIPT = "convert_hf_to_gguf.py"
OUTPUT_GGUF = "tonbo_assistant_q4.gguf"


def ensure_llama_cpp_convert(work_dir: Path) -> Path:
    """确保 llama.cpp 转换脚本可用"""
    llama_dir = work_dir / "llama.cpp"
    convert_path = llama_dir / CONVERT_SCRIPT

    if convert_path.exists():
        return convert_path

    print("Downloading llama.cpp convert script...")
    llama_dir.mkdir(parents=True, exist_ok=True)

    # 只下载需要的转换脚本和依赖
    base_url = "https://raw.githubusercontent.com/ggml-org/llama.cpp/master/"
    files_to_download = [
        CONVERT_SCRIPT,
        "gguf-py/gguf/__init__.py",
        "gguf-py/gguf/constants.py",
        "gguf-py/gguf/gguf_writer.py",
        "gguf-py/gguf/lazy.py",
        "gguf-py/gguf/metadata.py",
        "gguf-py/gguf/quants.py",
        "gguf-py/gguf/tensor_mapping.py",
        "gguf-py/gguf/utility.py",
        "gguf-py/gguf/vocab.py",
    ]

    for rel_path in files_to_download:
        dest = llama_dir / rel_path
        dest.parent.mkdir(parents=True, exist_ok=True)
        url = base_url + rel_path
        try:
            urllib.request.urlretrieve(url, dest)
        except Exception as e:
            print(f"Failed to download {url}: {e}")
            print("\nAlternative: clone llama.cpp manually:")
            print(f"  git clone {LLAMA_CPP_REPO} {llama_dir}")
            sys.exit(1)

    return convert_path


def export_gguf(model_dir: Path, output_dir: Path, quantize: str = "q4_k_m"):
    """将 HuggingFace 模型导出为量化 GGUF"""
    output_dir.mkdir(parents=True, exist_ok=True)
    work_dir = output_dir.parent
    convert_script = ensure_llama_cpp_convert(work_dir)

    f16_path = output_dir / "tonbo_assistant_f16.gguf"
    final_path = output_dir / OUTPUT_GGUF

    print(f"[1/3] Converting HF model to GGUF F16...")
    print(f"       Model: {model_dir}")
    cmd = [
        sys.executable, str(convert_script),
        str(model_dir),
        "--outfile", str(f16_path),
        "--outtype", "f16",
    ]
    result = subprocess.run(cmd, cwd=str(convert_script.parent), capture_output=True, text=True)
    if result.returncode != 0:
        print("Convert failed:")
        print(result.stderr)
        sys.exit(1)
    print(f"       F16 GGUF: {f16_path} ({f16_path.stat().st_size / 1e6:.0f} MB)")

    # 量化到 Q4_K_M (手机最佳平衡)
    print(f"[2/3] Quantizing to {quantize.upper()}...")
    quantize_bin = work_dir / "llama.cpp" / "llama-quantize"
    if not quantize_bin.exists():
        # Windows 没有预编译 quantize，使用 F16 或尝试 pip 量化
        print("       llama-quantize not found, using F16 model (larger but works)")
        shutil.copy(f16_path, final_path)
    else:
        subprocess.run([str(quantize_bin), str(f16_path), str(final_path), quantize.upper()], check=True)

    size_mb = final_path.stat().st_size / 1e6
    print(f"[3/3] Final model: {final_path} ({size_mb:.0f} MB)")

    deploy_meta = {
        "model_file": OUTPUT_GGUF,
        "model_name": "Tonbo-Assistant-0.5B",
        "format": "GGUF",
        "quantization": quantize,
        "size_mb": round(size_mb, 1),
        "deploy_command": f"adb push {final_path} /data/local/tmp/llm/{OUTPUT_GGUF}",
    }
    with open(output_dir / "deploy.json", "w", encoding="utf-8") as f:
        json.dump(deploy_meta, f, indent=2, ensure_ascii=False)

    print("\nDeploy to phone:")
    print(f"  adb shell mkdir -p /data/local/tmp/llm/")
    print(f"  adb push {final_path} /data/local/tmp/llm/{OUTPUT_GGUF}")


def main():
    parser = argparse.ArgumentParser(description="Export Tonbo model to GGUF for Android")
    parser.add_argument("--lora-dir", default=None, help="LoRA dir (will merge first)")
    parser.add_argument("--merged-dir", default=None, help="Already merged model dir")
    parser.add_argument("--output", default="output/android")
    parser.add_argument("--quantize", default="q4_k_m")
    args = parser.parse_args()

    if args.merged_dir:
        model_dir = Path(args.merged_dir)
    elif args.lora_dir:
        print("Merging LoRA first...")
        from merge_lora import main as merge_main
        merged_dir = Path("output/tonbo_merged")
        import merge_lora
        # 直接调用 merge
        import importlib
        sys.argv = ["merge_lora.py", "--lora-dir", args.lora_dir]
        # 更简单：子进程
        subprocess.run([sys.executable, "merge_lora.py", "--lora-dir", args.lora_dir], check=True)
        model_dir = Path("output/tonbo_merged")
    else:
        print("Specify --lora-dir or --merged-dir")
        sys.exit(1)

    if not model_dir.exists():
        print(f"Model directory not found: {model_dir}")
        sys.exit(1)

    export_gguf(model_dir, Path(args.output), args.quantize)


if __name__ == "__main__":
    main()
