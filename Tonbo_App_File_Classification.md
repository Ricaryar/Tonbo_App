# Tonbo App 文件分類表

## 📁 項目結構概覽

```
Tonbo_App/
├── 📱 應用核心文件
├── 🎨 UI/UX 資源文件
├── 🔧 配置和構建文件
├── 📚 文檔和指南
├── 🧪 測試文件
└── 🏗️ 構建輸出文件
```

---

## 📱 **應用核心文件 (Core Application Files)**

### 🎯 **主要活動 (Main Activities)**
| 文件名 | 功能描述 | 語言支持 | 無障礙功能 |
|--------|----------|----------|------------|
| `MainActivity.java` | 主頁面，功能選擇入口 | 🇺🇸🇨🇳🇭🇰 | ✅ TTS, 震動, 語音命令 |
| `SplashActivity.java` | 啟動畫面 | 🇺🇸🇨🇳🇭🇰 | ✅ TTS |
| `EnvironmentActivity.java` | 環境識別功能 | 🇺🇸🇨🇳🇭🇰 | ✅ TTS, 震動, 語音命令 |
| `DocumentCurrencyActivity.java` | 文檔助手和貨幣識別 | 🇺🇸🇨🇳🇭🇰 | ✅ TTS, 震動, 語音命令 |
| `FindItemsActivity.java` | 尋找物品功能 | 🇺🇸🇨🇳🇭🇰 | ✅ TTS, 震動, 語音命令 |
| `SettingsActivity.java` | 系統設定 | 🇺🇸🇨🇳🇭🇰 | ✅ TTS, 震動, 語音命令 |
| `EmergencySettingsActivity.java` | 緊急求助設定 | 🇺🇸🇨🇳🇭🇰 | ✅ TTS, 震動, 語音命令 |

### 🏗️ **基礎架構 (Base Architecture)**
| 文件名 | 功能描述 | 繼承關係 | 核心功能 |
|--------|----------|----------|----------|
| `BaseAccessibleActivity.java` | 無障礙基礎活動類 | 所有Activity的父類 | TTS, 震動, 語音命令, 語言管理 |
| `LocaleManager.java` | 語言管理 | 單例模式 | 多語言切換, 語言持久化 |
| `TTSManager.java` | 語音合成管理 | 單例模式 | 多語言TTS, 語音隊列管理 |
| `VibrationManager.java` | 震動反饋管理 | 單例模式 | 震動模式, 震動強度控制 |

### 🎤 **語音功能 (Voice Features)**
| 文件名 | 功能描述 | 語言支持 | 錯誤處理 |
|--------|----------|----------|----------|
| `GlobalVoiceCommandManager.java` | 全局語音命令管理 | 🇺🇸🇨🇳🇭🇰 | ✅ 完整錯誤處理 |
| `VoiceCommandManager.java` | 語音命令處理 | 🇺🇸🇨🇳🇭🇰 | ✅ 權限檢查 |

### 🔍 **AI/ML 功能 (AI/ML Features)**
| 文件名 | 功能描述 | 模型支持 | 性能優化 |
|--------|----------|----------|----------|
| `ObjectDetectorHelper.java` | 物體檢測助手 | YOLO, SSD | ✅ GPU加速 |
| `YoloDetector.java` | YOLO檢測器 | YOLOv8 | ✅ TensorFlow Lite |
| `OCRHelper.java` | 光學字符識別 | Google ML Kit | ✅ 中英文雙語 |
| `CurrencyDetector.java` | 貨幣檢測 | 自定義算法 | ✅ 多幣種支持 |
| `ColorLightingAnalyzer.java` | 顏色和光照分析 | 自定義算法 | ✅ 實時分析 |

### 🎨 **UI 組件 (UI Components)**
| 文件名 | 功能描述 | 自定義功能 | 無障礙支持 |
|--------|----------|------------|------------|
| `DetectionOverlayView.java` | 檢測結果覆蓋層 | 自定義繪製 | ✅ 內容描述 |
| `FunctionAdapter.java` | 功能列表適配器 | RecyclerView | ✅ 語音導航 |
| `EmergencyContactsAdapter.java` | 緊急聯絡人適配器 | RecyclerView | ✅ 語音導航 |
| `HomeFunction.java` | 功能數據模型 | 數據類 | ✅ 序列化支持 |

