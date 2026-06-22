# 項目設置指南

本指南幫助你在不同電腦上設置和運行 Tonbo App 項目。

## 📋 前置要求

- Android Studio Arctic Fox 或更新版本
- Android SDK API 21+
- Gradle 8.0+
- Java JDK 8 或更高版本

## 🔧 首次設置步驟

### 1. 克隆項目

```bash
git clone https://github.com/Charlieppy2/Tonbo_App.git
cd Tonbo_App3
```

### 2. 配置 Android SDK 路徑

項目需要知道你的 Android SDK 位置。有兩種方法：

#### 方法一：使用 `local.properties` 文件（推薦）

1. **複製模板文件**：
   ```bash
   cp local.properties.template local.properties
   ```

2. **編輯 `local.properties`**，設置你的 Android SDK 路徑：

   **macOS**:
   ```properties
   sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
   ```

   **Linux**:
   ```properties
   sdk.dir=/home/YOUR_USERNAME/Android/Sdk
   ```

   **Windows**:
   ```properties
   sdk.dir=C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
   ```

3. **如何找到你的 SDK 路徑**：
   - 打開 Android Studio
   - 進入 `Preferences` (macOS) 或 `Settings` (Windows/Linux)
   - 選擇 `Appearance & Behavior` → `System Settings` → `Android SDK`
   - 查看 "Android SDK Location" 欄位

#### 方法二：設置環境變量

**macOS/Linux**:
```bash
export ANDROID_HOME=/path/to/your/android/sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

**Windows (PowerShell)**:
```powershell
$env:ANDROID_HOME="C:\Users\YOUR_USERNAME\AppData\Local\Android\Sdk"
$env:PATH="$env:PATH;$env:ANDROID_HOME\tools;$env:ANDROID_HOME\platform-tools"
```

**Windows (CMD)**:
```cmd
set ANDROID_HOME=C:\Users\YOUR_USERNAME\AppData\Local\Android\Sdk
set PATH=%PATH%;%ANDROID_HOME%\tools;%ANDROID_HOME%\platform-tools
```

### 3. 配置 API Keys（可選）

如果需要使用 LLM 智能對話功能：

1. **複製模板文件**：
   ```bash
   cp app/src/main/java/com/example/tonbo_app/LLMConfigLocal.java.template \
      app/src/main/java/com/example/tonbo_app/LLMConfigLocal.java
   ```

2. **編輯 `LLMConfigLocal.java`**，填入你的 API keys：
   ```java
   public static final String DEEPSEEK_API_KEY = "your-deepseek-key-here";
   public static final String ZHIPU_API_KEY = "your-zhipu-key-here";
   ```

**注意**：`LLMConfigLocal.java` 文件已被 `.gitignore` 排除，不會提交到 Git。

### 4. 同步 Gradle

在 Android Studio 中：
- 點擊 "Sync Project with Gradle Files" 按鈕
- 或使用快捷鍵：`Ctrl+Shift+O` (Windows/Linux) 或 `Cmd+Shift+O` (macOS)

或在命令行：
```bash
./gradlew --refresh-dependencies
```

### 5. 編譯項目

```bash
# 編譯 Debug 版本
./gradlew assembleDebug

# 編譯 Release 版本
./gradlew assembleRelease
```

編譯完成後，APK 文件位於：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## 🚀 運行項目

### 使用 Android Studio

1. 打開 Android Studio
2. 選擇 `File` → `Open`，選擇項目目錄
3. 等待 Gradle 同步完成
4. 連接 Android 設備或啟動模擬器
5. 點擊 "Run" 按鈕（綠色三角形）或按 `Shift+F10`

### 使用命令行

```bash
# 安裝到已連接的設備
./gradlew installDebug

# 或直接運行
./gradlew installDebug && adb shell am start -n com.example.tonbo_app/.MainActivity
```

## 🔍 常見問題

### 問題 1: "SDK location not found"

**解決方案**：
- 確保已創建 `local.properties` 文件並設置了正確的 SDK 路徑
- 或設置 `ANDROID_HOME` 環境變量
- 檢查 SDK 路徑是否正確（路徑中不能有空格或特殊字符）

### 問題 2: "Gradle sync failed"

**解決方案**：
- 檢查網絡連接（Gradle 需要下載依賴）
- 嘗試清理項目：`./gradlew clean`
- 刪除 `.gradle` 目錄並重新同步
- 檢查 `gradle/wrapper/gradle-wrapper.properties` 中的 Gradle 版本

### 問題 3: "Build failed" 或編譯錯誤

**解決方案**：
- 確保 Android SDK 已安裝並更新到最新版本
- 檢查 Java JDK 版本（需要 JDK 8 或更高）
- 清理並重新構建：`./gradlew clean build`

### 問題 4: 找不到 Android SDK

**解決方案**：
- 如果使用 Android Studio，SDK 通常會自動安裝在默認位置
- 可以在 Android Studio 的 SDK Manager 中查看和更改 SDK 位置
- 確保已安裝必要的 SDK 組件（Platform Tools, Build Tools 等）

## 📝 重要文件說明

- **`local.properties`**: 本地配置（SDK 路徑），**不應提交到 Git**
- **`local.properties.template`**: 模板文件，可以提交到 Git
- **`LLMConfigLocal.java`**: 本地 API keys 配置，**不應提交到 Git**
- **`LLMConfigLocal.java.template`**: 模板文件，可以提交到 Git
- **`.gitignore`**: 已正確配置，排除敏感文件

## 🔄 在不同電腦間切換

當你在不同電腦上工作時：

1. **克隆項目**（如果還沒有）
2. **創建 `local.properties`** 並設置該電腦的 SDK 路徑
3. **創建 `LLMConfigLocal.java`**（如果需要 LLM 功能）
4. **同步 Gradle** 並編譯

**記住**：每個開發者都需要在自己的電腦上創建這些本地配置文件。

## 📚 更多資源

- [Android 開發者文檔](https://developer.android.com/)
- [Gradle 文檔](https://docs.gradle.org/)
- [Android Studio 用戶指南](https://developer.android.com/studio/intro)

---

如有問題，請查看項目的 [README.md](README.md) 或提交 Issue。
