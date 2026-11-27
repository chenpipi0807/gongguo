package com.nx.adcleaner;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 一个按钮的监控面板：
 * 开启 → 起前台服务（常驻通知「广告监控中」）→ 无障碍服务干活。
 */
public class MainActivity extends Activity {

    private static final int BG = 0xFF0F1115;
    private static final int CARD = 0xFF1A1D24;
    private static final int TEXT = 0xFFE8EAED;
    private static final int MUTED = 0xFF9AA0A6;
    private static final int ACCENT = 0xFF4ADE80;
    private static final int WARN = 0xFFF87171;
    private static final int STROKE = 0xFF2A2F3A;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Prefs prefs;

    private TextView statusTitle;
    private TextView statusSub;
    private TextView statsValue;
    private Button mainBtn;

    private TextView accessValue;
    private TextView serviceValue;
    private TextView actionValue;
    private TextView overlayValue;
    private TextView targetValue;

    private LinearLayout logBox;
    private LinearLayout advancedBox;
    private TextView advancedToggle;
    private EditText keywordInput;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 1000);
        }
    };

    private final ActionLog.Listener logListener = new ActionLog.Listener() {
        @Override
        public void onChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    renderLog();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);

        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(30));
        scroll.addView(root, new ViewGroup.LayoutParams(MATCH, WRAP));
        setContentView(scroll);

        TextView name = label("工果", 28, TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(name);
        root.addView(label("广告自动跳过 · 免 root", 13, MUTED));

        buildMainCard(root);
        buildStatusCard(root);
        buildTargetCard(root);
        buildLogCard(root);
        buildAdvancedCard(root);

        root.addView(label("说明：只在你指定的 App 里匹配「跳过 / 关闭」文字并点一下，不联网、不上传、不修改其它应用。", 12, 0xFF6B7280),
                marginTop(dp(20)));

        renderLog();
    }

    private void buildMainCard(LinearLayout root) {
        LinearLayout card = card();

        statusTitle = label("监控未开启", 24, WARN);
        statusTitle.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(statusTitle);

        statusSub = label("", 13, MUTED);
        card.addView(statusSub);

        statsValue = label("", 12, 0xFF6B7280);
        card.addView(statsValue, marginTop(dp(6)));

        mainBtn = button("开启监控", ACCENT, 0xFF06210F);
        mainBtn.setTextSize(17);
        mainBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMonitor();
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(MATCH, dp(54));
        btnLp.topMargin = dp(16);
        card.addView(mainBtn, btnLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rowLp.topMargin = dp(10);
        card.addView(row, rowLp);

        Button openTarget = button("添加最近应用", STROKE, TEXT);
        openTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addRecentTarget();
            }
        });
        row.addView(openTarget, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button export = button("导出日志", STROKE, TEXT);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportLog();
            }
        });
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        exportLp.leftMargin = dp(10);
        row.addView(export, exportLp);

        root.addView(card);
    }

    private void buildStatusCard(LinearLayout root) {
        LinearLayout card = card();
        card.addView(sectionTitle("状态"));

        accessValue = label("", 14, TEXT);
        card.addView(statusRow("无障碍服务", accessValue, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        }));

        serviceValue = label("", 14, TEXT);
        card.addView(statusRow("前台服务", serviceValue, null));

        actionValue = label("还没有动作", 13, MUTED);
        card.addView(statusRow("最近动作", actionValue, null));

        overlayValue = label("", 13, MUTED);
        card.addView(statusRow("顶部「监控中」胶囊", overlayValue, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestOverlay();
            }
        }));

        TextView keepAlive = label("点这里把「工果」加入电池白名单，防止后台被系统杀掉", 12, ACCENT);
        keepAlive.setPadding(0, dp(10), 0, dp(2));
        keepAlive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestBatteryWhitelist();
            }
        });
        card.addView(keepAlive);

        root.addView(card);
    }

    private void buildTargetCard(LinearLayout root) {
        LinearLayout card = card();
        card.addView(sectionTitle("目标应用"));

        targetValue = label("", 13, MUTED);
        targetValue.setPadding(0, dp(6), 0, dp(6));
        card.addView(targetValue);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button add = button("添加最近使用", STROKE, TEXT);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addRecentTarget();
            }
        });
        row.addView(add, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button clear = button("清空", STROKE, TEXT);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setTargetsRaw("");
                refresh();
                toast("已清空目标应用");
            }
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        clearLp.leftMargin = dp(10);
        row.addView(clear, clearLp);
        card.addView(row, marginTop(dp(6)));

        card.addView(label("先去目标 App 里用一下，回来点「添加最近使用」即可（不用手打包名）", 12, 0xFF6B7280),
                marginTop(dp(10)));
        root.addView(card);
    }

    private void buildLogCard(LinearLayout root) {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(sectionTitle("最近记录"), new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView clear = label("清空", 12, ACCENT);
        clear.setPadding(dp(10), dp(6), dp(2), dp(6));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActionLog.clear();
            }
        });
        header.addView(clear);
        card.addView(header);

        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        logBox.setPadding(0, dp(6), 0, 0);
        card.addView(logBox, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(card);
    }

    private void buildAdvancedCard(LinearLayout root) {
        LinearLayout card = card();
        advancedToggle = sectionTitle("高级设置 ▸");
        advancedToggle.setPadding(0, dp(6), 0, dp(6));
        advancedToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = advancedBox.getVisibility() != View.VISIBLE;
                advancedBox.setVisibility(show ? View.VISIBLE : View.GONE);
                advancedToggle.setText(show ? "高级设置 ▾" : "高级设置 ▸");
            }
        });
        card.addView(advancedToggle);

        advancedBox = new LinearLayout(this);
        advancedBox.setOrientation(LinearLayout.VERTICAL);
        advancedBox.setVisibility(View.GONE);

        advancedBox.addView(switchRow("只在「广告」角标出现时出手", "最稳；角标不是文字时会导致完全不动手", prefs.requireAdBadge(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        prefs.setRequireAdBadge(isChecked);
                    }
                }));
        advancedBox.addView(switchRow("跳过开屏广告", "识别「跳过 / 跳过 3s」并点击", prefs.skipSplash(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        prefs.setSkipSplash(isChecked);
                    }
                }));
        advancedBox.addView(switchRow("关闭广告弹窗", "识别「关闭 / ×」并点击", prefs.closePopup(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        prefs.setClosePopup(isChecked);
                    }
                }));
        advancedBox.addView(switchRow("直播广告自动上滑", "这种广告读不到文字，用「控件树里没有文字」判断后上滑", prefs.liveAd(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        prefs.setLiveAd(isChecked);
                    }
                }));
        advancedBox.addView(switchRow("中插广告自动上滑", "很多播放器里上滑=下一集，容易误跳集，默认关闭", prefs.swipeUp(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        prefs.setSwipeUp(isChecked);
                    }
                }));
        advancedBox.addView(switchRow("所有应用生效", "默认只处理目标应用", prefs.allApps(),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        prefs.setAllApps(isChecked);
                    }
                }));

        advancedBox.addView(label("自定义关键词（逗号分隔，命中即点）", 13, TEXT));
        keywordInput = new EditText(this);
        keywordInput.setSingleLine(true);
        keywordInput.setHint("例如：跳过广告,关闭广告");
        keywordInput.setHintTextColor(0xFF6B7280);
        keywordInput.setTextColor(TEXT);
        keywordInput.setTextSize(14);
        keywordInput.setText(prefs.keywordsRaw());
        keywordInput.setInputType(InputType.TYPE_CLASS_TEXT);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0xFF11141A);
        inputBg.setCornerRadius(dp(10));
        inputBg.setStroke(dp(1), STROKE);
        keywordInput.setBackground(inputBg);
        keywordInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        advancedBox.addView(keywordInput, marginTop(dp(8)));

        Button save = button("保存关键词", STROKE, TEXT);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setKeywordsRaw(keywordInput.getText().toString());
                toast("已保存");
            }
        });
        advancedBox.addView(save, marginTop(dp(10)));

        card.addView(advancedBox);
        root.addView(card);
    }

    private void toggleMonitor() {
        if (Status.serviceRunning) {
            prefs.setEnabled(false);
            MonitorService.stop(this);
            Status.serviceRunning = false;
            toast("已停止，广告不再自动处理");
            refresh();
            return;
        }
        prefs.setEnabled(true);
        if (!isServiceEnabled()) {
            toast("还需要在系统里开一次无障碍");
            openAccessibilitySettings();
            return;
        }
        ensureNotificationPermission();
        startMonitorQuietly();
        toast("已开始监控");
        refresh();
    }

    /** 启动失败不弹错：无障碍服务本身也在干活，前台服务只是保活和通知。 */
    private void startMonitorQuietly() {
        try {
            MonitorService.start(this);
        } catch (RuntimeException ignored) {
            // 忽略
        }
    }

    private void refresh() {
        boolean running = Status.serviceRunning;
        statusTitle.setText(running ? "广告监控中" : "监控未开启");
        statusTitle.setTextColor(running ? ACCENT : WARN);
        statusSub.setText(running
                ? "已处理 " + Status.actions + " 次 · 开机自启，不用再点按钮"
                : "点下面的按钮开始，需要先开启无障碍服务");
        statsValue.setText("扫描 " + Status.scans + " 次 · 见到「广告」角标 " + Status.badgeHits
                + " 次 · 出手 " + Status.actions + " 次");

        mainBtn.setText(running ? "停止监控" : "开启监控");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(running ? 0xFF3A1F1F : ACCENT);
        mainBtn.setBackground(bg);
        mainBtn.setTextColor(running ? WARN : 0xFF06210F);

        boolean accessible = isServiceEnabled();
        accessValue.setText(accessible ? "已开启" : "未开启 · 点这行去开");
        accessValue.setTextColor(accessible ? ACCENT : WARN);
        serviceValue.setText(running ? "运行中" : "未运行");
        serviceValue.setTextColor(running ? ACCENT : MUTED);
        String reason = Status.lastReason;
        actionValue.setText(reason);

        boolean overlayOn = false;
        try {
            overlayOn = Settings.canDrawOverlays(this);
        } catch (RuntimeException ignored) {
            // 忽略
        }
        overlayValue.setText(overlayOn ? "已开启" : "未授权 · 点这开启");
        overlayValue.setTextColor(overlayOn ? ACCENT : WARN);

        if (targetValue != null) {
            java.util.List<String> targets = prefs.targets();
            if (targets.isEmpty()) {
                targetValue.setText("未设置。先去要处理的 App 里用一下，回来点「添加最近使用」");
                targetValue.setTextColor(WARN);
            } else {
                StringBuilder sb = new StringBuilder();
                for (String pkg : targets) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(pkg);
                }
                targetValue.setText(sb.toString());
                targetValue.setTextColor(ACCENT);
            }
        }
    }

    private void renderLog() {
        if (logBox == null) {
            return;
        }
        logBox.removeAllViews();
        List<String> items = ActionLog.snapshot();
        if (items.isEmpty()) {
            logBox.addView(label("暂无记录。正常看剧时这里应该是空的，只有广告出现才会有记录。", 12, MUTED));
            return;
        }
        int count = 0;
        for (String item : items) {
            TextView line = label(item, 12, MUTED);
            line.setPadding(0, dp(3), 0, dp(3));
            logBox.addView(line);
            if (++count >= 12) {
                break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActionLog.addListener(logListener);
        if (keywordInput != null) {
            keywordInput.setText(prefs.keywordsRaw());
        }
        // 授权过一次之后就不用再点按钮：打开 App 自动起
        AccessibilityKeeper.ensureEnabled(this);
        if (prefs.enabled() && isServiceEnabled() && !Status.serviceRunning) {
            startMonitorQuietly();
        }
        MonitorService.refreshOverlay();
        refresh();
        renderLog();
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        ActionLog.removeListener(logListener);
        super.onPause();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    private boolean isServiceEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null) {
            return false;
        }
        String target = getPackageName() + "/" + AdSkipService.class.getName();
        for (String part : flat.split(":")) {
            if (part.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("在列表里找到「净屏」并打开");
        } catch (RuntimeException e) {
            toast("请手动进入 设置 → 无障碍 开启");
        }
    }

    private void requestOverlay() {
        boolean granted;
        try {
            granted = Settings.canDrawOverlays(this);
        } catch (RuntimeException e) {
            granted = false;
        }
        if (granted) {
            MonitorService.refreshOverlay();
            toast("顶部胶囊已开启，可以拖动位置");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            toast("打开「显示在其他应用上层」后返回即可");
        } catch (RuntimeException e) {
            toast("请手动到 设置 → 应用 → 净屏 → 显示在其他应用上层");
        }
    }

    private void requestBatteryWhitelist() {        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (RuntimeException e) {
            toast("请手动到 设置 → 应用 → 工果 → 电池 → 不限制");
        }
    }

    private void addRecentTarget() {
        final java.util.List<String> recent = AdSkipService.recentPackages();
        if (recent.isEmpty()) {
            toast("还没读到其它应用，先去目标 App 里划两下再回来");
            return;
        }
        final String[] items = recent.toArray(new String[0]);
        new android.app.AlertDialog.Builder(this)
                .setTitle("选择要处理的应用")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        prefs.addTarget(items[which]);
                        refresh();
                        toast("已添加 " + items[which]);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void launchTarget() {
        java.util.List<String> targets = prefs.targets();
        if (targets.isEmpty()) {
            toast("先添加目标应用");
            return;
        }
        Intent intent = getPackageManager().getLaunchIntentForPackage(targets.get(0));
        if (intent == null) {
            toast("没有找到 " + targets.get(0));
            return;
        }
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            toast("打开失败：" + e.getMessage());
        }
    }

    private void exportLog() {
        try {
            String where = LogStore.export(this);
            toast("已导出：" + where);
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    private View statusRow(String name, TextView value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        row.addView(label(name, 14, TEXT), new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        valueLp.leftMargin = dp(12);
        value.setGravity(Gravity.END);
        row.addView(value, valueLp);
        if (listener != null) {
            row.setOnClickListener(listener);
        }
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView view = label(text, 12, ACCENT);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private TextView label(String text, float sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private View switchRow(String title, String subtitle, boolean checked,
                           CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(title, 15, TEXT));
        texts.addView(label(subtitle, 12, MUTED));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);

        row.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        toggleLp.leftMargin = dp(8);
        row.addView(toggle, toggleLp);
        return row;
    }

    private Button button(String text, int bgColor, int textColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(textColor);
        btn.setTextSize(15);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        btn.setStateListAnimator(null);
        return btn;
    }

    private LinearLayout.LayoutParams marginTop(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = px;
        return lp;
    }

    private View marginTop(View view, int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = px;
        view.setLayoutParams(lp);
        return view;
    }


    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