### 🚨 **緊急功能 (Emergency Features)**
| 文件名 | 功能描述 | 權限需求 | 安全功能 |
|--------|----------|----------|----------|
| `EmergencyManager.java` | 緊急求助管理 | 電話, 簡訊 | ✅ 自動發送 |

### 📷 **相機功能 (Camera Features)**
| 文件名 | 功能描述 | API支持 | 兼容性 |
|--------|----------|----------|--------|
| `LegacyCameraHelper.java` | 舊版相機助手 | Camera API | ✅ API 21+ |

---

## 🎨 **UI/UX 資源文件 (UI/UX Resources)**

### 📱 **佈局文件 (Layout Files)**
| 文件名 | 對應Activity | 主要功能 | 無障礙支持 |
|--------|-------------|----------|------------|
| `activity_main.xml` | MainActivity | 主頁佈局 | ✅ 內容描述 |
| `activity_splash.xml` | SplashActivity | 啟動畫面 | ✅ 內容描述 |
| `activity_environment.xml` | EnvironmentActivity | 環境識別 | ✅ 內容描述 |
| `activity_document_currency.xml` | DocumentCurrencyActivity | 文檔助手 | ✅ 內容描述 |
| `activity_find_items.xml` | FindItemsActivity | 尋找物品 | ✅ 內容描述 |
| `activity_settings.xml` | SettingsActivity | 系統設定 | ✅ 內容描述 |
| `activity_emergency_settings.xml` | EmergencySettingsActivity | 緊急設定 | ✅ 內容描述 |
| `item_function.xml` | FunctionAdapter | 功能項目 | ✅ 內容描述 |
| `item_emergency_contact.xml` | EmergencyContactsAdapter | 聯絡人項目 | ✅ 內容描述 |

### 🎨 **圖標資源 (Icon Resources)**
| 文件名 | 用途 | 尺寸 | 格式 |
|--------|------|------|------|
| `ic_environment.xml` | 環境識別圖標 | Vector | XML |
| `ic_scan.xml` | 掃描圖標 | Vector | XML |
| `ic_search.xml` | 搜尋圖標 | Vector | XML |
| `ic_assistance.xml` | 協助圖標 | Vector | XML |
| `ic_voice_command.xml` | 語音命令圖標 | Vector | XML |
| `ic_tonbo_logo.xml` | 應用Logo | Vector | XML |
| `ic_launcher_*.xml` | 應用圖標 | Vector | XML |
| `ic_launcher_*.webp` | 應用圖標 | 多尺寸 | WebP |

### 🎨 **背景和樣式 (Backgrounds & Styles)**
| 文件名 | 用途 | 樣式類型 | 自適應 |
|--------|------|----------|--------|
| `button_circle_background.xml` | 圓形按鈕背景 | Shape | ✅ |
| `button_square_background.xml` | 方形按鈕背景 | Shape | ✅ |
| `emergency_button_background.xml` | 緊急按鈕背景 | Shape | ✅ |
| `emergency_button_selector.xml` | 緊急按鈕選擇器 | Selector | ✅ |
| `function_item_background.xml` | 功能項目背景 | Shape | ✅ |
| `card_background.xml` | 卡片背景 | Shape | ✅ |
| `edit_text_background.xml` | 輸入框背景 | Shape | ✅ |
| `input_background.xml` | 輸入背景 | Shape | ✅ |
| `result_background.xml` | 結果背景 | Shape | ✅ |
| `scan_frame_background.xml` | 掃描框背景 | Shape | ✅ |
| `settings_section_background.xml` | 設定區塊背景 | Shape | ✅ |
| `voice_button_background.xml` | 語音按鈕背景 | Shape | ✅ |
| `menu_button_background.xml` | 選單按鈕背景 | Shape | ✅ |
| `guidance_background.xml` | 引導背景 | Shape | ✅ |

---

## 🌐 **多語言資源 (Multilingual Resources)**

### 📝 **字符串資源 (String Resources)**
| 文件夾 | 語言 | 字符數 | 覆蓋率 |
|--------|------|--------|--------|
| `values/strings.xml` | 🇺🇸 英文 (默認) | ~300+ | 100% |
| `values-en/strings.xml` | 🇺🇸 英文 | ~300+ | 100% |
| `values-zh-rHK/strings.xml` | 🇭🇰 繁體中文 (廣東話) | ~300+ | 100% |
| `values-zh-rCN/strings.xml` | 🇨🇳 簡體中文 (普通話) | ~300+ | 100% |

