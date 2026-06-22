package com.example.tonbo_app;

import android.graphics.RectF;
import android.util.Log;

/**
 * 空間描述生成器
 * 使用非視覺性空間語言描述物體位置
 * 
 * @author LUO Feiyang
 */
public class SpatialDescriptionGenerator {
    private static final String TAG = "SpatialDescriptionGenerator";
    
    private String currentLanguage = "cantonese";
    
    public SpatialDescriptionGenerator() {
    }
    
    /**
     * 設置當前語言
     */
    public void setLanguage(String language) {
        this.currentLanguage = language;
    }
    
    /**
     * 生成物體位置描述
     * @param result 檢測結果
     * @param imageWidth 圖像寬度
     * @param imageHeight 圖像高度
     * @return 位置描述文本
     */
    public String describePosition(ObjectDetectorHelper.DetectionResult result, 
                                   float imageWidth, float imageHeight) {
        if (result == null || result.getBoundingBox() == null) {
            return getLocalizedString("position_unknown");
        }
        
        RectF bbox = result.getBoundingBox();
        String objectName = getObjectName(result);
        
        // 計算物體中心點（使用相對座標 0-1）
        float centerX = (bbox.left + bbox.right) / 2.0f;
        float centerY = (bbox.top + bbox.bottom) / 2.0f;
        
        // 計算物體大小（相對面積）
        float width = bbox.right - bbox.left;
        float height = bbox.bottom - bbox.top;
        float area = width * height;
        
        // 生成水平方向描述
        String horizontal = getHorizontalDirection(centerX);
        
        // 生成垂直方向描述
        String vertical = getVerticalDirection(centerY);
        
        // 估算距離
        String distance = estimateDistance(area);
        
        // 組合描述
        return formatDescription(objectName, horizontal, vertical, distance);
    }
    
    /**
     * 獲取水平方向描述
     */
    private String getHorizontalDirection(float centerX) {
        // centerX 是相對座標 (0-1)
        if (centerX < 0.25f) {
            return getLocalizedString("far_left");
        } else if (centerX < 0.4f) {
            return getLocalizedString("left_front");
        } else if (centerX < 0.6f) {
            return getLocalizedString("straight_ahead");
        } else if (centerX < 0.75f) {
            return getLocalizedString("right_front");
        } else {
            return getLocalizedString("far_right");
        }
    }
    
    /**
     * 獲取垂直方向描述
     */
    private String getVerticalDirection(float centerY) {
        // centerY 是相對座標 (0-1)
        if (centerY < 0.3f) {
            return getLocalizedString("high_up");
        } else if (centerY < 0.7f) {
            return getLocalizedString("middle");
        } else {
            return getLocalizedString("low_down");
        }
    }
    
    /**
     * 估算距離（基於物體大小）
     */
    private String estimateDistance(float area) {
        // area 是相對面積 (0-1)
        if (area > 0.15f) {
            return getLocalizedString("very_close") + "（約0.5米）";
        } else if (area > 0.08f) {
            return getLocalizedString("close") + "（約1米）";
        } else if (area > 0.04f) {
            return getLocalizedString("medium_distance") + "（約2米）";
        } else if (area > 0.02f) {
            return getLocalizedString("far") + "（約3米）";
        } else {
            return getLocalizedString("very_far") + "（約4米以上）";
        }
    }
    
    /**
     * 格式化描述文本
     */
    private String formatDescription(String objectName, String horizontal, 
                                     String vertical, String distance) {
        String template = getLocalizedString("position_template");
        return String.format(template, objectName, horizontal, vertical, distance);
    }
    
    /**
     * 獲取物體名稱（根據語言）
     */
    private String getObjectName(ObjectDetectorHelper.DetectionResult result) {
        switch (currentLanguage) {
            case "english":
                return result.getLabel() != null ? result.getLabel() : result.getLabelZh();
            case "mandarin":
                return result.getLabelZh() != null ? result.getLabelZh() : result.getLabel();
            case "cantonese":
            default:
                return result.getLabelZh() != null ? result.getLabelZh() : result.getLabel();
        }
    }
    
