package com.example.tonbo_app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Color and lighting analyzer
 * Used to analyze primary colors, tones, brightness, and lighting conditions in images
 */
public class ColorLightingAnalyzer {
    private static final String TAG = "ColorLightingAnalyzer";
    
    // Color analysis parameters
    private static final int SAMPLE_SIZE = 100; // Sample size
    private static final int COLOR_TOLERANCE = 50; // Color tolerance
    private static final float MIN_COLOR_PERCENTAGE = 5.0f; // Minimum color percentage
    
    // Lighting analysis parameters
    private static final int BRIGHTNESS_THRESHOLD_LOW = 85; // Low brightness threshold
    private static final int BRIGHTNESS_THRESHOLD_HIGH = 170; // High brightness threshold
    private static final int CONTRAST_THRESHOLD_LOW = 30; // Low contrast threshold
    private static final int CONTRAST_THRESHOLD_HIGH = 80; // High contrast threshold
    
    /**
     * Color analysis result
     */
    public static class ColorAnalysisResult {
        private String primaryColor;
        private String secondaryColor;
        private String dominantTone;
        private List<ColorInfo> colorPalette;
        
        public ColorAnalysisResult() {
            colorPalette = new ArrayList<>();
        }
        
        // Getters and setters
        public String getPrimaryColor() { return primaryColor; }
        public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
        
        public String getSecondaryColor() { return secondaryColor; }
        public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }
        
        public String getDominantTone() { return dominantTone; }
        public void setDominantTone(String dominantTone) { this.dominantTone = dominantTone; }
        
        public List<ColorInfo> getColorPalette() { return colorPalette; }
        
