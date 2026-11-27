package com.nx.adcleaner;

import java.util.List;
import java.util.Locale;

/** 文案匹配规则。所有判断都基于界面上可见的文字 / 无障碍描述。 */
public final class Rules {

    private Rules() {
    }

    /**
     * 开屏 / 激励广告的「跳过」按钮。
     * 常见形态：跳过、跳过广告、跳过 3、3s 跳过、5 | 跳过、SKIP。
     */
    public static boolean isSkip(String text, List<String> customKeywords) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.isEmpty() || value.length() > 14) {
            return false;
        }
        // 排除同名的正常功能入口，避免误点
        if (value.contains("弹幕") || value.contains("片尾") || value.contains("设置")
                || value.contains("教程") || value.contains("引导")) {
            return false;
        }
        if (value.contains("跳过")) {
            return true;
        }
        if (customKeywords != null) {
            for (String keyword : customKeywords) {
                if (value.contains(keyword)) {
                    return true;
                }
            }
        }
        String lower = value.toLowerCase(Locale.ROOT).replace(" ", "");
        return lower.equals("skip") || lower.equals("skipad") || lower.equals("skipads");
    }

    /**
     * 弹窗右上角的关闭按钮。
     * 只接受单独出现的「关闭 / close / ×」，避免误伤「关闭弹幕」这类正常开关。
     */
    public static boolean isClose(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.isEmpty() || value.length() > 10) {
            return false;
        }
        switch (value) {
            case "关闭":
            case "关闭广告":
            case "关闭弹窗":
            case "×":
            case "✕":
            case "✖":
            case "╳":
                return true;
            default:
                break;
        }
        String lower = value.toLowerCase(Locale.ROOT).replace(" ", "");
        return lower.equals("close") || lower.equals("dismiss") || lower.equals("x");
    }

    /**
     * 广告角标：监管要求广告要能被识别，很多 App 会在广告左上角标「广告」。
     * 常见形态：广告、广告 · 3s、广告 5 秒、AD。
     * 注意「关闭广告」「广告解锁」这类按钮不算角标。
     */
    public static boolean isAdBadge(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.isEmpty() || value.length() > 8) {
            return false;
        }
        if (value.startsWith("关闭") || value.startsWith("跳过") || value.startsWith("看")) {
            return false;
        }
        if (value.startsWith("广告")) {
            return true;
        }
        return value.equalsIgnoreCase("ad") || value.equals("赞助") || value.equalsIgnoreCase("sponsored");
    }

    /**
     * 直播广告：全屏的直播间预览（中间「点击进入直播间」、左下「直播中」、底部「上滑继续看短剧」）。
     * 这种广告没有「跳过」也没有「关闭」，唯一的出口就是上滑。
     */
    public static boolean isLiveAd(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.isEmpty() || value.length() > 12) {
            return false;
        }
        if (value.contains("进入直播间")) {
            return true;
        }
        return value.equals("直播中");
    }

    /** 短剧连续播放时出现的中插广告提示：上滑继续观看短剧。 */
    public static boolean isSwipeHint(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        if (value.isEmpty() || value.length() > 24) {
            return false;
        }
        return value.contains("上滑继续") || value.contains("上滑观看")
                || value.contains("上滑看剧") || value.contains("上滑看短剧");
    }
}
