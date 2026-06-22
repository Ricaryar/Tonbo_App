# Java文件與用例圖對應關係
## Java Files to Use Cases Mapping

---

## 📋 整體對應表

### 圖1: 整體系統用例圖 (Overall System Use Case Diagram)

#### 核心功能組

| Use Case | Java Files | 說明 |
|----------|-----------|------|
| **Environment Recognition** | `RealAIDetectionActivity.java`<br>`ObjectDetectorHelper.java`<br>`YoloDetector.java`<br>`MLKitObjectDetector.java`<br>`DetectionOverlayView.java`<br>`OptimizedDetectionOverlayView.java`<br>`NightModeOptimizer.java`<br>`ColorLightingAnalyzer.java`<br>`DetectionPerformanceMonitor.java`<br>`SpatialDescriptionGenerator.java` | 環境識別相關的所有類 |
| **Document & Currency Reading** | `DocumentCurrencyActivity.java`<br>`OCRHelper.java`<br>`CurrencyDetector.java` | 文件和貨幣閱讀功能 |
| **Emergency Assistance** | `EmergencyManager.java`<br>`MainActivity.java` (緊急按鈕部分) | 緊急求助功能 |
| **Voice Command** | `VoiceCommandActivity.java`<br>`VoiceCommandManager.java`<br>`VoiceCommandBuilder.java`<br>`StreamingVoiceAI.java`<br>`ConversationManager.java`<br>`ConversationResponseGenerator.java`<br>`LLMClient.java`<br>`LLMConfig.java`<br>`ASRManager.java`<br>`SherpaOnnxASR.java`<br>`FunASRASR.java` | 語音命令和對話功能 |

#### 輔助功能組

| Use Case | Java Files | 說明 |
|----------|-----------|------|
| **Find Items** | `FindItemsActivity.java`<br>`ObjectSearchManager.java` | 尋找物品功能 |
| **Travel Assistant** | `TravelAssistantActivity.java` | 出行協助功能 |
| **Instant Assistance** | `InstantAssistanceActivity.java`<br>`VideoCallActivity.java` | 即時協助功能 |
| **Gesture Management** | `GestureManagementActivity.java`<br>`GestureInputActivity.java`<br>`GestureRecognitionManager.java`<br>`GestureDrawView.java` | 手勢管理功能 |

#### 系統功能組

| Use Case | Java Files | 說明 |
|----------|-----------|------|
| **User Login/Register** | `LoginActivity.java`<br>`UserManager.java` | 用戶登入和註冊 |
| **System Settings** | `SettingsActivity.java` | 系統設定 |
| **Language Switch** | `LocaleManager.java`<br>`MainActivity.java` (語言切換部分) | 語言切換功能 |
| **Main Navigation** | `MainActivity.java`<br>`SplashActivity.java`<br>`FunctionAdapter.java`<br>`FunctionListFragment.java`<br>`FunctionPagerAdapter.java`<br>`HomeFunction.java` | 主頁面和導航 |

#### 基礎架構類

| Class | 用途 | 相關Use Cases |
|-------|------|-------------|
| `BaseAccessibleActivity.java` | 所有Activity的基類 | 所有Use Cases |
| `TTSManager.java` | 語音合成管理 | 所有需要語音播報的Use Cases |
| `VibrationManager.java` | 震動反饋管理 | 所有需要震動反饋的Use Cases |
| `DynamicGuidanceManager.java` | 動態引導管理 | 所有需要引導的Use Cases |
| `AppConstants.java` | 應用常量 | 所有Use Cases |

---

### 圖2: 核心功能用例圖 (Core Features Use Case Diagram)

#### Environment Recognition (環境識別)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Detect Objects** | `RealAIDetectionActivity.java`<br>`ObjectDetectorHelper.java`<br>`YoloDetector.java`<br>`MLKitObjectDetector.java` | 物體檢測核心功能 |
| **Get Position Description** | `SpatialDescriptionGenerator.java`<br>`RealAIDetectionActivity.java` | 位置描述生成 |
| **Analyze Color & Lighting** | `ColorLightingAnalyzer.java`<br>`RealAIDetectionActivity.java` | 顏色和光線分析 |
| **Night Mode Activation** | `NightModeOptimizer.java`<br>`RealAIDetectionActivity.java` | 夜間模式優化 |
| **Display Detection Results** | `DetectionOverlayView.java`<br>`OptimizedDetectionOverlayView.java` | 檢測結果顯示 |
| **Monitor Performance** | `DetectionPerformanceMonitor.java`<br>`RealAIDetectionActivity.java` | 性能監控 |

