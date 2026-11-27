package com.nx.adcleaner;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 无障碍服务：读控件树 → 匹配文字 → 点一下或滑一下。
 *
 * 除了干活，它还负责「自证活着」：
 * - 每 30 秒写一条心跳（当前包名、窗口类名、界面上的文字），用来判断到底有没有读到界面；
 * - 每次出手前后写快照，方便事后核对它到底看到了什么。
 */
public class AdSkipService extends AccessibilityService {

    private static final int MAX_NODES = 700;
    private static final long SCAN_DEBOUNCE_MS = 90L;
    private static final long CLICK_COOLDOWN_MS = 600L;
    private static final long REPEAT_COOLDOWN_MS = 1500L;
    private static final long SWIPE_COOLDOWN_MS = 8000L;
    private static final long LIVE_COOLDOWN_MS = 4000L;
    private static final long LIVE_WINDOW_MS = 60000L;
    private static final int LIVE_MAX = 3;
    /** 直播广告：控件树里一个可见文字都没有，持续这么久才动手。 */
    private static final long STRUCT_COOLDOWN_MS = 15000L;
    private static final long STRUCT_PERSIST_MS = 2500L;
    /** 广告界面是静态 Surface，可能不产生无障碍事件，靠这个心跳定时扫描兜底。 */
    private static final long TICK_MS = 800L;
    private static final long HEARTBEAT_MS = 30000L;
    private static final int MAX_SNAPSHOTS = 40;

    private static volatile AdSkipService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingScan;

    private long lastClickAt;
    private long lastSwipeAt;
    private String lastKey = "";
    private long lastKeyAt;
    private int snapshots;
    private long lastSnapshotAt;

    private String lastPkg = "";
    private String lastOtherPkg = "";

    /** 最近出现过前台的应用（去重、最多 8 个），供界面选择添加。 */
    private static final java.util.ArrayDeque<String> RECENT = new java.util.ArrayDeque<>();
    private String lastWindowClass = "";
    private boolean lastBadge;

    private long lastLiveAt;
    private long liveWindowStart;
    private int liveCount;
    private String lastLiveCls = "";

