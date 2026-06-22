package com.example.tonbo_app;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Enhanced streaming voice AI conversation system for visually impaired users
 * Supports continuous conversation, intelligent sleep mode, voice wake-up, and context management
 */
public class StreamingVoiceAI {
    private static final String TAG = "StreamingVoiceAI";
    
    // Sleep mode configuration (for visually impaired users - longer timeout)
    private static final long IDLE_SLEEP_TIMEOUT_MS = 30000; // 30 seconds of silence before sleep
    private static final long WAKE_UP_CHECK_INTERVAL_MS = 5000; // Check for wake-up every 5 seconds when sleeping
    private static final int MAX_RETRY_ATTEMPTS = 3; // Maximum retry attempts for errors
    private static final long RETRY_DELAY_MS = 1000; // Delay before retry
    
    private Context context;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private VoiceAIAssistant aiAssistant;
    private ConversationManager conversationManager; // Use ConversationManager for better context
    private ConversationResponseGenerator responseGenerator; // Use LLM-powered response generator
    private TTSManager ttsManager;
    private VibrationManager vibrationManager;
    
    private Handler mainHandler;
    private Handler sleepHandler; // Handler for sleep mode
    private Handler wakeUpHandler; // Handler for wake-up detection
    
    private boolean isListening = false;
    private boolean isProcessing = false;
    private boolean isSleeping = false; // Sleep mode state
    private String currentLanguage = "cantonese";
    
    // Sleep mode tracking
    private long lastActivityTime = 0;
    private Runnable sleepRunnable;
    private Runnable wakeUpCheckRunnable;
    
    // Error recovery
    private int consecutiveErrors = 0;
    private int retryAttempts = 0;
    private List<Locale> localeChain = new ArrayList<>();
    private int localeFallbackIndex = 0;
    private int offlinePhase = 0;
    
    // Callback interface
    public interface StreamingAICallback {
        void onPartialText(String partialText);
        void onFinalText(String finalText);
        void onAIResponse(String response);
        void onError(String error);
        void onListeningStateChanged(boolean isListening);
        void onSleepModeChanged(boolean isSleeping); // New: sleep mode state change
        void onWakeUpDetected(); // New: wake-up detected
        void onStopRequested(); // New: stop requested by user
    }
    
    private StreamingAICallback callback;
    
    // Wake-up commands (can be spoken to wake up from sleep mode)
    private static final String[] WAKE_UP_COMMANDS_CANTONESE = {
        "開始對話", "開始說話", "喚醒", "繼續對話", "開始", "你好"
    };
    private static final String[] WAKE_UP_COMMANDS_MANDARIN = {
        "开始对话", "开始说话", "唤醒", "继续对话", "开始", "你好"
    };
    private static final String[] WAKE_UP_COMMANDS_ENGLISH = {
        "start conversation", "wake up", "continue", "hello", "hey", "start"
    };
    
    // Stop/exit commands (can be spoken to stop continuous conversation mode)
    private static final String[] STOP_COMMANDS_CANTONESE = {
        "停止對話", "停止說話", "退出連續對話", "結束對話", "停止", "取消", "退出", "關閉"
    };
    private static final String[] STOP_COMMANDS_MANDARIN = {
        "停止对话", "停止说话", "退出连续对话", "结束对话", "停止", "取消", "退出", "关闭"
    };
    private static final String[] STOP_COMMANDS_ENGLISH = {
        "stop conversation", "stop talking", "exit continuous mode", "end conversation", 
        "stop", "cancel", "exit", "close", "quit"
    };
    
    public StreamingVoiceAI(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.sleepHandler = new Handler(Looper.getMainLooper());
        this.wakeUpHandler = new Handler(Looper.getMainLooper());
        this.aiAssistant = new VoiceAIAssistant(context);
        this.conversationManager = new ConversationManager();
        this.responseGenerator = new ConversationResponseGenerator(context);
        this.responseGenerator.setConversationManager(conversationManager);
        this.ttsManager = TTSManager.getInstance(context);
        this.vibrationManager = VibrationManager.getInstance(context);
        
        initializeSpeechRecognizer();
    }
    
