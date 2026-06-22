package com.example.tonbo_app;

/**
 * 應用常量和配置
 * 集中管理所有硬編碼的數值和字符串
 */
public final class AppConstants {
    
    // 私有構造函數，防止實例化
    private AppConstants() {}
    
    // 語言相關
    public static final String LANGUAGE_CANTONESE = "cantonese";
    public static final String LANGUAGE_ENGLISH = "english";
    public static final String LANGUAGE_MANDARIN = "mandarin";
    
    // 檢測相關
    public static final int MAX_DETECTION_RESULTS = 3;
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final int RETRY_DELAY_MS = 100;
    public static final int MAX_CONSECUTIVE_FAILURES = 5;
    public static final int DETECTION_FREQUENCY_LIMIT_MS = 100;
    public static final int MAX_DETECTION_TIME_MS = 1000;
    
    // 性能監控
    public static final int MAX_DETECTION_TIME_RECORDS = 100;
    public static final int MAX_CONFIDENCE_RECORDS = 200;
    
    // 語音命令
    public static final int PERMISSION_REQUEST_RECORD_AUDIO = 200;
    public static final int REQUEST_CODE_SPEECH_INPUT = 1000;
    
    // 緊急求助
    public static final long EMERGENCY_LONG_PRESS_DURATION_MS = 3000;
    
    // 動畫
    public static final int FADE_TRANSITION_DURATION_MS = 200;
    public static final int SLIDE_TRANSITION_DURATION_MS = 300;
    public static final int SCALE_TRANSITION_DURATION_MS = 250;
    
    // 置信度閾值（先回到偏保守配置，優先準確率）
    public static final float CONFIDENCE_THRESHOLD = 0.45f;       // 提高基準閾值，減少誤檢
    public static final float HIGH_CONFIDENCE_THRESHOLD = 0.7f;   // 高置信度閾值
    public static final float SCORE_THRESHOLD = 0.45f;            // 與置信度一致，先保守再微調
    public static final float NMS_THRESHOLD = 0.5f;               // 提高到0.5，減少重複檢測
    public static final float IOU_THRESHOLD = 0.5f;               // 保持0.5

    // 步行辅助（WalkAssist）参数
    public static final float WALK_ASSIST_OBSTACLE_CONFIDENCE_THRESHOLD = 0.4f;
    public static final int WALK_ASSIST_SAFE_FRAMES_FOR_CLEAR = 8;
    public static final long WALK_ASSIST_CLEAR_COOLDOWN_MS = 3000;
    
    // 模型參數
    public static final String MODEL_FILE = "ssd_mobilenet_v1.tflite";
    public static final String YOLO_MODEL_FILE = "yolov8n.tflite";
    public static final String CURRENCY_CLS_MODEL_FILE = "currency_cls.onnx";
    public static final int INPUT_SIZE = 640;
    public static final int NUM_CLASSES = 90;
    public static final int MAX_RESULTS = 5;

    // 港幣圖像分類（YOLOv8n-cls）
    public static final int CURRENCY_CLS_INPUT_SIZE = 224;
    public static final float CURRENCY_CLS_CONFIDENCE_THRESHOLD = 0.65f;
    public static final float CURRENCY_CLS_HIGH_CONFIDENCE_THRESHOLD = 0.85f;
}
