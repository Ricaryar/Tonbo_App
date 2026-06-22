# Use Case Diagrams 組織建議
## Use Case Diagrams Organization Guide

---

## 📊 建議的用例圖分組

### 圖1: **整體系統用例圖 (Overall System Use Case Diagram)**
**包含所有主要功能和主要Actor**

#### Actors (參與者):
- **Visually Impaired User** (主要用戶)
- **Guest User** (訪客用戶)
- **System** (系統)

#### Use Cases (用例) 及對應Java文件:
- **核心功能組**:
  - Environment Recognition (環境識別)
    - `RealAIDetectionActivity.java`, `ObjectDetectorHelper.java`, `YoloDetector.java`
  - Document & Currency Reading (文件與貨幣閱讀)
    - `DocumentCurrencyActivity.java`, `OCRHelper.java`, `CurrencyDetector.java`
  - Emergency Assistance (緊急求助)
    - `EmergencyManager.java`, `MainActivity.java`
  - Voice Command (語音命令)
    - `VoiceCommandActivity.java`, `VoiceCommandManager.java`, `LLMClient.java`
  
- **輔助功能組**:
  - Find Items (尋找物品)
    - `FindItemsActivity.java`, `ObjectSearchManager.java`
  - Travel Assistant (出行協助)
    - `TravelAssistantActivity.java`
  - Instant Assistance (即時協助)
    - `InstantAssistanceActivity.java`, `VideoCallActivity.java`
  - Gesture Management (手勢管理)
    - `GestureManagementActivity.java`, `GestureRecognitionManager.java`
  
- **系統功能組**:
  - User Login/Register (用戶登入/註冊)
    - `LoginActivity.java`, `UserManager.java`
  - System Settings (系統設定)
    - `SettingsActivity.java`
  - Language Switch (語言切換)
    - `LocaleManager.java`, `MainActivity.java`

**用途**: 展示系統整體功能和用戶關係

---

### 圖2: **核心功能用例圖 (Core Features Use Case Diagram)**
**專注於4個核心功能**

#### Actors:
- **Visually Impaired User** (主要用戶)
- **System** (系統，用於自動化功能)

#### Use Cases 及對應Java文件:

##### 1. Environment Recognition (環境識別)

**主要用例**:
- **Detect Objects** (檢測物體)
  - 子用例: Load AI Model → `ObjectDetectorHelper.java`
  - 子用例: Process Camera Frame → `YoloDetector.java`, `MLKitObjectDetector.java`
  - 子用例: Run Model Inference → `ObjectDetectorHelper.java`
  - 子用例: Apply Multi-frame Fusion → `ObjectDetectorHelper.java`
  - 子用例: Filter Results → `ObjectDetectorHelper.java`
  - Java文件: `RealAIDetectionActivity.java`, `ObjectDetectorHelper.java`, `YoloDetector.java`

- **Get Position Description** (獲取位置描述)
  - 子用例: Calculate Object Position → `SpatialDescriptionGenerator.java`
  - 子用例: Generate Position Text → `SpatialDescriptionGenerator.java`
  - 子用例: Announce Position → `TTSManager.java`
  - Java文件: `SpatialDescriptionGenerator.java`, `RealAIDetectionActivity.java`

- **Analyze Color & Lighting** (分析顏色和光線)
  - 子用例: Extract Color Information → `ColorLightingAnalyzer.java`
  - 子用例: Analyze Brightness → `ColorLightingAnalyzer.java`
  - 子用例: Detect Light Direction → `ColorLightingAnalyzer.java`
  - Java文件: `ColorLightingAnalyzer.java`, `RealAIDetectionActivity.java`

- **Night Mode Activation** (夜間模式激活)
  - 子用例: Infer Lighting Condition → `NightModeOptimizer.java`
  - 子用例: Adjust Detection Parameters → `NightModeOptimizer.java`
  - 子用例: Announce Night Mode → `TTSManager.java`
  - Java文件: `NightModeOptimizer.java`, `RealAIDetectionActivity.java`

- **Display Detection Results** (顯示檢測結果)
  - 子用例: Draw Bounding Boxes → `DetectionOverlayView.java`
  - 子用例: Show Confidence Labels → `OptimizedDetectionOverlayView.java`
  - 子用例: Color Code by Confidence → `OptimizedDetectionOverlayView.java`
  - Java文件: `DetectionOverlayView.java`, `OptimizedDetectionOverlayView.java`

- **Monitor Performance** (性能監控)
  - 子用例: Track Detection Time → `DetectionPerformanceMonitor.java`
  - 子用例: Calculate Success Rate → `DetectionPerformanceMonitor.java`
  - 子用例: Generate Performance Report → `DetectionPerformanceMonitor.java`
  - Java文件: `DetectionPerformanceMonitor.java`, `RealAIDetectionActivity.java`

**用例關係**:
- Detect Objects <<include>> Load AI Model
- Detect Objects <<include>> Process Camera Frame
- Detect Objects <<include>> Run Model Inference
- Detect Objects <<include>> Apply Multi-frame Fusion
- Detect Objects <<include>> Filter Results
- Detect Objects <<extend>> Night Mode Activation (當低光時)
- Detect Objects <<include>> Display Detection Results
- Detect Objects <<include>> Monitor Performance

---

##### 2. Document & Currency Reading (文件與貨幣閱讀)

