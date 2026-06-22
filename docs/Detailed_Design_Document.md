# Detailed Design Documentation (Tonbo App)

**Scope**: Data Design, Software/Hardware Architectural Design (System Design), User Interface Design  
**Project**: Tonbo (瞳伴) – Intelligent Visual Assistant for Visually Impaired Users  
**Platform**: Android (Java; optional Kotlin modules)  

---

## 1. Data Design

### 1.1 Data Design Overview

The application uses a **lightweight data strategy**:
- **Persistent settings/state** are stored in **SharedPreferences** (fast, simple, reliable).
- **Runtime-only processing data** (e.g., detection results, conversation context) stays **in memory** for performance.
- **Files** are used for models/resources (e.g., TFLite models) and optional logs/caches.
- Some features (e.g., “Find Items”) may persist structured records (can be SharedPreferences or a local DB depending on implementation).

### 1.2 Data Models

#### 1.2.1 User Profile / User State
- **Purpose**: Maintain user session and app preferences.
- **Typical fields**:
  - `username`, `email`, `userType`
  - `preferences` / `settings` (language, accessibility toggles, TTS parameters)
- **Persistence**: SharedPreferences (via `UserManager`, `LocaleManager`, and settings screens).

#### 1.2.2 Detection Result
- **Purpose**: Carry object detection outputs for overlay rendering + audio announcements.
- **Typical fields**:
  - `objectClass`, `confidence`, `boundingBox`, `position`, `timestamp`
- **Persistence**: Runtime memory (buffered for multi-frame fusion / filtering).

#### 1.2.3 Conversation History (LLM Context)
- **Purpose**: Maintain conversational context for continuous conversation mode.
- **Typical fields**:
  - `messages`, `timestamps`, `context`
- **Persistence**: Runtime memory with bounded history size (to control latency/cost).

#### 1.2.4 Emergency Event Log
- **Purpose**: Record emergency trigger events (for debugging / audit / future analytics).
- **Typical fields**:
  - `timestamp`, `eventType`, `status`
- **Persistence**: Log output (can be extended to local storage later).

#### 1.2.5 Item Marker (Find Items)
- **Purpose**: Store marked items for later searching.
- **Typical fields**:
  - `itemName`, `description`, optional `image` / `timestamp` / optional `location`
- **Persistence**: Local storage (SharedPreferences / local DB / files depending on implementation).

### 1.3 Data Storage Design

#### 1.3.1 SharedPreferences (Primary)
Used for:
- Language preference and locale persistence
- Accessibility toggles (vibration, screen reader, gesture options)
- TTS parameters (speech rate / pitch / volume)
- User session flags (guest mode, login state)

#### 1.3.2 Runtime Memory (Performance-Critical)
Used for:
- Camera frame buffers (CameraX pipeline)
- Detection results and multi-frame fusion buffers
- Short conversation history context

#### 1.3.3 File Storage
Used for:
- AI models/resources (e.g., `.tflite`)
- Optional caches or logs

### 1.4 Data Flow (High-Level)

#### 1.4.1 Environment Recognition Flow
Camera Frames → Preprocessing → TFLite Inference → Filtering/Multi-frame Fusion → Overlay Rendering → TTS Announcement

#### 1.4.2 Document / Currency Reading Flow
Camera Capture → OCR/Currency Analysis → Result Formatting → TTS Read-out → Display Result Text

#### 1.4.3 Voice / LLM Conversation Flow
User Speech → ASR → Command/Conversation Routing → (Optional) LLM Request → Response → TTS Output → Update Context

### 1.5 Data Design Diagram
- **Diagram**: `docs/data_design/Data_Model_Diagram.puml`

---

## 2. Software/Hardware Architectural Design (System Design)

### 2.1 Architecture Overview

Tonbo App uses a **layered architecture** to separate UI, business logic, data persistence, and AI/ML processing:
- Presentation Layer (Activities + Views)
- Business Logic Layer (Managers/Helpers/Detectors)
- Data Layer (Models + Storage/Persistence)
- AI/ML Layer (TFLite, ML Kit, optional LLM APIs)

This improves maintainability, testability, and allows independent evolution of AI components.

### 2.2 Software Architecture (Layers)

#### 2.2.1 Presentation Layer
**Role**: User interaction, navigation, accessibility UI behavior.
- Activities provide screens such as:
  - Main/Home, Environment Recognition, Document/Currency, Voice Command, Settings, Find Items, Gesture Management, etc.
- Custom Views render overlays and capture gestures.

