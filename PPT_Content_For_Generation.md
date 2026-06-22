# PPT Content for Generation
## Tonbo - Intelligent Visual Assistant for Visually Impaired Users

**Instructions:** Copy the content below directly into your PPT creation tool (PowerPoint, Google Slides, etc.)

---

## Slide 1: Title Slide

**Title:** Tonbo (瞳伴) - Intelligent Visual Assistant Application

**Subtitle:** Final Year Project - Interim Report Presentation

**Content:**
- Project ID: IT-114118-02
- Supervisor: Sky Wong
- Course: IT114118 AI and Mobile Applications Development
- Institution: Hong Kong Institute of Vocational Education (Tuen Mun)
- Department: Department of Information Technology
- Academic Year: 2025/2026

**Team Members:**
- Liu Cuiru (240123448@stu.vtc.edu.hk) - Leader
- Li Xiaojing (240128886@stu.vtc.edu.hk)
- Hui Pui Yi (240017387@stu.vtc.edu.hk)
- Luo Feiyang (240146622@stu.vtc.edu.hk)

---

## Slide 2: Project Overview

**Title:** Project Overview

**Content:**

**Problem Statement:**
- Visually impaired users face daily challenges in:
  - Environmental navigation
  - Document and currency reading
  - Emergency situations
  - Information access

**Solution Approach:**
- AI-powered mobile assistant
- Computer vision + Voice synthesis
- Offline-first architecture
- Multi-language support

**Target Users:** Visually impaired individuals in Hong Kong
**Platform:** Android 5.0+ (API 21+)

---

## Slide 3: Project Objectives

**Title:** Project Objectives

**Content:**

**Primary Goals:**
1. Develop comprehensive Android application for visually impaired users
2. Implement real-time object detection (85%+ accuracy)
3. Provide multi-language support (Cantonese, Mandarin, English)
4. Ensure WCAG 2.1 Level AA compliance
5. Deliver production-ready application

**Impact Goals:**
- Enhance independence and quality of life
- Reduce reliance on human assistance
- Improve safety through emergency features
- Increase accessibility to information

---

## Slide 4: System Architecture

**Title:** System Architecture - High Level

**Visual:** Architecture Diagram (4 layers)

**Content:**

**Four-Layer Architecture:**

1. **Presentation Layer**
   - Activities, Fragments, Views
   - UI Components

2. **Business Logic Layer**
   - Managers, Helpers, Detectors
   - Command Processing

3. **Data Layer**
   - Models, Storage, Persistence
   - SharedPreferences, Local Database

4. **AI/ML Layer**
   - TensorFlow Lite (SSD MobileNet V1)
   - Google ML Kit (OCR)
   - LLM APIs (GLM-4-Flash)

**Key Technologies:**
- Java/Kotlin
- CameraX API
- Android TTS (Multi-language)
- LLM APIs (GLM-4-Flash)

---

## Slide 5: Core Functions Overview

**Title:** Core Functions Overview

**Content:**

**10 Core Functional Modules:**

1. ✅ Environment Recognition
2. ✅ Document & Currency Reading
3. ✅ Emergency Assistance
4. ✅ Voice Command
5. ✅ System Settings
6. ✅ Find Items
7. ✅ Travel Assistant
8. ✅ Instant Assistance
9. ✅ Gesture Management
10. ✅ User System

**Completion Status:** 98% Complete
- All 10 modules: 100% each
- Implementation Phase: 98% complete

---

## Slide 6: Use Case Diagram

**Title:** Use Case Diagram - Overall System

**Visual:** Use Case Diagram (11 use cases)

**Content:**

**11 Main Use Cases:**
- UC1: Environment Recognition
- UC2: Document Reading
- UC3: Emergency Assistance
- UC4: Voice Command
- UC5: Find Items
- UC6: Travel Assistant
- UC7: Instant Assistance
- UC8: Gesture Management
- UC9: User Login/Register
- UC10: System Settings
- UC11: Continuous Conversation

**Actors:**
- Visually Impaired User
- Guest User

---

## Slide 7: Class Diagram - Key Classes

**Title:** Class Diagram - Key Classes

**Visual:** Simplified Class Diagram

**Content:**

**Key Class Categories:**

**Activity Classes:**
- MainActivity
- RealAIDetectionActivity
- DocumentCurrencyActivity
- VoiceCommandActivity

