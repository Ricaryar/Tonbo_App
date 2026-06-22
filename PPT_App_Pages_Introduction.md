# PPT Content - App Pages Introduction
## Tonbo - Each Function Page Detailed Introduction

**Purpose:** Detailed introduction for each app page/screen for PPT presentation

---

## Page 1: Splash Screen (启动画面)

### Slide Title: Splash Screen - Application Entry Point

### Content:

**Function:**
- First screen users see when launching the app
- Displays app logo and loading animation
- Checks user login status
- Navigates to appropriate screen (Login or Main)

**Key Features:**
- **Animated Logo:** Smooth animation during startup
- **Voice Welcome:** Automatic voice greeting in user's preferred language
- **Login Status Check:** Automatically determines if user needs to login
- **Smart Navigation:** Routes to LoginActivity or MainActivity based on status
- **Gesture Login Support:** Checks if gesture login is enabled

**User Experience:**
- Provides immediate feedback that app is loading
- Sets tone with voice greeting
- Seamless transition to main functionality

**Technical Implementation:**
- Uses Handler for delayed navigation
- Integrates with UserManager for login status
- Supports gesture login detection
- Multi-language voice prompts

**Accessibility Features:**
- Voice announcement of app status
- Screen reader compatible
- High contrast design

---

## Page 2: Login Screen (登录页面)

### Slide Title: Login Screen - User Authentication

### Content:

**Function:**
- User authentication and account management
- Supports login, registration, and guest mode
- First-time user onboarding

**Key Features:**
- **Three Access Modes:**
  1. **User Login:** Existing users enter credentials
  2. **Registration:** New users create accounts
  3. **Guest Mode:** Quick access without registration

**User Experience:**
- Clear options for different user types
- Voice guidance for each option
- Simple, accessible form design
- No complex navigation required

**Technical Implementation:**
- UserManager integration for authentication
- SharedPreferences for session management
- Form validation and error handling
- Multi-language support for all text

**Accessibility Features:**
- Voice prompts for each option
- Large touch targets (buttons)
- Screen reader labels
- Error messages announced via voice

**Design Highlights:**
- Clean, uncluttered interface
- High contrast text
- Clear button labels
- Consistent with app theme

---

## Page 3: Main Screen (主页面)

### Slide Title: Main Screen - Central Hub

### Content:

**Function:**
- Central navigation hub for all app features
- Quick access to 7 core functions
- Emergency assistance access
- Language switching

**Key Features:**
- **7 Core Function Cards:**
  1. Environment Recognition (环境识别)
  2. Document & Currency Reading (阅读助手)
  3. Voice Command (语音命令)
  4. Find Items (寻找物品)
  5. Instant Assistance (即时协助)
  6. Travel Assistant (出行协助)
  7. Gesture Management (手势管理)

**Layout Design:**
- **Fixed Top Section:** Title and system buttons always visible
- **Scrollable Content:** Function cards in scrollable area
- **Square Button Design:** Modern, accessible button layout
- **Center-Aligned Text:** Easy to read descriptions

**Special Features:**
- **Emergency Button:** Long-press 3 seconds to dial 999
- **Language Switch:** Quick access to language settings
- **Settings Access:** System settings button in top bar
- **Voice Welcome:** Automatic feature introduction

**User Experience:**
- Voice announcement of each function when selected
- Vibration feedback on button press
- Clear visual hierarchy
- Intuitive navigation

**Technical Implementation:**
- Card-based RecyclerView layout
- Intent navigation to feature activities
- Emergency button with countdown
- Language persistence

**Accessibility Features:**
- Voice announcement for each function
- Large touch targets (64-72dp)
- High contrast design
- Screen reader support
- Vibration feedback

---

## Page 4: Environment Recognition (环境识别)

### Slide Title: Environment Recognition - Real-time Object Detection

### Content:

**Function:**
- Real-time object detection using AI
- Identifies objects in camera view
- Voice announcement of detected objects
- Position information (left/center/right)

**Key Features:**
- **AI Technology:** TensorFlow Lite with SSD MobileNet V1
- **80 COCO Categories:** People, vehicles, animals, furniture, electronics
- **Multi-frame Fusion:** 3-frame confirmation for stability
- **Detection Accuracy:** 85-90%
- **False Positive Rate:** 10-15%

**Visual Elements:**
- **Detection Boxes:** Colored bounding boxes on camera preview
  - Green: High confidence (>0.7)
  - Yellow: Medium confidence (0.5-0.7)
  - Red: Low confidence (<0.5)
- **Text Labels:** Object names displayed on screen
- **Performance Stats:** Real-time detection metrics