**主要用例**:
- **Read Document** (閱讀文檔)
  - 子用例: Align Document with Camera → `DocumentCurrencyActivity.java`
  - 子用例: Capture Image → `CameraX`
  - 子用例: Run OCR Processing → `OCRHelper.java`
  - 子用例: Extract Text Blocks → `OCRHelper.java`
  - 子用例: Format Text → `OCRHelper.java`
  - Java文件: `DocumentCurrencyActivity.java`, `OCRHelper.java`

- **Recognize Currency** (識別貨幣)
  - 子用例: Capture Currency Image → `DocumentCurrencyActivity.java`
  - 子用例: Analyze Currency Features → `CurrencyDetector.java`
  - 子用例: Identify Denomination → `CurrencyDetector.java`
  - 子用例: Verify Currency Type → `CurrencyDetector.java`
  - Java文件: `DocumentCurrencyActivity.java`, `CurrencyDetector.java`

- **Read Text Aloud** (語音朗讀)
  - 子用例: Process Text Content → `DocumentCurrencyActivity.java`
  - 子用例: Queue Text for TTS → `TTSManager.java`
  - 子用例: Read Full Text → `TTSManager.java`
  - 子用例: Read Line by Line → `TTSManager.java` (可選模式)
  - Java文件: `TTSManager.java`, `DocumentCurrencyActivity.java`

**用例關係**:
- Read Document <<include>> Align Document
- Read Document <<include>> Capture Image
- Read Document <<include>> Run OCR Processing
- Read Document <<include>> Read Text Aloud
- Recognize Currency <<include>> Capture Currency Image
- Recognize Currency <<include>> Analyze Currency Features
- Recognize Currency <<include>> Read Text Aloud

---

##### 3. Emergency Assistance (緊急求助)

**主要用例**:
- **Trigger Emergency Call** (觸發緊急電話)
  - 子用例: Validate Emergency Trigger → `EmergencyManager.java`
  - 子用例: Prepare Emergency Call → `EmergencyManager.java`
  - 子用例: Dial Emergency Service → `EmergencyManager.java`
  - 子用例: Handle Call Status → `EmergencyManager.java`
  - Java文件: `EmergencyManager.java`

- **Long Press Activation** (長按激活)
  - 子用例: Detect Button Press → `MainActivity.java`
  - 子用例: Start Countdown Timer → `MainActivity.java`
  - 子用例: Monitor Press Duration → `MainActivity.java`
  - 子用例: Validate 3-Second Duration → `MainActivity.java`
  - 子用例: Trigger Emergency → `MainActivity.java`
  - Java文件: `MainActivity.java`

**用例關係**:
- Long Press Activation <<include>> Detect Button Press
- Long Press Activation <<include>> Start Countdown Timer
- Long Press Activation <<include>> Validate 3-Second Duration
- Long Press Activation <<include>> Trigger Emergency Call
- Trigger Emergency Call <<include>> Prepare Emergency Call
- Trigger Emergency Call <<include>> Dial Emergency Service
- Trigger Emergency Call <<extend>> Handle Permission (當權限不足時)

---

##### 4. Voice Command (語音命令)

**主要用例**:
- **Speak Command** (語音命令)
  - 子用例: Start Speech Recognition → `ASRManager.java`
  - 子用例: Capture Audio → `ASRManager.java`
  - 子用例: Process Speech → `ASRManager.java`, `SherpaOnnxASR.java`, `FunASRASR.java`
  - 子用例: Preprocess Text → `VoiceCommandManager.java`
  - 子用例: Fuzzy Match Command → `VoiceCommandManager.java`, `VoiceCommandBuilder.java`
  - 子用例: Execute Command → `VoiceCommandManager.java`
  - Java文件: `VoiceCommandActivity.java`, `VoiceCommandManager.java`, `ASRManager.java`

- **Continuous Conversation** (連續對話)
  - 子用例: Activate Continuous Mode → `StreamingVoiceAI.java`
  - 子用例: Manage Conversation Context → `ConversationManager.java`
  - 子用例: Process User Input → `StreamingVoiceAI.java`
  - 子用例: Handle Sleep Mode → `StreamingVoiceAI.java`
  - 子用例: Handle Wake-up → `StreamingVoiceAI.java`
  - 子用例: Handle Exit Command → `StreamingVoiceAI.java`
  - Java文件: `StreamingVoiceAI.java`, `ConversationManager.java`

- **LLM Interaction** (LLM交互)
  - 子用例: Prepare LLM Request → `LLMClient.java`
  - 子用例: Send to LLM API → `LLMClient.java`
  - 子用例: Process LLM Response → `ConversationResponseGenerator.java`
  - 子用例: Fallback to Keyword Match → `ConversationResponseGenerator.java` (當LLM不可用)
  - Java文件: `LLMClient.java`, `LLMConfig.java`, `ConversationResponseGenerator.java`

**用例關係**:
- Speak Command <<include>> Start Speech Recognition
- Speak Command <<include>> Process Speech
- Speak Command <<include>> Fuzzy Match Command
- Speak Command <<include>> Execute Command
- Continuous Conversation <<include>> Manage Conversation Context
- Continuous Conversation <<include>> Process User Input
- Continuous Conversation <<extend>> LLM Interaction (當LLM啟用時)
- LLM Interaction <<extend>> Fallback to Keyword Match (當LLM不可用時)

**用途**: 詳細展示核心功能的子用例和內部流程

---

### 圖3: **緊急求助用例圖 (Emergency Assistance Use Case Diagram)**
**詳細展示緊急功能的流程**

