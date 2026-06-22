# Detailed Design Documentation Outline
## 详细设计文档大纲

---

## 5. Documentation for Detailed Design
### 5. 详细设计文档

---

## 5.1 Data Design (数据设计)

### 5.1.1 Overview (概述)
- Purpose of data design
- Data storage strategy
- Data persistence approach

### 5.1.2 Data Models (数据模型)

#### 5.1.2.1 User Profile (用户配置)
- **Attributes**:
  - `username`: String
  - `email`: String
  - `userType`: String (user/volunteer)
  - `preferences`: Map<String, Object>
  - `settings`: Map<String, Object>
- **Storage**: SharedPreferences
- **Implementation**: `UserManager.java`
- **Purpose**: Store user login status, preferences, and settings

#### 5.1.2.2 Detection Result (检测结果)
- **Attributes**:
  - `objectClass`: String
  - `confidence`: float
  - `boundingBox`: Rect
  - `position`: String (left/center/right)
  - `timestamp`: long
- **Storage**: Memory (runtime only)
- **Implementation**: `ObjectDetectorHelper.java`
- **Purpose**: Temporary storage for multi-frame fusion and announcement

#### 5.1.2.3 Conversation History (对话历史)
- **Attributes**:
  - `messages`: List<Message>
  - `timestamps`: List<Long>
  - `context`: Map<String, Object>
- **Storage**: Memory (runtime, max 20 turns)
- **Implementation**: `ConversationManager.java`
- **Purpose**: Maintain LLM conversation context

#### 5.1.2.4 Emergency Event Log (紧急事件日志)
- **Attributes**:
  - `timestamp`: long
  - `eventType`: String
  - `status`: String
- **Storage**: Log file
- **Implementation**: `EmergencyManager.java`
- **Purpose**: Record emergency events for audit

#### 5.1.2.5 Item Marker (物品标记)
- **Attributes**:
  - `itemName`: String
  - `description`: String
  - `location`: Location
  - `image`: Bitmap
  - `timestamp`: long
- **Storage**: Local Database / SharedPreferences
- **Implementation**: `FindItemsActivity.java`
- **Purpose**: Store marked items for finding

### 5.1.3 Data Storage Architecture (数据存储架构)
- **SharedPreferences**: User settings, preferences, language
- **Memory**: Runtime data (detection results, conversation history)
- **Local Files**: Logs, cached images
- **Future**: SQLite database for complex queries

### 5.1.4 Data Flow (数据流)
- User input → Activity → Manager → Storage
- Detection → Memory → Processing → Announcement
- Settings → SharedPreferences → Manager → UI Update

**Diagram**: `docs/data_design/Data_Model_Diagram.puml`

---

## 5.2 Software/Hardware Architectural Design (系统架构设计)

### 5.2.1 System Architecture Overview (系统架构概述)
- **Architecture Pattern**: Layered Architecture (4 layers)
- **Design Principles**: Separation of concerns, Single Responsibility
- **Technology Stack**: Java, Android SDK, TensorFlow Lite, Google ML Kit

### 5.2.2 Architecture Layers (架构层次)

#### 5.2.2.1 Presentation Layer (表示层)
- **Components**:
  - Activities: `MainActivity`, `RealAIDetectionActivity`, `DocumentCurrencyActivity`, etc.
  - Fragments: (Future enhancement)
  - Custom Views: `DetectionOverlayView`, `GestureDrawView`
- **Responsibilities**:
  - User interface rendering
  - User input handling
  - Accessibility features (TTS, Vibration)
- **Key Files**: All `*Activity.java` files

#### 5.2.2.2 Business Logic Layer (业务逻辑层)
- **Components**:
  - **Managers** (Singleton Pattern):
    - `TTSManager`: Text-to-speech management
    - `VibrationManager`: Haptic feedback
    - `LocaleManager`: Language switching
    - `UserManager`: User session management
    - `EmergencyManager`: Emergency assistance
    - `ConversationManager`: LLM conversation context
  - **Helpers**:
    - `ObjectDetectorHelper`: Multi-frame fusion
    - `DetectionPerformanceMonitor`: Performance tracking
  - **Detectors**:
    - `YoloDetector`: Object detection
    - `OCRHelper`: Text recognition
    - `CurrencyDetector`: Currency recognition
