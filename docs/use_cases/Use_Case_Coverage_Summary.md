# Use Case Diagrams 功能覆盖总结
## Use Case Diagrams Feature Coverage Summary

---

## ✅ 功能覆盖检查

### 您列出的功能 vs 当前Use Case Diagrams

| 功能 | 状态 | 所在图表 | Use Case ID | 说明 |
|------|------|----------|-------------|------|
| **Environment** | ✅ | Overall System, Core Features | UC1 | 环境识别，包含物体检测、位置描述、夜间模式 |
| **Document** | ✅ | Overall System, Core Features | UC2 (部分) | 文档阅读，使用OCR识别 |
| **Currency** | ✅ | Overall System, Core Features | UC2 (部分) | 货币识别，香港货币检测 |
| **Emergency** | ✅ | Overall System, Core Features, Emergency专用图 | UC3 | 紧急求助，3秒长按激活 |
| **Voice Command** | ✅ | Overall System, Core Features | UC4 | 语音命令，模糊匹配 |
| **Continuous Conversation** | ✅ | Core Features | UC4 (扩展) | 连续对话模式，LLM集成 |
| **Gesture Management** | ✅ | Overall System | UC8 | 手势管理，创建和绑定手势 |
| **System Settings** | ✅ | Overall System | UC10 | 系统设置，语音参数调整 |
| **Language Switch** | ✅ | Overall System | UC11 | 语言切换，粤语/普通话/英语 |

---

## 📊 当前Use Case Diagrams结构

### 图1: Overall System Use Cases (整体系统用例图)
**文件**: `Use_Case_Diagram_1_Overall_System.puml`

**包含所有11个Use Cases**:
- ✅ UC1: Environment Recognition
- ✅ UC2: Document & Currency Reading (包含Document和Currency)
- ✅ UC3: Emergency Assistance
- ✅ UC4: Voice Command
- ✅ UC5: Find Items
- ✅ UC6: Travel Assistant
- ✅ UC7: Instant Assistance
- ✅ UC8: Gesture Management
- ✅ UC9: User Login/Register
- ✅ UC10: System Settings
- ✅ UC11: Language Switch

**用途**: 展示系统完整功能概览

---

### 图2: Core Features Use Cases (核心功能用例图)
**文件**: `Use_Case_Diagram_2_Core_Features.puml`

**包含4个核心功能的详细子用例**:

1. **Environment Recognition** ✅
   - Detect Objects
   - Get Position Description
   - Analyze Color & Lighting
   - Night Mode Activation
   - Display Results

2. **Document & Currency Reading** ✅
   - Read Document (Document功能)
   - Recognize Currency (Currency功能)
   - Read Text Aloud

3. **Emergency Assistance** ✅
   - Trigger Emergency Call
   - Long Press Activation

4. **Voice Command** ✅
   - Speak Command
   - Continuous Conversation ✅ (作为extend关系)
   - LLM Interaction

**用途**: 展示核心功能的详细实现流程

---

### 图3: Emergency Assistance Use Cases (紧急求助用例图)
**文件**: `Use_Case_Diagram_3_Emergency_Assistance.puml`

**包含紧急求助的完整流程**:
- Long Press Emergency Button (3 seconds)
- Countdown Feedback
- Dial Emergency Service (999)
- Voice Confirmation
- Vibration Alert
- Handle Permission

**用途**: 展示紧急功能的详细实现

---

### 图4: Document & Currency Use Cases (文档与货币用例图) ⭐ 新增
**文件**: `Use_Case_Diagram_4_Document_Currency.puml`

**Document Reading详细流程**:
- Open Document Reading Mode
- Switch to Text Mode
- Capture Document Image
- Preprocess Image
- Perform OCR Recognition
- Extract Text Blocks
- Format Text Output
- Read Text Aloud (Full Text / Line by Line)
- Display Results
- Handle OCR Error

**Currency Recognition详细流程**:
- Open Currency Recognition Mode
- Switch to Currency Mode
- Capture Currency Image
- Analyze Currency Features
- Detect Currency Type
- Identify Denomination
- Announce Currency Value
- Display Currency Info
- Handle Recognition Error