#### Actors:
- **Visually Impaired User in Emergency** (緊急情況下的用戶)
- **Emergency Service (999)** (緊急服務系統)
- **System** (系統，用於自動化處理)

#### Use Cases 及對應Java文件:

##### 主要流程用例:

- **Long Press Emergency Button** (長按緊急按鈕)
  - 子用例: Detect Button Touch → `MainActivity.java`
  - 子用例: Start Press Timer → `MainActivity.java`
  - 子用例: Provide Initial Feedback → `TTSManager.java`, `VibrationManager.java`
  - 子用例: Monitor Press Duration → `MainActivity.java`
  - 子用例: Validate 3-Second Duration → `MainActivity.java`
  - Java文件: `MainActivity.java`

- **Countdown Feedback** (倒計時反饋)
  - 子用例: Announce Press Status → `TTSManager.java`
  - 子用例: Provide Vibration Feedback → `VibrationManager.java`
  - 子用例: Update User Status → `MainActivity.java`
  - Java文件: `MainActivity.java`, `TTSManager.java`

- **Dial Emergency Service** (撥打緊急服務)
  - 子用例: Prepare Emergency Call → `EmergencyManager.java`
  - 子用例: Check Phone Permission → `EmergencyManager.java`
  - 子用例: Initiate Call to 999 → `EmergencyManager.java`
  - 子用例: Handle Call Status → `EmergencyManager.java`
  - Java文件: `EmergencyManager.java`

- **Voice Confirmation** (語音確認)
  - 子用例: Generate Confirmation Message → `EmergencyManager.java`
  - 子用例: Announce Call Status → `TTSManager.java`
  - 子用例: Provide Calming Message → `TTSManager.java`
  - Java文件: `EmergencyManager.java`, `TTSManager.java`

- **Vibration Alert** (震動提醒)
  - 子用例: Trigger Emergency Vibration → `VibrationManager.java`
  - 子用例: Provide Strong Feedback → `VibrationManager.java`
  - Java文件: `EmergencyManager.java`, `VibrationManager.java`

- **Handle Permission** (處理權限)
  - 子用例: Check CALL_PHONE Permission → `EmergencyManager.java`
  - 子用例: Open Dialer Interface → `EmergencyManager.java` (當無權限時)
  - 子用例: Provide Instruction → `TTSManager.java`
  - Java文件: `EmergencyManager.java`

- **Log Emergency Event** (記錄緊急事件)
  - 子用例: Record Timestamp → `EmergencyManager.java`
  - 子用例: Save Event Log → `EmergencyManager.java`
  - Java文件: `EmergencyManager.java`

**用例關係**:
- Long Press Emergency Button <<include>> Detect Button Touch
- Long Press Emergency Button <<include>> Start Press Timer
- Long Press Emergency Button <<include>> Countdown Feedback
- Long Press Emergency Button <<include>> Validate 3-Second Duration
- Long Press Emergency Button <<include>> Dial Emergency Service
- Dial Emergency Service <<include>> Prepare Emergency Call
- Dial Emergency Service <<include>> Check Phone Permission
- Dial Emergency Service <<include>> Voice Confirmation
- Dial Emergency Service <<include>> Vibration Alert
- Dial Emergency Service <<extend>> Handle Permission (當權限不足時)
- Dial Emergency Service <<include>> Log Emergency Event

**替代流程**:
- 如果用戶在3秒前釋放按鈕 → 提供提示信息
- 如果權限不足 → 打開撥號界面
- 如果撥號失敗 → 提供錯誤提示

**用途**: 展示緊急功能的完整流程和交互，包括所有子用例和異常處理

---

### 圖4: **用戶系統用例圖 (User System Use Case Diagram)**
**展示用戶管理和系統設定**

#### Actors:
- **Visually Impaired User** (已註冊用戶)
- **Guest User** (訪客用戶)
- **System** (系統，用於自動化處理)

#### Use Cases 及對應Java文件:

##### 用戶管理用例:

- **User Login** (用戶登入)
  - 子用例: Enter Credentials → `LoginActivity.java`
  - 子用例: Validate Credentials → `UserManager.java`
  - 子用例: Set Login Status → `UserManager.java`
  - 子用例: Load User Preferences → `UserManager.java`
  - 子用例: Navigate to Main Screen → `LoginActivity.java`
  - Java文件: `LoginActivity.java`, `UserManager.java`

- **User Register** (用戶註冊)
  - 子用例: Enter Registration Info → `LoginActivity.java`
  - 子用例: Validate Information → `LoginActivity.java`
  - 子用例: Create User Account → `UserManager.java`
  - 子用例: Save User Data → `UserManager.java`
  - 子用例: Auto Login → `UserManager.java`
  - Java文件: `LoginActivity.java`, `UserManager.java`

- **Guest Mode** (訪客模式)
  - 子用例: Select Guest Mode → `LoginActivity.java`
  - 子用例: Skip Registration → `UserManager.java`
  - 子用例: Set Guest Status → `UserManager.java`
  - 子用例: Navigate to Main Screen → `LoginActivity.java`
  - Java文件: `LoginActivity.java`, `UserManager.java`

- **User Logout** (用戶登出)
  - 子用例: Confirm Logout → `UserManager.java`
  - 子用例: Clear User Session → `UserManager.java`
  - 子用例: Return to Login → `UserManager.java`
  - Java文件: `UserManager.java`