#### Document & Currency Reading (文件與貨幣閱讀)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Read Document** | `DocumentCurrencyActivity.java`<br>`OCRHelper.java` | OCR文字識別 |
| **Recognize Currency** | `DocumentCurrencyActivity.java`<br>`CurrencyDetector.java` | 港幣識別 |
| **Read Text Aloud** | `DocumentCurrencyActivity.java`<br>`TTSManager.java` | 語音朗讀 |

#### Emergency Assistance (緊急求助)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Trigger Emergency Call** | `EmergencyManager.java`<br>`MainActivity.java` | 觸發緊急電話 |
| **Long Press Activation** | `MainActivity.java` (緊急按鈕處理) | 長按激活機制 |

#### Voice Command (語音命令)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Speak Command** | `VoiceCommandActivity.java`<br>`VoiceCommandManager.java`<br>`VoiceCommandBuilder.java`<br>`ASRManager.java` | 語音識別和命令處理 |
| **Continuous Conversation** | `StreamingVoiceAI.java`<br>`ConversationManager.java` | 連續對話模式 |
| **LLM Interaction** | `LLMClient.java`<br>`LLMConfig.java`<br>`ConversationResponseGenerator.java` | LLM智能對話 |

---

### 圖3: 緊急求助用例圖 (Emergency Assistance Use Case Diagram)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Long Press Emergency Button** | `MainActivity.java`<br>`AppConstants.java` (定義長按時間) | 長按按鈕檢測 |
| **Countdown Feedback** | `MainActivity.java`<br>`TTSManager.java` | 倒計時語音反饋 |
| **Dial Emergency Service** | `EmergencyManager.java` | 撥打999 |
| **Voice Confirmation** | `EmergencyManager.java`<br>`TTSManager.java` | 語音確認 |
| **Vibration Alert** | `EmergencyManager.java`<br>`VibrationManager.java` | 震動提醒 |
| **Handle Permission** | `EmergencyManager.java` | 權限處理 |

---

### 圖4: 用戶系統用例圖 (User System Use Case Diagram)

#### 用戶管理

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **User Login** | `LoginActivity.java`<br>`UserManager.java` | 用戶登入 |
| **User Register** | `LoginActivity.java`<br>`UserManager.java` | 用戶註冊 |
| **Guest Mode** | `LoginActivity.java`<br>`UserManager.java` | 訪客模式 |
| **User Logout** | `UserManager.java` | 用戶登出 |

#### 系統設定

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Adjust Speech Parameters** | `SettingsActivity.java`<br>`TTSManager.java` | 調整語音參數 |
| **Configure Accessibility** | `SettingsActivity.java`<br>`VibrationManager.java` | 配置無障礙設定 |
| **Switch Language** | `SettingsActivity.java`<br>`LocaleManager.java`<br>`MainActivity.java` | 切換語言 |
| **Reset Settings** | `SettingsActivity.java` | 重置設定 |

---

### 圖5: 輔助功能用例圖 (Auxiliary Features Use Case Diagram)

#### Find Items (尋找物品)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Mark Item** | `FindItemsActivity.java`<br>`ObjectSearchManager.java` | 標記物品 |
| **Find Marked Item** | `FindItemsActivity.java`<br>`ObjectSearchManager.java` | 尋找標記物品 |
| **Save Item Location** | `FindItemsActivity.java`<br>`ObjectSearchManager.java` | 保存物品位置 |

#### Travel Assistant (出行協助)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Get Navigation** | `TravelAssistantActivity.java` | 獲取導航 |
| **Check Traffic Info** | `TravelAssistantActivity.java` | 查詢交通信息 |
| **Get Weather Update** | `TravelAssistantActivity.java` | 獲取天氣更新 |
| **Share Emergency Location** | `TravelAssistantActivity.java` | 分享緊急位置 |

#### Instant Assistance (即時協助)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Call Volunteer** | `InstantAssistanceActivity.java` | 呼叫志願者 |
| **Send Quick Message** | `InstantAssistanceActivity.java` | 發送快速訊息 |
| **Video Call** | `InstantAssistanceActivity.java`<br>`VideoCallActivity.java` | 視訊通話 |

#### Gesture Management (手勢管理)

| Use Case | Java Files | 詳細說明 |
|----------|-----------|---------|
| **Create Custom Gesture** | `GestureManagementActivity.java`<br>`GestureInputActivity.java`<br>`GestureDrawView.java` | 創建自定義手勢 |
| **Bind Function to Gesture** | `GestureManagementActivity.java` | 綁定功能到手勢 |
| **Recognize Gesture** | `GestureRecognitionManager.java`<br>`GestureManagementActivity.java` | 識別手勢 |