    private String lastActivityClass = "";
    private long emptySince;
    private long lastStructSwipeAt;
    private long lastDiagAt;
    private String lastWindowInfo = "";
    private long lastRichAt;
    private long pendingResumeAt;

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            try {
                beat();
            } catch (RuntimeException ignored) {
                // 心跳失败不致命
            }
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            try {
                Prefs prefs = Prefs.get(AdSkipService.this);
                if (prefs.enabled()) {
                    scan();
                }
            } catch (RuntimeException ignored) {
                // 忽略
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Status.resetCounters();
        ActionLog.add("无障碍服务已连接");
        lastRichAt = SystemClock.uptimeMillis();
        LogStore.log(this, "connect", "service connected");
        handler.postDelayed(heartbeat, HEARTBEAT_MS);
        handler.postDelayed(ticker, TICK_MS);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence pkgName = event.getPackageName();
        if (pkgName == null) {
            return;
        }
        String pkg = pkgName.toString();
        if (pkg.equals(getPackageName())) {
            return;
        }
        CharSequence cls = event.getClassName();
        lastPkg = pkg;
        if (!pkg.equals(getPackageName())) {
            lastOtherPkg = pkg;
            rememberRecent(pkg);
        }
        lastWindowClass = cls == null ? "" : cls.toString();
        Prefs prefs = Prefs.get(this);
        // 只记目标 App 自己的 Activity，别被音量条之类的系统弹窗打断
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && cls != null && cls.toString().contains(".")
                && prefs.matchesPackage(pkg)) {
            lastActivityClass = cls.toString();
        }
        if (!prefs.enabled() || !prefs.matchesPackage(pkg)) {
            return;
        }
        scheduleScan();
    }

    private void scheduleScan() {
        if (pendingScan != null) {
            handler.removeCallbacks(pendingScan);
        }
        pendingScan = new Runnable() {
            @Override
            public void run() {
                pendingScan = null;
                try {
                    scan();
                } catch (RuntimeException ignored) {
                    // 界面重建时节点会失效，忽略本次即可
                }
            }
        };
        handler.postDelayed(pendingScan, SCAN_DEBOUNCE_MS);
    }

    private void scan() {
        Prefs prefs = Prefs.get(this);
        if (!prefs.enabled()) {
            return;
        }
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (RuntimeException e) {
            return;
        }
        if (root == null) {
            return;
        }
        // 定时扫描时，当前窗口可能已经不是目标应用了，必须再核一次包名
        try {
            CharSequence rootPkg = root.getPackageName();
            if (rootPkg == null || !prefs.matchesPackage(rootPkg.toString())) {
                return;
            }
        } catch (RuntimeException e) {
            return;
        }
        int windowId;
        try {
            windowId = root.getWindowId();
        } catch (RuntimeException e) {
            return;
        }

        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes, 0);
        Status.scans++;

        long now = SystemClock.uptimeMillis();
        boolean badge = hasAdBadge(nodes, windowId);

        if (badge) {
            Status.badgeHits++;
        }
        // 角标从无到有时存一份快照，方便判断识别得准不准
        if (badge && !lastBadge) {
            snapshot("badge", nodes);
        }
        lastBadge = badge;

        // 统计「此刻真正可见的文字节点」，直播广告的判据就是它归零
        int visibleTexts = 0;
        for (AccessibilityNodeInfo node : nodes) {
            if (usable(node, windowId) && textOf(node) != null) {
                visibleTexts++;
            }
        }

        // 直播广告：全屏直播间预览，文字是画在视频画面里的，无障碍读不到，
        // 所以只能用「结构」判断：还在短剧播放页，但一个可见文字都没有。
        // 直播广告：全屏直播间预览，文字是画在视频画面里的，无障碍读不到，
        // 所以只能用「结构」判断：还在目标应用里，但一个可见文字都没有。
        boolean overlayEmpty = hasEmptyOverlay();
        if (now - lastDiagAt > 5000) {
            lastDiagAt = now;
            LogStore.log(this, "diag", "act=" + lastActivityClass + " vis=" + visibleTexts
                    + " nodes=" + nodes.size()
                    + " overlay=" + overlayEmpty + " win=" + lastWindowInfo
                    + " emptySince=" + emptySince);
        }
        if (visibleTexts >= 5) {
            lastRichAt = now;
        }
        boolean emptyNow = visibleTexts == 0 || overlayEmpty;
        if (prefs.liveAd() && emptyNow) {
            if (emptySince == 0) {
                emptySince = now;
            } else if (now - emptySince >= STRUCT_PERSIST_MS
                    && now - lastStructSwipeAt >= STRUCT_COOLDOWN_MS) {
                if (now - liveWindowStart > LIVE_WINDOW_MS) {
                    liveWindowStart = now;
                    liveCount = 0;
                }
                if (liveCount < LIVE_MAX && doSwipeUp()) {
                    lastStructSwipeAt = now;
                    pendingResumeAt = 0;
                    emptySince = 0;
                    liveCount++;
                    snapshot("struct", nodes);
                    record("上滑退出直播广告", "可见文字=0");
                    return;
                }
            }
        } else {
            emptySince = 0;
        }

        // 文字版兜底：有些广告会把「点击进入直播间」暴露成节点
        boolean live = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (usable(node, windowId) && Rules.isLiveAd(textOf(node))) {
                live = true;
                break;
            }
        }
        if (live && !lastWindowClass.equals(lastLiveCls)) {
            lastLiveCls = lastWindowClass;
            snapshot("live", nodes);
        }
        if (live && prefs.liveAd() && now - lastLiveAt > LIVE_COOLDOWN_MS) {
            if (now - liveWindowStart > LIVE_WINDOW_MS) {
                liveWindowStart = now;
                liveCount = 0;
            }
            if (liveCount < LIVE_MAX && doSwipeUp()) {
                lastLiveAt = now;
                liveCount++;
                record("上滑退出直播广告", "命中进入直播间");
                return;
            }
        }

        boolean gate = prefs.requireAdBadge() && !badge;
        if (gate) {
            return;
        }

        // 规则 3：中插广告的「上滑继续观看短剧」。很多播放器里上滑本身就等于下一集，
        // 默认关闭；开启时也只允许每 8 秒滑一次。
        if (prefs.swipeUp() && now - lastSwipeAt > SWIPE_COOLDOWN_MS) {
            for (AccessibilityNodeInfo node : nodes) {
                if (!usable(node, windowId)) {
                    continue;
                }
                String text = textOf(node);
                if (Rules.isSwipeHint(text) && doSwipeUp()) {
                    lastSwipeAt = now;
                    snapshot("swipe", nodes);
                    record("上滑跳过中插广告", text);
                    return;
                }
            }
        }

        if (now - lastClickAt < CLICK_COOLDOWN_MS) {
            return;
        }

        List<String> keywords = prefs.keywords();

        // 规则 1：开屏 / 激励视频的跳过按钮
        if (prefs.skipSplash()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (!usable(node, windowId)) {
                    continue;
                }
                String text = textOf(node);
                if (!Rules.isSkip(text, keywords) || !shouldAct("跳过", text)) {
                    continue;
                }
                if (!isPlausibleButton(node)) {
                    continue;
                }
                if (clickNode(node, windowId)) {
                    lastClickAt = now;
                    snapshot("skip", nodes);
                    record("点击跳过", text);
                    return;
                }
            }
        }

        // 规则 2：弹窗关闭按钮
        if (prefs.closePopup()) {
            for (AccessibilityNodeInfo node : nodes) {
                if (!usable(node, windowId)) {
                    continue;
                }
                String text = textOf(node);
                if (!Rules.isClose(text) || !shouldAct("关闭", text)) {
                    continue;
                }
                if (!isPlausibleButton(node)) {
                    continue;
                }
                if (clickNode(node, windowId)) {
                    lastClickAt = now;
                    snapshot("close", nodes);
                    record("关闭弹窗", text);
                    return;
                }
            }
        }
    }

    /** 节点必须是此刻真实可见、且属于当前窗口的，否则坐标可能是过期的。 */
    private boolean usable(AccessibilityNodeInfo node, int windowId) {
        try {
            return node.isVisibleToUser() && node.getWindowId() == windowId;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 只对「像按钮」的小控件出手，避免点到整行设置项、整屏广告容器。 */
    private boolean isPlausibleButton(AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        try {
            node.getBoundsInScreen(rect);
        } catch (RuntimeException e) {
            return false;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        if (width <= 0 || height <= 0) {
            return false;
        }
        return rect.width() > 0 && rect.height() > 0
                && rect.width() <= width * 0.35f
                && rect.height() <= height * 0.18f;
    }

    /**
     * 直播广告是叠在播放器上面的一个独立窗口（约 6 个控件、0 个可见文字），
     * 播放器本体还在下面，所以「当前窗口」永远有文字。必须把所有窗口都看一遍。
     */
    private boolean hasEmptyOverlay() {
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (RuntimeException e) {
            return false;
        }
        if (windows == null) {
            return false;
        }
        StringBuilder info = new StringBuilder();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        long screen = (long) metrics.widthPixels * metrics.heightPixels;
        boolean found = false;
        for (AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root;
            try {
                root = window.getRoot();
            } catch (RuntimeException e) {
                continue;
            }
            if (root == null) {
                continue;
            }
            CharSequence pkg = root.getPackageName();
            if (pkg == null || !Prefs.get(this).matchesPackage(pkg.toString())) {
                continue;
            }
            Rect rect = new Rect();
            try {
                root.getBoundsInScreen(rect);
            } catch (RuntimeException e) {
                continue;
            }
            List<AccessibilityNodeInfo> nodes = new ArrayList<>();
            collect(root, nodes, 0);
            int texts = 0;
            for (AccessibilityNodeInfo node : nodes) {
                if (usable(node, window.getId()) && textOf(node) != null) {
                    texts++;
                }
            }
            long area = (long) rect.width() * rect.height();
            boolean full = screen > 0 && area >= screen * 0.6;
            info.append('[').append(window.getType()).append(' ')
                    .append(rect.width()).append('x').append(rect.height())
                    .append(" n=").append(nodes.size())
                    .append(" t=").append(texts)
                    .append(full ? " F" : " p").append(']');
            if (full && nodes.size() <= 20 && texts == 0) {
                found = true;
            }
        }
        lastWindowInfo = info.length() > 160 ? info.substring(0, 160) : info.toString();
        return found;
    }

    private boolean hasAdBadge(List<AccessibilityNodeInfo> nodes, int windowId) {
        for (AccessibilityNodeInfo node : nodes) {
            if (!usable(node, windowId)) {
                continue;
            }
            if (Rules.isAdBadge(textOf(node))) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldAct(String what, String text) {
        String key = what + "|" + text;
        return !(key.equals(lastKey) && SystemClock.uptimeMillis() - lastKeyAt < REPEAT_COOLDOWN_MS);
    }

    private void record(String what, String text) {
        lastKey = what + "|" + text;
        lastKeyAt = SystemClock.uptimeMillis();
        String shown = text == null ? "" : text.trim();
        if (shown.length() > 16) {
            shown = shown.substring(0, 16) + "…";
        }
        Status.actions++;
        Status.lastActionAt = SystemClock.uptimeMillis();
        Status.lastReason = what + "：" + shown;
        ActionLog.add(what + "：" + shown);
        LogStore.log(this, "action", what + "|" + shown + "|cls=" + lastWindowClass);
        MonitorService.refresh(this);
    }

    private void beat() {
        Prefs prefs = Prefs.get(this);
        if (!prefs.enabled() || !prefs.matchesPackage(lastPkg)) {
            return;
        }
        MonitorService.refresh(this);
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (RuntimeException e) {
            return;
        }
        if (root == null) {
            LogStore.log(this, "beat", "root=null pkg=" + lastPkg + " cls=" + lastWindowClass);
            return;
        }
        LogStore.log(this, "beat", "pkg=" + lastPkg + " cls=" + lastWindowClass
                + " scans=" + Status.scans + " badge=" + Status.badgeHits + " act=" + Status.actions
                + " texts=" + topTexts(root, 25));
    }

    private String topTexts(AccessibilityNodeInfo root, int limit) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes, 0);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (AccessibilityNodeInfo node : nodes) {
            String text = textOf(node);
            if (text == null) {
                continue;
            }
            String trimmed = text.trim().replace('\n', ' ');
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > 24) {
                trimmed = trimmed.substring(0, 24);
            }
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(trimmed);
            if (++count >= limit) {
                break;
            }
        }
        return sb.toString();
    }

    /** 把当前界面控件树压成一行行文本存进日志，用来核对识别是否准确。 */
    private void snapshot(String why, List<AccessibilityNodeInfo> nodes) {
        long now = SystemClock.uptimeMillis();
        if (snapshots >= MAX_SNAPSHOTS || now - lastSnapshotAt < 3000) {
            return;
        }
        snapshots++;
        lastSnapshotAt = now;
        LogStore.log(this, "snap", "why=" + why + " cls=" + lastWindowClass + "\n" + treeText(nodes, 60));
    }

    private String treeText(List<AccessibilityNodeInfo> nodes, int limit) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (AccessibilityNodeInfo node : nodes) {
            String text = textOf(node);
            Rect rect = new Rect();
            try {
                node.getBoundsInScreen(rect);
            } catch (RuntimeException ignored) {
                continue;
            }
            sb.append(++count).append(' ')
                    .append(simpleClass(node)).append(" b=")
                    .append(rect.left).append(',').append(rect.top).append(',')
                    .append(rect.right).append(',').append(rect.bottom)
                    .append(" clk=").append(node.isClickable())
                    .append(" vis=").append(node.isVisibleToUser());
            if (text != null) {
                String trimmed = text.trim().replace('\n', ' ');
                if (trimmed.length() > 40) {
                    trimmed = trimmed.substring(0, 40);
                }
                sb.append(" t=").append(trimmed);
            }
            sb.append('\n');
            if (count >= limit) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 手动抓取当前界面：通过广播触发。
     * adb shell am broadcast -a com.nx.adcleaner.DUMP
     * 之后用 adb shell run-as com.nx.adcleaner cat files/events.jsonl 读结果。
     */
    public static String dumpActiveWindow() {
        AdSkipService service = instance;
        return service == null ? null : service.dumpNow();
    }

    private String dumpNow() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (RuntimeException e) {
            return null;
        }
        if (root == null) {
            return null;
        }
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collect(root, nodes, 0);
        return "pkg=" + lastPkg + " cls=" + lastWindowClass + " n=" + nodes.size() + "\n"
                + treeText(nodes, 200);
    }

    private String simpleClass(AccessibilityNodeInfo node) {
        CharSequence cls = null;
        try {
            cls = node.getClassName();
        } catch (RuntimeException ignored) {
            // 节点失效
        }
        if (cls == null) {
            return "?";
        }
        String value = cls.toString();
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
    }

    private void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out, int depth) {
        if (node == null || out.size() >= MAX_NODES || depth > 40) {
            return;
        }
        out.add(node);
        int count;
        try {
            count = node.getChildCount();
        } catch (RuntimeException e) {
            return;
        }
        for (int i = 0; i < count && out.size() < MAX_NODES; i++) {
            AccessibilityNodeInfo child;
            try {
                child = node.getChild(i);
            } catch (RuntimeException e) {
                continue;
            }
            if (child != null) {
                collect(child, out, depth + 1);
            }
        }
    }

    private String textOf(AccessibilityNodeInfo node) {
        try {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                return text.toString();
            }
            CharSequence desc = node.getContentDescription();
            if (desc != null && desc.length() > 0) {
                return desc.toString();
            }
        } catch (RuntimeException ignored) {
            // 节点已失效
        }
        return null;
    }

    /**
     * 只点匹配到的这个控件本身：
     * 可点就 performAction，不可点就在它的中心按一下（屏幕最上层就是按钮自己）。
     * 不再往上找「可点的父容器」——广告的父容器往往是整屏可点区域，一点就跳广告落地页。
     */
    private boolean clickNode(AccessibilityNodeInfo node, int windowId) {
        if (!usable(node, windowId)) {
            return false;
        }
        Rect rect = new Rect();
        try {
            node.getBoundsInScreen(rect);
        } catch (RuntimeException e) {
            return false;
        }
        if (rect.width() <= 0 || rect.height() <= 0) {
            return false;
        }

        try {
            if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // 落到坐标点击
        }

        // 整屏大小的节点不按中心点：那一下会落在播放器或广告画面上
        if (isFullScreen(rect)) {
            return false;
        }
        if (!usable(node, windowId)) {
            return false;
        }
        return tap(rect.exactCenterX(), rect.exactCenterY());
    }

    private boolean isFullScreen(Rect rect) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        long screen = (long) metrics.widthPixels * metrics.heightPixels;
        long area = (long) rect.width() * rect.height();
        return screen > 0 && area > screen * 0.5;
    }

    private boolean tap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                .build();
        try {
            return dispatchGesture(gesture, null, null);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean doSwipeUp() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        if (width <= 0 || height <= 0) {
            return false;
        }
        float x = width / 2f;
        Path path = new Path();
        path.moveTo(x, height * 0.85f);
        path.lineTo(x, height * 0.15f);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 100))
                .build();
        try {
            return dispatchGesture(gesture, null, null);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
        // 系统回收无障碍能力时无需处理
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (pendingScan != null) {
            handler.removeCallbacks(pendingScan);
        }
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    /** 给界面读状态用；服务没开时返回 null。 */
    public static String describe() {
        AdSkipService service = instance;
        if (service == null) {
            return null;
        }
        return "pkg=" + service.lastPkg + " cls=" + service.lastWindowClass;
    }

    /** 最近一次前台应用的包名，供界面「添加最近应用」使用。 */
    public static String lastForegroundPackage() {
        AdSkipService service = instance;
        return service == null ? "" : service.lastOtherPkg;
    }

    /** 最近出现过前台的第三方应用（过滤掉系统 UI / 桌面）。 */
    public static java.util.List<String> recentPackages() {
        java.util.List<String> out = new java.util.ArrayList<>();
        synchronized (RECENT) {
            out.addAll(RECENT);
        }
        return out;
    }

    private static void rememberRecent(String pkg) {
        if (pkg.startsWith("android") || pkg.startsWith("com.android.")
                || pkg.contains("launcher") || pkg.contains("systemui")
                || pkg.startsWith("com.huawei.android.") || pkg.equals("com.huawei.android.launcher")) {
            return;
        }
        synchronized (RECENT) {
            RECENT.remove(pkg);
            RECENT.addFirst(pkg);
            while (RECENT.size() > 8) {
                RECENT.removeLast();
            }
        }
    }
}
