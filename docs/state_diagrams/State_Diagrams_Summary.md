# State Diagrams 状态图总结
## State Diagrams Summary

---

## 📊 所有Activity状态图列表

### ✅ 已创建的状态图（共13个）

| # | Activity | 状态图文件 | 主要状态 |
|---|----------|-----------|---------|
| 1 | **SplashActivity** | `State_Diagram_Splash.puml` | Initializing → Animating → Waiting → CheckingLogin → MainScreen/GestureLogin |
| 2 | **LoginActivity** | `State_Diagram_Login.puml` | Initializing → UserTypeSelection → LoginForm → Validating → LoggingIn → MainScreen |
| 3 | **MainActivity** | `State_Diagram_Main.puml` | Initializing → HomeScreen → (各种功能页面) → EmergencyCountdown → EmergencyDialing |
| 4 | **RealAIDetectionActivity** | `State_Diagram_Environment_Recognition.puml` | Idle → Initializing → CameraStarting → Detecting → Processing → Announcing |
| 5 | **DocumentCurrencyActivity** | `State_Diagram_DocumentCurrency.puml` | Initializing → RequestingPermission → TextMode/CurrencyMode → Capturing → Processing → DisplayingResults |
| 6 | **VoiceCommandActivity** | `State_Diagram_Voice_Command.puml` | Idle → Listening → Processing → Executing → Feedback → ContinuousMode → SleepMode |
| 7 | **FindItemsActivity** | `State_Diagram_FindItems.puml` | Initializing → CameraReady → MarkingMode/FindingMode → Capturing → SavingItem/Scanning |
| 8 | **TravelAssistantActivity** | `State_Diagram_TravelAssistant.puml` | Initializing → Ready → (各种功能请求) → Processing → EmergencyLocationRequested |
| 9 | **InstantAssistanceActivity** | `State_Diagram_InstantAssistance.puml` | Initializing → Ready → CheckingConnection → Connected → QuickCall/QuickMessage/VideoCall |
| 10 | **GestureManagementActivity** | `State_Diagram_GestureManagement.puml` | Initializing → Ready → Drawing → GestureDrawn → Saving → GestureSaved |
| 11 | **GestureInputActivity** | `State_Diagram_GestureInput.puml` | Initializing → WaitingForGesture → Drawing → Recognizing → Matching → MainScreen |
| 12 | **SettingsActivity** | `State_Diagram_Settings.puml` | Initializing → LoadingSettings → Ready → AdjustingSettings → ApplyingSettings → SavingSettings |
| 13 | **VideoCallActivity** | `State_Diagram_VideoCall.puml` | Initializing → RequestingPermissions → InitializingCall → Connecting → VideoCalling → CallEnded |

---

## 📋 状态图详细说明

### 1. SplashActivity (启动页)
**文件**: `State_Diagram_Splash.puml`

**状态流程**:
- Initializing: onCreate()初始化
- Animating: 启动动画（Logo、应用名称、口号）
- Waiting: 等待3秒
- CheckingLogin: 检查登录状态和手势登录设置
- GestureLogin: 如果启用手势登录，跳转到GestureInputActivity
- MainScreen: 否则跳转到MainActivity

---

### 2. LoginActivity (登录页)
**文件**: `State_Diagram_Login.puml`

**状态流程**:
- Initializing: onCreate()初始化
- UserTypeSelection: 选择用户类型（需要帮助人士/志愿者）
- LoginForm: 显示登录表单
- Validating: 验证输入
- ValidatingInput: 验证邮箱/密码格式
- LoggingIn: 执行登录
- LoginSuccess: 登录成功
- LoginError: 登录失败
- Registering: 注册流程
- GuestMode: 访客模式
- MainScreen: 跳转到MainActivity

---

### 3. MainActivity (主页面)
**文件**: `State_Diagram_Main.puml`

