package com.nx.adcleaner;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 屏幕顶部常驻的「监控中」小胶囊，可拖动，点一下打开 App。
 * 系统状态栏右侧那一排（WiFi/信号）只有系统应用能放，App 只能这样叠一层。
 */
public class MonitorOverlay {

    private final Context context;
    private WindowManager windowManager;
    private View root;
    private WindowManager.LayoutParams params;

    public MonitorOverlay(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean canShow() {
        try {
            return Settings.canDrawOverlays(context);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void show() {
        if (root != null || !canShow()) {
            return;
        }
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCC1A1D24);
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), 0xFF2A2F3A);
        box.setBackground(background);
        box.setPadding(dp(10), dp(5), dp(12), dp(5));

        View dot = new View(context);
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(0xFF4ADE80);
        dot.setBackground(dotBackground);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.rightMargin = dp(6);
        box.addView(dot, dotParams);

        TextView label = new TextView(context);
        label.setText("监控中");
        label.setTextColor(0xFFE8EAED);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        box.addView(label);

        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(intent);
                } catch (RuntimeException ignored) {
                    // 后台启动受限时忽略
                }
            }
        });

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(12);
        params.y = dp(38);

        box.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downX);
                        int dy = (int) (event.getRawY() - downY);
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                            moved = true;
                        }
                        params.x = startX + dx;
                        params.y = startY + dy;
                        try {
                            windowManager.updateViewLayout(root, params);
                        } catch (RuntimeException ignored) {
                            // 视图已移除
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            view.performClick();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        root = box;
        try {
            windowManager.addView(root, params);
        } catch (RuntimeException e) {
            root = null;
        }
    }

    public void hide() {
        if (root == null || windowManager == null) {
            return;
        }
        try {
            windowManager.removeView(root);
        } catch (RuntimeException ignored) {
            // 已被系统移除
        }
        root = null;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
