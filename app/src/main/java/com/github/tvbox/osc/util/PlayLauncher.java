package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.VodInfo;

/**
 * 避免通过 Intent 传递超大 VodInfo（集数多时易触发 TransactionTooLargeException 闪退）。
 */
public final class PlayLauncher {
    private static volatile VodInfo pendingVodInfo;

    private PlayLauncher() {
    }

    public static void prepare(VodInfo vodInfo) {
        pendingVodInfo = vodInfo;
    }

    public static VodInfo take() {
        VodInfo info = pendingVodInfo;
        pendingVodInfo = null;
        return info;
    }
}