#### 2.2.2 Business Logic Layer
**Role**: Encapsulate device/OS APIs and implement feature logic.
- **Managers** (commonly Singleton):
  - TTS feedback (`TTSManager`)
  - Vibration feedback (`VibrationManager`)
  - Language switching (`LocaleManager`)
  - Session management (`UserManager`)
  - Emergency handling (`EmergencyManager`)
  - Conversation context (`ConversationManager`)
- **Detectors / Helpers**:
  - Object detection (e.g., `YoloDetector` + `ObjectDetectorHelper`)
  - OCR (`OCRHelper`)
  - Currency recognition (`CurrencyDetector`)
  - Night mode optimization (`NightModeOptimizer`)

#### 2.2.3 Data Layer
**Role**: Persistence and data access.
- SharedPreferences for settings/session/language
- Optional local files/DB for larger or structured records

#### 2.2.4 AI/ML Layer
**Role**: AI inference and optional cloud intelligence.
- **TensorFlow Lite**: real-time object detection
- **Google ML Kit**: OCR text recognition
- **LLM APIs (Optional)**: deep conversation responses; keys kept out of Git via local config

### 2.3 Hardware Architecture (Device Requirements)

#### 2.3.1 Required Hardware
- **Camera**: environment recognition, OCR, currency recognition
- **Microphone**: voice command + continuous conversation
- **Speaker**: TTS output
- **Vibrator**: haptic feedback and emergency pattern

#### 2.3.2 Optional Hardware / Sensors
- Motion sensors (e.g., shake detection if enabled)
- GPS (travel assistant future enhancements)

#### 2.3.3 Network Requirement
- Optional: for LLM API calls (continuous conversation intelligence)
- Offline-first for core visual/OCR functions when possible

### 2.4 Key Design Patterns
- **Template Method**: shared accessibility behavior via base activity class.
- **Singleton**: managers that must remain single-instance (TTS, vibration, locale, emergency, conversation).
- **Strategy/Pluggability** (conceptual): different AI providers/engines (ASR/LLM) can be selected/configured.

### 2.5 System Architecture Diagram
- **Diagram**: `docs/architecture/System_Architecture_Diagram.puml`

---

## 3. User Interface Design

### 3.1 UI Design Principles (Accessibility-First)
- **Voice-first interactions**: critical actions are confirmed via TTS.
- **Haptic feedback**: button clicks/success/error/emergency patterns.
- **Large touch targets**: suitable for visually impaired users.
- **Predictable navigation**: consistent back navigation to Main screen.
- **High contrast / readable layout**: designed for low-vision users.
- **Multi-language**: Cantonese, Mandarin, English (UI + TTS synchronized).

### 3.2 UI Structure and Components

#### 3.2.1 Base Activity (Accessibility Backbone)
All main screens inherit common accessibility utilities (TTS, vibration, language manager, standard announcements).

#### 3.2.2 Main Screens (Examples)
- **Splash**: app launch and routing.
- **Main/Home**: hub screen to enter major modules; emergency button is always accessible.
- **Environment Recognition**: camera preview + overlay + audio announcements; includes night mode optimization.
- **Document/Currency**: capture + OCR + currency recognition; read-out of results.
- **Voice Command**: speech recognition + command execution; continuous conversation mode.
- **Settings**: speech parameters and accessibility toggles.

#### 3.2.3 Custom Views
- Detection overlay views (draw bounding boxes / labels)
- Gesture drawing view (gesture capture/recognition)

### 3.3 Navigation Design

#### 3.3.1 Navigation Flow (High-Level)
Splash → (Login/Gesture Input) → Main → Feature Activities → Back → Main

#### 3.3.2 Emergency Interaction (Always Available)
From Main screen: **long-press 3 seconds** triggers emergency dialing flow to **999** with voice + vibration feedback.

### 3.4 UI Design Diagrams
- **UI Component Hierarchy**: `docs/ui_design/UI_Component_Hierarchy.puml`
- **Navigation Flow**: `docs/ui_design/Navigation_Flow_Diagram.puml`

---

## 4. Diagram Index (Quick Links)

- Data Design:
  - `docs/data_design/Data_Model_Diagram.puml`
  - `docs/data_design/Data_Storage_Architecture.puml`
- System Design:
  - `docs/architecture/System_Architecture_Diagram.puml`
  - `docs/architecture/Technology_Stack_Architecture.puml`
- UI Design:
  - `docs/ui_design/UI_Component_Hierarchy.puml`
  - `docs/ui_design/Navigation_Flow_Diagram.puml`