        public void addColorInfo(ColorInfo colorInfo) {
            colorPalette.add(colorInfo);
        }
    }
    
    /**
     * Lighting analysis result
     */
    public static class LightingAnalysisResult {
        private String brightnessLevel;
        private String contrastLevel;
        private String lightingCondition;
        private float averageBrightness;
        private float contrastRatio;
        private String lightDirection;
        
        // Getters and setters
        public String getBrightnessLevel() { return brightnessLevel; }
        public void setBrightnessLevel(String brightnessLevel) { this.brightnessLevel = brightnessLevel; }
        
        public String getContrastLevel() { return contrastLevel; }
        public void setContrastLevel(String contrastLevel) { this.contrastLevel = contrastLevel; }
        
        public String getLightingCondition() { return lightingCondition; }
        public void setLightingCondition(String lightingCondition) { this.lightingCondition = lightingCondition; }
        
        public float getAverageBrightness() { return averageBrightness; }
        public void setAverageBrightness(float averageBrightness) { this.averageBrightness = averageBrightness; }
        
        public float getContrastRatio() { return contrastRatio; }
        public void setContrastRatio(float contrastRatio) { this.contrastRatio = contrastRatio; }
        
        public String getLightDirection() { return lightDirection; }
        public void setLightDirection(String lightDirection) { this.lightDirection = lightDirection; }
    }
    
    /**
     * Color information
     */
    public static class ColorInfo {
        private String colorName;
        private int colorValue;
        private float percentage;
        
        public ColorInfo(String colorName, int colorValue, float percentage) {
            this.colorName = colorName;
            this.colorValue = colorValue;
            this.percentage = percentage;
        }
        
        // Getters
        public String getColorName() { return colorName; }
        public int getColorValue() { return colorValue; }
        public float getPercentage() { return percentage; }
    }
    
    /**
     * Analyze image colors
     */
    public ColorAnalysisResult analyzeColors(Bitmap bitmap) {
        Log.d(TAG, "Start color analysis");
        ColorAnalysisResult result = new ColorAnalysisResult();
        
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w(TAG, "Invalid bitmap");
            return result;
        }
        
        try {
            // Sample pixels
            List<Integer> pixelSamples = samplePixels(bitmap);
            
            // Analyze primary colors
            Map<String, Integer> colorCount = countColors(pixelSamples);
            
            // Generate color palette
            generateColorPalette(colorCount, result);
            
            // Determine primary and secondary colors
            determinePrimarySecondaryColors(result);
            
            // Determine dominant tone
            determineDominantTone(result);
            
            Log.d(TAG, "Color analysis complete: " + result.getPrimaryColor() + " + " + result.getSecondaryColor());
            
        } catch (Exception e) {
            Log.e(TAG, "Color analysis failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Analyze image lighting conditions
     */
    public LightingAnalysisResult analyzeLighting(Bitmap bitmap) {
        Log.d(TAG, "Start lighting analysis");
        LightingAnalysisResult result = new LightingAnalysisResult();
        
        if (bitmap == null || bitmap.isRecycled()) {
            Log.w(TAG, "Invalid bitmap");
            return result;
        }
        
        try {
            // Calculate average brightness
            float averageBrightness = calculateAverageBrightness(bitmap);
            result.setAverageBrightness(averageBrightness);
            
            // Analyze brightness level
            result.setBrightnessLevel(analyzeBrightnessLevel(averageBrightness));
            
            // Calculate contrast
            float contrastRatio = calculateContrastRatio(bitmap);
            result.setContrastRatio(contrastRatio);
            
            // Analyze contrast level
            result.setContrastLevel(analyzeContrastLevel(contrastRatio));
            
            // Analyze light direction
            result.setLightDirection(analyzeLightDirection(bitmap));
            
            // Comprehensive lighting condition
            result.setLightingCondition(determineLightingCondition(averageBrightness, contrastRatio));
            
            Log.d(TAG, "Lighting analysis complete: " + result.getLightingCondition());
            
        } catch (Exception e) {
            Log.e(TAG, "Lighting analysis failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Sample pixels
     */
    private List<Integer> samplePixels(Bitmap bitmap) {
        List<Integer> samples = new ArrayList<>();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Random sampling
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            int x = (int) (Math.random() * width);
            int y = (int) (Math.random() * height);
            samples.add(bitmap.getPixel(x, y));
        }
        
        return samples;
    }
    
    /**
     * Count colors
     */
    private Map<String, Integer> countColors(List<Integer> pixels) {
        Map<String, Integer> colorCount = new HashMap<>();
        
        for (int pixel : pixels) {
            String colorCategory = categorizeColor(pixel);
            Integer currentCount = colorCount.get(colorCategory);
            colorCount.put(colorCategory, (currentCount != null ? currentCount : 0) + 1);
        }
        
        return colorCount;
    }
    
    /**
     * Categorize pixel into color category
     */
    private String categorizeColor(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        
        // Convert to HSV for better color classification
        float[] hsv = new float[3];
        Color.RGBToHSV(r, g, b, hsv);
        
        float hue = hsv[0];
        float saturation = hsv[1];
        float value = hsv[2];
        
        // Low saturation = grayscale
        if (saturation < 0.2f) {
            if (value < 0.3f) return "黑色";
            if (value > 0.7f) return "白色";
            return "灰色";
        }
        
        // Classify colors based on hue
        if (hue < 15 || hue > 345) return "紅色";
        if (hue < 45) return "橙色";
        if (hue < 75) return "黃色";
        if (hue < 165) return "綠色";
        if (hue < 210) return "青色";
        if (hue < 270) return "藍色";
        if (hue < 315) return "紫色";
        return "紅色";
    }
    
    /**
     * Generate color palette
     */
    private void generateColorPalette(Map<String, Integer> colorCount, ColorAnalysisResult result) {
        int totalSamples = SAMPLE_SIZE;
        
        for (Map.Entry<String, Integer> entry : colorCount.entrySet()) {
            float percentage = (float) entry.getValue() / totalSamples * 100;
            if (percentage >= MIN_COLOR_PERCENTAGE) {
                result.addColorInfo(new ColorInfo(entry.getKey(), 0, percentage));
            }
        }
        
        // Sort by percentage
        Collections.sort(result.getColorPalette(), new Comparator<ColorInfo>() {
            @Override
            public int compare(ColorInfo a, ColorInfo b) {
                return Float.compare(b.getPercentage(), a.getPercentage());
            }
        });
    }
    
    /**
     * Determine primary and secondary colors
     */
    private void determinePrimarySecondaryColors(ColorAnalysisResult result) {
        if (result.getColorPalette().size() > 0) {
            result.setPrimaryColor(result.getColorPalette().get(0).getColorName());
        }
        if (result.getColorPalette().size() > 1) {
            result.setSecondaryColor(result.getColorPalette().get(1).getColorName());
        }
    }
    
    /**
     * Determine dominant tone
     */
    private void determineDominantTone(ColorAnalysisResult result) {
        if (result.getColorPalette().isEmpty()) {
            result.setDominantTone("中性");
            return;
        }
        
        // Calculate ratio of warm and cool colors
        float warmColors = 0;
        float coolColors = 0;
        
        for (ColorInfo colorInfo : result.getColorPalette()) {
            String colorName = colorInfo.getColorName();
            if (isWarmColor(colorName)) {
                warmColors += colorInfo.getPercentage();
            } else if (isCoolColor(colorName)) {
                coolColors += colorInfo.getPercentage();
            }
        }
        
        if (warmColors > coolColors + 10) {
            result.setDominantTone("暖色調");
        } else if (coolColors > warmColors + 10) {
            result.setDominantTone("冷色調");
        } else {
            result.setDominantTone("中性色調");
        }
    }
    
    /**
     * Check if color is warm
     */
    private boolean isWarmColor(String colorName) {
        return colorName.equals("紅色") || colorName.equals("橙色") || colorName.equals("黃色");
    }
    
    /**
     * Check if color is cool
     */
    private boolean isCoolColor(String colorName) {
        return colorName.equals("藍色") || colorName.equals("青色") || colorName.equals("紫色");
    }
    
    /**
     * Calculate average brightness
     */
    private float calculateAverageBrightness(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        long totalBrightness = 0;
        int sampleCount = 0;
        
        // Sample to calculate average brightness
        for (int i = 0; i < width; i += width / 20) {
            for (int j = 0; j < height; j += height / 20) {
                int pixel = bitmap.getPixel(i, j);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                
                // Use standard brightness formula
                float brightness = 0.299f * r + 0.587f * g + 0.114f * b;
                totalBrightness += brightness;
                sampleCount++;
            }
        }
        
        return totalBrightness / (float) sampleCount;
    }
    
    /**
     * Analyze brightness level
     */
    private String analyzeBrightnessLevel(float averageBrightness) {
        if (averageBrightness < BRIGHTNESS_THRESHOLD_LOW) {
            return "較暗";
        } else if (averageBrightness > BRIGHTNESS_THRESHOLD_HIGH) {
            return "較亮";
        } else {
            return "適中";
        }
    }
    
    /**
     * Calculate contrast ratio
     */
    private float calculateContrastRatio(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        int minBrightness = 255;
        int maxBrightness = 0;
        
        // Sample to calculate max and min brightness
        for (int i = 0; i < width; i += width / 15) {
            for (int j = 0; j < height; j += height / 15) {
                int pixel = bitmap.getPixel(i, j);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                
                float brightness = 0.299f * r + 0.587f * g + 0.114f * b;
                
                if (brightness < minBrightness) minBrightness = (int) brightness;
                if (brightness > maxBrightness) maxBrightness = (int) brightness;
            }
        }
        
        return maxBrightness - minBrightness;
    }
    
    /**
     * Analyze contrast level
     */
    private String analyzeContrastLevel(float contrastRatio) {
        if (contrastRatio < CONTRAST_THRESHOLD_LOW) {
            return "低對比";
        } else if (contrastRatio > CONTRAST_THRESHOLD_HIGH) {
            return "高對比";
        } else {
            return "中等對比";
        }
    }
    
    /**
     * Analyze light direction (simplified version)
     */
    private String analyzeLightDirection(Bitmap bitmap) {
        // Simplified implementation: analyze brightness differences at image edges
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        float leftBrightness = 0;
        float rightBrightness = 0;
        float topBrightness = 0;
        float bottomBrightness = 0;
        
        int sampleSize = 10;
        
        // Left side brightness
        for (int j = 0; j < height; j += height / sampleSize) {
            int pixel = bitmap.getPixel(width / 8, j);
            leftBrightness += getPixelBrightness(pixel);
        }
        
        // Right side brightness
        for (int j = 0; j < height; j += height / sampleSize) {
            int pixel = bitmap.getPixel(width * 7 / 8, j);
            rightBrightness += getPixelBrightness(pixel);
        }
        
        // Top brightness
        for (int i = 0; i < width; i += width / sampleSize) {
            int pixel = bitmap.getPixel(i, height / 8);
            topBrightness += getPixelBrightness(pixel);
        }
        
        // Bottom brightness
        for (int i = 0; i < width; i += width / sampleSize) {
            int pixel = bitmap.getPixel(i, height * 7 / 8);
            bottomBrightness += getPixelBrightness(pixel);
        }
        
        // Analyze light direction
        if (leftBrightness > rightBrightness + 20) {
            return "左側光線";
        } else if (rightBrightness > leftBrightness + 20) {
            return "右側光線";
        } else if (topBrightness > bottomBrightness + 20) {
            return "頂部光線";
        } else if (bottomBrightness > topBrightness + 20) {
            return "底部光線";
        } else {
            return "均勻光線";
        }
    }
    
    /**
     * Get pixel brightness
     */
    private float getPixelBrightness(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }
    
    /**
     * Comprehensive lighting condition judgment
     */
    private String determineLightingCondition(float brightness, float contrast) {
        if (brightness < BRIGHTNESS_THRESHOLD_LOW && contrast < CONTRAST_THRESHOLD_LOW) {
            return "昏暗環境";
        } else if (brightness > BRIGHTNESS_THRESHOLD_HIGH && contrast > CONTRAST_THRESHOLD_HIGH) {
            return "明亮高對比";
        } else if (brightness > BRIGHTNESS_THRESHOLD_HIGH) {
            return "明亮環境";
        } else if (contrast > CONTRAST_THRESHOLD_HIGH) {
            return "高對比環境";
        } else {
            return "正常光線";
        }
    }
}
