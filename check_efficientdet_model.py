#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
檢查 TensorFlow Lite 模型是否為 EfficientDet-Lite1
"""

import sys
import os

try:
    import tensorflow as tf
    print(f"TensorFlow 版本: {tf.__version__}")
except ImportError:
    print("錯誤: 未安裝 TensorFlow")
    print("請運行: pip install tensorflow")
    sys.exit(1)

model_path = r"C:\Users\charl\Downloads\efficientdet-tflite-lite1-detection-metadata-v1\1.tflite"

if not os.path.exists(model_path):
    print(f"錯誤: 文件不存在: {model_path}")
    sys.exit(1)

print(f"\n檢查模型文件: {model_path}")
file_size = os.path.getsize(model_path)
print(f"文件大小: {file_size / (1024*1024):.2f} MB")

try:
    # 載入模型
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    
    print("\n" + "="*60)
    print("模型輸入張量:")
    print("="*60)
    input_details = interpreter.get_input_details()
    for i, detail in enumerate(input_details):
        print(f"\n輸入 [{i}]:")
        print(f"  名稱: {detail['name']}")
        print(f"  形狀: {detail['shape']}")
        print(f"  數據類型: {detail['dtype']}")
        print(f"  量化參數: {detail.get('quantization_parameters', {})}")
    
    print("\n" + "="*60)
    print("模型輸出張量:")
    print("="*60)
    output_details = interpreter.get_output_details()
    for i, detail in enumerate(output_details):
        print(f"\n輸出 [{i}]:")
        print(f"  名稱: {detail['name']}")
        print(f"  形狀: {detail['shape']}")
        print(f"  數據類型: {detail['dtype']}")
        print(f"  量化參數: {detail.get('quantization_parameters', {})}")
    
    # 檢查模型元數據
    print("\n" + "="*60)
    print("模型特徵分析:")
    print("="*60)
    
    # 檢查輸入形狀（EfficientDet-Lite1 通常是 320x320 或 384x384）
    input_shape = input_details[0]['shape']
    if len(input_shape) == 4:
        height, width = input_shape[1], input_shape[2]
        print(f"輸入圖像尺寸: {width}x{height}")
        
        # EfficientDet-Lite1 常見尺寸
        efficientdet_sizes = [320, 384, 512, 640]
        if height in efficientdet_sizes and width in efficientdet_sizes:
            print(f"[OK] 尺寸符合 EfficientDet-Lite1 規格 ({height}x{width})")
        else:
            print(f"[WARN] 尺寸不符合常見的 EfficientDet-Lite1 規格")
    
    # 檢查輸出數量（EfficientDet 通常有 4 個輸出：boxes, scores, classes, num_detections）
    print(f"\n輸出張量數量: {len(output_details)}")
    if len(output_details) >= 3:
        print("[OK] 輸出數量符合目標檢測模型（通常有 boxes, scores, classes）")
    else:
        print("[WARN] 輸出數量較少，可能不是標準的目標檢測模型")
    
    # 檢查輸出名稱
    output_names = [detail['name'] for detail in output_details]
    print(f"\n輸出張量名稱: {output_names}")
    
    # EfficientDet 常見的輸出名稱模式
    efficientdet_keywords = ['boxes', 'scores', 'classes', 'detections', 'num_detections']
    found_keywords = [kw for kw in efficientdet_keywords if any(kw in name.lower() for name in output_names)]
    if found_keywords:
        print(f"[OK] 找到 EfficientDet 相關關鍵字: {found_keywords}")
    else:
        print("[WARN] 未找到典型的 EfficientDet 輸出關鍵字")
    
    # 檢查輸出形狀
    print("\n輸出張量形狀分析:")
    for i, detail in enumerate(output_details):
        shape = detail['shape']
        name = detail['name']
        print(f"  {name}: {shape}")
        
        # 檢查是否像檢測框（通常是 [1, N, 4]）
        if len(shape) == 3 and shape[0] == 1 and shape[2] == 4:
            print(f"    → 可能是邊界框 (boxes): [batch, num_detections, 4]")
        # 檢查是否像分數（通常是 [1, N]）
        elif len(shape) == 2 and shape[0] == 1:
            print(f"    → 可能是置信度分數 (scores) 或類別 (classes): [batch, num_detections]")
        # 檢查是否像檢測數量（通常是 [1]）
        elif len(shape) == 1 and shape[0] == 1:
            print(f"    → 可能是檢測數量 (num_detections): [1]")
    
    print("\n" + "="*60)
    print("結論:")
    print("="*60)
    
    # 綜合判斷
    is_likely_efficientdet = True
    reasons = []
    
    if len(input_details) == 1 and len(input_shape) == 4:
        if input_shape[1] in [320, 384, 512, 640] and input_shape[2] in [320, 384, 512, 640]:
            reasons.append("[OK] 輸入尺寸符合 EfficientDet-Lite1")
        else:
            is_likely_efficientdet = False
            reasons.append("[FAIL] 輸入尺寸不符合 EfficientDet-Lite1")
    
    if len(output_details) >= 3:
        reasons.append("[OK] 輸出張量數量符合目標檢測模型")
    else:
        is_likely_efficientdet = False
        reasons.append("[FAIL] 輸出張量數量不足")
    
    if found_keywords:
        reasons.append("[OK] 輸出名稱包含目標檢測關鍵字")
    else:
        reasons.append("[WARN] 輸出名稱未包含典型關鍵字（可能是命名不同）")
    
    for reason in reasons:
        print(reason)
    
    if is_likely_efficientdet:
        print("\n[結論] 這個模型很可能是 EfficientDet-Lite1 或類似的目標檢測模型")
    else:
        print("\n[結論] 這個模型可能不是標準的 EfficientDet-Lite1，但可能是其他目標檢測模型")
    
    print("\n" + "="*60)
    print("建議:")
    print("="*60)
    print("1. 檢查模型來源和文檔")
    print("2. 測試模型是否能正確檢測物體")
    print("3. 驗證輸出格式是否符合預期")
    
except Exception as e:
    print(f"\n錯誤: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

