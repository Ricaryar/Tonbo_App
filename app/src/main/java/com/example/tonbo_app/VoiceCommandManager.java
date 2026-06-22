package com.example.tonbo_app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 語音命令管理器
 * 負責語音識別和命令解析
 */
public class VoiceCommandManager {
    
    private static final String TAG = "VoiceCommandManager";
    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;
    /** 疑問/推薦/介紹類語句不當作導航目的地，避免「好去處」等含「去」字誤觸出行 */
    private static final java.util.regex.Pattern SKIP_TRAVEL_PARSE_FOR_QUESTION =
            java.util.regex.Pattern.compile(
                    "什么|什麼|哪些|哪儿|哪里|哪裡|怎麼|怎么|怎樣|怎样|如何|為何|为何|為什麼|为什么|吗|嗎|\\?|？|好去处|好去處|推荐|推薦"
                            + "|(?i)\\b(what|where|which|how|why)\\b");
    private static VoiceCommandManager instance;
    
    private Context context;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    /** 主動停止時 Android 常回報 ERROR_CLIENT，需忽略以免誤觸重試 */
    private volatile boolean intentionalStop = false;
    private List<Locale> localeChain = new ArrayList<>();
    private int localeFallbackIndex = 0;
    /** 0=離線優先, 1=唔強制離線（語音包未裝時再試） */
    private int offlinePhase = 0;
    
    // ASR管理器（可選，用於支持多種ASR引擎）
    private ASRManager asrManager;
    private boolean useASRManager = false; // 是否使用ASRManager（默認false，使用原生）
    
    // 命令映射表 - 廣東話
    private Map<String, String> cantoneseCommands = new HashMap<>();
    // 命令映射表 - 英文
    private Map<String, String> englishCommands = new HashMap<>();
    // 命令映射表 - 普通話
    private Map<String, String> mandarinCommands = new HashMap<>();
    
    // 當前語言
    private String currentLanguage = "cantonese";
    
    /**
     * 語音命令回調接口
     */
    public interface VoiceCommandListener {
        void onCommandRecognized(String command, String originalText);
        void onListeningStarted();
        void onListeningStopped();
        void onError(String error);
        void onPartialResult(String partialText);
        /**
         * 當識別到非命令的語音時調用
         * @param text 識別到的文本
         */
        void onTextRecognized(String text);
    }
    
    private VoiceCommandListener commandListener;
    
    private VoiceCommandManager(Context context) {
        this.context = context.getApplicationContext();
        initializeCommands();
        initializeSpeechRecognizer();
        // 初始化ASRManager（可選）
        try {
            asrManager = new ASRManager(context);
            // 默認使用Android Native引擎（與當前行為一致）
            asrManager.setASREngine(ASRManager.ASREngine.ANDROID_NATIVE);
            if (asrManager.hasSherpaModel()) {
                asrManager.warmUpSherpa();
                Log.d(TAG, "检测到 Sherpa 模型，后台预热");
            }
            Log.d(TAG, "ASRManager初始化成功");
        } catch (Exception e) {
            Log.w(TAG, "ASRManager初始化失敗，將使用原生SpeechRecognizer: " + e.getMessage());
        }
    }
    
    public static synchronized VoiceCommandManager getInstance(Context context) {
        if (instance == null) {
            instance = new VoiceCommandManager(context);
        }
        return instance;
    }
    
    /**
     * 初始化命令映射表
     */
    private void initializeCommands() {
        cantoneseCommands = VoiceCommandBuilder.buildCantoneseCommands();
        englishCommands = VoiceCommandBuilder.buildEnglishCommands();
        mandarinCommands = VoiceCommandBuilder.buildMandarinCommands();
        
        Log.d(TAG, "命令映射表初始化完成");
    }
    