    /**
     * 初始化語音識別器
     */
    private void initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true); // 啟用部分結果
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            
            // 設置連續識別模式
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500);
        }
    }
    
    /**
     * 設置回調
     */
    public void setCallback(StreamingAICallback callback) {
        this.callback = callback;
    }
    
    /**
     * Set language
     */
    public void setLanguage(String language) {
        this.currentLanguage = language;
        if (aiAssistant != null) {
            aiAssistant.setLanguage(language);
        }
        if (responseGenerator != null) {
            responseGenerator.setLanguage(language);
        }
        if (ttsManager != null) {
            ttsManager.changeLanguage(language);
        }
        
        // Update recognition language
        if (recognizerIntent != null) {
            configureRecognizerLocale();
        }
    }
    
    /**
     * 獲取語言代碼
     */
    private String getLocaleForLanguage(String language) {
        switch (language) {
            case "cantonese":
                return "zh-HK";
            case "mandarin":
                return "zh-CN";
            case "english":
                return "en-US";
            default:
                return "zh-HK";
        }
    }
    
    /**
     * Start continuous conversation (enhanced for visually impaired users)
     */
    public void startContinuousConversation() {
        if (isListening && !isSleeping) {
            Log.w(TAG, "Already in conversation");
            return;
        }
        
        if (speechRecognizer == null) {
            initializeSpeechRecognizer();
        }
        
        if (speechRecognizer == null) {
            String errorMsg = getLocalizedError("speech_recognition_unavailable");
            if (callback != null) {
                callback.onError(errorMsg);
            }
            announceError(errorMsg);
            return;
        }
        
        // Wake up from sleep mode if sleeping
        if (isSleeping) {
            wakeUpFromSleep();
            return;
        }
        
        isListening = true;
        isProcessing = false;
        isSleeping = false;
        consecutiveErrors = 0;
        retryAttempts = 0;
        lastActivityTime = System.currentTimeMillis();
        
        // Cancel any pending sleep
        cancelSleepMode();
        
        // Announce start (for visually impaired users)
        announceConversationStart();
        
        // 設置識別監聽器
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready to receive speech");
                lastActivityTime = System.currentTimeMillis();
                cancelSleepMode(); // Cancel sleep when ready
                
                if (callback != null) {
                    callback.onListeningStateChanged(true);
                }
                
                // Subtle vibration feedback for visually impaired users
                vibrationManager.vibrateClick();
            }
            
            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Speech beginning detected");
                isProcessing = true;
                lastActivityTime = System.currentTimeMillis();
                cancelSleepMode(); // Cancel sleep when user speaks
                
                // Vibration feedback for visually impaired users
                vibrationManager.vibrateClick();
            }
            
            @Override
            public void onRmsChanged(float rmsdB) {
                // 音量變化，可用於視覺反饋
            }
            
            @Override
            public void onBufferReceived(byte[] buffer) {
                // 接收緩衝區數據
            }
            
            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "說話結束");
            }
            
            @Override
            public void onError(int error) {
                Log.e(TAG, "Speech recognition error: " + getErrorText(error) + " (code=" + error + ")");
                consecutiveErrors++;
                isProcessing = false;

                if (shouldTryNextLocale(error) && tryNextRecognitionLocale()) {
                    Log.w(TAG, "識別失敗，已切換備用設定");
                    return;
                }
                
                // Don't stop listening for recoverable errors
                boolean shouldRetry = shouldRetryError(error);
                
                if (shouldRetry && retryAttempts < MAX_RETRY_ATTEMPTS) {
                    retryAttempts++;
                    Log.d(TAG, "Retrying... Attempt " + retryAttempts + "/" + MAX_RETRY_ATTEMPTS);
                    
                    // Retry after delay
                    mainHandler.postDelayed(() -> {
                        if (isListening && speechRecognizer != null) {
                            try {
                                speechRecognizer.startListening(recognizerIntent);
                            } catch (Exception e) {
                                Log.e(TAG, "Retry failed: " + e.getMessage());
                                handleFatalError(getErrorText(error));
                            }
                        }
                    }, RETRY_DELAY_MS);
                } else {
                    // Too many errors or fatal error
                    if (consecutiveErrors >= MAX_RETRY_ATTEMPTS) {
                        handleFatalError(getErrorText(error));
                    } else {
                        // Continue listening for non-fatal errors
                        if (isListening && speechRecognizer != null) {
                            mainHandler.postDelayed(() -> {
                                if (isListening && speechRecognizer != null) {
                                    try {
                                        speechRecognizer.startListening(recognizerIntent);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Restart failed: " + e.getMessage());
                                    }
                        }
                            }, RETRY_DELAY_MS);
                        }
                    }
                }
            }
            
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    Log.d(TAG, "Recognition result: " + recognizedText);
                    
                    // Reset error counter on successful recognition
                    consecutiveErrors = 0;
                    retryAttempts = 0;
                    lastActivityTime = System.currentTimeMillis();
                    
                    // Process recognized text
                    processRecognizedText(recognizedText);
                } else {
                    // No match - schedule sleep check
                    scheduleSleepMode();
                }
                
                isProcessing = false;
                
                // Auto-continue listening (continuous conversation mode)
                if (isListening && !isSleeping) {
                    mainHandler.postDelayed(() -> {
                        if (isListening && !isSleeping && speechRecognizer != null) {
                            try {
                            speechRecognizer.startListening(recognizerIntent);
                            } catch (Exception e) {
                                Log.e(TAG, "Continue listening failed: " + e.getMessage());
                            }
                        }
                    }, 500);
                }
            }
            
            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String partialText = matches.get(0);
                    Log.d(TAG, "部分結果: " + partialText);
                    
                    // 實時顯示部分結果
                    if (callback != null) {
                        callback.onPartialText(partialText);
                    }
                }
            }
            
            @Override
            public void onEvent(int eventType, Bundle params) {
                // 其他事件
            }
        });
        
        configureRecognizerLocale();
        // Start listening
        speechRecognizer.startListening(recognizerIntent);
        Log.d(TAG, "Continuous conversation started, locale=" + localeChain.get(localeFallbackIndex));
    }
    
    /**
     * Stop conversation
     */
    public void stopConversation() {
        isListening = false;
        isSleeping = false;
        cancelSleepMode();
        
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception e) {
                Log.e(TAG, "Stop listening failed: " + e.getMessage());
            }
        }
        
        if (callback != null) {
            callback.onListeningStateChanged(false);
            callback.onSleepModeChanged(false);
        }
        
        // Announce stop (for visually impaired users)
        String stopMsg = getLocalizedMessage("conversation_stopped");
        announceInfo(stopMsg);
        
        Log.d(TAG, "Conversation stopped");
    }
    
    /**
     * Process recognized text (with wake-up detection and context management)
     */
    private void processRecognizedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        mainHandler.post(() -> {
            // Check if this is a stop command (exit continuous conversation mode)
            if (isStopCommand(text)) {
                handleStopRequest();
                return;
            }
            
            // Check if this is a wake-up command (when in sleep mode)
            if (isSleeping && isWakeUpCommand(text)) {
                wakeUpFromSleep();
                return;
            }
            
            // Update UI with recognition result
            if (callback != null) {
                callback.onFinalText(text);
            }
            
            // Update activity time
            lastActivityTime = System.currentTimeMillis();
            cancelSleepMode();
            
            // Generate AI response with conversation context
            generateAIResponse(text);
        });
    }
    
    /**
     * Generate AI response (using LLM with conversation context)
     */
    private void generateAIResponse(String userInput) {
        if (responseGenerator == null) {
            Log.w(TAG, "Response generator not available");
            return;
        }
        
        // Use ConversationResponseGenerator with LLM support
        responseGenerator.generateResponseAsync(userInput, new ConversationResponseGenerator.ResponseCallback() {
            @Override
            public void onResponse(String response) {
                if (response != null && !response.isEmpty()) {
                    // Check if it's a command by checking if response contains command keywords
                    boolean isCommand = isCommandResponse(response);
                    String commandType = isCommand ? "command" : "chat";
                    
                    // Add to conversation history using ConversationManager
                    conversationManager.addTurn(userInput, response, isCommand, commandType);
                    
                    // Update activity time
                    lastActivityTime = System.currentTimeMillis();
                    
                    // Return response via callback
                    if (callback != null) {
                        callback.onAIResponse(response);
                    }
                    
                    // Speak response (for visually impaired users)
                    speakResponse(response);
                    
                    // Schedule sleep mode check after response
                    scheduleSleepMode();
                } else {
                    // Empty response - handle error
                    Log.w(TAG, "Empty response received");
                    String errorMsg = getLocalizedError("response_generation_failed");
                    if (callback != null) {
                        callback.onError(errorMsg);
                    }
                    announceError(errorMsg);
                }
            }
        });
    }
    
    /**
     * Check if response is a command (simple heuristic)
     */
    private boolean isCommandResponse(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        
        String lowerResponse = response.toLowerCase();
        // Check for common command indicators
        String[] commandIndicators = {
            "打開", "開啟", "開始", "停止", "關閉", "執行",
            "open", "start", "stop", "close", "execute",
            "已打開", "已開啟", "已開始", "已停止", "已關閉"
        };
        
        for (String indicator : commandIndicators) {
            if (lowerResponse.contains(indicator)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 語音播報回應
     */
    private void speakResponse(String response) {
        if (ttsManager == null || response == null || response.isEmpty()) {
            return;
        }
        
        // 停止當前播報（如果有的話）
        ttsManager.stopSpeaking();
        
        // 播報新回應
        String englishText = translateToEnglish(response);
        ttsManager.speak(response, englishText, true);
        
        // 震動反饋
        vibrationManager.vibrateNotification();
    }
    
    /**
     * 簡單的翻譯（用於TTS）
     */
    private String translateToEnglish(String text) {
        // 簡單的關鍵詞翻譯
        if (text.contains("你好")) return "Hello";
        if (text.contains("再見")) return "Goodbye";
        if (text.contains("謝謝")) return "Thank you";
        return text;
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
                return "無法識別";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "識別器忙碌";
            case SpeechRecognizer.ERROR_SERVER:
                return "服務器錯誤";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "語音超時";
            case 12:
                return "語言不支援";
            case 13:
                return "語言不可用";
            default:
                return "未知錯誤";
        }
    }

    private void configureRecognizerLocale() {
        if (recognizerIntent == null) return;
        boolean offline = !isNetworkAvailable();
        offlinePhase = offline ? 0 : 1;
        localeChain = SpeechRecognitionLocaleHelper.buildLocaleChain(currentLanguage, offline);
        localeFallbackIndex = 0;
        applyRecognitionLocale(localeChain.get(localeFallbackIndex), shouldPreferOffline());
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
            return false;
        }
    }
    
    /**
     * Get conversation history (from ConversationManager)
     */
    public List<ConversationManager.ConversationTurn> getConversationHistory() {
        if (conversationManager != null) {
            return conversationManager.getAllHistory();
        }
        return new ArrayList<>();
    }
    
    /**
     * Clear conversation history
     */
    public void clearHistory() {
        if (conversationManager != null) {
            conversationManager.clearHistory();
        }
    }
    
    /**
     * Check if text is a wake-up command
     */
    private boolean isWakeUpCommand(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        String lowerText = text.toLowerCase().trim();
        String[] wakeUpCommands;
        
        switch (currentLanguage) {
            case "mandarin":
                wakeUpCommands = WAKE_UP_COMMANDS_MANDARIN;
                break;
            case "english":
                wakeUpCommands = WAKE_UP_COMMANDS_ENGLISH;
                break;
            case "cantonese":
            default:
                wakeUpCommands = WAKE_UP_COMMANDS_CANTONESE;
                break;
        }
        
        for (String command : wakeUpCommands) {
            if (lowerText.contains(command.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if text is a stop command (exit continuous conversation)
     */
    private boolean isStopCommand(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        String lowerText = text.toLowerCase().trim();
        String[] stopCommands;
        
        switch (currentLanguage) {
            case "mandarin":
                stopCommands = STOP_COMMANDS_MANDARIN;
                break;
            case "english":
                stopCommands = STOP_COMMANDS_ENGLISH;
                break;
            case "cantonese":
            default:
                stopCommands = STOP_COMMANDS_CANTONESE;
                break;
        }
        
        for (String command : stopCommands) {
            if (lowerText.contains(command.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Handle stop request from user
     */
    private void handleStopRequest() {
        Log.d(TAG, "Stop command detected, exiting continuous conversation mode");
        
        // Announce stop (for visually impaired users)
        String stopMsg = getLocalizedMessage("stop_command_detected");
        announceInfo(stopMsg);
        vibrationManager.vibrateNotification();
        
        // Notify callback
        if (callback != null) {
            callback.onStopRequested();
        }
        
        // Stop conversation
        stopConversation();
    }
    
    /**
     * Schedule sleep mode (after idle timeout)
     */
    private void scheduleSleepMode() {
        cancelSleepMode();
        
        sleepRunnable = () -> {
            long timeSinceActivity = System.currentTimeMillis() - lastActivityTime;
            if (timeSinceActivity >= IDLE_SLEEP_TIMEOUT_MS && isListening && !isProcessing) {
                enterSleepMode();
            } else {
                // Reschedule check
                scheduleSleepMode();
            }
        };
        
        sleepHandler.postDelayed(sleepRunnable, IDLE_SLEEP_TIMEOUT_MS);
    }
    
    /**
     * Cancel sleep mode scheduling
     */
    private void cancelSleepMode() {
        if (sleepRunnable != null) {
            sleepHandler.removeCallbacks(sleepRunnable);
            sleepRunnable = null;
        }
    }
    
    /**
     * Enter sleep mode (to save battery)
     */
    private void enterSleepMode() {
        if (isSleeping) {
            return;
        }
        
        isSleeping = true;
        Log.d(TAG, "Entering sleep mode (idle timeout)");
        
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception e) {
                Log.e(TAG, "Stop listening for sleep failed: " + e.getMessage());
            }
        }
        
        if (callback != null) {
            callback.onSleepModeChanged(true);
        }
        
        // Announce sleep mode (for visually impaired users)
        String sleepMsg = getLocalizedMessage("sleep_mode_activated");
        announceInfo(sleepMsg);
        vibrationManager.vibrateNotification();
        
        // Start wake-up detection
        startWakeUpDetection();
    }
    
    /**
     * Wake up from sleep mode
     */
    private void wakeUpFromSleep() {
        if (!isSleeping) {
            return;
        }
        
        isSleeping = false;
        Log.d(TAG, "Waking up from sleep mode");
        
        stopWakeUpDetection();
        
        if (callback != null) {
            callback.onWakeUpDetected();
            callback.onSleepModeChanged(false);
        }
        
        // Announce wake-up (for visually impaired users)
        String wakeMsg = getLocalizedMessage("wake_up_detected");
        announceInfo(wakeMsg);
        vibrationManager.vibrateSuccess();
        
        // Resume listening
        if (speechRecognizer != null) {
            try {
                speechRecognizer.startListening(recognizerIntent);
                lastActivityTime = System.currentTimeMillis();
            } catch (Exception e) {
                Log.e(TAG, "Wake-up listening failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Start wake-up detection (lightweight listening for wake-up commands)
     */
    private void startWakeUpDetection() {
        stopWakeUpDetection();
        
        wakeUpCheckRunnable = () -> {
            if (isSleeping && speechRecognizer != null) {
                try {
                    // Lightweight listening for wake-up
                    speechRecognizer.startListening(recognizerIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Wake-up detection failed: " + e.getMessage());
                }
                
                // Schedule next check
                wakeUpHandler.postDelayed(wakeUpCheckRunnable, WAKE_UP_CHECK_INTERVAL_MS);
            }
        };
        
        wakeUpHandler.postDelayed(wakeUpCheckRunnable, WAKE_UP_CHECK_INTERVAL_MS);
    }
    
    /**
     * Stop wake-up detection
     */
    private void stopWakeUpDetection() {
        if (wakeUpCheckRunnable != null) {
            wakeUpHandler.removeCallbacks(wakeUpCheckRunnable);
            wakeUpCheckRunnable = null;
        }
    }
    
    /**
     * Check if error should be retried
     */
    private boolean shouldRetryError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Handle fatal error
     */
    private void handleFatalError(String errorText) {
        isListening = false;
        isSleeping = false;
        cancelSleepMode();
        stopWakeUpDetection();
        
        if (callback != null) {
            callback.onError(errorText);
            callback.onListeningStateChanged(false);
        }
        
        announceError(errorText);
    }
    
    /**
     * Announce conversation start (for visually impaired users)
     */
    private void announceConversationStart() {
        String startMsg = getLocalizedMessage("conversation_started");
        announceInfo(startMsg);
        vibrationManager.vibrateSuccess();
    }
    
    /**
     * Announce info message
     */
    private void announceInfo(String message) {
        if (ttsManager != null && message != null && !message.isEmpty()) {
            ttsManager.speak(message, message, false);
        }
    }
    
    /**
     * Announce error message
     */
    private void announceError(String message) {
        if (ttsManager != null && message != null && !message.isEmpty()) {
            ttsManager.speak(message, message, false);
        }
        vibrationManager.vibrateError();
    }
    
    /**
     * Get localized message
     */
    private String getLocalizedMessage(String key) {
        switch (key) {
            case "conversation_started":
                if ("english".equals(currentLanguage)) {
                    return "Continuous conversation mode started. You can speak freely. I will automatically pause after 30 seconds of silence. Say 'start conversation' to wake me up.";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "连续对话模式已启动。您可以自由说话。静音30秒后我会自动暂停。说'开始对话'可以唤醒我。";
                } else {
                    return "連續對話模式已啟動。您可以自由說話。靜音30秒後我會自動暫停。說'開始對話'可以喚醒我。";
                }
            case "conversation_stopped":
                if ("english".equals(currentLanguage)) {
                    return "Conversation stopped.";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "对话已停止。";
                } else {
                    return "對話已停止。";
                }
            case "sleep_mode_activated":
                if ("english".equals(currentLanguage)) {
                    return "Sleep mode activated. Say 'start conversation' to wake me up.";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "已进入睡眠模式。说'开始对话'可以唤醒我。";
                } else {
                    return "已進入睡眠模式。說'開始對話'可以喚醒我。";
                }
            case "wake_up_detected":
                if ("english".equals(currentLanguage)) {
                    return "Wake up detected. Conversation resumed.";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "检测到唤醒。对话已恢复。";
                } else {
                    return "檢測到喚醒。對話已恢復。";
                }
            case "stop_command_detected":
                if ("english".equals(currentLanguage)) {
                    return "Stop command detected. Exiting continuous conversation mode.";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "检测到停止命令。正在退出连续对话模式。";
                } else {
                    return "檢測到停止命令。正在退出連續對話模式。";
                }
            default:
                return "";
        }
    }
    
    /**
     * Get localized error message
     */
    private String getLocalizedError(String key) {
        switch (key) {
            case "speech_recognition_unavailable":
                if ("english".equals(currentLanguage)) {
                    return "Speech recognition is not available.";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "语音识别不可用。";
                } else {
                    return "語音識別不可用。";
                }
            case "response_generation_failed":
                if ("english".equals(currentLanguage)) {
                    return "Failed to generate response. Please try again.";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "生成回應失敗。請重試。";
                } else {
                    return "生成回應失敗。請重試。";
                }
            default:
                return getErrorText(0);
        }
    }
    
    /**
     * Release resources
     */
    public void release() {
        stopConversation();
        
        // Cancel all handlers
        cancelSleepMode();
        stopWakeUpDetection();
        
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (sleepHandler != null) {
            sleepHandler.removeCallbacksAndMessages(null);
        }
        if (wakeUpHandler != null) {
            wakeUpHandler.removeCallbacksAndMessages(null);
        }
        
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }
    
    /**
     * 檢查是否正在對話
     */
    public boolean isConversing() {
        return isListening;
    }
}

