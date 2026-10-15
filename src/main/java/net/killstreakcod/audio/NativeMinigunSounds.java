package net.killstreakcod.audio;

/**
 * Compatibility cleanup entry; NativeMinigunAudio owns the single motor loop.
 * 保留兼容清理入口；唯一的电机循环音由 NativeMinigunAudio 管理。
 */
public final class NativeMinigunSounds {
    private NativeMinigunSounds() {}

    public static void clear() {
        NativeMinigunAudio.clear();
    }
}
