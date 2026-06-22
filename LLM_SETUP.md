# LLM API Keys 配置指南

## 重要：保護你的 API Keys

API keys 是敏感信息，**永遠不要**提交到 Git 倉庫！

## 配置步驟

### 1. 創建本地配置文件

複製模板文件：
```bash
cp app/src/main/java/com/example/tonbo_app/LLMConfigLocal.java.template \
   app/src/main/java/com/example/tonbo_app/LLMConfigLocal.java
```

### 2. 編輯配置文件

打開 `app/src/main/java/com/example/tonbo_app/LLMConfigLocal.java`，填入你的 API keys：

```java
package com.example.tonbo_app;

public class LLMConfigLocal {
    // DeepSeek API Key
    public static final String DEEPSEEK_API_KEY = "sk-your-deepseek-key-here";
    
    // GLM-4-Flash (Zhipu) API Key
    public static final String ZHIPU_API_KEY = "your-zhipu-key-here";
}
```

### 3. 驗證配置

- ✅ `LLMConfigLocal.java` 已在 `.gitignore` 中，不會被提交
- ✅ 模板文件 `LLMConfigLocal.java.template` 可以安全提交
- ✅ 應用會自動從 `LLMConfigLocal.java` 讀取 API keys

## 你的 API Keys

請在 `LLMConfigLocal.java` 中配置你的 API keys（不要在此文件中顯示真實的 API keys）。

## 驗證

運行應用後，查看 Logcat：

```
LLMConfig: Initialized with GLM-4-Flash API key (free, reliable)
MainActivity: LLM 已啟用，提供商: GLM-4-Flash
```

如果看到警告：
```
LLM API keys not configured. Please create LLMConfigLocal.java with your API keys.
```

說明配置文件未創建，請按照上述步驟創建。

## 安全提醒

- ⚠️ **永遠不要**將 `LLMConfigLocal.java` 提交到 Git
- ⚠️ **永遠不要**在公開場所分享 API keys
- ✅ 使用 `.gitignore` 保護配置文件
- ✅ 定期檢查 API 使用情況
- ✅ 如果泄露，立即在服務商處重新生成

