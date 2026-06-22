#!/usr/bin/env python3
"""
YOLOv8 轉換為 TFLite 格式腳本
將 yolov8n.pt 或 yolov8n.onnx 轉換為 yolov8n.tflite
"""

import os
import sys

def check_dependencies():
    """檢查必要的依賴是否安裝"""
    try:
        import ultralytics
        print("✅ ultralytics 已安裝")
        return True
    except ImportError:
        print("❌ ultralytics 未安裝")
        print("請運行: pip install ultralytics")
        return False

def convert_from_pt(pt_path, output_dir="app/src/main/assets"):
    """從 PyTorch 格式 (.pt) 轉換為 TFLite"""
    from ultralytics import YOLO
    
    print(f"📦 載入模型: {pt_path}")
    model = YOLO(pt_path)
    
    print("🔄 開始轉換為 TFLite 格式...")
    try:
        # 導出為 TFLite，使用 8-bit 量化以減小文件大小
        success = model.export(
            format='tflite',
            imgsz=640,        # YOLOv8 標準輸入尺寸
            int8=True,        # 使用 8-bit 量化（更小更快）
            data='coco.yaml'  # COCO 數據集（80類別）
        )
        
        if success:
            # 查找生成的 tflite 文件
            # Ultralytics 通常在 runs/export/ 目錄下生成文件
            import glob
            tflite_files = glob.glob("**/yolov8n*.tflite", recursive=True)
            
            if tflite_files:
                source_file = tflite_files[0]
                target_file = os.path.join(output_dir, "yolov8n.tflite")
                
                # 確保輸出目錄存在
                os.makedirs(output_dir, exist_ok=True)
                
                # 複製文件
                import shutil
                shutil.copy2(source_file, target_file)
                
                file_size = os.path.getsize(target_file) / (1024 * 1024)  # MB
                print(f"✅ 轉換成功！")
                print(f"📁 文件位置: {target_file}")
                print(f"📊 文件大小: {file_size:.2f} MB")
                
                if file_size < 1:
                    print("⚠️  警告：文件大小過小，可能轉換失敗")
                elif file_size > 10:
                    print("⚠️  警告：文件大小過大，可能不是量化版本")
                else:
                    print("✅ 文件大小正常（預期 4-6 MB）")
                
                return True
            else:
                print("❌ 找不到生成的 tflite 文件")
                return False
        else:
            print("❌ 轉換失敗")
            return False
            
    except Exception as e:
        print(f"❌ 轉換過程中出錯: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """主函數"""
    print("=" * 60)
    print("YOLOv8 轉 TFLite 轉換工具")
    print("=" * 60)
    
    # 檢查依賴
    if not check_dependencies():
        sys.exit(1)
    
    # 查找模型文件
    assets_dir = "app/src/main/assets"
    pt_file = os.path.join(assets_dir, "yolov8n.pt")
    onnx_file = os.path.join(assets_dir, "yolov8n.onnx")
    output_file = os.path.join(assets_dir, "yolov8n.tflite")
    
    # 檢查輸出文件是否存在且正常
    if os.path.exists(output_file):
        file_size = os.path.getsize(output_file)
        if file_size > 1024 * 1024:  # 大於 1MB
            print(f"✅ 目標文件已存在且大小正常 ({file_size / (1024*1024):.2f} MB)")
            response = input("是否要重新轉換？(y/n): ").strip().lower()
            if response != 'y':
                print("取消轉換")
                return
        else:
            print(f"⚠️  現有文件損壞 ({file_size} 字節)，將重新轉換")
    
    # 優先使用 .pt 文件（更可靠）
    if os.path.exists(pt_file):
        print(f"\n📂 找到 PyTorch 模型: {pt_file}")
        if convert_from_pt(pt_file, assets_dir):
            print("\n✅ 轉換完成！")
            print(f"\n📋 下一步：")
            print(f"   1. 檢查文件: ls -lh {output_file}")
            print(f"   2. 重新編譯: ./gradlew assembleDebug")
            print(f"   3. 運行應用測試 YOLO 檢測")
            return
    
    # 如果沒有 .pt 文件，提示下載
    print("\n❌ 未找到 yolov8n.pt 文件")
    print("\n📥 請先下載模型文件：")
    print("\n方法 1: 使用 Ultralytics Python API（自動下載）")
    print("```python")
    print("from ultralytics import YOLO")
    print("model = YOLO('yolov8n.pt')  # 自動下載")
    print("```")
    print("\n方法 2: 手動下載")
    print("訪問: https://github.com/ultralytics/assets/releases")
    print("下載: yolov8n.pt")
    print(f"放置到: {pt_file}")
    
    if os.path.exists(onnx_file):
        print(f"\n💡 提示：發現 {onnx_file}")
        print("   可以嘗試使用 onnx2tf 工具轉換（需要額外配置）")
        print("   或使用在線轉換工具")

if __name__ == "__main__":
    main()

