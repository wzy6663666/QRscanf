package com.qrscanf.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public class ScannerOverlayView extends View {

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final RectF topDim = new RectF();
    private final RectF bottomDim = new RectF();
    private final RectF leftDim = new RectF();
    private final RectF rightDim = new RectF();
    private ValueAnimator animator;
    private float progress;
    private boolean scanning = true;
    private float focusX;
    private float focusY;
    private boolean focusVisible;

    public ScannerOverlayView(Context context) {
        this(context, null);
    }

    public ScannerOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScannerOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        dimPaint.setColor(0x99000000);
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(3.4f * density);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        borderPaint.setColor(0x26FFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f * density);
        linePaint.setStrokeWidth(2f * density);
        focusPaint.setColor(0xCCFFFFFF);
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(2f * density);
        focusPaint.setShadowLayer(6f * density, 0, 0, 0xCC7B8BFF);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void showFocus(float x, float y) {
        focusX = x;
        focusY = y;
        focusVisible = true;
        invalidate();
        postDelayed(() -> {
            focusVisible = false;
            invalidate();
        }, 900);
    }

    public void setScanning(boolean scanning) {
        this.scanning = scanning;
        if (scanning) {
            startAnimationIfNeeded();
        } else if (animator != null) {
            animator.pause();
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (scanning) {
            startAnimationIfNeeded();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void startAnimationIfNeeded() {
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2100);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
        }
        if (!animator.isStarted()) {
            animator.start();
        } else if (animator.isPaused()) {
            animator.resume();
        }
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        float side = Math.min(w * 0.71f, 272f * density);
        float centerX = w / 2f;
        float centerY = h * 0.455f;
        float left = centerX - side / 2f;
        float top = centerY - side / 2f;
        top = Math.max(top, 132f * density);
        float panelReserve = 188f * density;
        if (top + side > h - panelReserve) {
            top = Math.max(132f * density, h - panelReserve - side);
        }
        frame.set(left, top, left + side, top + side);

        topDim.set(0, 0, w, frame.top);
        bottomDim.set(0, frame.bottom, w, h);
        leftDim.set(0, frame.top, frame.left, frame.bottom);
        rightDim.set(frame.right, frame.top, w, frame.bottom);

        linePaint.setShader(new LinearGradient(frame.left, 0, frame.right, 0,
                new int[]{0x007B8BFF, 0xCC7B8BFF, 0x007B8BFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (frame.isEmpty()) {
            return;
        }
        canvas.drawRect(topDim, dimPaint);
        canvas.drawRect(bottomDim, dimPaint);
        canvas.drawRect(leftDim, dimPaint);
        canvas.drawRect(rightDim, dimPaint);

        canvas.drawRect(frame, borderPaint);

        float density = getResources().getDisplayMetrics().density;
        float len = 24f * density;
        float r = 14f * density;
        // top-left
        canvas.drawLine(frame.left, frame.top + r, frame.left, frame.top + len, cornerPaint);
        canvas.drawLine(frame.left + r, frame.top, frame.left + len, frame.top, cornerPaint);
        // top-right
        canvas.drawLine(frame.right - r, frame.top, frame.right - len, frame.top, cornerPaint);
        canvas.drawLine(frame.right, frame.top + r, frame.right, frame.top + len, cornerPaint);
        // bottom-left
        canvas.drawLine(frame.left, frame.bottom - r, frame.left, frame.bottom - len, cornerPaint);
        canvas.drawLine(frame.left + r, frame.bottom, frame.left + len, frame.bottom, cornerPaint);
        // bottom-right
        canvas.drawLine(frame.right - r, frame.bottom, frame.right - len, frame.bottom, cornerPaint);
        canvas.drawLine(frame.right, frame.bottom - r, frame.right, frame.bottom - len, cornerPaint);

        if (scanning) {
            float y = frame.top + 8f * density + (frame.height() - 16f * density) * progress;
            linePaint.setAlpha(220);
            canvas.drawLine(frame.left + 10f * density, y, frame.right - 10f * density, y, linePaint);
        }

        if (focusVisible) {
            canvas.drawCircle(focusX, focusY, 26f * density, focusPaint);
        }
    }
}