**Manager Classes:**
- TTSManager
- VibrationManager
- LocaleManager
- UserManager
- ConversationManager

**Detector Classes:**
- ObjectDetectorHelper
- YoloDetector
- OCRHelper
- CurrencyDetector

**AI/ML Classes:**
- NightModeOptimizer
- ColorLightingAnalyzer
- LLMClient
- LLMConfig

---

## Slide 8: Core Technology - Object Detection

**Title:** Core Technology - Object Detection

**Content:**

**Technology:** TensorFlow Lite with SSD MobileNet V1

**Capabilities:**
- 80 COCO categories
  - People, vehicles, animals
  - Furniture, electronics
- Real-time detection
  - 5+ FPS on mid-range devices
- Multi-frame fusion
  - 3-frame confirmation for stability

**Performance Metrics:**
- Detection Accuracy: **85-90%**
- False Positive Rate: **10-15%**
- Average Detection Time: **<500ms**

**Innovation:**
- Night mode optimization
- Inference-based lighting detection

---

## Slide 9: Core Technology - OCR and Currency

**Title:** Core Technology - OCR and Currency Recognition

**Content:**

**OCR Technology:**
- Google ML Kit Text Recognition
- Chinese and English support
- Document reading with voice playback

**Currency Recognition:**
- Specialized Hong Kong currency detection
- Notes and coins identification
- Voice output in multiple languages

**Features:**
- Scanning area guidance
- Multi-language voice output
- Real-time processing

---

## Slide 10: Core Technology - LLM Integration

**Title:** Core Technology - LLM Integration

**Content:**

**LLM Provider:**
- GLM-4-Flash (Zhipu AI)
  - Primary provider
  - Free, excellent Chinese support

**Features:**
- Intelligent conversation
- Command understanding
- Context-aware responses
- Multi-turn conversation support
- Automatic fallback to keyword matching

**Integration:**
- Seamless with voice command system
- Priority-based processing (LLM first)
- Offline fallback available

---

## Slide 11: Emergency Assistance - Simplified Design

**Title:** Emergency Assistance - Simplified Design

**Content:**

**Design Philosophy:**
- Simplicity and speed for emergencies
- Reduced cognitive load
- Single-function design

**Functionality:**
- Long-press 3 seconds
- Directly dials 999 (Hong Kong emergency)
- No contact selection required
- Voice and vibration feedback

**Rationale:**
- Faster activation
- Better for emergency situations
- More reliable

---

## Slide 12: Voice Command System

**Title:** Voice Command System

**Content:**

**Multi-language Support:**
- Cantonese
- Mandarin
- English
- Automatic language detection

**Features:**
- Fuzzy matching algorithm
- Text preprocessing
- LLM integration
- Priority-based processing

**Command Categories:**
- Navigation
- Status queries
- Screen reading
- Confirmation/Cancellation
- Gesture management
- Detection control

---

## Slide 13: State Transition Diagram

**Title:** State Transition Diagram - Key States

**Visual:** State Diagram (Main application states)

**Content:**

**Key State Transitions:**

**Application Lifecycle:**
- Splash → Login → Main Screen

**Environment Recognition:**
- Idle → Initializing → Detecting → Processing → Announcing

**Voice Command:**
- Listening → Processing → Executing → Feedback

**Emergency Assistance:**
- Ready → Button-Pressed → Long-Press-Countdown → Dialing

---

## Slide 14: Sequence Diagram - Environment Recognition

**Title:** Sequence Diagram - Environment Recognition Flow

**Visual:** Sequence Diagram

**Content:**

**Flow:**
1. User opens environment recognition
2. System activates camera
3. Object detection every 3 frames
4. Multi-frame fusion filtering
5. Result processing and prioritization
6. Voice announcement with position

**Key Components:**
- CameraX API
- TensorFlow Lite
- Multi-frame fusion
- TTS Manager

---

## Slide 15: Technical Achievements

**Title:** Technical Achievements

**Content:**

**Performance Metrics:**

**Detection Accuracy:**
- Current: **85-90%**
- Initial: 70%
- Improvement: +15-20%

**False Positive Rate:**
- Current: **10-15%**
- Initial: 30%
- Reduction: -15%

**Performance:**
- Detection Time: **<500ms** (average)
- Frame Rate: **5+ FPS** (mid-range devices)

