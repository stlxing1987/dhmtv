package xyz.doikki.videoplayer.exo;

import com.google.android.exoplayer2.C;

/**
 * ExoPlayer 运行时配置，由 App 层在播放前注入（避免 player 模块依赖 Hawk）。
 */
public final class ExoPlayerConfig {

    /** 最小缓冲时长（毫秒），默认 20s */
    private static int minBufferMs = 20_000;
    /** 最大缓冲时长（毫秒），默认 40s */
    private static int maxBufferMs = 40_000;
    /** 起播缓冲（毫秒） */
    private static int bufferForPlaybackMs = 2_500;
    /** 重缓冲阈值（毫秒） */
    private static int bufferForPlaybackAfterRebufferMs = 5_000;
    /** 缓冲字节上限，防止 4K 高码率占满内存；0 表示不限制 */
    private static int targetBufferBytes = 96 * 1024 * 1024;
    /** 是否启用磁盘缓存 */
    private static boolean cacheEnabled = true;
    /** x86 模拟器等场景优先软解 */
    private static boolean preferSoftwareDecoder = false;
    /** Android TV 隧道模式（SurfaceView + Exo） */
    private static boolean tunnelingEnabled = true;
    /** 格式推断提示，用于解析失败时重试 */
    private static int formatHint = C.TYPE_OTHER;

    private ExoPlayerConfig() {
    }

    public static void apply(int bufferSeconds, boolean enableCache, boolean softwareDecoder, boolean tunneling) {
        int sec = Math.max(10, bufferSeconds);
        minBufferMs = sec * 1000;
        maxBufferMs = minBufferMs * 2;
        bufferForPlaybackMs = Math.min(2_500, minBufferMs / 4);
        bufferForPlaybackAfterRebufferMs = Math.min(5_000, minBufferMs / 2);
        cacheEnabled = enableCache;
        preferSoftwareDecoder = softwareDecoder;
        tunnelingEnabled = tunneling;
    }

    public static void resetPlaybackHints() {
        formatHint = C.TYPE_OTHER;
    }

    public static void setPreferSoftwareDecoder(boolean prefer) {
        preferSoftwareDecoder = prefer;
    }

    public static void setFormatHint(int hint) {
        formatHint = hint;
    }

    public static int getFormatHint() {
        return formatHint;
    }

    public static int getMinBufferMs() {
        return minBufferMs;
    }

    public static int getMaxBufferMs() {
        return maxBufferMs;
    }

    public static int getBufferForPlaybackMs() {
        return bufferForPlaybackMs;
    }

    public static int getBufferForPlaybackAfterRebufferMs() {
        return bufferForPlaybackAfterRebufferMs;
    }

    public static int getTargetBufferBytes() {
        return targetBufferBytes;
    }

    public static boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public static boolean isPreferSoftwareDecoder() {
        return preferSoftwareDecoder;
    }

    public static boolean isTunnelingEnabled() {
        return tunnelingEnabled;
    }
}
