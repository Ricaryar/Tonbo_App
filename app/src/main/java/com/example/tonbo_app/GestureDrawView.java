package com.example.tonbo_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 手勢繪畫視圖
 * 用於用戶繪製手勢
 */
public class GestureDrawView extends View {
    private static final String TAG = "GestureDrawView";
    
    private Paint paint;
    private Path currentPath;
    private List<Path> paths;
    private List<Integer> colors;
    
    private int currentColor;
    
    public GestureDrawView(Context context) {
        super(context);
        init();
    }
    
    public GestureDrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        
        paths = new ArrayList<>();
        colors = new ArrayList<>();
        currentColor = Color.WHITE;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 繪製背景
        canvas.drawColor(Color.TRANSPARENT);
        
        // 繪製所有路徑
        for (int i = 0; i < paths.size(); i++) {
            paint.setColor(colors.get(i));
            canvas.drawPath(paths.get(i), paint);
        }
        
        // 繪製當前路徑
        if (currentPath != null) {
            paint.setColor(currentColor);
            canvas.drawPath(currentPath, paint);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                return true;
                
            case MotionEvent.ACTION_MOVE:
                currentPath.lineTo(x, y);
                invalidate();
                return true;
                
            case MotionEvent.ACTION_UP:
                if (currentPath != null) {
                    paths.add(new Path(currentPath));
                    colors.add(currentColor);
                    currentPath = null;
                    invalidate();
                }
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * 清除所有繪畫
     */
    public void clear() {
        paths.clear();
        colors.clear();
        currentPath = null;
        invalidate();
    }
    
    /**
     * 獲取當前繪畫的點列表
     */
    public List<Path> getPaths() {
        List<Path> result = new ArrayList<>(paths);
        if (currentPath != null) {
            result.add(new Path(currentPath));
        }
        return result;
    }
    
    /**
     * 檢查是否已繪畫
     */
    public boolean hasDrawing() {
        return !paths.isEmpty() || currentPath != null;
    }
}
