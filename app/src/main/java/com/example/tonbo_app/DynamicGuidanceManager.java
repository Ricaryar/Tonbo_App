package com.example.tonbo_app;

import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 動態引導管理器
 * 實時跟蹤物體位置，引導用戶移動手機
 * 
 * @author LUO Feiyang
 */
public class DynamicGuidanceManager {
    private static final String TAG = "DynamicGuidanceManager";
    
    // 目標物體
    private String targetObject = null;
    
    // 上次找到的位置
    private RectF lastKnownPosition = null;
    
    // 當前位置
    private RectF currentPosition = null;
    
    // 跟蹤狀態
    private boolean isTracking = false;
    
    // 引導狀態
    private GuidanceState currentState = GuidanceState.NOT_FOUND;
    
    // 引導回調
    public interface GuidanceListener {
        void onGuidanceUpdate(String guidance, GuidanceState state);
        void onObjectFound(RectF position);
        void onObjectLost();
    }
    
    private GuidanceListener guidanceListener;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SpatialDescriptionGenerator spatialDesc;
    
    // 引導狀態
    public enum GuidanceState {
        NOT_FOUND,      // 未找到
        FOUND,          // 已找到
        CENTERING,      // 正在居中
        LOST            // 跟丟了
    }
    
    public DynamicGuidanceManager() {
        spatialDesc = new SpatialDescriptionGenerator();
    }
    
    /**
     * 設置引導監聽器
     */
    public void setGuidanceListener(GuidanceListener listener) {
        this.guidanceListener = listener;
    }
    
    /**
     * 設置語言
     */
    public void setLanguage(String language) {
        if (spatialDesc != null) {
            spatialDesc.setLanguage(language);
        }
    }
    
    /**
     * 開始跟蹤物體
     */
    public void startTracking(String objectName) {
        Log.d(TAG, "開始跟蹤物體: " + objectName);
        targetObject = objectName;
        isTracking = true;
        currentState = GuidanceState.NOT_FOUND;
        lastKnownPosition = null;
        currentPosition = null;
    }
    
    /**
     * 停止跟蹤
     */
    public void stopTracking() {
        Log.d(TAG, "停止跟蹤");
        isTracking = false;
        targetObject = null;
        lastKnownPosition = null;
        currentPosition = null;
        currentState = GuidanceState.NOT_FOUND;
        handler.removeCallbacksAndMessages(null);
    }
    
    /**
     * 更新物體位置
     */
    public void updatePosition(ObjectDetectorHelper.DetectionResult result, 
                              float imageWidth, float imageHeight) {
        if (!isTracking || result == null) {
            return;
        }
        
        RectF newPosition = result.getBoundingBox();
        currentPosition = newPosition;
        
        // 檢查物體是否在畫面中央（容差範圍內）
        float centerX = (newPosition.left + newPosition.right) / 2.0f;
        float centerY = (newPosition.top + newPosition.bottom) / 2.0f;
        
        boolean isCentered = (centerX >= 0.4f && centerX <= 0.6f) && 
                            (centerY >= 0.4f && centerY <= 0.6f);
        
        if (isCentered) {
            // 物體在中央
            if (currentState != GuidanceState.FOUND) {
                currentState = GuidanceState.FOUND;
                lastKnownPosition = newPosition;
                
                String positionDesc = spatialDesc.describePosition(result, imageWidth, imageHeight);
                if (guidanceListener != null) {
                    guidanceListener.onObjectFound(newPosition);
                    guidanceListener.onGuidanceUpdate(
                        "找到了！" + positionDesc, 
                        GuidanceState.FOUND
                    );
                }
                Log.d(TAG, "物體已找到並居中: " + positionDesc);
            }
        } else {
            // 物體不在中央，需要引導
            if (currentState == GuidanceState.NOT_FOUND || 
                currentState == GuidanceState.LOST) {
                currentState = GuidanceState.CENTERING;
                lastKnownPosition = newPosition;
                
                String guidance = generateCenteringGuidance(newPosition, imageWidth, imageHeight);
                if (guidanceListener != null) {
                    guidanceListener.onGuidanceUpdate(guidance, GuidanceState.CENTERING);
                }
                Log.d(TAG, "引導用戶: " + guidance);
            } else if (currentState == GuidanceState.CENTERING) {
                // 檢查位置是否改變
                if (lastKnownPosition != null) {
                    float distance = calculateDistance(newPosition, lastKnownPosition);
                    if (distance > 0.1f) {
                        // 位置有明顯變化，更新引導
                        String guidance = generateCenteringGuidance(newPosition, imageWidth, imageHeight);
                        if (guidanceListener != null) {
                            guidanceListener.onGuidanceUpdate(guidance, GuidanceState.CENTERING);
                        }
                        lastKnownPosition = newPosition;
                    }
                }
            }
        }
    }
    
    /**
     * 物體丟失
     */
    public void onObjectLost() {
        if (!isTracking) {
            return;
        }
        
        if (currentState == GuidanceState.FOUND || 
            currentState == GuidanceState.CENTERING) {
            currentState = GuidanceState.LOST;
            currentPosition = null;
            
            String guidance = "物體已離開畫面，請緩慢移動手機搜索";
            
            if (guidanceListener != null) {
                guidanceListener.onObjectLost();
                guidanceListener.onGuidanceUpdate(
                    "物體已離開畫面。" + guidance, 
                    GuidanceState.LOST
                );
            }
            Log.d(TAG, "物體跟丟: " + guidance);
        }
    }
    
    /**
     * 生成居中引導指令
     */
    private String generateCenteringGuidance(RectF position, 
                                            float imageWidth, float imageHeight) {
        if (position == null) {
            return spatialDesc.getLocalizedString("guidance_not_found");
        }
        
        float centerX = (position.left + position.right) / 2.0f;
        float centerY = (position.top + position.bottom) / 2.0f;
        
        StringBuilder guidance = new StringBuilder();
        
        // 水平方向引導
        if (centerX < 0.4f) {
            guidance.append("請將手機向右移動");
        } else if (centerX > 0.6f) {
            guidance.append("請將手機向左移動");
        }
        
        // 垂直方向引導
        if (centerY < 0.4f) {
            if (guidance.length() > 0) guidance.append("，");
            guidance.append("請將手機向下移動");
        } else if (centerY > 0.6f) {
            if (guidance.length() > 0) guidance.append("，");
            guidance.append("請將手機向上移動");
        }
        
        if (guidance.length() == 0) {
            return "物體已在畫面中央附近";
        }
        
        return guidance.toString();
    }
    
    /**
     * 計算兩個位置之間的距離
     */
    private float calculateDistance(RectF pos1, RectF pos2) {
        float centerX1 = (pos1.left + pos1.right) / 2.0f;
        float centerY1 = (pos1.top + pos1.bottom) / 2.0f;
        float centerX2 = (pos2.left + pos2.right) / 2.0f;
        float centerY2 = (pos2.top + pos2.bottom) / 2.0f;
        
        float dx = centerX1 - centerX2;
        float dy = centerY1 - centerY2;
        
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 檢查是否正在跟蹤
     */
    public boolean isTracking() {
        return isTracking;
    }
    
    /**
     * 獲取當前狀態
     */
    public GuidanceState getCurrentState() {
        return currentState;
    }
}