##### 系統設定用例:

- **Adjust Speech Parameters** (調整語音參數)
  - 子用例: Adjust Speech Rate → `SettingsActivity.java`, `TTSManager.java`
  - 子用例: Adjust Pitch → `SettingsActivity.java`, `TTSManager.java`
  - 子用例: Adjust Volume → `SettingsActivity.java`, `TTSManager.java`
  - 子用例: Test Speech Settings → `SettingsActivity.java`, `TTSManager.java`
  - 子用例: Save Settings → `SettingsActivity.java`, `SharedPreferences`
  - Java文件: `SettingsActivity.java`, `TTSManager.java`

- **Configure Accessibility** (配置無障礙設定)
  - 子用例: Toggle Vibration Feedback → `SettingsActivity.java`, `VibrationManager.java`
  - 子用例: Toggle Screen Reader Support → `SettingsActivity.java`
  - 子用例: Toggle Gesture Controls → `SettingsActivity.java`
  - 子用例: Adjust Vibration Intensity → `SettingsActivity.java`, `VibrationManager.java`
  - 子用例: Save Accessibility Settings → `SettingsActivity.java`
  - Java文件: `SettingsActivity.java`, `VibrationManager.java`

- **Switch Language** (切換語言)
  - 子用例: Select Language → `MainActivity.java`, `SettingsActivity.java`
  - 子用例: Update UI Language → `LocaleManager.java`
  - 子用例: Update TTS Language → `LocaleManager.java`, `TTSManager.java`
  - 子用例: Save Language Preference → `LocaleManager.java`
  - 子用例: Reload UI → `LocaleManager.java`
  - Java文件: `LocaleManager.java`, `MainActivity.java`, `SettingsActivity.java`

- **Reset Settings** (重置設定)
  - 子用例: Confirm Reset → `SettingsActivity.java`
  - 子用例: Restore Default Values → `SettingsActivity.java`
  - 子用例: Clear All Preferences → `SettingsActivity.java`
  - 子用例: Reload Settings → `SettingsActivity.java`
  - Java文件: `SettingsActivity.java`

**用例關係**:
- User Login <<include>> Enter Credentials
- User Login <<include>> Validate Credentials
- User Login <<include>> Load User Preferences
- User Register <<include>> Enter Registration Info
- User Register <<include>> Validate Information
- User Register <<include>> Create User Account
- Adjust Speech Parameters <<include>> Test Speech Settings
- Adjust Speech Parameters <<include>> Save Settings
- Switch Language <<include>> Update UI Language
- Switch Language <<include>> Update TTS Language
- Switch Language <<include>> Save Language Preference

**用途**: 展示用戶管理和個人化設定功能的詳細流程

---

### 圖5: **輔助功能用例圖 (Auxiliary Features Use Case Diagram)**
**展示其他輔助功能**

#### Actors:
- **Visually Impaired User** (主要用戶)
- **Volunteer** (志願者，未來功能)
- **System** (系統，用於自動化處理)

#### Use Cases 及對應Java文件:

##### Find Items (尋找物品):

- **Mark Item** (標記物品)
  - 子用例: Capture Item Image → `FindItemsActivity.java`
  - 子用例: Add Item Description → `FindItemsActivity.java`
  - 子用例: Save Item Features → `ObjectSearchManager.java`
  - 子用例: Store Item Location → `ObjectSearchManager.java`
  - 子用例: Save to Database → `FindItemsActivity.java`
  - Java文件: `FindItemsActivity.java`, `ObjectSearchManager.java`

- **Find Marked Item** (尋找標記物品)
  - 子用例: Start Item Search → `FindItemsActivity.java`
  - 子用例: Analyze Camera Feed → `ObjectSearchManager.java`
  - 子用例: Match Item Features → `ObjectSearchManager.java`
  - 子用例: Provide Location Guidance → `FindItemsActivity.java`, `TTSManager.java`
  - 子用例: Announce Item Found → `TTSManager.java`
  - Java文件: `FindItemsActivity.java`, `ObjectSearchManager.java`

- **Save Item Location** (保存物品位置)
  - 子用例: Get GPS Location → `FindItemsActivity.java`
  - 子用例: Record Environment Features → `ObjectSearchManager.java`
  - 子用例: Store Location Data → `ObjectSearchManager.java`
  - Java文件: `FindItemsActivity.java`, `ObjectSearchManager.java`

**用例關係**:
- Mark Item <<include>> Capture Item Image
- Mark Item <<include>> Save Item Features
- Mark Item <<include>> Save Item Location
- Find Marked Item <<include>> Analyze Camera Feed
- Find Marked Item <<include>> Match Item Features
- Find Marked Item <<include>> Provide Location Guidance

---

##### Travel Assistant (出行協助):

- **Get Navigation** (獲取導航)
  - 子用例: Enter Destination → `TravelAssistantActivity.java`
  - 子用例: Calculate Route → `TravelAssistantActivity.java` (未來: 集成Google Maps)
  - 子用例: Provide Turn-by-Turn Guidance → `TravelAssistantActivity.java`, `TTSManager.java`
  - 子用例: Update Navigation Status → `TravelAssistantActivity.java`
  - Java文件: `TravelAssistantActivity.java`

- **Check Traffic Info** (查詢交通信息)
  - 子用例: Request Traffic Data → `TravelAssistantActivity.java`
  - 子用例: Process Traffic Information → `TravelAssistantActivity.java`
  - 子用例: Announce Traffic Status → `TTSManager.java`
  - Java文件: `TravelAssistantActivity.java`