    /**
     * 生成引導指令（當物體不在畫面中時）
     */
    public String generateGuidance(RectF lastKnownPosition, float imageWidth, float imageHeight) {
        if (lastKnownPosition == null) {
            return getLocalizedString("guidance_not_found");
        }
        
        // 計算上次位置的方向
        float lastCenterX = (lastKnownPosition.left + lastKnownPosition.right) / 2.0f;
        float lastCenterY = (lastKnownPosition.top + lastKnownPosition.bottom) / 2.0f;
        
        // 生成引導指令
        StringBuilder guidance = new StringBuilder();
        
        // 水平方向引導
        if (lastCenterX < 0.3f) {
            guidance.append(getLocalizedString("move_left"));
        } else if (lastCenterX > 0.7f) {
            guidance.append(getLocalizedString("move_right"));
        }
        
        // 垂直方向引導
        if (lastCenterY < 0.3f) {
            if (guidance.length() > 0) guidance.append("，");
            guidance.append(getLocalizedString("move_up"));
        } else if (lastCenterY > 0.7f) {
            if (guidance.length() > 0) guidance.append("，");
            guidance.append(getLocalizedString("move_down"));
        }
        
        if (guidance.length() == 0) {
            return getLocalizedString("guidance_center");
        }
        
        return guidance.toString();
    }
    
    /**
     * 獲取本地化字符串
     */
    public String getLocalizedString(String key) {
        switch (key) {
            case "position_unknown":
                if ("english".equals(currentLanguage)) {
                    return "Unknown position";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "未知位置";
                } else {
                    return "未知位置";
                }
            case "far_left":
                if ("english".equals(currentLanguage)) {
                    return "far left";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "左側遠處";
                } else {
                    return "左側遠處";
                }
            case "left_front":
                if ("english".equals(currentLanguage)) {
                    return "left front";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "左前方";
                } else {
                    return "左前方";
                }
            case "straight_ahead":
                if ("english".equals(currentLanguage)) {
                    return "straight ahead";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "正前方";
                } else {
                    return "正前方";
                }
            case "right_front":
                if ("english".equals(currentLanguage)) {
                    return "right front";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "右前方";
                } else {
                    return "右前方";
                }
            case "far_right":
                if ("english".equals(currentLanguage)) {
                    return "far right";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "右側遠處";
                } else {
                    return "右側遠處";
                }
            case "high_up":
                if ("english".equals(currentLanguage)) {
                    return "high up";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "高處";
                } else {
                    return "高處";
                }
            case "middle":
                if ("english".equals(currentLanguage)) {
                    return "middle";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "中間";
                } else {
                    return "中間";
                }
            case "low_down":
                if ("english".equals(currentLanguage)) {
                    return "low down";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "低處";
                } else {
                    return "低處";
                }
            case "very_close":
                if ("english".equals(currentLanguage)) {
                    return "very close";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "很近";
                } else {
                    return "很近";
                }
            case "close":
                if ("english".equals(currentLanguage)) {
                    return "close";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "近處";
                } else {
                    return "近處";
                }
            case "medium_distance":
                if ("english".equals(currentLanguage)) {
                    return "medium distance";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "中等距離";
                } else {
                    return "中等距離";
                }
            case "far":
                if ("english".equals(currentLanguage)) {
                    return "far";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "遠處";
                } else {
                    return "遠處";
                }
            case "very_far":
                if ("english".equals(currentLanguage)) {
                    return "very far";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "很遠";
                } else {
                    return "很遠";
                }
            case "position_template":
                if ("english".equals(currentLanguage)) {
                    return "%s is at your %s %s, about %s away";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "%s在您%s%s，距離您大約%s";
                } else {
                    return "%s在您%s%s，距離您大約%s";
                }
            case "guidance_not_found":
                if ("english".equals(currentLanguage)) {
                    return "Object not found. Please move your phone slowly to search";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "未找到物體，請緩慢移動手機搜索";
                } else {
                    return "未找到物體，請緩慢移動手機搜索";
                }
            case "move_left":
                if ("english".equals(currentLanguage)) {
                    return "Move your phone to the left";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "請將手機向左移動";
                } else {
                    return "請將手機向左移動";
                }
            case "move_right":
                if ("english".equals(currentLanguage)) {
                    return "Move your phone to the right";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "請將手機向右移動";
                } else {
                    return "請將手機向右移動";
                }
            case "move_up":
                if ("english".equals(currentLanguage)) {
                    return "Move your phone up";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "請將手機向上移動";
                } else {
                    return "請將手機向上移動";
                }
            case "move_down":
                if ("english".equals(currentLanguage)) {
                    return "Move your phone down";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "請將手機向下移動";
                } else {
                    return "請將手機向下移動";
                }
            case "guidance_center":
                if ("english".equals(currentLanguage)) {
                    return "Object is in the center of the frame";
                } else if ("mandarin".equals(currentLanguage)) {
                    return "物體在畫面中央";
                } else {
                    return "物體在畫面中央";
                }
            default:
                return "";
        }
    }
}

