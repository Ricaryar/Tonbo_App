package com.example.tonbo_app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

import kotlin.jvm.internal.FunctionAdapter;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private FunctionAdapter adapter;
    private TextToSpeech textToSpeech;
    private Button emergencyButton;
    private Vibrator vibrator;
    private final ArrayList<HomeFunction> functionList = new ArrayList<>();

    // 語言設置
    private String currentLanguage = "cantonese";
    private Locale cantoneseLocale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化震動反饋
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // 創建廣東話Locale (香港粵語)
        // 使用 zh-HK 而不是 yue-HK，因為 Android TTS 更好地支持 zh-HK
        cantoneseLocale = new Locale("zh", "HK");

        initViews();
        initTextToSpeech();
        setupFunctionList();
        setupRecyclerView();

        // 初始語音指引
        speakWelcomeMessage();
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                setLanguage(currentLanguage);
            } else {
                Toast.makeText(this, "語音初始化失敗", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void speakWelcomeMessage() {
        new android.os.Handler().postDelayed(() -> {
            String welcomeText = "瞳伴主頁面加載完成。當前有四個功能：環境識別、閱讀助手、尋找物品、即時協助。" +
                    "點擊選擇功能。底部有緊急求助按鈕，長按三秒發送求助信息。";
            speak(welcomeText, "Tonbo main page loaded. Four functions available. Tap to select.");
        }, 1000);
    }

    private void setLanguage(String language) {
        int result = TextToSpeech.LANG_MISSING_DATA;

        switch (language) {
            case "cantonese":
                // 優先使用香港廣東話 (zh-HK)
                result = textToSpeech.setLanguage(cantoneseLocale);
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 如果 zh-HK 不支持，嘗試使用台灣國語作為備選（而不是普通話）
                    Log.w("MainActivity", "⚠️ 廣東話不支持，嘗試使用台灣國語");
                    result = textToSpeech.setLanguage(Locale.TAIWAN);
                    
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        // 最後備選：繁體中文（可能是普通話）
                        Log.w("MainActivity", "⚠️ 台灣國語不支持，使用繁體中文");
                        result = textToSpeech.setLanguage(Locale.TRADITIONAL_CHINESE);
                    }
                }
                break;
                
            case "english":
                result = textToSpeech.setLanguage(Locale.ENGLISH);
                break;
                
            case "mandarin":
            default:
                // 普通話使用簡體中文 (zh-CN) 或繁體中文
                result = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = textToSpeech.setLanguage(Locale.TRADITIONAL_CHINESE);
                }
                break;
        }
        
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("MainActivity", "❌ 語言不支持: " + language);
        } else {
            Log.d("MainActivity", "✅ TTS 語言設置成功: " + language);
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        emergencyButton = findViewById(R.id.emergencyButton);

        // 設置緊急按鈕
        emergencyButton.setOnLongClickListener(v -> {
            vibrate(200); // 長震動反饋
            speak("緊急求助已發送，請保持冷靜，協助正在趕來", "Emergency help has been sent, please stay calm, assistance is on the way");
            Toast.makeText(MainActivity.this, "緊急求助已發送", Toast.LENGTH_LONG).show();

            // 這裡可以添加實際的緊急求助邏輯
            sendEmergencyAlert();
            return true;
        });

        emergencyButton.setOnClickListener(v -> {
            vibrate(50); // 短震動反饋
            speak("這是緊急求助按鈕，請長按三秒發送求助信息", "This is emergency button, long press for 3 seconds to send help request");
        });

        // 語言切換按鈕
        Button languageButton = findViewById(R.id.languageButton);
        languageButton.setOnClickListener(v -> {
            vibrate(50);
            toggleLanguage();
        });
    }

    private void sendEmergencyAlert() {
        // 實現緊急求助功能
        // 可以發送短信、撥打電話、通知緊急聯絡人等
        Log.d("Emergency", "Emergency alert sent");
    }

    private void vibrate(long milliseconds) {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(milliseconds);
        }
    }

    private void toggleLanguage() {
        switch (currentLanguage) {
            case "cantonese":
                currentLanguage = "english";
                setLanguage(currentLanguage);  // ✅ 先設置語言
                speak("已切換到英文", "Switched to English");  // ✅ 再播報（用新語言）
                updateLanguageButton("EN");
                break;
            case "english":
                currentLanguage = "mandarin";
                setLanguage(currentLanguage);  // ✅ 先設置語言
                speak("已切換到普通話", "Switched to Mandarin");  // ✅ 再播報（用新語言）
                updateLanguageButton("普");
                break;
            case "mandarin":
            default:
                currentLanguage = "cantonese";
                setLanguage(currentLanguage);  // ✅ 先設置語言
                speak("已切換到廣東話", "Switched to Cantonese");  // ✅ 再播報（用新語言）
                updateLanguageButton("廣");
                break;
        }
    }

    private void updateLanguageButton(String languageText) {
        Button languageButton = findViewById(R.id.languageButton);
        if (languageButton != null) {
            languageButton.setText(languageText);
        }
    }

    private void setupFunctionList() {
        functionList.add(new com.example.tonbo.HomeFunction("環境識別", "描述周圍環境和物體", R.drawable.ic_environment));
        functionList.add(new com.example.tonbo.HomeFunction("閱讀助手", "掃描文件和識別貨幣", R.drawable.ic_scan));
        functionList.add(new com.example.tonbo.HomeFunction("尋找物品", "尋找標記的個人物品", R.drawable.ic_search));
        functionList.add(new com.example.tonbo.HomeFunction("即時協助", "視訊連線志工協助", R.drawable.ic_assistance));
    }

    private void setupRecyclerView() {
        adapter = new FunctionAdapter(functionList, new FunctionAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(com.example.tonbo.HomeFunction function) {
                vibrate(100); // 點擊震動反饋

                switch (currentLanguage) {
                    case "cantonese":
                        speak("正在啟動" + function.getName(), null);
                        break;
                    case "english":
                        speak(null, "Starting " + getEnglishFunctionName(function.getName()));
                        break;
                    default:
                        speak("正在啟動" + function.getName(), null);
                        break;
                }

                // 根據功能啟動相應頁面
                handleFunctionClick(function.getName());
            }

            @Override
            public void onItemFocus(com.example.tonbo.HomeFunction function) {
                vibrate(30); // 焦點移動輕微震動
                String cantoneseText = "當前焦點：" + function.getName() + "，" + function.getDescription();
                String englishText = "Current focus: " + getEnglishFunctionName(function.getName()) + ", " +
                        getEnglishDescription(function.getDescription());
                speak(cantoneseText, englishText);
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    private void handleFunctionClick(String functionName) {
        switch (functionName) {
            case "環境識別":
                startEnvironmentActivity();
                break;
            case "閱讀助手":
                startDocumentCurrencyActivity();
                break;
            case "尋找物品":
                speak("尋找物品功能開發中", "Find items feature is under development");
                Toast.makeText(this, "尋找物品功能開發中", Toast.LENGTH_SHORT).show();
                break;
            case "即時協助":
                speak("即時協助功能開發中", "Live assistance feature is under development");
                Toast.makeText(this, "即時協助功能開發中", Toast.LENGTH_SHORT).show();
                break;
        }
    }


    private void startEnvironmentActivity() {
        try {
            Intent intent = new Intent(MainActivity.this, EnvironmentActivity.class);
            // 传递当前语言设置
            intent.putExtra("language", currentLanguage);
            startActivity(intent);
        } catch (Exception e) {
            speak("环境识别功能暂不可用", "Environment recognition is temporarily unavailable");
        }
    }

    private void startDocumentCurrencyActivity() {
        try {
            Intent intent = new Intent(MainActivity.this, DocumentCurrencyActivity.class);
            // 傳遞當前語言設置
            intent.putExtra("language", currentLanguage);
            startActivity(intent);
        } catch (Exception e) {
            speak("閱讀助手功能暫不可用", "Document assistant is temporarily unavailable");
        }
    }

    private String getEnglishFunctionName(String chineseName) {
        switch (chineseName) {
            case "環境識別": return "Environment Recognition";
            case "閱讀助手": return "Document Assistant";
            case "尋找物品": return "Find Items";
            case "即時協助": return "Live Assistance";
            default: return chineseName;
        }
    }

    private String getEnglishDescription(String chineseDescription) {
        switch (chineseDescription) {
            case "描述周圍環境和物體": return "Describe surroundings and objects";
            case "掃描文件和識別貨幣": return "Scan documents and recognize currency";
            case "尋找標記的個人物品": return "Find marked personal items";
            case "視訊連線志工協助": return "Video call with volunteers";
            default: return chineseDescription;
        }
    }

    private void speak(String cantoneseText, String englishText) {
        if (textToSpeech != null) {
            String textToSpeak = currentLanguage.equals("english") ?
                    (englishText != null ? englishText : cantoneseText) :
                    (cantoneseText != null ? cantoneseText : englishText);

            if (textToSpeak != null) {
                textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}