- **Get Weather Update** (獲取天氣更新)
  - 子用例: Request Weather Data → `TravelAssistantActivity.java`
  - 子用例: Process Weather Information → `TravelAssistantActivity.java`
  - 子用例: Announce Weather → `TTSManager.java`
  - Java文件: `TravelAssistantActivity.java`

- **Share Emergency Location** (分享緊急位置)
  - 子用例: Get Current Location → `TravelAssistantActivity.java`
  - 子用例: Format Location Data → `TravelAssistantActivity.java`
  - 子用例: Send Location to Contacts → `TravelAssistantActivity.java`
  - Java文件: `TravelAssistantActivity.java`

**用例關係**:
- Get Navigation <<include>> Enter Destination
- Get Navigation <<include>> Calculate Route
- Get Navigation <<include>> Provide Turn-by-Turn Guidance
- Check Traffic Info <<include>> Request Traffic Data
- Check Traffic Info <<include>> Announce Traffic Status
- Get Weather Update <<include>> Request Weather Data
- Get Weather Update <<include>> Announce Weather

---

##### Instant Assistance (即時協助):

- **Call Volunteer** (呼叫志願者)
  - 子用例: Select Volunteer Contact → `InstantAssistanceActivity.java`
  - 子用例: Initiate Call → `InstantAssistanceActivity.java`
  - 子用例: Handle Call Status → `InstantAssistanceActivity.java`
  - Java文件: `InstantAssistanceActivity.java`

- **Send Quick Message** (發送快速訊息)
  - 子用例: Select Message Template → `InstantAssistanceActivity.java`
  - 子用例: Customize Message → `InstantAssistanceActivity.java`
  - 子用例: Select Recipients → `InstantAssistanceActivity.java`
  - 子用例: Send Message → `InstantAssistanceActivity.java`
  - Java文件: `InstantAssistanceActivity.java`

- **Video Call** (視訊通話)
  - 子用例: Request Video Call → `InstantAssistanceActivity.java`
  - 子用例: Initialize Video Call → `VideoCallActivity.java`
  - 子用例: Establish Connection → `VideoCallActivity.java`
  - 子用例: Manage Video Call → `VideoCallActivity.java`
  - Java文件: `InstantAssistanceActivity.java`, `VideoCallActivity.java`

**用例關係**:
- Call Volunteer <<include>> Select Volunteer Contact
- Call Volunteer <<include>> Initiate Call
- Send Quick Message <<include>> Select Message Template
- Send Quick Message <<include>> Select Recipients
- Video Call <<include>> Initialize Video Call
- Video Call <<include>> Establish Connection

---

##### Gesture Management (手勢管理):

- **Create Custom Gesture** (創建自定義手勢)
  - 子用例: Open Gesture Input → `GestureInputActivity.java`
  - 子用例: Draw Gesture Pattern → `GestureDrawView.java`
  - 子用例: Capture Gesture Data → `GestureRecognitionManager.java`
  - 子用例: Save Gesture Pattern → `GestureRecognitionManager.java`
  - Java文件: `GestureInputActivity.java`, `GestureDrawView.java`, `GestureRecognitionManager.java`

- **Bind Function to Gesture** (綁定功能到手勢)
  - 子用例: Select Gesture → `GestureManagementActivity.java`
  - 子用例: Choose Target Function → `GestureManagementActivity.java`
  - 子用例: Create Binding → `GestureManagementActivity.java`
  - 子用例: Save Binding → `GestureManagementActivity.java`
  - Java文件: `GestureManagementActivity.java`

- **Recognize Gesture** (識別手勢)
  - 子用例: Detect Gesture Input → `GestureRecognitionManager.java`
  - 子用例: Match Gesture Pattern → `GestureRecognitionManager.java`
  - 子用例: Execute Bound Function → `GestureRecognitionManager.java`
  - Java文件: `GestureRecognitionManager.java`, `GestureManagementActivity.java`

**用例關係**:
- Create Custom Gesture <<include>> Draw Gesture Pattern
- Create Custom Gesture <<include>> Capture Gesture Data
- Create Custom Gesture <<include>> Save Gesture Pattern
- Bind Function to Gesture <<include>> Select Gesture
- Bind Function to Gesture <<include>> Choose Target Function
- Bind Function to Gesture <<include>> Save Binding
- Recognize Gesture <<include>> Detect Gesture Input
- Recognize Gesture <<include>> Match Gesture Pattern
- Recognize Gesture <<include>> Execute Bound Function

**用途**: 展示輔助功能的詳細用例和內部流程

---

## 🎯 推薦的用例圖組合方案

### **方案A: 簡化版 (適合中期報告)**
**3個用例圖**

1. **整體系統用例圖** - 展示所有主要功能
2. **核心功能用例圖** - 詳細展示4個核心功能
3. **緊急求助用例圖** - 詳細展示緊急功能流程

**優點**: 簡潔清晰，重點突出
**適用**: 中期報告演示

---

### **方案B: 完整版 (適合最終報告)**
**5個用例圖**

1. **整體系統用例圖**
2. **核心功能用例圖**
3. **緊急求助用例圖**
4. **用戶系統用例圖**
5. **輔助功能用例圖**

**優點**: 完整詳細，覆蓋所有功能
**適用**: 最終報告和完整文檔

---