### 🎨 **主題資源 (Theme Resources)**
| 文件名 | 用途 | API支持 | 特性 |
|--------|------|----------|------|
| `values/themes.xml` | 默認主題 | API 21+ | 基礎主題 |
| `values-night/themes.xml` | 夜間主題 | API 21+ | 深色模式 |
| `values-v23/themes.xml` | API 23+ 主題 | API 23+ | 狀態欄支持 |

### 🎨 **顏色資源 (Color Resources)**
| 文件名 | 顏色數量 | 用途 |
|--------|----------|------|
| `values/colors.xml` | 20+ | 應用主題色彩 |

---

## 🔧 **配置和構建文件 (Configuration & Build Files)**

### 📱 **應用配置 (App Configuration)**
| 文件名 | 用途 | 權限 | 功能 |
|--------|------|------|------|
| `AndroidManifest.xml` | 應用清單 | 相機, 麥克風, 電話, 簡訊 | 活動註冊, 權限聲明 |

### 🏗️ **構建配置 (Build Configuration)**
| 文件名 | 用途 | 版本 | 依賴 |
|--------|------|------|------|
| `build.gradle.kts` | 項目構建配置 | Gradle 8.0+ | Android SDK |
| `app/build.gradle.kts` | 應用構建配置 | Gradle 8.0+ | 依賴庫 |
| `gradle/libs.versions.toml` | 版本目錄 | Gradle 8.0+ | 依賴版本管理 |
| `gradle.properties` | Gradle屬性 | Gradle 8.0+ | 構建優化 |
| `settings.gradle.kts` | 項目設置 | Gradle 8.0+ | 模組配置 |
| `proguard-rules.pro` | 代碼混淆規則 | ProGuard | 代碼保護 |

### 🔧 **Gradle 包裝器 (Gradle Wrapper)**
| 文件名 | 用途 | 版本 |
|--------|------|------|
| `gradlew` | Unix/Linux 構建腳本 | Gradle 8.0+ |
| `gradlew.bat` | Windows 構建腳本 | Gradle 8.0+ |
| `gradle-wrapper.jar` | Gradle 包裝器 | Gradle 8.0+ |
| `gradle-wrapper.properties` | 包裝器配置 | Gradle 8.0+ |

---

## 🤖 **AI/ML 模型文件 (AI/ML Model Files)**

### 🧠 **機器學習模型 (Machine Learning Models)**
| 文件名 | 模型類型 | 大小 | 用途 |
|--------|----------|------|------|
| `ssd_mobilenet_v1.tflite` | SSD MobileNet | ~10MB | 物體檢測 |
| `yolov8n.tflite` | YOLOv8 Nano | ~6MB | 物體檢測 |
| `yolov8n.pt` | YOLOv8 PyTorch | ~6MB | 模型源文件 |

---

## 📚 **文檔和指南 (Documentation & Guides)**

### 📖 **項目文檔 (Project Documentation)**
| 文件名 | 內容 | 語言 | 用途 |
|--------|------|------|------|
| `README.md` | 項目說明 | 🇺🇸 | 項目介紹 |
| `ACCESSIBILITY_GUIDE.md` | 無障礙指南 | 🇺🇸 | 無障礙功能說明 |
| `GOOGLE_MAPS_API_SETUP.md` | Google Maps API 設置 | 🇺🇸 | API 配置指南 |

---

## 🧪 **測試文件 (Test Files)**

### 🔬 **單元測試 (Unit Tests)**
| 文件夾 | 測試類型 | 覆蓋範圍 |
|--------|----------|----------|
| `src/test/java/` | 單元測試 | 核心功能 |
| `src/androidTest/java/` | 儀器測試 | UI 測試 |

---

## 🏗️ **構建輸出文件 (Build Output Files)**

### 📦 **構建產物 (Build Artifacts)**
| 文件夾 | 內容 | 用途 |
|--------|------|------|
| `app/build/` | 構建中間文件 | 編譯過程 |
| `app/build/outputs/apk/` | APK 文件 | 應用安裝包 |
| `app/build/reports/` | 構建報告 | 代碼分析 |
| `build/reports/` | 項目報告 | 整體分析 |