**Common Features**:
- Toggle Flash Light
- Clear Results
- Navigate Back
- Provide Voice Guidance
- Update Language UI

**用途**: 详细展示文档阅读和货币识别的完整流程

---

### 图5: Voice Command & Continuous Conversation Use Cases (语音命令与连续对话用例图) ⭐ 新增
**文件**: `Use_Case_Diagram_5_Voice_Conversation.puml`

**Voice Command详细流程**:
- Open Voice Command
- Start Speech Recognition
- Capture Audio Input
- Preprocess Text
- Fuzzy Match Command
- Execute Command
- Provide Audio Feedback
- Handle Command Error
- Stop Recognition

**Continuous Conversation详细流程**:
- Activate Continuous Mode
- Start Continuous Listening
- Process User Input
- Manage Conversation Context
- Generate AI Response
- Speak AI Response
- Enter Sleep Mode
- Wake Up from Sleep
- Exit Continuous Mode
- Handle Stop Command

**LLM Integration详细流程**:
- Check LLM Availability
- Send Request to LLM API
- Receive LLM Response
- Process LLM Response
- Update Conversation History
- Handle LLM Error

**ASR Processing详细流程**:
- Initialize ASR Engine
- Start Listening
- Process Partial Results
- Process Final Results
- Handle Recognition Error

**用途**: 详细展示语音命令和连续对话的完整流程，包括LLM集成和ASR处理

---

### 图6: System Settings & Language Switch Use Cases (系统设置与语言切换用例图) ⭐ 新增
**文件**: `Use_Case_Diagram_6_System_Settings.puml`

**System Settings详细流程**:
- Open Settings Screen
- Adjust Speech Rate
- Adjust Speech Pitch
- Adjust Speech Volume
- Toggle Vibration Feedback
- Toggle Screen Reader
- Toggle Gesture Controls
- Test Voice Settings
- Save Settings
- Load Settings
- Reset to Default Settings
- Apply Settings Immediately
- Provide Voice Confirmation

**Language Switch详细流程**:
- Tap Language Switch Button
- Cycle Through Languages
- Update Locale Manager
- Update UI Text
- Update TTS Language
- Save Language Preference
- Announce Current Language
- Update All Activities
- Reload Resources

**Settings Persistence**:
- Store in SharedPreferences
- Retrieve from SharedPreferences
- Validate Settings Values

**用途**: 详细展示系统设置和语言切换的完整流程

---

## 💡 当前状态

### ✅ 已完成
**所有您列出的功能都已经包含在Use Case Diagrams中！**

### 📊 Use Case Diagrams总数

现在共有 **6个Use Case Diagrams**：

1. ✅ Overall System Use Cases - 整体系统概览
2. ✅ Core Features Use Cases - 核心功能详细流程
3. ✅ Emergency Assistance Use Cases - 紧急求助详细流程
4. ✅ **Document & Currency Use Cases** - 文档与货币详细流程 ⭐ 新增
5. ✅ **Voice Command & Continuous Conversation Use Cases** - 语音命令与连续对话详细流程 ⭐ 新增
6. ✅ **System Settings & Language Switch Use Cases** - 系统设置与语言切换详细流程 ⭐ 新增

### 📝 新增图表说明

**图4-6** 提供了更详细的功能流程展示，包含：
- 详细的子用例分解
- 完整的include/extend关系
- 错误处理流程
- 系统交互流程

---

## 📝 结论

**所有功能都已覆盖！** 当前Use Case Diagrams结构符合中期报告要求：

- ✅ Environment - 在Overall和Core Features中
- ✅ Document - 在Overall和Core Features中（作为"Read Document"）
- ✅ Currency - 在Overall和Core Features中（作为"Recognize Currency"）
- ✅ Emergency - 在Overall、Core Features和专用图中
- ✅ Voice Command - 在Overall和Core Features中
- ✅ Continuous Conversation - 在Core Features中（作为Voice Command的extend）
- ✅ Gesture Management - 在Overall中
- ✅ System Settings - 在Overall中
- ✅ Language Switch - 在Overall中

**建议**: 保持当前的3个Use Case Diagrams结构，已经完整覆盖所有功能！

---

**最后更新**: 2025年1月