## 📝 用例圖繪製建議

### 使用工具:
- **Draw.io** (推薦) - 免費，支持UML
- **Lucidchart** - 專業，需要訂閱
- **PlantUML** - 代碼生成，適合版本控制
- **Visio** - 微軟工具

### 繪製要點:
1. **Actor位置**: 放在圖的左右兩側
2. **Use Case位置**: 放在圖的中間
3. **關係線條**:
   - 實線箭頭: 關聯關係 (Association)
   - 帶<<include>>的虛線: 包含關係
   - 帶<<extend>>的虛線: 擴展關係
4. **分組**: 使用包(Package)或分區來組織相關用例

### 標註建議:
- **Use Case名稱**: 使用動詞開頭 (如: Detect Objects, Read Document)
- **Actor名稱**: 使用名詞 (如: Visually Impaired User)
- **關係說明**: 清楚標註include和extend關係

---

## 🔗 用例之間的關係

### Include關係 (包含) - 必須執行的子用例:

#### 環境識別模塊:
- **Environment Recognition** <<include>> **Load AI Model**
- **Environment Recognition** <<include>> **Process Camera Frame**
- **Environment Recognition** <<include>> **Run Model Inference**
- **Environment Recognition** <<include>> **Apply Multi-frame Fusion**
- **Environment Recognition** <<include>> **Filter Results**
- **Environment Recognition** <<include>> **Display Detection Results**
- **Detect Objects** <<include>> **Get Position Description**
- **Detect Objects** <<include>> **Monitor Performance**

#### 文件閱讀模塊:
- **Document Reading** <<include>> **Align Document**
- **Document Reading** <<include>> **Capture Image**
- **Document Reading** <<include>> **Run OCR Processing**
- **Document Reading** <<include>> **Read Text Aloud**
- **Currency Reading** <<include>> **Capture Currency Image**
- **Currency Reading** <<include>> **Analyze Currency Features**
- **Currency Reading** <<include>> **Read Text Aloud**

#### 緊急求助模塊:
- **Emergency Assistance** <<include>> **Detect Button Press**
- **Emergency Assistance** <<include>> **Start Countdown Timer**
- **Emergency Assistance** <<include>> **Validate 3-Second Duration**
- **Emergency Assistance** <<include>> **Prepare Emergency Call**
- **Emergency Assistance** <<include>> **Dial Emergency Service**
- **Emergency Assistance** <<include>> **Voice Confirmation**
- **Emergency Assistance** <<include>> **Vibration Alert**

#### 語音命令模塊:
- **Voice Command** <<include>> **Start Speech Recognition**
- **Voice Command** <<include>> **Process Speech**
- **Voice Command** <<include>> **Fuzzy Match Command**
- **Voice Command** <<include>> **Execute Command**
- **Continuous Conversation** <<include>> **Manage Conversation Context**
- **Continuous Conversation** <<include>> **Process User Input**

### Extend關係 (擴展) - 可選的擴展用例:

#### 條件擴展:
- **Environment Recognition** <<extend>> **Night Mode Activation** (當檢測到低光環境時)
- **Environment Recognition** <<extend>> **Analyze Color & Lighting** (當用戶請求時)
- **Voice Command** <<extend>> **LLM Interaction** (當LLM服務可用且啟用時)
- **Voice Command** <<extend>> **Continuous Conversation** (當用戶激活連續模式時)
- **Emergency Assistance** <<extend>> **Handle Permission** (當撥打電話權限不足時)
- **LLM Interaction** <<extend>> **Fallback to Keyword Match** (當LLM服務不可用時)

#### 異常處理擴展:
- **Dial Emergency Service** <<extend>> **Open Dialer Interface** (當無直接撥號權限時)
- **Process Speech** <<extend>> **Handle Recognition Error** (當語音識別失敗時)
- **Run OCR Processing** <<extend>> **Handle OCR Error** (當OCR識別失敗時)
- **Detect Objects** <<extend>> **Handle Detection Error** (當檢測失敗時)

### Generalization關係 (泛化) - 繼承關係:

- **Read Document** 和 **Recognize Currency** 都泛化自 **Document Processing**
- **Mark Item** 和 **Find Marked Item** 都泛化自 **Item Management**
- **Call Volunteer** 和 **Send Quick Message** 都泛化自 **Contact Assistance**

---

## 📋 詳細用例統計表

### 圖1: 整體系統用例圖
| 功能組 | Use Cases數量 | 子用例數量 | 複雜度 | 優先級 |
|--------|-------------|-----------|--------|--------|
| 核心功能組 | 4 | 20+ | ⭐⭐⭐ | 高 |
| 輔助功能組 | 4 | 15+ | ⭐⭐ | 中 |
| 系統功能組 | 3 | 10+ | ⭐ | 中 |
| **總計** | **11** | **45+** | **⭐⭐** | - |

### 圖2: 核心功能用例圖
| 功能模塊 | 主要用例 | 子用例數量 | Java文件數 | 複雜度 |
|---------|---------|-----------|-----------|--------|
| Environment Recognition | 6 | 18+ | 10+ | ⭐⭐⭐ |
| Document & Currency Reading | 3 | 12+ | 3 | ⭐⭐ |
| Emergency Assistance | 2 | 8+ | 2 | ⭐⭐ |
| Voice Command | 3 | 15+ | 8+ | ⭐⭐⭐ |
| **總計** | **14** | **53+** | **23+** | **⭐⭐⭐** |