**Optimizations:**
- Multi-frame fusion
- Intelligent frame skipping
- Night mode optimization
- Memory management

---

## Slide 16: Challenges and Solutions

**Title:** Challenges and Solutions

**Content:**

**Challenge 1: Detection Accuracy Issues**
- Problem: Initial accuracy 70%, false positive rate 30%
- Solution: Multi-frame fusion, increased confidence thresholds
- Result: Accuracy 85-90%, false positives 10-15%

**Challenge 2: Performance on Low-end Devices**
- Problem: Detection lag on limited processing power
- Solution: Intelligent frame skipping, adaptive quality
- Result: Smooth operation on mid-range devices

**Challenge 3: Emergency Function Complexity**
- Problem: Original design too complex for emergencies
- Solution: Simplified to single-function (long-press 3 seconds)
- Result: Faster activation, reduced cognitive load

---

## Slide 17: Project Progress

**Title:** Project Progress

**Content:**

**Timeline:**
- Project Start: December 2024
- Mid-term Deadline: February 2, 2025
- Final Deadline: April 20, 2025

**Current Status:**
- Implementation Phase: **98% Complete**
- All 10 core modules: **100% each**
- AI/ML integration: **Complete**
- Multi-language support: **Complete**
- Accessibility features: **Complete**

**Remaining Tasks:**
- Final bug fixes and refinements (2%)
- Testing phase preparation
- Documentation completion

---

## Slide 18: Function Completion Matrix

**Title:** Function Completion Matrix

**Visual:** Table

**Content:**

| Module | Completion | Status |
|--------|-----------|--------|
| Environment Recognition | 100% | ✅ Complete |
| Document & Currency Reading | 100% | ✅ Complete |
| Emergency Assistance | 100% | ✅ Complete |
| Voice Command | 100% | ✅ Complete |
| System Settings | 100% | ✅ Complete |
| Find Items | 100% | ✅ Complete |
| Travel Assistant | 100% | ✅ Complete |
| Instant Assistance | 100% | ✅ Complete |
| Gesture Management | 100% | ✅ Complete |
| User System | 100% | ✅ Complete |
| **Overall** | **98%** | **🔄 In Progress** |

---

## Slide 19: UI Design - Accessibility First

**Title:** UI Design - Accessibility First

**Content:**

**Design Principles:**
- WCAG 2.1 Level AA compliance
- High contrast (black background, white text)
- Large touch targets (minimum 48dp)
- Voice feedback for all actions
- Consistent navigation patterns

**Key Features:**
- Multi-language TTS system
- Vibration feedback
- Screen reader support
- Simplified interface
- Customizable settings

---

## Slide 20: Summary and Next Steps

**Title:** Summary and Next Steps

**Content:**

**Key Achievements:**
- ✅ All 10 core modules implemented (100% each)
- ✅ Performance targets achieved or exceeded
- ✅ Complete accessibility design
- ✅ Multi-language support (3 languages)
- ✅ Night mode optimization
- ✅ LLM integration

**Next Steps:**
- Final bug fixes and refinements
- Testing phase (March 10 - April 21, 2025)
- User acceptance testing
- Deployment phase preparation

**Thank You!**

---

## Additional Visual Elements to Include

### For Slide 4 (System Architecture):
```
┌─────────────────────────────────────┐
│     Presentation Layer              │
│  (Activities, Fragments, Views)     │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│     Business Logic Layer            │
│  (Managers, Helpers, Detectors)     │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│     Data Layer                      │
│  (Models, Storage, Persistence)     │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│     AI/ML Layer                     │
│  (TensorFlow Lite, ML Kit, LLM)     │
└─────────────────────────────────────┘
```

### For Slide 6 (Use Case Diagram):
- Use standard UML use case diagram notation
- Show 11 use cases as ovals
- Show 2 actors (stick figures)
- Connect actors to use cases with lines

### For Slide 7 (Class Diagram):
- Show main classes as rectangles
- Show relationships with arrows
- Group by category (Activities, Managers, Detectors, AI/ML)

### For Slide 13 (State Diagram):
- Show states as rounded rectangles
- Show transitions as arrows with labels
- Include initial and final states

### For Slide 14 (Sequence Diagram):
- Show participants as vertical lifelines
- Show messages as horizontal arrows
- Include activation boxes

