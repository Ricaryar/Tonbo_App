# LLM 智能語音對話集成指南

## 概述

應用支持 **三層對話回退機制**：

1. **離線 LLM**（優先）— 設備端 Gemma-3 1B 模型，無需網絡
2. **雲端 LLM**（備用）— 智譜 GLM-4-Flash API
3. **關鍵詞匹配**（兜底）— 本地模板回應

---

## 離線 LLM（推薦）

### 為什麼用 LoRA 微調而不是從零訓練？

從零訓練一個 LLM 需要數百萬美元算力和海量數據，不適合 FYP 項目。推薦方案是：

- 使用 **Gemma-3 1B** 作為基礎模型（已量化，約 700MB）
- 用 **LoRA 微調** 讓模型學會 Tonbo 視障助手領域的對話風格
- 通過 **MediaPipe** 在 Android 設備端推理

### 快速開始（不訓練，直接用基礎模型）

```powershell
# 1. 安裝 Python 依賴
cd training
pip install -r requirements.txt

# 2. 下載預轉換的 Gemma-3 1B 模型
python convert_for_android.py --download-base

# 3. 部署到 Android 設備
.\deploy_offline_model.ps1

# 4. 運行 App，查看 Logcat 標籤 OfflineLLMClient
```

### 自己訓練（LoRA 微調）

```powershell
cd training

# 1. 準備訓練數據（可擴充 data/tonbo_assistant_train.jsonl）
python prepare_dataset.py

# 2. LoRA 微調（需要 NVIDIA GPU，8GB+ 顯存）
python finetune_lora.py --data data/tonbo_assistant_train.jsonl --epochs 3

# 3. 轉換並部署
python convert_for_android.py --download-base --lora-dir output/tonbo_lora
.\deploy_offline_model.ps1
```

### 擴充訓練數據

編輯 `training/data/tonbo_assistant_train.jsonl`，每行一條 JSON：

```json
{"messages": [
  {"role": "system", "content": "你是一個友善的語音助手，專為視障人士設計。請用廣東話回應..."},
  {"role": "user", "content": "用戶說的話"},
  {"role": "assistant", "content": "期望的回覆"}
]}
```

建議收集 100+ 條真實對話樣本，涵蓋：環境識別、出行協助、緊急求助、文件識別、港幣識別等場景。

### 設備要求

| 項目 | 最低 | 推薦 |
|------|------|------|
| RAM | 3GB | 4GB+ |
| 存儲 | 1GB 可用 | 2GB+ |
| 設備 | 中端 Android | Pixel 8 / Samsung S23+ |

### 離線 LLM 架構

```
用戶語音 → ASR → ConversationResponseGenerator
                        ↓
              OfflineLLMClient (Gemma-3 1B, MediaPipe)
                        ↓ 失敗
              LLMClient (GLM-4-Flash, 雲端)
                        ↓ 失敗
              關鍵詞匹配
```

### 相關代碼

- `OfflineLLMClient.java` — 設備端推理
- `ConversationResponseGenerator.java` — 三層回退邏輯
- `training/finetune_lora.py` — LoRA 微調腳本
- `training/convert_for_android.py` — 模型轉換

---

## 雲端 LLM（備用）

## API Keys 配置

### DeepSeek
- **免費額度**: 每月 100 萬 tokens
- **模型**: `deepseek-chat`
- **配置**: API Key 需在 `LLMConfigLocal.java` 中配置（參考 `LLMConfigLocal.java.template`）

### GLM-4-Flash (備用)
- **狀態**: 完全免費
- **模型**: `glm-4-flash`
- **配置**: API Key 需在 `LLMConfigLocal.java` 中配置（參考 `LLMConfigLocal.java.template`）

**重要**: API Keys 不應提交到 Git。請使用 `LLMConfigLocal.java` 文件（已在 `.gitignore` 中排除）來存儲您的 API keys。

## 功能特點

- ✅ **智能對話**: 使用大模型生成自然、流暢的回應
- ✅ **對話記憶**: 支持多輪對話上下文理解
- ✅ **自動回退**: LLM 不可用時自動使用關鍵詞匹配
- ✅ **多語言支持**: 根據應用語言設置自動調整回應語言
- ✅ **零配置**: 只需配置 API keys 即可使用

## 使用方式

### 配置 API Keys

1. 複製模板文件：
   ```bash
   cp app/src/main/java/com/example/tonbo_app/LLMConfigLocal.java.template \
      app/src/main/java/com/example/tonbo_app/LLMConfigLocal.java
   ```