**Smart Features:**
- **Intelligent Limiting:** Maximum 2 objects announced at once
- **Priority System:** Safety-related objects (people, vehicles) prioritized
- **Position Description:** "Person on the left", "Car in the center"
- **Night Mode:** Automatic optimization for low-light conditions
- **Deduplication:** Avoids repeating same object

**User Experience:**
- Real-time camera preview
- Immediate voice feedback
- Clear visual indicators
- Smooth performance (5+ FPS)

**Technical Implementation:**
- CameraX API for camera access
- TensorFlow Lite model inference
- Multi-frame fusion algorithm
- TTS for voice announcements
- Performance monitoring

**Accessibility Features:**
- Voice announcement of all detected objects
- Vibration feedback for detections
- Large detection boxes for visibility
- Screen reader compatible
- Adjustable detection sensitivity

**Performance Metrics:**
- Detection Time: <500ms average
- Frame Rate: 5+ FPS (mid-range devices)
- Accuracy: 85-90%
- False Positives: 10-15%

---

## Page 5: Document & Currency Reading (文档和货币阅读)

### Slide Title: Document & Currency Reading - OCR and Currency Detection

### Content:

**Function:**
- OCR text recognition from documents
- Hong Kong currency identification
- Voice playback of recognized content
- Separate modes for text and currency

**Key Features:**
- **OCR Technology:** Google ML Kit Text Recognition
- **Language Support:** Chinese and English text
- **Currency Detection:** Specialized Hong Kong currency system
- **Dual Mode Operation:** Separate text and currency modes

**Text Reading Mode:**
- **Scanning Area:** Visual guide for document alignment
- **Real-time Processing:** Instant text recognition
- **Voice Playback:** Reads recognized text aloud
- **Multi-language Output:** Supports Cantonese, Mandarin, English

**Currency Recognition Mode:**
- **Banknote Detection:** Identifies Hong Kong banknotes
- **Coin Detection:** Recognizes Hong Kong coins
- **Voice Announcement:** "One hundred dollar note", "Ten dollar coin"
- **Visual Confirmation:** Currency value displayed on screen

**User Experience:**
- Large scanning area indicator
- Clear mode selection buttons
- Immediate feedback on recognition
- Easy mode switching

**Technical Implementation:**
- Google ML Kit Text Recognition API
- Custom currency detection algorithm
- CameraX for image capture
- TTS for voice output
- Image preprocessing

**Accessibility Features:**
- Voice guidance for scanning area
- Audio confirmation of recognition
- Large buttons for mode selection
- Screen reader support
- Vibration feedback

**Use Cases:**
- Reading printed documents
- Identifying currency during transactions
- Reading signs and labels
- Verifying banknotes

---

## Page 6: Voice Command (语音命令)

### Slide Title: Voice Command - Intelligent Voice Control

### Content:

**Function:**
- Multi-language voice recognition
- Natural language command processing
- LLM integration for intelligent responses
- Continuous conversation mode

**Key Features:**
- **Multi-language Support:** Cantonese, Mandarin, English
- **LLM Integration:** GLM-4-Flash for intelligent conversation
- **Command Recognition:** Fuzzy matching algorithm
- **Continuous Mode:** Hands-free continuous dialogue

**Command Categories:**
1. **Navigation:** "打开环境识别", "返回主页"
2. **Status Queries:** "我在哪里", "当前功能"
3. **Screen Reading:** "读屏幕", "读所有项目"
4. **Confirmation/Cancellation:** "确认", "取消"
5. **Gesture Management:** "打开手势管理"
6. **Detection Control:** "暂停检测", "恢复检测"
7. **Quick Operations:** "快速帮助"

**LLM Features:**
- **Intelligent Responses:** Natural language understanding
- **Context Awareness:** Maintains conversation history
- **Multi-turn Conversation:** Supports continuous dialogue
- **Automatic Fallback:** Keyword matching if LLM unavailable

**User Experience:**
- Large microphone button
- Visual listening indicator
- Real-time recognition display
- Voice feedback for all actions

**Technical Implementation:**
- Android SpeechRecognizer API
- LLMClient for API integration
- Fuzzy matching algorithm
- Text preprocessing
- ConversationManager for context

**Accessibility Features:**
- Voice prompts for all states
- Vibration feedback
- Clear status indicators
- Screen reader compatible
- Adjustable recognition sensitivity

**Modes:**
- **Single Command Mode:** One command at a time
- **Continuous Conversation Mode:** Hands-free dialogue
- **Sleep Mode:** Auto-sleep after 30 seconds of silence
- **Wake-up Detection:** "开始对话" to resume

