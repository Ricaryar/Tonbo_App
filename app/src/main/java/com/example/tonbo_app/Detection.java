package com.example.tonbo_app;

/**
 * Simple POJO for overlay drawing: bounding box in view coordinates + label + confidence.
 */
public class Detection {
    private final float left;
    private final float top;
    private final float right;
    private final float bottom;
    private final String label;
    private final float confidence;

    public Detection(float left, float top, float right, float bottom, String label) {
        this(left, top, right, bottom, label, 0f);
    }

    public Detection(float left, float top, float right, float bottom, String label, float confidence) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.label = label;
        this.confidence = confidence;
    }

    public float getLeft()       { return left; }
    public float getTop()        { return top; }
    public float getRight()      { return right; }
    public float getBottom()     { return bottom; }
    public String getLabel()     { return label; }
    public float getConfidence() { return confidence; }
}
