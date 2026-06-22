#!/usr/bin/env python3
"""
将 YOLOv8n-cls 货币分类模型导出为 ONNX 并复制到 Android assets。

用法:
  python export_currency_cls.py --weights path/to/best.pt
  python export_currency_cls.py --weights path/to/best.pt --output ../app/src/main/assets/currency_cls.onnx
"""

import argparse
import shutil
from pathlib import Path

from ultralytics import YOLO


def main() -> None:
    parser = argparse.ArgumentParser(description="Export HKD currency classifier to ONNX")
    parser.add_argument(
        "--weights",
        required=True,
        help="Path to trained best.pt (YOLOv8n-cls)",
    )
    parser.add_argument(
        "--output",
        default=str(Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "currency_cls.onnx"),
        help="Destination ONNX path in the Android project",
    )
    parser.add_argument("--imgsz", type=int, default=224)
    args = parser.parse_args()

    weights = Path(args.weights).resolve()
    output = Path(args.output).resolve()

    if not weights.exists():
        raise FileNotFoundError(weights)

    model = YOLO(str(weights))
    print("Classes:", model.names)

    exported = model.export(format="onnx", imgsz=args.imgsz)
    exported_path = Path(exported).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(exported_path, output)
    size_mb = output.stat().st_size / (1024 * 1024)
    print(f"Exported to {output} ({size_mb:.2f} MB)")


if __name__ == "__main__":
    main()
