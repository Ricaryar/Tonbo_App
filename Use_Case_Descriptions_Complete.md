# Use Case Descriptions 完整版
## Complete Use Case Descriptions for Tonbo App

---

## 📍 位置說明

**主要位置**: `Interim_Report_Outline_FYP.md` - Section 4.1

**完整版本**: 本文檔包含所有10個用例的詳細描述

---

## 核心功能用例 (Core Features)

### UC1: Environment Recognition (環境識別)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - Camera permission granted
  - AI model loaded successfully
- **Main Flow**:
  1. User opens environment recognition feature from main screen
  2. System activates camera using CameraX API
  3. System loads TensorFlow Lite model (SSD MobileNet V1)
  4. System processes camera frames (every 3 frames for performance)
  5. System performs object detection using AI model
  6. System applies multi-frame fusion (3-frame confirmation for stability)
  7. System filters results based on confidence threshold
  8. System analyzes lighting conditions (infer from detection confidence)
  9. System activates night mode if low light detected
  10. System generates position descriptions (left/center/right)
  11. System announces detected objects with position information via TTS
  12. System displays detection results on overlay view
- **Postcondition**: 
  - Objects are detected and announced to user
  - Detection results are displayed on screen
  - Night mode is activated if needed
- **Alternative Flows**:
  - If camera fails to start: System announces error and returns to main screen
  - If no objects detected: System announces "No objects detected"
  - If low light detected: System activates night mode and adjusts parameters
- **Exception Flows**:
  - If AI model fails to load: System shows error message
  - If detection crashes: System recovers and continues
- **Related Java Files**:
  - `RealAIDetectionActivity.java`
  - `ObjectDetectorHelper.java`
  - `YoloDetector.java`
  - `NightModeOptimizer.java`
  - `SpatialDescriptionGenerator.java`

---

### UC2: Document Reading (文件閱讀)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - Camera permission granted
  - Document is in camera view
- **Main Flow**:
  1. User opens document reading feature
  2. System activates camera
  3. System displays scanning area guide on screen
  4. User aligns document with scanning area
  5. User taps capture button or system auto-captures
  6. System captures image from camera
  7. System preprocesses image (rotation, cropping)
  8. System performs OCR recognition using Google ML Kit
  9. System extracts text blocks from OCR results
  10. System formats text (removes noise, corrects layout)
  11. System reads recognized text aloud via TTS
  12. User can choose full-text or line-by-line reading mode
- **Postcondition**: 
  - Document text is recognized and read to user
  - Text is displayed on screen (for low-vision users)
- **Alternative Flows**:
  - If OCR fails: System announces error and allows retry
  - If text is too long: System offers line-by-line reading
  - If document not aligned: System provides guidance
- **Related Java Files**:
  - `DocumentCurrencyActivity.java`
  - `OCRHelper.java`
  - `TTSManager.java`

---

### UC3: Currency Reading (貨幣識別)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - Camera permission granted
  - Currency (Hong Kong dollar) is in camera view
- **Main Flow**:
  1. User opens currency reading feature (same as document reading)
  2. System activates camera
  3. User aligns currency note/coin with camera
  4. System captures image
  5. System analyzes currency features (size, color, patterns)
  6. System identifies denomination using CurrencyDetector
  7. System announces currency value via TTS
  8. System displays currency information on screen
- **Postcondition**: 
  - Currency denomination is identified and announced
- **Alternative Flows**:
  - If currency not recognized: System announces "Unable to recognize currency, please try again"
  - If currency partially visible: System provides guidance
- **Related Java Files**:
  - `DocumentCurrencyActivity.java`
  - `CurrencyDetector.java`

---

### UC4: Emergency Assistance (緊急求助)

- **Actor**: Visually Impaired User in Emergency
- **Precondition**: 
  - App is running
  - Emergency button is accessible on main screen
- **Main Flow**:
  1. User long-presses emergency button for 3 seconds
  2. System detects button press and starts timer
  3. System provides initial feedback: "Emergency button pressed, please continue holding for 3 seconds"
  4. System provides vibration feedback (click vibration)
  5. System monitors press duration
  6. After 3 seconds, system triggers emergency alert
  7. System provides voice confirmation: "Calling emergency service 999, please stay calm"
  8. System provides strong vibration pattern (emergency vibration)
  9. System checks phone permission
  10. If permission granted: System directly dials 999
  11. If permission not granted: System opens dialer with 999 pre-filled
  12. System logs emergency event with timestamp
- **Postcondition**: 
  - Emergency service 999 is called
  - User receives confirmation feedback