### 圖3: 緊急求助用例圖
| 用例類型 | 用例數量 | 子用例數量 | Java文件數 | 複雜度 |
|---------|---------|-----------|-----------|--------|
| 主要流程用例 | 7 | 15+ | 4 | ⭐⭐ |
| 異常處理用例 | 2 | 4+ | 2 | ⭐ |
| **總計** | **9** | **19+** | **4** | **⭐⭐** |

### 圖4: 用戶系統用例圖
| 功能類別 | 用例數量 | 子用例數量 | Java文件數 | 複雜度 |
|---------|---------|-----------|-----------|--------|
| 用戶管理 | 4 | 12+ | 2 | ⭐ |
| 系統設定 | 4 | 15+ | 2 | ⭐⭐ |
| **總計** | **8** | **27+** | **4** | **⭐** |

### 圖5: 輔助功能用例圖
| 功能模塊 | 用例數量 | 子用例數量 | Java文件數 | 複雜度 |
|---------|---------|-----------|-----------|--------|
| Find Items | 3 | 12+ | 2 | ⭐⭐ |
| Travel Assistant | 4 | 12+ | 1 | ⭐⭐ |
| Instant Assistance | 3 | 12+ | 2 | ⭐⭐ |
| Gesture Management | 3 | 12+ | 4 | ⭐⭐ |
| **總計** | **13** | **48+** | **9** | **⭐⭐** |

### 整體統計
| 用例圖 | 主要Actor數 | 主要Use Cases | 總子用例數 | Java文件數 | 複雜度 |
|--------|-----------|---------------|-----------|-----------|--------|
| 圖1: 整體系統 | 3 | 11 | 45+ | 50+ | ⭐⭐ |
| 圖2: 核心功能 | 2 | 14 | 53+ | 23+ | ⭐⭐⭐ |
| 圖3: 緊急求助 | 3 | 9 | 19+ | 4 | ⭐⭐ |
| 圖4: 用戶系統 | 3 | 8 | 27+ | 4 | ⭐ |
| 圖5: 輔助功能 | 3 | 13 | 48+ | 9 | ⭐⭐ |
| **總計** | **5** | **55** | **192+** | **90+** | **⭐⭐** |

---

## 💡 詳細建議

### 方案A: 簡化版 (適合中期報告) - 3個用例圖

#### 圖1: 整體系統用例圖
**包含內容**:
- 3個Actors: Visually Impaired User, Guest User, System
- 11個主要Use Cases (核心4個 + 輔助4個 + 系統3個)
- 基本關係: Association
- 分組: 使用Package分組

**詳細度**: 高層次概述
**Java文件**: 標註主要Activity類
**用途**: 展示系統完整性和功能覆蓋

---

#### 圖2: 核心功能用例圖
**包含內容**:
- 1個主要Actor: Visually Impaired User
- 4個核心功能模塊，每個包含3-6個子用例
- 詳細關係: Include, Extend
- 分組: 按功能模塊分組

**詳細度**: 中等詳細
**Java文件**: 標註所有相關類
**用途**: 展示核心技術實現和算法

**建議子用例優先級**:
- **高優先級** (必須包含):
  - Detect Objects (檢測物體)
  - Read Document (閱讀文檔)
  - Trigger Emergency Call (觸發緊急電話)
  - Speak Command (語音命令)
  
- **中優先級** (建議包含):
  - Get Position Description (位置描述)
  - Night Mode Activation (夜間模式)
  - Continuous Conversation (連續對話)
  - Multi-frame Fusion (多幀融合)

- **低優先級** (可選):
  - Analyze Color & Lighting (顏色分析)
  - Monitor Performance (性能監控)
  - LLM Interaction (LLM交互)

---

#### 圖3: 緊急求助用例圖
**包含內容**:
- 2個Actors: User in Emergency, Emergency Service (999)
- 7個主要流程用例
- 2個異常處理用例
- 完整流程順序

**詳細度**: 非常詳細
**Java文件**: 標註所有相關方法
**用途**: 展示關鍵安全功能的完整實現

**建議包含的詳細流程**:
1. Long Press Detection (按鈕檢測)
2. Countdown Feedback (倒計時反饋)
3. Emergency Call Preparation (準備撥號)
4. Permission Handling (權限處理)
5. Dial Emergency Service (撥打999)
6. Voice Confirmation (語音確認)
7. Vibration Alert (震動提醒)

---

### 方案B: 完整版 (適合最終報告) - 5個用例圖

在方案A基礎上增加:

#### 圖4: 用戶系統用例圖
**詳細度**: 中等
**重點**: 用戶管理和個人化設定流程

#### 圖5: 輔助功能用例圖
**詳細度**: 中等
**重點**: 輔助功能的詳細實現

---

## 🎨 繪製詳細建議

### 用例圖層次結構

#### 第一層: 整體系統圖
- 展示所有主要功能
- 標註主要Actor
- 基本Association關係

#### 第二層: 功能模塊圖 (圖2-5)
- 詳細展示每個模塊的子用例
- 標註Include和Extend關係
- 顯示用例之間的依賴

#### 第三層: 詳細流程圖 (可選)
- 針對複雜用例的進一步分解
- 顯示內部處理步驟
- 標註Java方法調用

---

### 用例命名規範

#### 主要用例命名:
- 使用動詞開頭: "Detect Objects", "Read Document"
- 簡潔明確: 2-4個單詞
- 英文命名，中文註釋