---

## Page 7: Find Items (寻找物品)

### Slide Title: Find Items - Personal Item Location

### Content:

**Function:**
- Mark personal items with camera
- Find marked items using computer vision
- Remember item locations
- Voice-guided search process

**Key Features:**
- **Marking Mode:** Capture and save item images
- **Finding Mode:** Scan environment to locate items
- **Location Memory:** Remembers where items were found
- **Voice Guidance:** Step-by-step search instructions

**Marking Process:**
1. Select "Mark Item" mode
2. Capture item image with camera
3. Add description (voice or text)
4. Save item to list

**Finding Process:**
1. Select "Find Item" from saved list
2. Camera scans environment
3. System matches item features
4. Voice announces when found
5. Provides location guidance

**User Experience:**
- Simple mode selection
- Clear camera preview
- Voice confirmation of actions
- Easy item list management

**Technical Implementation:**
- Computer vision for item matching
- Image storage and retrieval
- Feature extraction and comparison
- Location tracking

**Accessibility Features:**
- Voice guidance throughout process
- Vibration feedback
- Large buttons
- Screen reader support
- Clear status announcements

**Use Cases:**
- Finding keys
- Locating personal belongings
- Organizing items
- Quick item verification

---

## Page 8: Travel Assistant (出行协助)

### Slide Title: Travel Assistant - Navigation and Travel Information

### Content:

**Function:**
- Navigation assistance
- Route planning
- Traffic information
- Weather updates
- Emergency location sharing

**Key Features:**
- **Navigation:** Route planning and guidance
- **Traffic Info:** Real-time traffic conditions
- **Weather:** Current weather and forecasts
- **Location Share:** Emergency location sharing
- **Multi-language:** Cantonese, Mandarin, English

**Navigation Features:**
- Route calculation
- Turn-by-turn directions
- Voice guidance
- Alternative routes

**Information Services:**
- Traffic updates
- Weather conditions
- Public transport info
- Location services

**User Experience:**
- Simple function selection
- Voice output of all information
- Clear status indicators
- Easy navigation

**Technical Implementation:**
- Location services integration
- Third-party API integration (future)
- Voice synthesis for directions
- Map data processing

**Accessibility Features:**
- Voice announcements
- Large touch targets
- Screen reader support
- Vibration feedback
- Clear audio instructions

**Use Cases:**
- Planning routes
- Checking traffic
- Weather updates
- Emergency location sharing

---

## Page 9: Instant Assistance (即时协助)

### Slide Title: Instant Assistance - Volunteer Connection

### Content:

**Function:**
- Quick connection with volunteers
- Multiple communication methods
- Emergency assistance coordination
- Real-time support

**Key Features:**
- **Quick Call:** One-tap calling to volunteers
- **Quick Message:** Pre-set emergency messages
- **Video Call:** Visual assistance via video
- **Connection Status:** Real-time connection monitoring

**Communication Methods:**
1. **Phone Call:** Direct voice communication
2. **Text Message:** Pre-configured emergency messages
3. **Video Call:** Visual assistance when needed

**User Experience:**
- Large, accessible buttons
- Voice confirmation of actions
- Clear connection status
- Simple interface

**Technical Implementation:**
- Phone call intent
- SMS sending
- Video call integration (future)
- Permission management

**Accessibility Features:**
- Voice prompts for all actions
- Vibration feedback
- Large buttons
- Screen reader support
- Audio status updates

**Use Cases:**
- Need immediate help
- Request visual assistance
- Emergency communication
- Quick volunteer contact

---

## Page 10: Gesture Management (手势管理)

### Slide Title: Gesture Management - Custom Gesture Control

### Content:

**Function:**
- Create custom gestures
- Bind gestures to functions
- Quick access to features
- Gesture recognition

**Key Features:**
- **Gesture Creation:** Draw custom patterns on screen
- **Function Binding:** Link gestures to app functions
- **Gesture Library:** Save and manage multiple gestures
- **Quick Access:** Execute functions with gestures

**Creation Process:**
1. Select "Create Gesture"
2. Draw pattern on screen
3. Enter gesture name
4. Select function to bind
5. Save gesture

**Management Features:**
- View all saved gestures
- Edit gesture names
- Rebind functions
- Delete gestures
- Enable/disable gesture login

**User Experience:**
- Intuitive drawing interface
- Voice confirmation
- Easy gesture list management
- Quick function access

**Technical Implementation:**
- Touch event capture
- Gesture pattern storage
- Pattern matching algorithm
- Function binding system