- **Alternative Flows**:
  - If user releases button before 3 seconds: System announces "Please long press for 3 seconds to call emergency service"
  - If dial fails: System provides error message and opens dialer
- **Exception Flows**:
  - If phone permission denied: System opens dialer interface
  - If phone unavailable: System shows error message
- **Related Java Files**:
  - `EmergencyManager.java`
  - `MainActivity.java`
  - `TTSManager.java`
  - `VibrationManager.java`

---

### UC5: Voice Command (語音命令)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - Microphone permission granted
  - Voice command feature is accessible
- **Main Flow**:
  1. User opens voice command feature or activates from any screen
  2. User speaks a command (e.g., "打開環境識別", "Open environment recognition")
  3. System starts speech recognition using ASRManager
  4. System captures audio input
  5. System processes speech using ASR engine (Android Native, Sherpa-Onnx, or FunASR)
  6. System receives recognized text
  7. System preprocesses text (removes filler words, normalizes)
  8. System performs fuzzy matching using VoiceCommandManager
  9. System identifies matching command
  10. System executes corresponding function
  11. System provides audio feedback confirming action
- **Postcondition**: 
  - Command is recognized and executed
  - User receives feedback
- **Alternative Flows**:
  - If command not recognized: System announces "Command not recognized, please try again"
  - If multiple matches: System asks for clarification
  - If continuous mode activated: System enters continuous conversation mode
- **Related Java Files**:
  - `VoiceCommandActivity.java`
  - `VoiceCommandManager.java`
  - `ASRManager.java`
  - `VoiceCommandBuilder.java`

---

### UC6: Continuous Conversation (連續對話)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - Voice command feature is active
  - LLM service is configured (optional)
- **Main Flow**:
  1. User activates continuous conversation mode (long press voice button)
  2. System enters continuous mode
  3. System manages conversation context using ConversationManager
  4. User speaks naturally (questions, commands, conversation)
  5. System processes input continuously
  6. If LLM enabled: System sends to LLM API and gets intelligent response
  7. If LLM disabled: System uses keyword matching
  8. System responds via TTS
  9. System enters sleep mode after 30 seconds of silence
  10. System wakes up on voice input
  11. User can exit with "退出" or "exit" command
- **Postcondition**: 
  - Continuous conversation is active
  - User can have natural conversation with AI
- **Alternative Flows**:
  - If LLM unavailable: System falls back to keyword matching
  - If sleep mode: System waits for wake-up command
- **Related Java Files**:
  - `StreamingVoiceAI.java`
  - `ConversationManager.java`
  - `LLMClient.java`
  - `ConversationResponseGenerator.java`

---

## 輔助功能用例 (Auxiliary Features)

### UC7: Find Items (尋找物品)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - Camera permission granted
- **Main Flow**:
  1. User opens find items feature
  2. User chooses action: "Mark Item" or "Find Item"
  
  **If Marking Item**:
  3a. User captures item image
  4a. User adds item description via voice or text
  5a. System analyzes item features using ObjectSearchManager
  6a. System saves item data (image, description, location) to database
  7a. System confirms: "Item marked successfully"
  
  **If Finding Item**:
  3b. User selects item from saved list
  4b. System starts camera and analyzes feed
  5b. System matches item features using ObjectSearchManager
  6b. System provides location guidance: "Item is on your left"
  7b. System announces when item is found: "Item found!"
- **Postcondition**: 
  - Item is marked or found
  - Location information is provided
- **Related Java Files**:
  - `FindItemsActivity.java`
  - `ObjectSearchManager.java`

---

### UC8: Travel Assistant (出行協助)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - Location permission granted (optional, for some features)
- **Main Flow**:
  1. User opens travel assistant feature
  2. User selects function:
     - Navigation (導航)
     - Traffic Info (交通信息)
     - Weather Update (天氣更新)
     - Share Emergency Location (分享緊急位置)
  3. System processes request
  4. System provides information via voice
  5. System displays information on screen (if applicable)
- **Postcondition**: 
  - Travel information is provided
- **Note**: Some features require third-party API integration (future enhancement)
- **Related Java Files**:
  - `TravelAssistantActivity.java`

---

### UC9: Instant Assistance (即時協助)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - Phone permission granted
- **Main Flow**:
  1. User opens instant assistance feature
  2. User selects assistance type:
     - Call Volunteer (呼叫志願者)
     - Send Quick Message (發送快速訊息)
     - Video Call (視訊通話)
  3. System processes request
  4. System connects user with volunteer
  5. System provides voice feedback on connection status
