import com.nx.adcleaner.Rules;

import java.util.Arrays;

public class RulesTest {
    public static void main(String[] a) {
        String[] skip = {"跳过", "跳过广告", "跳过 3", "3s跳过", "5 | 跳过", "SKIP", "跳过本次", "跳过片头"};
        String[] notSkip = {"跳过弹幕", "关闭", "上滑继续观看短剧", "跳过设置里的某些很长很长很长的文字说明"};
        String[] close = {"关闭", "关闭广告", "×", "✕", "close", "X"};
        String[] notClose = {"关闭弹幕", "关闭评论", "开启"};
        String[] hint = {"上滑继续观看短剧", "上滑继续看短剧", "上滑观看"};
        String[] badge = {"广告", "广告 · 3s", "广告 5 秒", "AD"};
        String[] notBadge = {"关闭广告", "跳过广告", "看广告解锁", "广告解锁按钮点这里"};
        int ok = 0;
        int bad = 0;
        for (String s : skip) {
            if (Rules.isSkip(s, null)) { ok++; } else { bad++; System.out.println("MISS skip: " + s); }
        }
        for (String s : notSkip) {
            if (!Rules.isSkip(s, null)) { ok++; } else { bad++; System.out.println("FALSE skip: " + s); }
        }
        for (String s : close) {
            if (Rules.isClose(s)) { ok++; } else { bad++; System.out.println("MISS close: " + s); }
        }
        for (String s : notClose) {
            if (!Rules.isClose(s)) { ok++; } else { bad++; System.out.println("FALSE close: " + s); }
        }
        for (String s : hint) {
            if (Rules.isSwipeHint(s)) { ok++; } else { bad++; System.out.println("MISS hint: " + s); }
        }
        for (String s : badge) {
            if (Rules.isAdBadge(s)) { ok++; } else { bad++; System.out.println("MISS badge: " + s); }
        }
        for (String s : notBadge) {
            if (!Rules.isAdBadge(s)) { ok++; } else { bad++; System.out.println("FALSE badge: " + s); }
        }
        if (Rules.isSkip("投票跳过广告", Arrays.asList("投票"))) { ok++; } else { bad++; System.out.println("MISS custom keyword"); }
        System.out.println("passed=" + ok + " failed=" + bad);
        if (bad > 0) { System.exit(1); }
    }
}