**状态流程**:
- Initializing: onCreate()初始化
- HomeScreen: 主屏幕（显示所有功能按钮）
- 功能页面: EnvironmentRecognition, DocumentCurrency, VoiceCommand, FindItems, TravelAssistant, InstantAssistance, GestureManagement, Settings
- EmergencyCountdown: 紧急按钮长按倒计时（3秒）
- EmergencyDialing: 拨打999
- LanguageSwitching: 语言切换
- ShakeDetection: 摇一摇检测
- Paused: 页面暂停
- 所有功能页面都可以返回到HomeScreen

---

### 4. RealAIDetectionActivity (环境识别页)
**文件**: `State_Diagram_Environment_Recognition.puml`

**状态流程**:
- Idle: 空闲状态
- Initializing: 初始化检测器
- CameraStarting: 启动相机
- Detecting: 实时检测（每3帧处理一次）
- Processing: 处理检测结果（多帧融合、夜间模式优化）
- Announcing: 播报检测结果
- Stopped: 停止检测

---

### 5. DocumentCurrencyActivity (文档货币页)
**文件**: `State_Diagram_DocumentCurrency.puml`

**状态流程**:
- Initializing: onCreate()初始化
- RequestingPermission: 请求相机权限
- PermissionGranted: 权限授予
- CameraInitializing: 初始化相机
- TextMode: 文本模式（OCR识别）
- CurrencyMode: 货币模式（货币识别）
- Capturing: 捕获图像
- Processing: 处理图像
- OCRProcessing: OCR识别处理
- CurrencyProcessing: 货币检测处理
- DisplayingResults: 显示结果
- ReadingAloud: 语音朗读
- Clearing: 清除结果
- FlashToggling: 切换闪光灯

---

### 6. VoiceCommandActivity (语音命令页)
**文件**: `State_Diagram_Voice_Command.puml`

**状态流程**:
- Idle: 空闲状态
- Listening: 开始语音识别
- Processing: 处理识别结果（模糊匹配）
- Executing: 执行命令
- Feedback: 提供反馈
- ContinuousMode: 连续对话模式
- SleepMode: 休眠模式（30秒静音）
- 支持语音唤醒和退出

---

### 7. FindItemsActivity (找物品页)
**文件**: `State_Diagram_FindItems.puml`

**状态流程**:
- Initializing: onCreate()初始化
- CameraReady: 相机准备就绪
- MarkingMode: 标记物品模式
- FindingMode: 寻找物品模式
- Capturing: 捕获物品图像
- ImageCaptured: 图像已捕获
- EnteringName: 输入物品名称
- SavingItem: 保存物品到数据库
- ItemSaved: 物品已保存
- Scanning: 扫描相机画面
- Matching: 匹配物品
- ItemFound: 找到物品
- ItemNotFound: 未找到物品
- AnnouncingLocation: 播报物品位置
- ViewingItemsList: 查看已标记物品列表

---

### 8. TravelAssistantActivity (出行助手页)
**文件**: `State_Diagram_TravelAssistant.puml`

**状态流程**:
- Initializing: onCreate()初始化
- Ready: 准备就绪
- NavigationRequested: 导航功能请求
- RoutePlanningRequested: 路线规划请求
- TrafficInfoRequested: 交通信息请求
- WeatherRequested: 天气信息请求
- EmergencyLocationRequested: 紧急位置分享请求
- RequestingLocation: 获取GPS位置
- LocationReceived: 位置已获取
- SharingLocation: 分享位置（通过SMS）
- LocationShared: 位置已分享
- Processing: 处理功能请求（大部分功能显示"即将推出"）

---

### 9. InstantAssistanceActivity (即时协助页)
**文件**: `State_Diagram_InstantAssistance.puml`

**状态流程**:
- Initializing: onCreate()初始化
- Ready: 准备就绪
- CheckingConnection: 检查连接状态
- Connected: 已连接
- Disconnected: 未连接
- QuickCallRequested: 快速呼叫请求
- RequestingCallPermission: 请求通话权限
- PermissionGranted: 权限授予
- Dialing: 拨打电话
- Calling: 通话中
- CallEnded: 通话结束
- QuickMessageRequested: 快速消息请求
- RequestingSMSPermission: 请求短信权限
- SendingSMS: 发送短信
- SMSSent: 短信已发送
- VideoCallRequested: 视频通话请求
- RequestingCameraPermission: 请求相机权限
- VideoCalling: 视频通话中
- VideoCallEnded: 视频通话结束

