# Tonbo 离线 LLM 一键训练脚本
# 用法: .\run_training.ps1

$ErrorActionPreference = "Stop"
$env:PYTHONIOENCODING = "utf-8"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Tonbo 离线 LLM 自训练流水线" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 0: 检查 Python
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    Write-Host "ERROR: 找不到 Python，请先安装 Python 3.10+" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Python: $(python --version)" -ForegroundColor Green

# Step 1: 安装依赖
Write-Host ""
Write-Host "[Step 1/5] 安装训练依赖..." -ForegroundColor Yellow
pip install -r requirements.txt --quiet
Write-Host "[OK] 依赖安装完成" -ForegroundColor Green

# Step 2: 准备数据集
Write-Host ""
Write-Host "[Step 2/5] 生成训练数据集..." -ForegroundColor Yellow
python prepare_dataset.py
$sampleCount = (Get-Content "data\tonbo_assistant_train.jsonl" | Measure-Object -Line).Lines
Write-Host "[OK] 训练样本: $sampleCount 条" -ForegroundColor Green

# Step 3: 检查 GPU
Write-Host ""
Write-Host "[Step 3/5] 检查训练环境..." -ForegroundColor Yellow
$gpuInfo = python -c "import torch; print('CUDA' if torch.cuda.is_available() else 'CPU')" 2>&1
Write-Host "       计算设备: $gpuInfo" -ForegroundColor $(if ($gpuInfo -eq "CUDA") { "Green" } else { "Yellow" })

if ($gpuInfo -eq "CPU") {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "  你的电脑没有 NVIDIA GPU!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "本地 CPU 训练会非常慢 (可能数小时)。" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "推荐方案 (免费 GPU):" -ForegroundColor Green
    Write-Host "  1. 打开 Google Colab: https://colab.research.google.com" -ForegroundColor White
    Write-Host "  2. 上传 training/Tonbo_LLM_Colab.ipynb" -ForegroundColor White
    Write-Host "  3. 运行时 -> 更改运行时类型 -> T4 GPU" -ForegroundColor White
    Write-Host "  4. 运行所有单元格，训练约 10-20 分钟" -ForegroundColor White
    Write-Host "  5. 下载 output/tonbo_lora 文件夹到本地" -ForegroundColor White
    Write-Host "  6. 运行: python export_to_gguf.py --lora-dir output/tonbo_lora" -ForegroundColor White
    Write-Host ""
    $choice = Read-Host "仍要在 CPU 上训练吗? (y/N)"
    if ($choice -ne "y" -and $choice -ne "Y") {
        Write-Host "已取消。请使用 Colab 训练。" -ForegroundColor Yellow
        exit 0
    }
    $trainArgs = @("--cpu", "--epochs", "1", "--batch-size", "1")
} else {
    $trainArgs = @("--epochs", "5")
}

# Step 4: 训练
Write-Host ""
Write-Host "[Step 4/5] 开始 LoRA 微调训练..." -ForegroundColor Yellow
Write-Host "       这可能需要 10-60 分钟，请耐心等待" -ForegroundColor Gray
python finetune_lora.py @trainArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: 训练失败" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] 训练完成!" -ForegroundColor Green

# Step 5: 测试 + 导出
Write-Host ""
Write-Host "[Step 5/5] 测试模型并导出 GGUF..." -ForegroundColor Yellow
python test_model.py --lora-dir output/tonbo_lora
python export_to_gguf.py --lora-dir output/tonbo_lora

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  训练完成! 你的自定义模型已就绪" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "模型文件: output/android/tonbo_assistant_q4.gguf" -ForegroundColor White
Write-Host ""
Write-Host "部署到手机:" -ForegroundColor Yellow
Write-Host "  .\deploy_offline_model.ps1" -ForegroundColor White
Write-Host ""
