package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

public class OverlayView extends View {

    private List<Detection> detections;
    private Paint boxPaint;
    private Paint textPaint;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);

        textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(50f);
    }

    public void setDetections(List<Detection> detections) {
        this.detections = detections;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (detections == null) return;

        for (Detection det : detections) {
            canvas.drawRect(
                    det.getLeft(),
                    det.getTop(),
                    det.getRight(),
                    det.getBottom(),
                    boxPaint
            );

            canvas.drawText(
                    det.getLabel(),
                    det.getLeft(),
                    det.getTop() - 10,
                    textPaint
            );
        }
    }
}