---

### 10. GestureManagementActivity (手势管理页)
**文件**: `State_Diagram_GestureManagement.puml`

**状态流程**:
- Initializing: onCreate()初始化
- Ready: 准备就绪
- Drawing: 用户开始绘制手势
- GestureDrawn: 手势绘制完成
- Saving: 保存手势
- EnteringName: 输入手势名称
- BindingFunction: 绑定功能
- FunctionBound: 功能已绑定
- GestureSaved: 手势已保存到数据库
- Clearing: 清除手势
- ViewingGestures: 查看已保存手势
- EditingGesture: 编辑手势
- UpdatingGesture: 更新手势
- GestureUpdated: 手势已更新
- DeletingGesture: 删除手势
- GestureDeleted: 手势已删除
- TogglingLogin: 切换手势登录开关
- LoginEnabled: 启用手势登录
- LoginDisabled: 关闭手势登录

---

### 11. GestureInputActivity (手势输入页)
**文件**: `State_Diagram_GestureInput.puml`

**状态流程**:
- Initializing: onCreate()初始化
- WaitingForGesture: 等待用户绘制手势
- Drawing: 用户开始绘制
- GestureDrawn: 手势绘制完成
- Recognizing: 处理手势
- Matching: 匹配已保存的手势
- GestureMatched: 找到匹配的手势
- GestureNotMatched: 未找到匹配
- ExecutingFunction: 执行绑定的功能
- FunctionExecuted: 功能执行完成
- MainScreen: 跳转到MainActivity（如果匹配成功）
- 如果未匹配，返回WaitingForGesture重试

---

### 12. SettingsActivity (设置页)
**文件**: `State_Diagram_Settings.puml`

**状态流程**:
- Initializing: onCreate()初始化
- LoadingSettings: 从SharedPreferences加载设置
- Ready: 准备就绪
- AdjustingSpeechRate: 调整语音速度
- AdjustingSpeechPitch: 调整音调
- AdjustingSpeechVolume: 调整音量
- TogglingVibration: 切换震动反馈
- TogglingScreenReader: 切换读屏支持
- TogglingGestures: 切换手势控制
- ApplyingSettings: 应用设置更改
- SavingSettings: 保存设置到SharedPreferences
- SettingsSaved: 设置已保存
- TestingVoice: 测试语音设置
- PlayingTestVoice: 播放测试语音
- ResettingSettings: 重置设置
- ResettingToDefault: 重置为默认值

---

### 13. VideoCallActivity (视频通话页)
**文件**: `State_Diagram_VideoCall.puml`

**状态流程**:
- Initializing: onCreate()初始化
- RequestingPermissions: 请求相机/麦克风权限
- PermissionGranted: 权限授予
- InitializingCall: 初始化相机/麦克风
- Connecting: 开始视频通话
- Connected: 连接已建立
- VideoCalling: 视频通话进行中
- Muting: 静音
- Unmuting: 取消静音
- SwitchingCamera: 切换相机（前置/后置）
- EndingCall: 结束通话
- CallEnded: 通话已结束
- ConnectionFailed: 连接失败

---

## 📝 状态图特点

### 共同模式
1. **Initializing状态**: 所有Activity都有初始化状态
2. **Paused状态**: 支持onPause()和onResume()
3. **权限处理**: 需要权限的Activity都有权限请求流程
4. **错误处理**: 包含错误状态和重试机制
5. **返回导航**: 所有Activity都可以返回到MainActivity

### 特殊功能
- **Emergency**: MainActivity中的紧急按钮有3秒倒计时
- **Continuous Mode**: VoiceCommandActivity支持连续对话和休眠模式
- **Multi-mode**: DocumentCurrencyActivity支持文本和货币两种模式
- **Gesture Recognition**: GestureInputActivity和GestureManagementActivity支持手势识别和管理

---

**最后更新**: 2025年1月