**Accessibility Features:**
- Voice guidance for drawing
- Vibration feedback
- Large drawing area
- Screen reader support
- Audio confirmation

**Use Cases:**
- Quick function access
- Personalized shortcuts
- Gesture-based navigation
- Custom control patterns

---

## Page 11: System Settings (系统设置)

### Slide Title: System Settings - Accessibility Customization

### Content:

**Function:**
- Customize speech parameters
- Adjust accessibility options
- Language switching
- Reset settings

**Key Features:**
- **Speech Settings:**
  - Speech Rate: 0-200% adjustment
  - Pitch: 0-200% adjustment
  - Volume: 0-200% adjustment
- **Accessibility Options:**
  - Vibration Feedback: On/Off
  - Screen Reader Support: Enable/Disable
  - Gesture Operations: Enable/Disable
- **Language Settings:**
  - Cantonese (廣東話)
  - English
  - Mandarin (普通話)

**Settings Categories:**
1. **Speech Parameters:** Rate, pitch, volume
2. **Accessibility:** Vibration, screen reader, gestures
3. **Language:** Multi-language selection
4. **System:** Reset, about, help

**User Experience:**
- Clear category organization
- Slider controls for adjustments
- Voice test feature
- Instant preview of changes

**Technical Implementation:**
- SharedPreferences for persistence
- TTS parameter adjustment
- LocaleManager integration
- Settings validation

**Accessibility Features:**
- Voice labels for all settings
- Large controls
- Screen reader support
- Vibration feedback
- Audio confirmation

**Special Features:**
- **Voice Test:** Preview speech settings
- **Reset Function:** Restore defaults
- **Settings Persistence:** Auto-save all changes
- **Multi-language UI:** All settings in user's language

---

## Page 12: Video Call (视频通话)

### Slide Title: Video Call - Visual Assistance

### Content:

**Function:**
- Video call with volunteers
- Real-time visual assistance
- Camera switching
- Audio/video controls

**Key Features:**
- **Video Connection:** Real-time video streaming
- **Camera Control:** Switch between front/back camera
- **Audio Control:** Mute/unmute audio
- **Call Management:** Answer, end, hold calls

**Call Features:**
- **Video Preview:** See volunteer's video
- **Self View:** See your own video
- **Camera Switch:** Toggle cameras
- **Audio Toggle:** Mute/unmute
- **Call Controls:** End call, hold, etc.

**User Experience:**
- Large video display
- Accessible controls
- Voice announcements
- Clear status indicators

**Technical Implementation:**
- Video call API integration
- Camera management
- Audio/video streaming
- Permission handling

**Accessibility Features:**
- Voice announcements
- Large buttons
- Screen reader support
- Vibration feedback
- Audio status updates

**Use Cases:**
- Visual assistance needed
- Remote help requests
- Real-time guidance
- Emergency visual support

---

## Summary Slide: All Pages Overview

### Slide Title: Complete App Pages - Overview

### Content:

**Total Pages:** 12 Main Screens

**Page Categories:**

1. **Entry Pages (2):**
   - Splash Screen
   - Login Screen

2. **Core Function Pages (7):**
   - Main Screen (Hub)
   - Environment Recognition
   - Document & Currency Reading
   - Voice Command
   - Find Items
   - Travel Assistant
   - Instant Assistance

3. **Management Pages (2):**
   - Gesture Management
   - System Settings

4. **Communication Pages (1):**
   - Video Call

**Common Features Across All Pages:**
- ✅ Multi-language support (Cantonese, Mandarin, English)
- ✅ Voice feedback for all actions
- ✅ Vibration feedback
- ✅ Screen reader compatibility
- ✅ High contrast design
- ✅ Large touch targets
- ✅ Consistent navigation
- ✅ Accessibility-first design

**Design Consistency:**
- Unified color scheme
- Consistent button styles
- Standardized layouts
- Common navigation patterns

---

## Presentation Tips for Each Page

### General Approach:
1. **Show the Page:** Display screenshot or mockup
2. **Explain Function:** What the page does
3. **Highlight Features:** Key capabilities
4. **Demonstrate Use:** How users interact
5. **Emphasize Accessibility:** How it helps visually impaired users

### For Each Page Slide:
- **Time:** 30-45 seconds per page
- **Focus:** User benefits and accessibility
- **Visuals:** Screenshots or mockups
- **Demo:** If possible, show live demo

### Key Points to Emphasize:
- User-centered design
- Accessibility features
- Ease of use
- Multi-language support
- Voice and vibration feedback

---

**End of App Pages Introduction**