### For Slide 18 (Function Completion Matrix):
- Use a clean table format
- Use checkmarks (✅) for complete items
- Use progress indicator (🔄) for in-progress

---

## Design Tips for PPT Creation

1. **Color Scheme:**
   - Primary: Dark background (black/dark blue)
   - Text: White/light colors (high contrast)
   - Accent: Green for success, Yellow for warnings

2. **Fonts:**
   - Title: Bold, 32-44pt
   - Body: Regular, 18-24pt
   - Use sans-serif fonts (Arial, Calibri, Helvetica)

3. **Layout:**
   - Keep slides uncluttered
   - Use bullet points (max 5-6 per slide)
   - Include visuals where appropriate
   - Maintain consistent spacing

4. **Visuals:**
   - Use diagrams from your UML files
   - Include screenshots of the app
   - Use icons for visual interest
   - Keep charts simple and clear

5. **Animations:**
   - Use subtle transitions
   - Avoid distracting animations
   - Keep it professional

---

## Notes for Each Slide

### Slide 1: Title Slide
- Make it visually appealing
- Include project logo if available
- Use formal, professional design

### Slide 2-3: Overview and Objectives
- Keep text concise
- Use bullet points
- Highlight key points

### Slide 4: System Architecture
- **Important:** Include the architecture diagram
- Explain each layer briefly
- Show technology stack clearly

### Slide 5: Core Functions
- Use checkmarks (✅) for completed items
- Show completion percentage prominently
- Keep list organized

### Slide 6: Use Case Diagram
- **Important:** Include the actual UML diagram
- Reference the diagram file from your docs folder
- Explain key use cases

### Slide 7: Class Diagram
- **Important:** Include simplified class diagram
- Show main classes and relationships
- Group by category

### Slide 8-10: Core Technologies
- Include performance metrics
- Use numbers and percentages
- Highlight innovations

### Slide 11-12: Key Features
- Explain design decisions
- Show benefits
- Include user impact

### Slide 13-14: Diagrams
- **Important:** Include actual UML diagrams
- Reference diagram files from docs folder
- Explain the flow

### Slide 15: Technical Achievements
- Use comparison (before/after)
- Show improvements clearly
- Use charts/graphs if possible

### Slide 16: Challenges and Solutions
- Use problem-solution-result format
- Show learning and growth
- Be honest about challenges

### Slide 17-18: Progress
- Show completion clearly
- Use visual indicators (progress bars)
- Be specific about numbers

### Slide 19: UI Design
- Include screenshots if possible
- Explain accessibility features
- Show user-centric design

### Slide 20: Summary
- Recap key points
- Show forward momentum
- End on positive note

---

## File References for Diagrams

When creating the PPT, reference these diagram files from your project:

1. **System Architecture Diagram:**
   - `docs/architecture/System_Architecture_Diagram.puml`
   - `docs/architecture/Technology_Stack_Architecture.puml`

2. **Use Case Diagram:**
   - `docs/use_cases/Use_Case_Diagram_1_Overall_System_EN.puml`

3. **Class Diagram:**
   - `docs/class_diagrams/Class_Diagram_Overall.puml`

4. **State Diagrams:**
   - `docs/state_diagrams/State_Diagram_Main.puml`
   - `docs/state_diagrams/State_Diagram_Environment_Recognition.puml`

5. **Sequence Diagrams:**
   - `docs/sequence_diagrams/Sequence_Diagram_Environment_Recognition.puml`
   - `docs/sequence_diagrams/Sequence_Diagram_LLM_Conversation_1_Basic.puml`

**Note:** You can use PlantUML online renderer (http://www.plantuml.com/plantuml/uml/) to convert .puml files to images for your PPT.

---

## Quick Copy-Paste Format

For easy copying into PPT tools, here's a simplified version:

**Slide 1:**
Title: Tonbo (瞳伴) - Intelligent Visual Assistant Application
Subtitle: Final Year Project - Interim Report
Team: [4 team members listed]
Course: IT114118 AI and Mobile Applications Development

**Slide 2:**
Problem: Visually impaired users face challenges
Solution: AI-powered mobile assistant
Target: Visually impaired individuals in Hong Kong

**Slide 3:**
Goals: 5 primary goals listed
Impact: 4 impact goals listed

[Continue for all 20 slides...]

---

**End of PPT Content**
