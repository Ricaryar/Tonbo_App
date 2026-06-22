package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * 優化的檢測結果顯示器
 * 提供更好的視覺效果和用戶體驗
 */
public class OptimizedDetectionOverlayView extends View {
    private static final String TAG = "DetectionOverlay";
    
    private List<YoloDetector.DetectionResult> detectionResults;
    private Paint boxPaint;
    private Paint fillPaint;
    private Paint textPaint;
    private Paint backgroundPaint;
    private int viewWidth;
    private int viewHeight;
    private int sourceImageWidth = -1;
    private int sourceImageHeight = -1;
    private String currentLanguage = "cantonese"; // 當前語言設置
    
    public OptimizedDetectionOverlayView(Context context) {
        super(context);
        init();
    }
    
    public OptimizedDetectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public OptimizedDetectionOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        // 初始化繪畫工具
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
        boxPaint.setAntiAlias(true);
        
        // 半透明填充遮罩
        fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAlpha(100); // 半透明
        fillPaint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);
        
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha(200);
        backgroundPaint.setAntiAlias(true);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (viewWidth <= 0 || viewHeight <= 0) {
            Log.w(TAG, "onDraw: 視圖尺寸無效，跳過繪製");
            return;
        }
        
        if (detectionResults == null || detectionResults.isEmpty()) {
            Log.d(TAG, "onDraw: 沒有檢測結果，跳過繪製");
            return;
        }
        
        Log.d(TAG, "onDraw: 開始繪製 " + detectionResults.size() + " 個檢測結果");
        
        // 繪製檢測結果
        int drawnCount = 0;
        for (int i = 0; i < detectionResults.size(); i++) {
            YoloDetector.DetectionResult result = detectionResults.get(i);
            if (result != null && result.getBoundingBox() != null) {
                drawDetectionResult(canvas, result, i);
                drawnCount++;
            }
        }
        
        Log.d(TAG, "onDraw: 完成繪製 " + drawnCount + " 個檢測框");
    }
    
    private void drawDetectionResult(Canvas canvas, YoloDetector.DetectionResult result, int index) {
        Rect boundingBox = result.getBoundingBox();
        if (boundingBox == null) {
            Log.w(TAG, "drawDetectionResult: boundingBox 為 null");
            return;
        }
        
        // 驗證邊界框是否在視圖範圍內
        if (boundingBox.left < 0 || boundingBox.top < 0 || 
            boundingBox.right > viewWidth || boundingBox.bottom > viewHeight) {
            Log.w(TAG, String.format("drawDetectionResult: 邊界框超出視圖範圍 [%d,%d,%d,%d] vs 視圖 [0,0,%d,%d]",
                boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
                viewWidth, viewHeight));
        }
        
        // 驗證邊界框尺寸
        if (boundingBox.width() <= 0 || boundingBox.height() <= 0) {
            Log.w(TAG, String.format("drawDetectionResult: 邊界框尺寸無效 [%d,%d]", 
                boundingBox.width(), boundingBox.height()));
            return;
        }
        
        // 獲取物體顏色（根據索引）
        int[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.CYAN, 
                       Color.MAGENTA, 0xFF00FF00, 0xFFFF00FF, 0xFFFF8000, 0xFF00FFFF};
        int objectColor = colors[index % colors.length];
        
        // 將來源像素座標映射到目前覆蓋層座標（依CenterCrop）
        float[] lt = mapSourceToView(boundingBox.left, boundingBox.top);
        float[] rb = mapSourceToView(boundingBox.right, boundingBox.bottom);
        Rect mapped = new Rect((int) lt[0], (int) lt[1], (int) rb[0], (int) rb[1]);

        // 若盒子過大，縮小到不超過視圖的90%
        int maxW = (int) (viewWidth * 0.9f);
        int maxH = (int) (viewHeight * 0.9f);
        int w = mapped.width();
        int h = mapped.height();
        if (w > maxW || h > maxH) {
            float cx = mapped.centerX();
            float cy = mapped.centerY();
            float scale = Math.min(maxW / (float) w, maxH / (float) h);
            int newW = Math.max(1, Math.round(w * scale));
            int newH = Math.max(1, Math.round(h * scale));
            int left = Math.max(0, Math.round(cx - newW / 2f));
            int top = Math.max(0, Math.round(cy - newH / 2f));
            int right = Math.min(viewWidth, left + newW);
            int bottom = Math.min(viewHeight, top + newH);
            mapped.set(left, top, right, bottom);
        }

        // 繪製空心邊框（只繪製邊框，不填充）
        boxPaint.setColor(objectColor);
        canvas.drawRect(mapped, boxPaint);
        
        // 準備標籤文本（根據當前語言選擇，與語音播報保持一致）
        String label;
        if ("english".equals(currentLanguage)) {
            label = result.getLabel(); // 英文模式使用英文標籤
        } else if ("mandarin".equals(currentLanguage)) {
            label = result.getLabelZh(); // 普通話模式使用中文標籤
        } else {
            label = result.getLabelZh(); // 廣東話模式使用中文標籤
        }
        
        // 記錄標籤選擇（用於驗證與語音播報的一致性）
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("顯示標籤[%d]: %s (當前語言: %s, 英文: %s, 中文: %s, 置信度: %.2f)", 
                index, label, currentLanguage, result.getLabel(), result.getLabelZh(), result.getConfidence()));
        }
        
        String confidence = String.format("%.1f%%", result.getConfidence() * 100);
        String displayText = label + " " + confidence;
        
        // 計算文本位置
        float textWidth = textPaint.measureText(displayText);
        float textHeight = textPaint.getTextSize();
        
        // 繪製文本背景
        float textX = mapped.left;
        float textY = mapped.top - 10;
        
        // 確保文本不超出屏幕
        if (textY < textHeight) {
            textY = boundingBox.bottom + textHeight + 10;
        }
        
        if (textX + textWidth > viewWidth) {
            textX = viewWidth - textWidth - 10;
        }
        
        // 繪製文本背景矩形（使用物體顏色，但更深）
        Paint textBackgroundPaint = new Paint(backgroundPaint);
        textBackgroundPaint.setColor(Color.argb(200, Color.red(objectColor), 
                                                 Color.green(objectColor), 
                                                 Color.blue(objectColor)));
        canvas.drawRect(
            textX - 5, textY - textHeight - 5,
            textX + textWidth + 5, textY + 5,
            textBackgroundPaint
        );
        
        // 繪製文本
        canvas.drawText(displayText, textX, textY, textPaint);
        
        // 繪製檢測索引
        if (index < 3) { // 只顯示前3個檢測結果的索引
            String indexText = String.valueOf(index + 1);
            float indexX = mapped.right - 30;
            float indexY = mapped.top + 30;
            
            // 繪製索引背景圓圈
            canvas.drawCircle(indexX, indexY, 15, backgroundPaint);
            
            // 繪製索引文本
            Paint indexPaint = new Paint(textPaint);
            indexPaint.setTextSize(24f);
            indexPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(indexText, indexX, indexY + 8, indexPaint);
        }
    }
    
    /**
     * 設定來源影像尺寸，讓像素座標可正確映射至覆蓋層。
     */
    public void setSourceImageSize(int width, int height) {
        this.sourceImageWidth = width;
        this.sourceImageHeight = height;
    }
    
    /**
     * 設定當前語言，用於選擇顯示英文或中文標籤
     */
    public void setLanguage(String language) {
        Log.d(TAG, "設置語言為: " + language + " (之前: " + currentLanguage + ")");
        this.currentLanguage = language;
        invalidate(); // Redraw with new language
    }
    
    /**
     * 將來源圖像座標映射到視圖座標
     * 考慮相機預覽的實際顯示區域和縮放模式
     */
    private float[] mapSourceToView(float x, float y) {
        if (sourceImageWidth <= 0 || sourceImageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            Log.w(TAG, "mapSourceToView: 尺寸無效，返回原始座標");
            return new float[]{x, y};
        }
        
        // 計算來源圖像和視圖的寬高比
        float sourceAspect = (float) sourceImageWidth / sourceImageHeight;
        float viewAspect = (float) viewWidth / viewHeight;
        
        float scaleX, scaleY;
        float offsetX = 0, offsetY = 0;
        
        // PreviewView 默認使用 FILL_CENTER 模式（類似 FIT_CENTER）
        // 圖像會按比例縮放以適應視圖，保持寬高比
        if (sourceAspect > viewAspect) {
            // 來源圖像更寬，以寬度為準縮放
            scaleX = (float) viewWidth / sourceImageWidth;
            scaleY = scaleX; // 保持寬高比
            // 垂直居中
            float scaledHeight = sourceImageHeight * scaleY;
            offsetY = (viewHeight - scaledHeight) * 0.5f;
        } else {
            // 來源圖像更高，以高度為準縮放
            scaleY = (float) viewHeight / sourceImageHeight;
            scaleX = scaleY; // 保持寬高比
            // 水平居中
            float scaledWidth = sourceImageWidth * scaleX;
            offsetX = (viewWidth - scaledWidth) * 0.5f;
        }
        
        // 應用轉換
        float mappedX = x * scaleX + offsetX;
        float mappedY = y * scaleY + offsetY;
        
        // 確保座標在視圖範圍內
        mappedX = Math.max(0, Math.min(viewWidth - 1, mappedX));
        mappedY = Math.max(0, Math.min(viewHeight - 1, mappedY));
        
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("座標轉換: [%.1f,%.1f] -> [%.1f,%.1f] (來源: %dx%d, 視圖: %dx%d, 縮放: %.3f,%.3f, 偏移: %.1f,%.1f)",
                x, y, mappedX, mappedY, sourceImageWidth, sourceImageHeight, viewWidth, viewHeight, scaleX, scaleY, offsetX, offsetY));
        }
        
        return new float[]{mappedX, mappedY};
    }
    
    private int getColorForConfidence(float confidence) {
        if (confidence >= 0.8f) {
            return Color.GREEN; // 高置信度 - 綠色
        } else if (confidence >= 0.6f) {
            return Color.YELLOW; // 中等置信度 - 黃色
        } else {
            return Color.RED; // 低置信度 - 紅色
        }
    }
    
    /**
     * 更新檢測結果
     * 只顯示前2個檢測結果，與語音播報保持一致
     */
    public void updateDetectionResults(List<YoloDetector.DetectionResult> results) {
        Log.d(TAG, "updateDetectionResults 被調用，結果數量: " + (results != null ? results.size() : 0));
        Log.d(TAG, "當前視圖尺寸: " + viewWidth + "x" + viewHeight);
        Log.d(TAG, "當前視圖可見性: " + getVisibility() + ", Alpha: " + getAlpha());
        
        // 直接使用傳入的結果（已在 RealAIDetectionActivity 中限制為前2個，確保與語音播報同步）
        this.detectionResults = results != null ? new ArrayList<>(results) : null;
        
        if (results != null && !results.isEmpty()) {
            Log.d(TAG, "檢測結果詳情:");
            for (int i = 0; i < results.size(); i++) {
                YoloDetector.DetectionResult result = results.get(i);
                android.graphics.Rect bbox = result.getBoundingBox();
                if (bbox != null) {
                    Log.d(TAG, String.format("  結果[%d]: %s, 置信度: %.2f, bbox: [%d,%d,%d,%d] (寬:%d, 高:%d)", 
                        i, result.getLabelZh(), result.getConfidence(),
                        bbox.left, bbox.top, bbox.right, bbox.bottom,
                        bbox.width(), bbox.height()));
                } else {
                    Log.w(TAG, "  結果[" + i + "]: bbox 為 null！");
                }
            }
        }
        
        // 確保視圖可見
        setVisibility(VISIBLE);
        setAlpha(1.0f);
        
        // 觸發重繪
        postInvalidate();
        invalidate();
        Log.d(TAG, "已觸發重繪");
    }
    
    /**
     * 清除檢測結果
     */
    public void clearDetectionResults() {
        this.detectionResults = null;
        invalidate();
    }
    
    /**
     * 設置檢測結果的邊界框座標
     * 將相對座標轉換為絕對座標
     */
    public void setDetectionResultsWithRelativeCoords(List<YoloDetector.DetectionResult> results) {
        if (results == null) {
            clearDetectionResults();
            return;
        }
        
        // 轉換相對座標為絕對座標
        for (YoloDetector.DetectionResult result : results) {
            Rect boundingBox = result.getBoundingBox();
            if (boundingBox != null) {
                // 轉換相對座標 (0-1000) 為絕對座標
                int left = (int) (boundingBox.left * viewWidth / 1000f);
                int top = (int) (boundingBox.top * viewHeight / 1000f);
                int right = (int) (boundingBox.right * viewWidth / 1000f);
                int bottom = (int) (boundingBox.bottom * viewHeight / 1000f);
                
                // 確保座標在有效範圍內
                left = Math.max(0, Math.min(viewWidth - 1, left));
                top = Math.max(0, Math.min(viewHeight - 1, top));
                right = Math.max(0, Math.min(viewWidth - 1, right));
                bottom = Math.max(0, Math.min(viewHeight - 1, bottom));
                
                // 更新邊界框
                result.getBoundingBox().set(left, top, right, bottom);
            }
        }
        
        updateDetectionResults(results);
    }
    
    /**
     * 獲取檢測結果數量
     */
    public int getDetectionCount() {
        return detectionResults != null ? detectionResults.size() : 0;
    }
    
    /**
     * 檢查是否有檢測結果
     */
    public boolean hasDetections() {
        return detectionResults != null && !detectionResults.isEmpty();
    }
}