- **Responsibilities**:
  - Business logic implementation
  - Data processing
  - Coordination between layers
- **Key Files**: All `*Manager.java`, `*Helper.java`, `*Detector.java` files

#### 5.2.2.3 Data Layer (数据层)
- **Components**:
  - Models: Data structures (User Profile, Detection Result, etc.)
  - Storage: SharedPreferences, Local files
  - Persistence: Data saving and loading
- **Responsibilities**:
  - Data persistence
  - Data retrieval
  - Data validation
- **Key Files**: `UserManager.java`, `FindItemsActivity.java` (for item storage)

#### 5.2.2.4 AI/ML Layer (AI/ML层)
- **Components**:
  - **TensorFlow Lite**: 
    - Model: SSD MobileNet V1 / YOLO
    - Purpose: Real-time object detection (80 COCO categories)
    - Input: 640x640 RGB image
    - Output: Detection results with bounding boxes
  - **Google ML Kit**:
    - Text Recognition API
    - Supports: Chinese and English
    - Purpose: OCR for document reading
  - **LLM APIs**:
    - DeepSeek API
    - GLM-4-Flash API
    - Purpose: Intelligent conversation
- **Responsibilities**:
  - AI model inference
  - ML processing
  - API integration
- **Key Files**: `YoloDetector.java`, `OCRHelper.java`, `LLMClient.java`

### 5.2.3 Component Interactions (组件交互)
- Activities → Managers: Business logic delegation
- Managers → Detectors: AI/ML processing
- Detectors → AI/ML Layer: Model inference
- Managers → Storage: Data persistence
- Activities → Views: UI rendering

### 5.2.4 Design Patterns (设计模式)
- **Singleton Pattern**: Managers (TTSManager, VibrationManager, etc.)
- **Template Method Pattern**: BaseAccessibleActivity
- **Factory Pattern**: (Future: DetectorFactory)
- **Observer Pattern**: (Future: Event listeners)

### 5.2.5 Hardware Requirements (硬件要求)
- **Camera**: Required for object detection, OCR, currency recognition
- **Microphone**: Required for voice commands
- **Vibrator**: Required for haptic feedback
- **Display**: Required for UI (with accessibility support)
- **Network**: Optional (for LLM API calls)

**Diagram**: `docs/architecture/System_Architecture_Diagram.puml`

---

## 5.3 User Interface Design (用户界面设计)

### 5.3.1 UI Design Principles (UI设计原则)
- **Accessibility First**: WCAG 2.1 compliance
- **Large Touch Targets**: Minimum 48dp touch area
- **High Contrast**: Clear visual distinction
- **Voice-First**: All actions have voice feedback
- **Simple Navigation**: Clear back navigation

### 5.3.2 UI Component Hierarchy (UI组件层次)

#### 5.3.2.1 BaseAccessibleActivity (基础无障碍Activity)
- **Purpose**: Common base class for all Activities
- **Features**:
  - TTS integration (`ttsManager`)
  - Vibration feedback (`vibrationManager`)
  - Language management (`localeManager`)
  - Accessibility initialization
  - Voice command handling
- **Inheritance**: All Activities extend this class
- **Key Methods**:
  - `initializeAccessibility()`
  - `announceInfo(String)`
  - `announceError(String)`
  - `announceNavigation(String)`

#### 5.3.2.2 Activity Classes (Activity类)
- **MainActivity**: Home screen with function cards
- **RealAIDetectionActivity**: Environment recognition with camera
- **DocumentCurrencyActivity**: Document and currency reading
- **VoiceCommandActivity**: Voice command interface
- **SettingsActivity**: System settings
- **FindItemsActivity**: Item marking and finding
- **TravelAssistantActivity**: Travel assistance features
- **InstantAssistanceActivity**: Quick assistance options
- **GestureManagementActivity**: Gesture creation and management
- **LoginActivity**: User authentication
- **SplashActivity**: App launch screen