2. 編輯 `LLMConfigLocal.java`，填入您的 API keys：
   ```java
   public static final String DEEPSEEK_API_KEY = "your-deepseek-key-here";
   public static final String ZHIPU_API_KEY = "your-zhipu-key-here";
   ```

3. 保存文件（該文件已被 `.gitignore` 排除，不會被提交到 Git）

### 自動啟用

應用啟動時會自動：
1. 初始化 LLM 配置（默認使用 GLM-4-Flash）
2. 測試 API 連接
3. 啟用 LLM 功能

### 切換 LLM 提供商

在代碼中可以切換：

```java
LLMConfig config = new LLMConfig(context);

// 切換到 DeepSeek
config.useDeepSeek();

// 或切換到 GLM-4-Flash
config.useGLM4Flash();
```

### 禁用 LLM

如果需要禁用 LLM，使用關鍵詞匹配：

```java
LLMConfig config = new LLMConfig(context);
config.setEnabled(false);
```

## 工作流程

1. **用戶語音輸入** → ASR 識別為文本
2. **檢查 LLM 是否啟用** → 如果啟用，使用 LLM 生成回應
3. **LLM 生成回應** → 發送到 DeepSeek 或 GLM-4-Flash API
4. **處理回應** → 後處理並返回給用戶
5. **回退機制** → 如果 LLM 失敗，自動使用關鍵詞匹配

## 日誌檢查

運行應用後，查看 Logcat：

### 成功啟用
```
LLMConfig: Initialized with default GLM-4-Flash API key
MainActivity: LLM 已啟用，提供商: zhipu
MainActivity: ✅ LLM 連接測試成功
ConversationResponseGenerator: Using LLM to generate response
```

### 使用關鍵詞匹配（LLM 未啟用或失敗）
```
ConversationResponseGenerator: Using keyword matching to generate response
```

## API 使用情況

### DeepSeek
- **免費額度**: 每月 100 萬 tokens（約 75 萬中文字）
- **超出後**: 按量付費（價格便宜）
- **建議**: 個人使用足夠，無需擔心費用

### GLM-4-Flash
- **完全免費**: 無使用限制
- **建議**: 預算有限時使用（默認提供商）

## 故障排除

### 問題 1: LLM 連接失敗

**可能原因**:
- 網絡連接問題
- API key 無效或未配置
- API 服務器暫時不可用

**解決方案**:
- 應用會自動回退到關鍵詞匹配，不影響基本功能
- 檢查網絡連接
- 確認 `LLMConfigLocal.java` 中的 API keys 正確
- 查看 Logcat 獲取詳細錯誤信息

### 問題 2: 回應速度慢

**解決方案**:
- 這是正常的，LLM API 需要網絡請求
- 通常響應時間在 1-3 秒
- 可以切換到 GLM-4-Flash（可能更快）

### 問題 3: 回應質量不佳

**解決方案**:
- 嘗試切換到 DeepSeek（質量通常更好）
- 檢查對話歷史是否正確傳遞
- 確保語言設置正確

## 技術細節

### API 端點
- **DeepSeek**: `https://api.deepseek.com/v1/chat/completions`
- **GLM-4-Flash**: `https://open.bigmodel.cn/api/paas/v4/chat/completions`

### 請求格式
```json
{
  "model": "deepseek-chat",
  "messages": [
    {"role": "system", "content": "系統提示"},
    {"role": "user", "content": "用戶消息"},
    {"role": "assistant", "content": "助手回應"}
  ],
  "temperature": 0.7,
  "max_tokens": 1000
}
```

### 系統提示
根據當前語言自動生成：
- **廣東話**: "你是一個友善的語音助手，專為視障人士設計。請用廣東話回應..."
- **普通話**: "你是一個友善的語音助手，專為視障人士設計。請用普通話回應..."
- **英文**: "You are a friendly voice assistant designed for visually impaired users..."

## 安全說明

- **API keys 配置**: API keys 應配置在 `LLMConfigLocal.java` 中（該文件已被 `.gitignore` 排除，不會提交到 Git）
- **模板文件**: 參考 `LLMConfigLocal.java.template` 來創建您的本地配置文件
- **安全存儲**: 生產環境建議使用環境變量或安全存儲
- **隱私保護**: 對話內容會發送到 LLM 服務商（通常有隱私保護政策）

## 下一步

1. ✅ 配置 API keys（參考 `LLMConfigLocal.java.template`）
2. ✅ 運行應用測試語音對話
3. ✅ 查看 Logcat 確認 LLM 正常工作
4. ✅ 享受智能語音對話體驗！

如有問題，請查看 Logcat 日誌獲取詳細信息。