#### 子用例命名:
- 更具體的行為: "Load AI Model", "Process Camera Frame"
- 技術導向: 反映實現細節
- 可包含技術術語: "Multi-frame Fusion", "Fuzzy Matching"

---

### 關係標註詳細說明

#### Include關係標註:
```
Detect Objects <<include>> Load AI Model
Detect Objects <<include>> Process Camera Frame
```
**說明**: 檢測物體必須包含加載模型和處理幀

#### Extend關係標註:
```
Detect Objects <<extend>> Night Mode Activation
(當低光環境時)
```
**說明**: 夜間模式是可選擴展，只在特定條件下觸發

#### 條件標註:
- 在Extend關係旁標註觸發條件
- 使用括號說明: (當LLM可用時), (當權限不足時)

---

## 📊 用例優先級分類

### 高優先級用例 (必須在報告中展示)
1. **Environment Recognition** - 核心AI功能
2. **Document Reading** - 核心OCR功能
3. **Emergency Assistance** - 關鍵安全功能
4. **Voice Command** - 核心交互功能

### 中優先級用例 (建議包含)
5. **Find Items** - 實用功能
6. **System Settings** - 個人化功能
7. **User Login** - 用戶管理

### 低優先級用例 (可選)
8. **Travel Assistant** - 未來擴展功能
9. **Instant Assistance** - 輔助功能
10. **Gesture Management** - 輔助功能

---

## 🔍 每個用例圖的詳細分解建議

### 圖1: 整體系統用例圖 - 詳細分解

#### Package 1: Core Features (核心功能)
```
Environment Recognition
├── 主要功能: 實時物體檢測
├── 技術: TensorFlow Lite, SSD MobileNet
└── Java: RealAIDetectionActivity, ObjectDetectorHelper

Document & Currency Reading
├── 主要功能: OCR文字識別 + 港幣識別
├── 技術: Google ML Kit
└── Java: DocumentCurrencyActivity, OCRHelper

Emergency Assistance
├── 主要功能: 長按3秒撥打999
├── 技術: Phone API, TTS, Vibration
└── Java: EmergencyManager, MainActivity

Voice Command
├── 主要功能: 語音識別 + LLM對話
├── 技術: ASR, LLM APIs
└── Java: VoiceCommandActivity, LLMClient
```

#### Package 2: Auxiliary Features (輔助功能)
```
Find Items
├── 主要功能: 物品標記和尋找
└── Java: FindItemsActivity, ObjectSearchManager

Travel Assistant
├── 主要功能: 導航和交通信息
└── Java: TravelAssistantActivity

Instant Assistance
├── 主要功能: 志願者聯繫
└── Java: InstantAssistanceActivity, VideoCallActivity

Gesture Management
├── 主要功能: 自定義手勢
└── Java: GestureManagementActivity, GestureRecognitionManager
```

#### Package 3: System Features (系統功能)
```
User Login/Register
├── 主要功能: 用戶認證
└── Java: LoginActivity, UserManager

System Settings
├── 主要功能: 個人化設定
└── Java: SettingsActivity

Language Switch
├── 主要功能: 三語言切換
└── Java: LocaleManager, MainActivity
```

---

### 圖2: 核心功能用例圖 - 詳細分解

#### Environment Recognition 詳細用例樹:
```
Environment Recognition
├── Detect Objects
│   ├── Load AI Model (include)
│   ├── Process Camera Frame (include)
│   ├── Run Model Inference (include)
│   ├── Apply Multi-frame Fusion (include)
│   ├── Filter Results (include)
│   └── Display Results (include)
├── Get Position Description (include)
│   ├── Calculate Position
│   └── Generate Description
├── Analyze Color & Lighting (extend, optional)
│   ├── Extract Colors
│   └── Analyze Brightness
├── Night Mode Activation (extend, when low light)
│   ├── Infer Lighting
│   └── Adjust Parameters
└── Monitor Performance (include)
    ├── Track Metrics
    └── Generate Report
```

---

## 📝 繪製檢查清單

### 每個用例圖必須包含:
- [ ] 清晰的Actor定義
- [ ] 所有主要Use Cases
- [ ] Include關係標註
- [ ] Extend關係標註（如適用）
- [ ] Package分組
- [ ] Java文件對應註釋
- [ ] 用例編號（可選）

### 圖表質量檢查:
- [ ] 用例名稱清晰易懂
- [ ] 關係線條不交叉（盡量）
- [ ] 分組邏輯清晰
- [ ] 註釋完整
- [ ] 符合UML標準

---

## 💡 最終建議

**對於中期報告，強烈建議使用方案A (3個用例圖)**:
1. **整體系統用例圖** - 展示項目全貌和功能完整性
2. **核心功能用例圖** - 詳細展示4個核心功能的技術實現
3. **緊急求助用例圖** - 展示關鍵安全功能的完整流程

**每個圖表的詳細度**:
- 圖1: 高層次概述 (11個主要用例)
- 圖2: 中等詳細 (14個主要用例 + 子用例)
- 圖3: 非常詳細 (9個用例 + 完整流程)

這樣既能展示系統完整性，又不會過於複雜，適合15分鐘的演示時間，同時滿足中期報告的要求。

---

---

## 📁 相關文檔

- **Java文件對應表**: `Java_Files_to_Use_Cases_Mapping.md` - 詳細的Java文件與用例圖對應關係

---

**最後更新**: 2025年1月