#### 5.3.2.3 Custom Views (自定义视图)
- **DetectionOverlayView**:
  - Purpose: Draw detection bounding boxes
  - Features: Real-time overlay updates
  - Used by: `RealAIDetectionActivity`
- **OptimizedDetectionOverlayView**:
  - Purpose: Enhanced overlay with smart positioning
  - Features: Confidence-based coloring, text positioning
  - Used by: `RealAIDetectionActivity`
- **GestureDrawView**:
  - Purpose: Capture and recognize gesture patterns
  - Features: Touch tracking, pattern matching
  - Used by: `GestureManagementActivity`

**Diagram**: `docs/ui_design/UI_Component_Hierarchy.puml`

### 5.3.3 Navigation Flow (导航流程)

#### 5.3.3.1 App Launch Flow (应用启动流程)
1. `SplashActivity` → Check login status
2. If not logged in → `LoginActivity`
3. If logged in or guest → `MainActivity`

#### 5.3.3.2 Main Navigation (主导航)
- **Central Hub**: `MainActivity`
- **Function Access**: 7 main function buttons
  - Environment Recognition → `RealAIDetectionActivity`
  - Document Reading → `DocumentCurrencyActivity`
  - Voice Command → `VoiceCommandActivity`
  - Find Items → `FindItemsActivity`
  - Travel Assistant → `TravelAssistantActivity`
  - Instant Assistance → `InstantAssistanceActivity`
  - Gesture Management → `GestureManagementActivity`
- **Settings Access**: Settings button → `SettingsActivity`
- **Emergency Access**: Always accessible emergency button (long press 3s)

#### 5.3.3.3 Return Navigation (返回导航)
- All feature Activities → Back button → `MainActivity`
- Consistent back navigation pattern
- Voice command "返回" / "back" → Return to MainActivity

**Diagram**: `docs/ui_design/Navigation_Flow_Diagram.puml`

### 5.3.4 Accessibility Features (无障碍功能)
- **Screen Reader Support**: Content descriptions for all UI elements
- **Voice Announcements**: Page titles, actions, errors
- **Haptic Feedback**: Vibration for button clicks, errors, success
- **Large Text Support**: Scalable text sizes
- **High Contrast Mode**: Clear visual distinction
- **Gesture Support**: Custom gestures for quick actions

### 5.3.5 UI Layouts (UI布局)
- **Main Screen**: Grid layout with function cards
- **Feature Screens**: Full-screen camera preview or content area
- **Settings Screen**: Scrollable list with SeekBars and Switches
- **Consistent Design**: Same header, back button, title structure

### 5.3.6 Multi-language Support (多语言支持)
- **Supported Languages**: Cantonese, Mandarin, English
- **Dynamic UI Update**: All text updates when language changes
- **TTS Synchronization**: TTS language matches UI language
- **Language Persistence**: Saved preference across app restarts

---

## 5.4 Summary (总结)

### 5.4.1 Data Design Summary
- Lightweight data storage using SharedPreferences
- Runtime data in memory for performance
- Future expansion to SQLite for complex queries

### 5.4.2 Architecture Summary
- Four-layer architecture ensures separation of concerns
- Singleton pattern for managers ensures single instance
- Template method pattern for consistent Activity behavior
- AI/ML layer isolated for easy model updates

### 5.4.3 UI Design Summary
- Accessibility-first design for visually impaired users
- Simple navigation with MainActivity as central hub
- Voice and haptic feedback for all interactions
- Multi-language support with dynamic UI updates

---

## Diagrams Reference (图表参考)

1. **Data Model Diagram**: `docs/data_design/Data_Model_Diagram.puml`
2. **System Architecture Diagram**: `docs/architecture/System_Architecture_Diagram.puml`
3. **UI Component Hierarchy**: `docs/ui_design/UI_Component_Hierarchy.puml`
4. **Navigation Flow Diagram**: `docs/ui_design/Navigation_Flow_Diagram.puml`
5. **Data Storage Architecture**: `docs/data_design/Data_Storage_Architecture.puml` (if exists)
6. **Technology Stack Architecture**: `docs/architecture/Technology_Stack_Architecture.puml` (if exists)

---

**Last Updated**: January 2025