- **Postcondition**: 
  - User is connected with volunteer assistance
- **Related Java Files**:
  - `InstantAssistanceActivity.java`
  - `VideoCallActivity.java`

---

### UC10: Gesture Management (手勢管理)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
- **Main Flow**:
  1. User opens gesture management feature
  2. User chooses action:
     - Create New Gesture (創建新手勢)
     - Manage Existing Gestures (管理現有手勢)
  3. If creating:
     a. User draws gesture pattern on GestureDrawView
     b. System captures gesture data
     c. User selects function to bind
     d. System saves gesture pattern and binding
  4. If managing:
     a. User selects gesture from list
     b. User can edit, delete, or test gesture
  5. System recognizes gesture when drawn and executes bound function
- **Postcondition**: 
  - Gesture is created and bound to function
  - Gesture recognition is active
- **Related Java Files**:
  - `GestureManagementActivity.java`
  - `GestureInputActivity.java`
  - `GestureRecognitionManager.java`
  - `GestureDrawView.java`

---

## 系統功能用例 (System Features)

### UC11: User Login/Register (用戶登入/註冊)

- **Actor**: Visually Impaired User, Guest User
- **Precondition**: 
  - App is launched
  - Splash screen completed
- **Main Flow**:
  1. User sees login screen after splash
  2. User chooses option:
     - Login (登入)
     - Register (註冊)
     - Guest Mode (訪客模式)
  3. If Login:
     a. User enters email and password
     b. System validates credentials using UserManager
     c. System sets login status
     d. System loads user preferences
     e. System navigates to main screen
  4. If Register:
     a. User enters registration information
     b. System validates information
     c. System creates user account
     d. System saves user data
     e. System auto-logs in user
     f. System navigates to main screen
  5. If Guest Mode:
     a. User skips registration
     b. System sets guest status
     c. System navigates to main screen
- **Postcondition**: 
  - User is logged in or in guest mode
  - Main screen is displayed
- **Related Java Files**:
  - `LoginActivity.java`
  - `UserManager.java`
  - `SplashActivity.java`

---

### UC12: System Settings (系統設定)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
  - User is logged in or in guest mode
- **Main Flow**:
  1. User opens system settings from main screen
  2. User adjusts settings:
     - Speech Parameters (語音參數):
       - Speech Rate (語速): 0-200%
       - Pitch (音調): 0-200%
       - Volume (音量): 0-200%
     - Accessibility Options (無障礙選項):
       - Vibration Feedback (震動反饋): On/Off
       - Screen Reader Support (讀屏支持): On/Off
       - Gesture Controls (手勢控制): On/Off
     - Language (語言):
       - Switch to Cantonese (切換到廣東話)
       - Switch to Mandarin (切換到普通話)
       - Switch to English (切換到英文)
  3. System saves settings to SharedPreferences
  4. System applies changes immediately
  5. System provides voice confirmation of changes
  6. System updates UI language if language changed
- **Postcondition**: 
  - Settings are saved and applied
  - UI is updated according to settings
- **Related Java Files**:
  - `SettingsActivity.java`
  - `TTSManager.java`
  - `VibrationManager.java`
  - `LocaleManager.java`

---

### UC13: Language Switch (語言切換)

- **Actor**: Visually Impaired User
- **Precondition**: 
  - App is running
- **Main Flow**:
  1. User taps language switch button (available on main screen and settings)
  2. System cycles through languages: Cantonese → Mandarin → English → Cantonese
  3. System updates LocaleManager
  4. System updates all UI text immediately
  5. System updates TTS language
  6. System saves language preference
  7. System announces current language: "Language switched to [language]"
- **Postcondition**: 
  - Language is switched
  - All UI elements are updated
  - TTS language is updated
- **Related Java Files**:
  - `LocaleManager.java`
  - `MainActivity.java`
  - `TTSManager.java`

---

## 📊 用例統計

| 類別 | 用例數量 | 用例編號 |
|------|---------|---------|
| 核心功能 | 6 | UC1-UC6 |
| 輔助功能 | 4 | UC7-UC10 |
| 系統功能 | 3 | UC11-UC13 |
| **總計** | **13** | **UC1-UC13** |

---

## 📁 相關文檔

- **中期報告**: `Interim_Report_Outline_FYP.md` - Section 4.1 (包含UC1-UC4)
- **用例圖組織**: `Use_Case_Diagrams_Organization.md`
- **Java文件對應**: `Java_Files_to_Use_Cases_Mapping.md`

---

**最後更新**: 2025年1月
