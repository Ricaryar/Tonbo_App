package com.example.tonbo_app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TTSManager {
    private static final String TAG = "TTSManager";
    private static TTSManager instance;

    private TextToSpeech textToSpeech;
    private Context context;
    private String currentLanguage = "english";
    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private boolean isSpeaking = false;

    public interface OnSpeechFinishedListener { void onFinished(); }
    private OnSpeechFinishedListener mListener;

    /** 播報完成（按 utteranceId），供導航頁等使用 */
    public interface OnSpeechCompleteListener {
        void onSpeechComplete(String utteranceId);
    }

    private OnSpeechCompleteListener speechCompleteListener;
    private final ConcurrentHashMap<String, Runnable> deferredUtteranceActions = new ConcurrentHashMap<>();

    public void setOnSpeechCompleteListener(OnSpeechCompleteListener listener) {
        this.speechCompleteListener = listener;
    }

    private ConcurrentLinkedQueue<String> speechQueue = new ConcurrentLinkedQueue<>();
    private Handler handler = new Handler(Looper.getMainLooper());

    private TTSManager(Context context) {
        this.context = context.getApplicationContext();
        initTTS();
    }

    public static synchronized TTSManager getInstance(Context context) {
        if (instance == null) instance = new TTSManager(context);
        return instance;
    }

    /**
     * 播報前清理：去掉 Markdown 星號/井號等，合併多餘省略號，減少 TTS 念「星號」「點點點」。
     */
    private static String sanitizeForTts(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isEmpty()) return s;
        s = s.replaceAll("\\*+", "");
        s = s.replaceAll("#+", "");
        s = s.replaceAll("`+", "");
        s = s.replaceAll("_{2,}", "");
        s = s.replaceAll("\\s*•\\s*", " ");
        s = s.replace("…", "。");
        s = s.replaceAll("\\.{3,}", "。");
        s = s.replaceAll("[。．]{2,}", "。");
        s = s.replaceAll("[，、]{3,}", "，");
        s = s.replaceAll("<[^>]{0,200}>", "");
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    private void initTTS() {
        if (isInitializing) return;
        isInitializing = true;
        textToSpeech = new TextToSpeech(context, status -> {
            isInitializing = false;
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true;
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) { isSpeaking = true; }
                    @Override public void onDone(String utteranceId) {
                        isSpeaking = false;
                        Runnable deferred = deferredUtteranceActions.remove(utteranceId);
                        if (deferred != null) {
                            handler.post(deferred);
                        }
                        if (speechCompleteListener != null) {
                            handler.post(() -> speechCompleteListener.onSpeechComplete(utteranceId));
                        }
                        if (mListener != null) {
                            handler.post(() -> mListener.onFinished());
                        }
                    }
                    @Override public void onError(String utteranceId) {
                        isSpeaking = false;
                        deferredUtteranceActions.remove(utteranceId);
                    }
                });
                setLanguage(currentLanguage);
            }
        });
    }

    // 核心 speak 方法，支持监听器回调以修复录音问题
    public void speak(String cantonese, String english, boolean priority, OnSpeechFinishedListener listener) {
        this.mListener = listener;
        String textToSpeak;
        if ("english".equals(currentLanguage)) {
            textToSpeak = (english != null && !english.trim().isEmpty()) ? english : cantonese;
        } else if ("mandarin".equals(currentLanguage)) {
            // 普通話界面：主文案應在首參（簡中）；次參可為英文備用
            textToSpeak = (cantonese != null && !cantonese.trim().isEmpty()) ? cantonese : english;
        } else {
            textToSpeak = (cantonese != null && !cantonese.trim().isEmpty()) ? cantonese : english;
        }
        textToSpeak = sanitizeForTts(textToSpeak);
        if (textToSpeak == null || textToSpeak.isEmpty()) return;

        if (textToSpeech != null && isInitialized) {
            int mode = priority ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            textToSpeech.speak(textToSpeak, mode, null, "TTS_TASK_ID");
        } else {
            handler.postDelayed(() -> speak(cantonese, english, priority, listener), 1000);
        }
    }

    // 补全队友使用的所有方法
    public void speak(String cantonese, String english) { speak(cantonese, english, false, null); }
    public void speak(String cantonese, String english, boolean priority) { speak(cantonese, english, priority, null); }

    /**
     * 帶 utteranceId 的播報，完成後觸發 {@link #setOnSpeechCompleteListener}。
     */
    public void speakWithId(String cantoneseText, String englishText, boolean priority, String utteranceId) {
        String textToSpeak;
        if ("english".equals(currentLanguage)) {
            textToSpeak = (englishText != null && !englishText.trim().isEmpty()) ? englishText : cantoneseText;
        } else if ("mandarin".equals(currentLanguage)) {
            textToSpeak = (cantoneseText != null && !cantoneseText.trim().isEmpty()) ? cantoneseText : englishText;
        } else {
            textToSpeak = (cantoneseText != null && !cantoneseText.trim().isEmpty()) ? cantoneseText : englishText;
        }
        textToSpeak = sanitizeForTts(textToSpeak);
        if (textToSpeak == null || textToSpeak.isEmpty()) return;

        if (textToSpeech != null && isInitialized) {
            int mode = priority ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            if (priority && textToSpeech != null) {
                textToSpeech.stop();
                speechQueue.clear();
            }
            textToSpeech.speak(textToSpeak, mode, null, utteranceId);
        } else {
            handler.postDelayed(() -> speakWithId(cantoneseText, englishText, priority, utteranceId), 1000);
        }
    }

    /**
     * 播報結束後在主線程執行一次 Runnable（不取代全局 OnSpeechCompleteListener）。
     */
    public void speakThenRun(String cantoneseText, String englishText, boolean priority, Runnable onComplete) {
        if (onComplete == null) {
            speak(cantoneseText, englishText, priority);
            return;
        }
        String id = "defer_" + System.nanoTime();
        deferredUtteranceActions.put(id, onComplete);
        speakWithId(cantoneseText, englishText, priority, id);
    }

    public String getCurrentLanguage() { return currentLanguage; }
    public void stopSpeaking() {
        handler.removeCallbacksAndMessages(null);
        deferredUtteranceActions.clear();
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        speechQueue.clear();
        isSpeaking = false;
    }
    public void stop() { if (textToSpeech != null) textToSpeech.stop(); }
    public void setSpeechRate(float rate) { if (textToSpeech != null) textToSpeech.setSpeechRate(rate); }
    public void setSpeechPitch(float pitch) { if (textToSpeech != null) textToSpeech.setPitch(pitch); }
    public void setSpeechVolume(float volume) { Log.d(TAG, "Volume: " + volume); }
    public void speakPageTitle(String name) { speak("當前頁面：" + name, "Current page: " + name, true); }
    public void speakSuccess(String msg) { speak("成功：" + msg, "Success: " + msg, true); }
    public void speakError(String err) { speak("錯誤：" + err, "Error: " + err, true); }
    public void speakNavigationHint(String hint) { speak("提示：" + hint, "Hint: " + hint, false); }
    public void changeLanguage(String lang) { this.currentLanguage = lang; setLanguage(lang); }
    public void setLanguageSilently(String lang) { this.currentLanguage = lang; setLanguage(lang); }

    private void setLanguage(String lang) {
        if (textToSpeech == null) return;
        if ("english".equals(lang)) textToSpeech.setLanguage(Locale.ENGLISH);
        else if ("mandarin".equals(lang)) textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
        else textToSpeech.setLanguage(new Locale("zh", "HK"));
    }

    public void forceInitialize() { if (textToSpeech == null) initTTS(); }
    public boolean isSpeaking() { return isSpeaking || (textToSpeech != null && textToSpeech.isSpeaking()); }
    public void pauseSpeaking() { if (textToSpeech != null) textToSpeech.stop(); }
    public void shutdown() { if (textToSpeech != null) textToSpeech.stop(); }
}