---

## 📊 **文件統計 (File Statistics)**

### 📈 **文件數量統計**
| 類別 | 數量 | 百分比 |
|------|------|--------|
| **Java 源文件** | 24 | 15% |
| **XML 佈局文件** | 9 | 6% |
| **XML 資源文件** | 46 | 29% |
| **圖標文件** | 10 | 6% |
| **配置文件** | 8 | 5% |
| **文檔文件** | 3 | 2% |
| **測試文件** | 2 | 1% |
| **構建文件** | 50+ | 36% |
| **總計** | 150+ | 100% |

### 📊 **代碼行數統計**
| 類別 | 行數 | 百分比 |
|------|------|--------|
| **Java 代碼** | ~8,000+ | 70% |
| **XML 佈局** | ~2,000+ | 18% |
| **XML 資源** | ~1,000+ | 9% |
| **配置文件** | ~200+ | 2% |
| **文檔** | ~500+ | 1% |
| **總計** | ~11,700+ | 100% |

---

## 🎯 **功能模組分類 (Feature Module Classification)**

### 🏠 **核心模組 (Core Modules)**
- **MainActivity** - 主頁面
- **BaseAccessibleActivity** - 無障礙基礎
- **LocaleManager** - 語言管理
- **TTSManager** - 語音合成

### 🔍 **AI/ML 模組 (AI/ML Modules)**
- **ObjectDetectorHelper** - 物體檢測
- **OCRHelper** - 文字識別
- **CurrencyDetector** - 貨幣檢測
- **ColorLightingAnalyzer** - 環境分析

### 🎤 **語音模組 (Voice Modules)**
- **GlobalVoiceCommandManager** - 全局語音命令
- **VoiceCommandManager** - 語音命令處理

### 🚨 **緊急模組 (Emergency Modules)**
- **EmergencyManager** - 緊急求助
- **EmergencySettingsActivity** - 緊急設定

### ⚙️ **設定模組 (Settings Modules)**
- **SettingsActivity** - 系統設定
- **VibrationManager** - 震動管理

### 📷 **相機模組 (Camera Modules)**
- **LegacyCameraHelper** - 相機助手

---

## 🔄 **文件依賴關係 (File Dependencies)**

### 📊 **依賴圖**
```
MainActivity
├── BaseAccessibleActivity
│   ├── LocaleManager
│   ├── TTSManager
│   ├── VibrationManager
│   └── GlobalVoiceCommandManager
├── EnvironmentActivity
│   ├── ObjectDetectorHelper
│   ├── YoloDetector
│   └── ColorLightingAnalyzer
├── DocumentCurrencyActivity
│   ├── OCRHelper
│   └── CurrencyDetector
├── FindItemsActivity
├── SettingsActivity
└── EmergencySettingsActivity
    └── EmergencyManager
```

---

## 📋 **維護建議 (Maintenance Recommendations)**

### 🔧 **代碼維護**
1. **定期更新依賴庫版本**
2. **保持多語言資源同步**
3. **優化AI模型性能**
4. **加強無障礙功能測試**

### 🧪 **測試策略**
1. **單元測試覆蓋核心功能**
2. **UI測試驗證無障礙功能**
3. **多語言測試確保一致性**
4. **性能測試優化AI功能**

### 📚 **文檔維護**
1. **保持README更新**
2. **完善無障礙指南**
3. **添加API文檔**
4. **更新用戶手冊**

---

## 🎉 **總結**

Tonbo App 是一個功能完整的無障礙Android應用，包含：

- ✅ **24個Java源文件** - 完整的應用邏輯
- ✅ **9個佈局文件** - 響應式UI設計
- ✅ **46個資源文件** - 豐富的視覺資源
- ✅ **4種語言支持** - 真正的國際化
- ✅ **完整的無障礙功能** - 視障用戶友好
- ✅ **AI/ML功能** - 智能物體識別和文字識別
- ✅ **語音控制** - 多語言語音命令
- ✅ **緊急功能** - 安全求助系統

這個文件分類表可以幫助開發者快速了解項目結構，便於維護和擴展功能。