---

## 🔗 類別關係圖

### 核心管理器類 (Core Managers)

```
TTSManager (語音合成)
├── 所有需要語音播報的Activity
└── 所有Use Cases

VibrationManager (震動反饋)
├── 所有需要震動的Activity
└── 所有Use Cases

LocaleManager (語言管理)
├── 所有Activity
└── 所有Use Cases

UserManager (用戶管理)
├── LoginActivity
└── User System Use Cases

EmergencyManager (緊急管理)
├── MainActivity
└── Emergency Assistance Use Cases

ConversationManager (對話管理)
├── VoiceCommandActivity
├── StreamingVoiceAI
└── Voice Command Use Cases
```

### AI/ML 檢測器類 (AI/ML Detectors)

```
ObjectDetectorHelper (物體檢測)
├── RealAIDetectionActivity
└── Environment Recognition Use Cases

YoloDetector (YOLO檢測器)
├── ObjectDetectorHelper
└── Environment Recognition Use Cases

MLKitObjectDetector (ML Kit檢測器)
├── ObjectDetectorHelper
└── Environment Recognition Use Cases

OCRHelper (OCR助手)
├── DocumentCurrencyActivity
└── Document Reading Use Cases

CurrencyDetector (貨幣檢測器)
├── DocumentCurrencyActivity
└── Currency Reading Use Cases

NightModeOptimizer (夜間模式)
├── RealAIDetectionActivity
└── Environment Recognition Use Cases

ColorLightingAnalyzer (顏色光線分析)
├── RealAIDetectionActivity
└── Environment Recognition Use Cases
```

### 語音處理類 (Voice Processing)

```
VoiceCommandManager (語音命令管理)
├── VoiceCommandActivity
└── Voice Command Use Cases

VoiceCommandBuilder (語音命令構建)
├── VoiceCommandManager
└── Voice Command Use Cases

ASRManager (語音識別管理)
├── VoiceCommandActivity
├── StreamingVoiceAI
└── Voice Command Use Cases

SherpaOnnxASR (Sherpa語音識別)
├── ASRManager
└── Voice Command Use Cases

FunASRASR (FunASR語音識別)
├── ASRManager
└── Voice Command Use Cases

StreamingVoiceAI (流式語音AI)
├── VoiceCommandActivity
└── Continuous Conversation Use Cases

LLMClient (LLM客戶端)
├── ConversationResponseGenerator
└── LLM Interaction Use Cases

LLMConfig (LLM配置)
├── LLMClient
└── LLM Interaction Use Cases
```

---

## 📊 用例圖與Java文件對應總結

### 圖1: 整體系統用例圖
**包含文件**: 所有Activity和Manager類（約50+個文件）

### 圖2: 核心功能用例圖
**主要文件**:
- `RealAIDetectionActivity.java`
- `ObjectDetectorHelper.java`
- `DocumentCurrencyActivity.java`
- `OCRHelper.java`
- `CurrencyDetector.java`
- `EmergencyManager.java`
- `VoiceCommandActivity.java`
- `VoiceCommandManager.java`
- `LLMClient.java`
- 相關的檢測器和分析器類

### 圖3: 緊急求助用例圖
**主要文件**:
- `EmergencyManager.java`
- `MainActivity.java` (緊急按鈕部分)
- `TTSManager.java`
- `VibrationManager.java`

### 圖4: 用戶系統用例圖
**主要文件**:
- `LoginActivity.java`
- `UserManager.java`
- `SettingsActivity.java`
- `LocaleManager.java`

### 圖5: 輔助功能用例圖
**主要文件**:
- `FindItemsActivity.java`
- `TravelAssistantActivity.java`
- `InstantAssistanceActivity.java`
- `GestureManagementActivity.java`
- 相關的管理器類

---

## 💡 繪製用例圖時的建議

### 1. 在用例圖中標註Java類
- 可以在Use Case下方用小字體標註主要實現類
- 例如: "Environment Recognition" 下方標註 `RealAIDetectionActivity`

### 2. 分組顯示
- 使用Package或分區來組織相關的Use Cases
- 每個Package對應一個功能模塊

### 3. 關係標註
- Include關係: 標註主要的Helper類
- Extend關係: 標註可選的優化類（如NightModeOptimizer）

---

**最後更新**: 2025年1月