    /**
     * 初始化語音識別器
     */
    private void initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "準備接收語音");
                    isListening = true;
                    if (commandListener != null) {
                        commandListener.onListeningStarted();
                    }
                }
                
                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "開始說話");
                }
                
                @Override
                public void onRmsChanged(float rmsdB) {
                    // 音量變化
                }
                
                @Override
                public void onBufferReceived(byte[] buffer) {
                    // 接收緩衝區數據
                }
                
                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "說話結束");
                    isListening = false;
                }
                
                @Override
                public void onError(int error) {
                    isListening = false;

                    if (intentionalStop && error == SpeechRecognizer.ERROR_CLIENT) {
                        intentionalStop = false;
                        Log.d(TAG, "忽略主動停止引起的客戶端錯誤");
                        return;
                    }
                    intentionalStop = false;

                    String errorMessage = getErrorText(error);
                    Log.e(TAG, "語音識別錯誤: " + errorMessage + " (code=" + error + ")");

                    if (error == SpeechRecognizer.ERROR_CLIENT
                            || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        recreateSpeechRecognizer();
                    }

                    if (shouldTryNextLocale(error) && tryNextRecognitionLocale()) {
                        Log.w(TAG, "識別失敗，已切換備用設定");
                        return;
                    }

                    if (!isNetworkAvailable() && isOfflineSpeechError(error)) {
                        errorMessage = "離線語音未就緒";
                    }

                    if (commandListener != null) {
                        commandListener.onError(errorMessage);
                        commandListener.onListeningStopped();
                    }
                }
                
                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        // 改進：使用多個識別結果提高準確率
                        String bestMatch = findBestMatchFromMultipleResults(matches);
                        if (bestMatch != null) {
                            Log.d(TAG, "最佳匹配結果: " + bestMatch + " (從 " + matches.size() + " 個候選中選擇)");
                            processCommand(bestMatch);
                        } else {
                            // 如果多個結果都無法匹配，使用第一個結果
                            String recognizedText = matches.get(0);
                            Log.d(TAG, "使用第一個識別結果: " + recognizedText);
                            processCommand(recognizedText);
                        }
                    } else {
                        Log.w(TAG, "沒有識別結果");
                        if (commandListener != null) {
                            commandListener.onError("沒有識別結果，請重試");
                        }
                    }
                    isListening = false;
                    if (commandListener != null) {
                        commandListener.onListeningStopped();
                    }
                }
                
                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String partialText = matches.get(0);
                        Log.d(TAG, "部分識別結果: " + partialText);
                        if (commandListener != null) {
                            commandListener.onPartialResult(partialText);
                        }
                    }
                }
                
                @Override
                public void onEvent(int eventType, Bundle params) {
                    // 其他事件
                }
            });
            
            Log.d(TAG, "語音識別器初始化成功");
        } else {
            Log.e(TAG, "設備不支持語音識別");
        }
    }

    private void recreateSpeechRecognizer() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception e) {
                Log.w(TAG, "重建語音識別器時釋放失敗: " + e.getMessage());
            }
            speechRecognizer = null;
        }
        initializeSpeechRecognizer();
    }
    
    /**
     * 設置語言
     */
    public void setLanguage(String language) {
        this.currentLanguage = language;
        Log.d(TAG, "語言已設置為: " + language);
    }
    
    /**
     * 開始監聽語音命令
     */
    public void startListening() {
        // 檢查權限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "錄音權限未授予");
            if (commandListener != null) {
                commandListener.onError("需要錄音權限");
            }
            return;
        }
        
        if (isListening) {
            Log.w(TAG, "已經在監聽中");
            return;
        }

        refreshAsrEngineSelection();

        if (!useASRManager && !isNetworkAvailable() && asrManager != null) {
            if (asrManager.hasSherpaModel()) {
                String err = asrManager.getSherpaInitError();
                Log.e(TAG, "离线 Sherpa 未就绪: " + err);
                if (commandListener != null) {
                    commandListener.onError(err != null ? err : "離線語音引擎初始化中，請稍後再試");
                }
            } else {
                Log.e(TAG, "离线且无 Sherpa ASR 模型，无法语音识别");
                if (commandListener != null) {
                    commandListener.onError("離線語音未就緒，請運行 deploy_sherpa_asr.ps1");
                }
            }
            return;
        }
        
        // 如果使用ASRManager且已初始化
        if (useASRManager && asrManager != null) {
            startListeningWithASRManager();
            return;
        }
        
        // 否則使用原生SpeechRecognizer（默認行為）
        startListeningWithNative();
    }
    
    /**
     * 使用ASRManager開始監聽
     */
    private void startListeningWithASRManager() {
        Log.d(TAG, "使用ASRManager開始監聽 - 引擎: " + asrManager.getCurrentEngineInfo());
        
        asrManager.startRecognition(new ASRManager.ASRCallback() {
            @Override
            public void onResult(String text, float confidence) {
                Log.d(TAG, "ASR識別結果: " + text + " (置信度: " + confidence + ")");
                if (asrManager != null) {
                    asrManager.stopRecognition();
                }
                isListening = false;
                
                if (commandListener != null) {
                    commandListener.onListeningStopped();
                }
                
                // 處理識別結果
                processCommand(text);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "ASR錯誤: " + error);
                isListening = false;

                if (useASRManager && asrManager != null
                        && asrManager.getCurrentEngine() == ASRManager.ASREngine.SHERPA_ONNX) {
                    if (isNetworkAvailable()) {
                        Log.w(TAG, "Sherpa 失败，在线时回退 Google 语音识别");
                        useASRManager = false;
                        if (commandListener != null) {
                            commandListener.onListeningStopped();
                        }
                        startListeningWithNative();
                    } else if (commandListener != null) {
                        commandListener.onError(error);
                        commandListener.onListeningStopped();
                    }
                    return;
                }

                if (commandListener != null) {
                    commandListener.onError(error);
                    commandListener.onListeningStopped();
                }
            }
            
            @Override
            public void onPartialResult(String partialText) {
                Log.d(TAG, "ASR部分結果: " + partialText);
                
                if (commandListener != null) {
                    commandListener.onPartialResult(partialText);
                }
            }
        });
        
        isListening = true;
        if (commandListener != null) {
            commandListener.onListeningStarted();
        }
    }
    
    /**
     * 使用原生SpeechRecognizer開始監聽（默認行為）
     */
    private void startListeningWithNative() {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer();
        }

        if (speechRecognizer == null) {
            Log.e(TAG, "語音識別器初始化失敗");
            if (commandListener != null) {
                commandListener.onError("語音識別器初始化失敗");
            }
            return;
        }

        intentionalStop = false;

        boolean offline = !isNetworkAvailable();
        offlinePhase = offline ? 0 : 1;
        localeChain = SpeechRecognitionLocaleHelper.buildLocaleChain(currentLanguage, offline);
        localeFallbackIndex = 0;

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        applyRecognitionLocale(localeChain.get(localeFallbackIndex), shouldPreferOffline());

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        speechRecognizer.startListening(recognizerIntent);
        Log.d(TAG, "開始監聽語音命令（原生） - 語言: " + currentLanguage
                + ", 識別Locale: " + localeChain.get(localeFallbackIndex)
                + ", 有網絡: " + !offline
                + ", 離線優先: " + shouldPreferOffline());
    }

    private boolean shouldPreferOffline() {
        return !isNetworkAvailable() && offlinePhase == 0;
    }

    private boolean shouldTryNextLocale(int error) {
        if (SpeechRecognitionLocaleHelper.isLanguageError(error)) {
            return true;
        }
        return !isNetworkAvailable()
                && (error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT);
    }

    private boolean isOfflineSpeechError(int error) {
        return SpeechRecognitionLocaleHelper.isLanguageError(error)
                || error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT;
    }

    private void applyRecognitionLocale(Locale locale, boolean preferOffline) {
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline);
    }

    private boolean tryNextRecognitionLocale() {
        if (speechRecognizer == null || recognizerIntent == null || localeChain.isEmpty()) {
            return false;
        }
        localeFallbackIndex++;
        if (localeFallbackIndex < localeChain.size()) {
            return startWithCurrentLocale("切換備用識別語言");
        }
        if (offlinePhase == 0 && !isNetworkAvailable()) {
            offlinePhase = 1;
            localeFallbackIndex = 0;
            return startWithCurrentLocale("離線語音包可能未安裝，改為唔強制離線再試");
        }
        return false;
    }

    private boolean startWithCurrentLocale(String reason) {
        applyRecognitionLocale(localeChain.get(localeFallbackIndex), shouldPreferOffline());
        try {
            speechRecognizer.startListening(recognizerIntent);
            Log.w(TAG, reason + ": " + localeChain.get(localeFallbackIndex)
                    + ", 離線優先=" + shouldPreferOffline());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "啟動識別失敗: " + e.getMessage());
            return false;
        }
    }

    private void refreshAsrEngineSelection() {
        if (asrManager == null) {
            return;
        }
        if (!asrManager.hasSherpaModel()) {
            useASRManager = false;
            Log.w(TAG, "未检测到 Sherpa 模型");
            return;
        }
        if (asrManager.prepareSherpaIfAvailable()) {
            useASRManager = true;
            asrManager.setASREngine(ASRManager.ASREngine.SHERPA_ONNX);
            Log.i(TAG, "使用 Sherpa-ONNX 离线 ASR（粤语/普通话/英文）");
        } else {
            useASRManager = false;
            String err = asrManager.getSherpaInitError();
            Log.e(TAG, "Sherpa 未就绪: " + (err != null ? err : "初始化超时"));
        }
    }

    public void warmUpSherpa() {
        if (asrManager != null) {
            asrManager.warmUpSherpa();
        }
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            Log.w(TAG, "檢查網絡失敗: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 停止監聽
     */
    public void stopListening() {
        intentionalStop = true;
        isListening = false;

        // 如果使用ASRManager
        if (useASRManager && asrManager != null) {
            asrManager.stopRecognition();
            Log.d(TAG, "停止監聽（ASRManager）");

            if (commandListener != null) {
                commandListener.onListeningStopped();
            }
            return;
        }

        // 使用原生SpeechRecognizer — cancel 比 stopListening 更少觸發 ERROR_CLIENT
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                Log.d(TAG, "停止監聽（原生）");
            } catch (Exception e) {
                Log.w(TAG, "停止監聽失敗: " + e.getMessage());
            }
        }

        if (commandListener != null) {
            commandListener.onListeningStopped();
        }
    }
    
    /**
     * 從多個識別結果中找出最佳匹配
     */
    private String findBestMatchFromMultipleResults(ArrayList<String> matches) {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        
        String bestMatch = null;
        double bestScore = 0.0;
        
        // 遍歷所有識別結果，找出匹配度最高的
        for (String match : matches) {
            String command = matchCommand(match.toLowerCase());
            if (command != null) {
                // 計算匹配質量（第一個結果權重更高）
                double quality = 1.0 / (matches.indexOf(match) + 1);
                
                // 如果找到精確匹配，直接返回
                if (match.toLowerCase().contains(command.toLowerCase())) {
                    Log.d(TAG, "在多個結果中找到精確匹配: " + match + " -> " + command);
                    return match;
                }
                
                // 記錄最佳匹配
                if (quality > bestScore) {
                    bestScore = quality;
                    bestMatch = match;
                }
            }
        }
        
        return bestMatch;
    }
    
    /**
     * 處理命令 - 支持連續命令和AI助手模式
     */
    private void processCommand(String recognizedText) {
        // 優先檢查是否為出行/導航目的地語句（我要去X、Go to X 等）
        TravelParseResult parseResult = parseDestinationFromTravelPhrase(recognizedText);
        if (parseResult != null && parseResult.hasDestination()) {
            Log.d(TAG, "識別到出行目的地: " + parseResult.destination);
            if (commandListener instanceof ExtendedVoiceCommandListener) {
                ((ExtendedVoiceCommandListener) commandListener).onTravelDestinationRecognized(parseResult);
            }
            return;
        }
        // 優先使用 LLM 處理，只有在非常明確的命令時才執行命令
        // 檢查是否包含連續命令（連接詞）
        List<String> commands = splitContinuousCommands(recognizedText);
        
        if (commands.size() > 1) {
            // 多個命令，按順序執行（連續命令通常是明確的）
            Log.d(TAG, "檢測到連續命令，共 " + commands.size() + " 個: " + commands);
            executeContinuousCommands(commands, recognizedText);
        } else {
            // 語音命令模式：用寬鬆匹配執行指令（Sherpa 常輸出簡體/口語變體）
            String command = matchCommand(recognizedText);
            
            if (command != null) {
                Log.d(TAG, "匹配到命令: " + command + " (原始文本: " + recognizedText + ")");
                if (commandListener != null) {
                    commandListener.onCommandRecognized(command, recognizedText);
                }
            } else {
                Log.d(TAG, "未匹配到命令，使用 LLM 處理: " + recognizedText);
                if (commandListener != null) {
                    commandListener.onTextRecognized(recognizedText);
                }
            }
        }
    }
    
    /**
     * 嚴格匹配命令（僅在非常明確時才匹配）
     * 優先使用 LLM 處理，只有明確的命令才執行
     */
    private String matchCommandStrict(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // 高優先級：時間查詢語句直接命中，避免被嚴格匹配規則漏掉
        if (isTimeQuery(text)) {
            Log.d(TAG, "時間查詢快捷匹配: " + text + " -> tell_time");
            return "tell_time";
        }
        
        // 獲取當前語言的命令映射
        Map<String, String> commandMap;
        switch (currentLanguage) {
            case "english":
                commandMap = englishCommands;
                break;
            case "mandarin":
                commandMap = mandarinCommands;
                break;
            case "cantonese":
            default:
                commandMap = cantoneseCommands;
                break;
        }
        
        // 預處理：移除常見的語音識別干擾詞
        String lowerText = normalizeForMatch(text);
        
        // 對於長句子（超過3個字符），要求完全匹配或高相似度
        boolean isLongSentence = lowerText.length() > 3;
        
        // 1. 完全匹配（最高優先級）
        for (Map.Entry<String, String> entry : commandMap.entrySet()) {
            String key = normalizeForMatch(entry.getKey());
            if (lowerText.equals(key)) {
                Log.d(TAG, "完全匹配: " + key + " -> " + entry.getValue());
                return entry.getValue();
            }
        }
        
        // 2. 對於長句子，要求更高的匹配度（相似度 > 0.8）
        if (isLongSentence) {
            double threshold = 0.8; // 提高閾值，只匹配非常相似的
            
            String bestMatch = null;
            double bestScore = 0.0;
            
            for (Map.Entry<String, String> entry : commandMap.entrySet()) {
                String key = normalizeForMatch(entry.getKey());
                double similarity = calculateSimilarity(lowerText, key);
                
                if (similarity > bestScore && similarity >= threshold) {
                    bestScore = similarity;
                    bestMatch = entry.getValue();
                }
            }
            
            if (bestMatch != null) {
                Log.d(TAG, "嚴格匹配（長句子）: " + bestMatch + " (相似度: " + String.format("%.2f", bestScore) + ")");
                return bestMatch;
            }
            
            // 長句子：關鍵詞包含匹配（Sherpa 簡體/口語）
            for (Map.Entry<String, String> entry : commandMap.entrySet()) {
                String key = normalizeForMatch(entry.getKey());
                if (key.length() >= 2 && lowerText.contains(key)) {
                    Log.d(TAG, "嚴格匹配（長句包含）: " + key + " -> " + entry.getValue());
                    return entry.getValue();
                }
            }
            
            // 長句子未找到高相似度匹配，返回 null（使用 LLM）
            Log.d(TAG, "長句子未找到高相似度匹配，使用 LLM 處理");
            return null;
        } else {
            // 短句子：使用原來的匹配邏輯，但要求完全匹配或高相似度
            // 1. 精確匹配（包含，但要求命令關鍵詞佔文本的80%以上）
            for (Map.Entry<String, String> entry : commandMap.entrySet()) {
                String key = normalizeForMatch(entry.getKey());
                if (lowerText.contains(key) && key.length() >= lowerText.length() * 0.5) {
                    // 命令關鍵詞佔文本的80%以上，認為是明確匹配
                    Log.d(TAG, "嚴格匹配（短句子）: " + key + " -> " + entry.getValue());
                    return entry.getValue();
                }
            }
            
            // 2. 高相似度匹配（> 0.75）
            double threshold = 0.75;
            String bestMatch = null;
            double bestScore = 0.0;
            
            for (Map.Entry<String, String> entry : commandMap.entrySet()) {
                String key = normalizeForMatch(entry.getKey());
                double similarity = calculateSimilarity(lowerText, key);
                
                if (similarity > bestScore && similarity >= threshold) {
                    bestScore = similarity;
                    bestMatch = entry.getValue();
                }
            }
            
            if (bestMatch != null) {
                Log.d(TAG, "嚴格匹配（高相似度）: " + bestMatch + " (相似度: " + String.format("%.2f", bestScore) + ")");
                return bestMatch;
            }
        }
        
        // 未找到明確匹配，返回 null（使用 LLM）
        return null;
    }
    
    /**
     * 檢查是否為命令（供外部調用，如AI助手）
     */
    public String checkIfCommand(String text) {
        return matchCommand(text.toLowerCase());
    }
    
    /**
     * 分割連續命令（識別連接詞）
     */
    private List<String> splitContinuousCommands(String text) {
        List<String> commands = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return commands;
        }
        
        // 獲取當前語言的連接詞列表
        List<String> connectors = getConnectorsForLanguage(currentLanguage);
        
        // 轉換為小寫進行匹配
        String lowerText = text.toLowerCase();
        
        // 嘗試用連接詞分割
        String[] parts = null;
        String usedConnector = null;
        
        for (String connector : connectors) {
            if (lowerText.contains(connector)) {
                // 找到連接詞，按連接詞分割
                parts = lowerText.split(connector, -1);
                usedConnector = connector;
                break;
            }
        }
        
        if (parts != null && parts.length > 1) {
            // 成功分割，清理每個部分
            for (String part : parts) {
                String cleaned = part.trim();
                // 移除連接詞殘留和標點
                cleaned = cleaned.replaceAll("^[，,。.、\\s]+|[，,。.、\\s]+$", "");
                if (!cleaned.isEmpty()) {
                    commands.add(cleaned);
                }
            }
            Log.d(TAG, "使用連接詞 '" + usedConnector + "' 分割命令: " + commands);
        } else {
            // 沒有找到連接詞，檢查是否有逗號、頓號等標點
            if (text.contains("，") || text.contains(",") || text.contains("、")) {
                parts = text.split("[，,、]", -1);
                for (String part : parts) {
                    String cleaned = part.trim();
                    if (!cleaned.isEmpty()) {
                        commands.add(cleaned);
                    }
                }
                Log.d(TAG, "使用標點符號分割命令: " + commands);
            }
        }
        
        return commands;
    }
    
    /**
     * 獲取當前語言的連接詞列表
     */
    private List<String> getConnectorsForLanguage(String language) {
        List<String> connectors = new ArrayList<>();
        
        switch (language) {
            case "english":
                connectors.add(" then ");
                connectors.add(" and then ");
                connectors.add(" after that ");
                connectors.add(" next ");
                connectors.add(" followed by ");
                connectors.add(" then");
                connectors.add(" and then");
                break;
            case "mandarin":
                connectors.add("然後");
                connectors.add("接著");
                connectors.add("接下來");
                connectors.add("之後");
                connectors.add("再");
                connectors.add("跟著");
                break;
            case "cantonese":
            default:
                connectors.add("然後");
                connectors.add("接著");
                connectors.add("跟住");
                connectors.add("之後");
                connectors.add("再");
                connectors.add("跟住");
                connectors.add("跟住再");
                break;
        }
        
        return connectors;
    }
    
    /**
     * 執行連續命令
     */
    private void executeContinuousCommands(List<String> commandTexts, String originalText) {
        List<CommandPair> commandPairs = new ArrayList<>();
        
        // 匹配每個命令文本
        for (String cmdText : commandTexts) {
            String command = matchCommand(cmdText.toLowerCase());
            if (command != null) {
                commandPairs.add(new CommandPair(command, cmdText));
                Log.d(TAG, "連續命令匹配: '" + cmdText + "' -> " + command);
            } else {
                Log.w(TAG, "連續命令中無法匹配: '" + cmdText + "'");
            }
        }
        
        if (commandPairs.isEmpty()) {
            // 沒有匹配到任何命令
            if (commandListener != null) {
                commandListener.onError("無法識別連續命令中的任何指令");
            }
            return;
        }
        
        // 通知監聽器有連續命令
        if (commandListener != null) {
            // 使用第一個命令作為主命令，但傳遞完整信息
            commandListener.onCommandRecognized("continuous_commands", originalText);
            
            // 通過回調傳遞所有命令（需要擴展接口）
            if (commandListener instanceof ExtendedVoiceCommandListener) {
                ((ExtendedVoiceCommandListener) commandListener).onContinuousCommandsRecognized(commandPairs, originalText);
            }
        }
    }
    
    /**
     * 命令對（命令ID和原始文本）
     */
    public static class CommandPair {
        public final String command;
        public final String originalText;
        
        public CommandPair(String command, String originalText) {
            this.command = command;
            this.originalText = originalText;
        }
    }
    
    /**
     * 解析出行目的地語句，如「我要去天水围」「Go to Central」
     * @return TravelParseResult，若非出行語句則返回 null
     */
    public TravelParseResult parseDestinationFromTravelPhrase(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String t = text.trim();
        if (SKIP_TRAVEL_PARSE_FOR_QUESTION.matcher(t).find()) {
            return null;
        }
        java.util.regex.Pattern[] cnPatterns = {
            java.util.regex.Pattern.compile("(?:我要去|去|導航到|导航到|带我去|帶我去|到)\\s*([^，。？！]+)")
        };
        for (java.util.regex.Pattern p : cnPatterns) {
            java.util.regex.Matcher m = p.matcher(t);
            if (m.find()) {
                String dest = m.group(1).trim();
                if (!dest.isEmpty()) return TravelParseResult.simple(dest);
            }
        }
        java.util.regex.Pattern enPattern = java.util.regex.Pattern.compile(
            "(?:go to|navigate to|take me to|drive to|walk to)\\s+([^,?!.]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher enM = enPattern.matcher(t);
        if (enM.find()) {
            String dest = enM.group(1).trim();
            if (!dest.isEmpty()) return TravelParseResult.simple(dest);
        }
        return null;
    }

    /**
     * 擴展的命令監聽器接口（支持連續命令與出行目的地）
     */
    public interface ExtendedVoiceCommandListener extends VoiceCommandListener {
        void onContinuousCommandsRecognized(List<CommandPair> commands, String originalText);
        /** 識別到出行目的地時調用 */
        default void onTravelDestinationRecognized(TravelParseResult parseResult) {}
    }
    
    /**
     * 匹配命令 - 改進版，支持模糊匹配
     */
    private String matchCommand(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        // 高優先級：時間查詢語句直接命中，提升口語說法容錯
        if (isTimeQuery(text)) {
            Log.d(TAG, "時間查詢快捷匹配(普通): " + text + " -> tell_time");
            return "tell_time";
        }

        Map<String, String> commandMap;
        
        switch (currentLanguage) {
            case "english":
                commandMap = englishCommands;
                break;
            case "mandarin":
                commandMap = mandarinCommands;
                break;
            case "cantonese":
            default:
                commandMap = cantoneseCommands;
                break;
        }
        
        // 預處理：統一繁簡/口語，識別文本與命令關鍵詞用同一規則
        String lowerText = normalizeForMatch(text);
        
        // 對於長句子（超過5個字符），需要更嚴格的匹配條件
        // 避免將普通對話誤識別為命令（如"香港有咩地方好去"中的"好"不應該匹配為"yes"）
        boolean isLongSentence = lowerText.length() > 5;
        
        // 1. 精確匹配（優先）
        for (Map.Entry<String, String> entry : commandMap.entrySet()) {
            String key = normalizeForMatch(entry.getKey());
            
            // 對於長句子，要求命令關鍵詞佔文本的較大比例，或者完全匹配
            if (isLongSentence) {
                // 長句子：要求命令關鍵詞長度至少是文本長度的30%，或者完全匹配
                double keyRatio = (double) key.length() / lowerText.length();
                boolean isExactMatch = lowerText.equals(key);
                boolean isHighRatio = keyRatio >= 0.3;
                
                if (isExactMatch || (lowerText.contains(key) && isHighRatio)) {
                    Log.d(TAG, "精確匹配（長句子）: " + key + " -> " + entry.getValue() + " (比例: " + String.format("%.2f", keyRatio) + ")");
                    return entry.getValue();
                }
            } else {
                // 短句子：使用原來的包含匹配邏輯
                if (lowerText.contains(key)) {
                    Log.d(TAG, "精確匹配: " + key + " -> " + entry.getValue());
                    return entry.getValue();
                }
            }
        }
        
        // 2. 模糊匹配（容錯）- 改進版
        String bestMatch = null;
        double bestScore = 0.0;
        double threshold = 0.55; // 降低閾值到0.55，提高容錯率
        
        // 收集所有可能的匹配（相似度 > 閾值）
        List<MatchCandidate> candidates = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : commandMap.entrySet()) {
            String key = normalizeForMatch(entry.getKey());
            double similarity = calculateSimilarity(lowerText, key);
            
            if (similarity >= threshold) {
                candidates.add(new MatchCandidate(entry.getValue(), key, similarity));
                Log.d(TAG, "模糊匹配候選: " + key + " (相似度: " + String.format("%.2f", similarity) + ") -> " + entry.getValue());
            }
        }
        
        // 如果有多個候選，選擇相似度最高的
        if (!candidates.isEmpty()) {
            // 按相似度排序
            candidates.sort((a, b) -> Double.compare(b.similarity, a.similarity));
            bestMatch = candidates.get(0).command;
            bestScore = candidates.get(0).similarity;
            
            // 如果最高分和次高分差距很小（< 0.1），可能需要進一步判斷
            if (candidates.size() > 1) {
                double scoreDiff = candidates.get(0).similarity - candidates.get(1).similarity;
                if (scoreDiff < 0.1 && bestScore < 0.75) {
                    // 相似度太接近，可能不確定，但還是返回最高分
                    Log.w(TAG, "警告：多個候選相似度接近，選擇: " + bestMatch + " (分數: " + String.format("%.2f", bestScore) + ")");
                }
            }
            
            Log.d(TAG, "模糊匹配成功: " + bestMatch + " (相似度: " + String.format("%.2f", bestScore) + ")");
            return bestMatch;
        }
        
        // 3. 部分匹配（最後嘗試）- 僅對短句子使用，避免長句子誤匹配
        if (!isLongSentence) {
            for (Map.Entry<String, String> entry : commandMap.entrySet()) {
                String key = normalizeForMatch(entry.getKey());
                // 檢查是否包含關鍵詞（至少3個字符）
                if (key.length() >= 3 && lowerText.contains(key.substring(0, Math.min(3, key.length())))) {
                    Log.d(TAG, "部分匹配: " + key + " -> " + entry.getValue());
                    return entry.getValue();
                }
            }
        } else {
            // 長句子不進行部分匹配，避免誤識別，應該使用 LLM 處理
            Log.d(TAG, "長句子未匹配到命令，建議使用 LLM 處理: " + lowerText);
        }
        
        return null;
    }

    /**
     * 判斷是否為時間查詢（涵蓋常見廣東話/普通話/英文口語）
     */
    private boolean isTimeQuery(String text) {
        if (text == null) return false;
        String t = text.toLowerCase().trim();
        if (t.isEmpty()) return false;

        // 英文時間問法
        if (t.contains("what time") || t.contains("current time") || t.equals("time")) {
            return true;
        }

        // 中文時間問法（含廣東話口語：而家/依家/宜家）
        boolean hasNow = t.contains("現在") || t.contains("现在") || t.contains("而家") || t.contains("依家") || t.contains("宜家");
        boolean hasTimeWord = t.contains("時間") || t.contains("时间");
        boolean hasPoint = t.contains("幾點") || t.contains("几点") || t.contains("幾點鐘") || t.contains("几点钟") || t.contains("幾點了") || t.contains("几点了");

        // 例如：「而家時間幾點」「而家幾點」「现在几点」
        return hasPoint || (hasNow && hasTimeWord);
    }
    
    /**
     * 計算兩個字符串的相似度（改進版 - 使用 Levenshtein 距離和多重策略）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        
        // 移除空格和標點符號進行比較
        String clean1 = s1.replaceAll("[\\s\\p{Punct}]", "");
        String clean2 = s2.replaceAll("[\\s\\p{Punct}]", "");
        
        if (clean1.equals(clean2)) return 0.95; // 幾乎完全匹配
        
        // 1. 計算 Levenshtein 距離相似度（最準確）
        double levenshteinScore = calculateLevenshteinSimilarity(clean1, clean2);
        
        // 2. 計算最長公共子串相似度
        double lcsScore = calculateLCSimilarity(clean1, clean2);
        
        // 3. 計算字符集合相似度（容錯同音字）
        double charSetScore = calculateCharSetSimilarity(clean1, clean2);
        
        // 4. 計算開頭匹配度（語音識別通常開頭較準確）
        double prefixScore = calculatePrefixSimilarity(clean1, clean2);
        
        // 加權平均（Levenshtein 權重最高）
        double finalScore = (levenshteinScore * 0.4) + 
                           (lcsScore * 0.3) + 
                           (charSetScore * 0.2) + 
                           (prefixScore * 0.1);
        
        return finalScore;
    }
    
    /**
     * 計算 Levenshtein 距離相似度（編輯距離）
     */
    private double calculateLevenshteinSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }
    
    /**
     * Levenshtein 距離算法（動態規劃）
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        // 初始化
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }
        
        // 動態規劃計算
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1,      // 刪除
                                dp[i][j - 1] + 1),      // 插入
                        dp[i - 1][j - 1] + 1            // 替換
                    );
                }
            }
        }
        
        return dp[len1][len2];
    }
    
    /**
     * 計算最長公共子串相似度
     */
    private double calculateLCSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        int lcsLength = longestCommonSubstring(s1, s2);
        return (double) lcsLength / maxLen;
    }
    
    /**
     * 計算最長公共子串長度
     */
    private int longestCommonSubstring(String s1, String s2) {
        int maxLen = 0;
        int len1 = s1.length();
        int len2 = s2.length();
        
        for (int i = 0; i < len1; i++) {
            for (int j = 0; j < len2; j++) {
                int k = 0;
                while (i + k < len1 && j + k < len2 && 
                       s1.charAt(i + k) == s2.charAt(j + k)) {
                    k++;
                }
                maxLen = Math.max(maxLen, k);
            }
        }
        
        return maxLen;
    }
    
    /**
     * 計算字符集合相似度（容錯同音字和順序錯誤）
     */
    private double calculateCharSetSimilarity(String s1, String s2) {
        if (s1.length() == 0 && s2.length() == 0) return 1.0;
        if (s1.length() == 0 || s2.length() == 0) return 0.0;
        
        // 計算字符頻率
        Map<Character, Integer> freq1 = new HashMap<>();
        Map<Character, Integer> freq2 = new HashMap<>();
        
        for (char c : s1.toCharArray()) {
            freq1.put(c, freq1.getOrDefault(c, 0) + 1);
        }
        for (char c : s2.toCharArray()) {
            freq2.put(c, freq2.getOrDefault(c, 0) + 1);
        }
        
        // 計算共同字符數
        int commonChars = 0;
        int totalChars = Math.max(s1.length(), s2.length());
        
        for (char c : freq1.keySet()) {
            if (freq2.containsKey(c)) {
                commonChars += Math.min(freq1.get(c), freq2.get(c));
            }
        }
        
        return (double) commonChars / totalChars;
    }
    
    /**
     * 計算前綴相似度（語音識別通常開頭較準確）
     */
    private double calculatePrefixSimilarity(String s1, String s2) {
        int minLen = Math.min(s1.length(), s2.length());
        if (minLen == 0) return 0.0;
        
        int matchLen = 0;
        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                matchLen++;
            } else {
                break;
            }
        }
        
        return (double) matchLen / minLen;
    }
    
    /**
     * 設置命令監聽器
     */
    public void setCommandListener(VoiceCommandListener listener) {
        this.commandListener = listener;
    }
    
    /**
     * 設置是否使用ASRManager（支持多種ASR引擎）
     * @param useASRManager true=使用ASRManager, false=使用原生SpeechRecognizer（默認）
     */
    public void setUseASRManager(boolean useASRManager) {
        this.useASRManager = useASRManager;
        Log.d(TAG, "設置使用ASRManager: " + useASRManager);
    }
    
    /**
     * 設置ASR引擎（僅在使用ASRManager時有效）
     * @param engine ASR引擎類型
     */
    public void setASREngine(ASRManager.ASREngine engine) {
        if (asrManager != null) {
            asrManager.setASREngine(engine);
            Log.d(TAG, "設置ASR引擎: " + engine);
        } else {
            Log.w(TAG, "ASRManager未初始化，無法設置引擎");
        }
    }
    
    /**
     * 獲取可用的ASR引擎列表
     */
    public List<ASRManager.ASREngine> getAvailableASREngines() {
        if (asrManager != null) {
            return asrManager.getAvailableEngines();
        }
        return new ArrayList<>();
    }
    
    /**
     * 是否正在監聽
     */
    public boolean isListening() {
        return isListening;
    }
    
    /**
     * 獲取錯誤文本
     */
    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "音頻錯誤";
            case SpeechRecognizer.ERROR_CLIENT:
                return "客戶端錯誤";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "權限不足";
            case SpeechRecognizer.ERROR_NETWORK:
                return "網絡錯誤";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "網絡超時";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "沒有匹配結果";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "識別器忙碌";
            case SpeechRecognizer.ERROR_SERVER:
                return "服務器錯誤";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "語音超時";
            case 11: // ERROR_TOO_MANY_REQUESTS
                return "請求過於頻繁";
            case 12: // ERROR_LANGUAGE_NOT_SUPPORTED
                return "語言不支援";
            case 13: // ERROR_LANGUAGE_UNAVAILABLE
                return "語言不可用";
            case 14: // ERROR_CANNOT_CHECK_SUPPORT
                return "無法檢查語言支援";
            default:
                return "未知錯誤(" + errorCode + ")";
        }
    }
    
    /**
     * 統一識別文本與命令關鍵詞的格式（繁簡、口語、標點）
     */
    private String normalizeForMatch(String text) {
        if (text == null) return "";
        String processed = text.toLowerCase().trim();
        if ("english".equals(currentLanguage)) {
            return processed.replaceAll("\\s+", " ").trim();
        }
        processed = processed
            .replaceAll("[，,。.、！!？?\\s]+", "")
            .replaceAll("(請|帮|幫|幫我|帮我|幫你|帮你)", "")
            .replaceAll("(一下|一下下|一下兒|一下儿)", "")
            .replaceAll("(啊|呀|呢|吧|嗎|吗|啦|咯|吖|喎|嘅)", "");
        return normalizeChineseVariants(processed);
    }

    private String normalizeChineseVariants(String text) {
        return text
            .replace("識別", "识别").replace("識", "识")
            .replace("閱讀", "阅读").replace("閱", "阅")
            .replace("讀", "读").replace("環境", "环境")
            .replace("開", "开").replace("關", "关").replace("關", "关")
            .replace("設", "设").replace("設定", "设定").replace("設置", "设置")
            .replace("尋找", "寻找").replace("尋", "寻")
            .replace("協助", "协助").replace("緊急", "紧急")
            .replace("檢測", "检测").replace("檢", "检")
            .replace("掃描", "扫描").replace("掃", "扫")
            .replace("語言", "语言").replace("語", "语")
            .replace("時間", "时间").replace("幾點", "几点")
            .replace("鐘", "钟").replace("聲", "声")
            .replace("監聽", "监听").replace("監", "监")
            .replace("檔", "档").replace("檔案", "档案")
            .replace("頁", "页").replace("畫面", "画面")
            .replace("週圍", "周围").replace("週", "周")
            .replace("講", "讲").replace("話", "话")
            .replace("點", "点").replace("樣", "样")
            .replace("麼", "么").replace("嗎", "吗")
            .replace("係", "系").replace("喺", "在")
            .replace("搵", "找").replace("嘢", "东西")
            .replace("睇", "看").replace("嚟", "来")
            .replace("畀", "给").replace("俾", "给");
    }

    /**
     * 預處理文本，移除語音識別常見的干擾詞和錯誤
     */
    private String preprocessText(String text) {
        if (text == null) return "";
        if ("english".equals(currentLanguage)) {
            return text.replaceAll("\\s+", " ").trim();
        }
        return normalizeForMatch(text);
    }
    
    /**
     * 匹配候選類（用於排序和選擇最佳匹配）
     */
    private static class MatchCandidate {
        String command;
        String key;
        double similarity;
        
        MatchCandidate(String command, String key, double similarity) {
            this.command = command;
            this.key = key;
            this.similarity = similarity;
        }
    }
    
    /**
     * 釋放資源
     */
    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
            Log.d(TAG, "語音識別器已釋放");
        }
        isListening = false;
    }
}