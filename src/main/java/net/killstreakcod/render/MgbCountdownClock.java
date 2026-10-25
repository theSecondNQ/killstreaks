package net.killstreakcod.render;

/**
 * Map the authoritative server end tick to local world time, including late join and resynchronization.
 * 将服务端确认的结束刻映射到本地世界时间，支持晚加入及重新同步，不按渲染帧减秒。
 */
public final class MgbCountdownClock {
    public static final int DURATION_TICKS = 200;
    private boolean initialized;
    private long serverTick, clientTick, serverEndTick;

    public boolean synchronize(long serverTime, int remaining, long clientTime) {
        if (initialized && serverTime <= serverTick)
            return false;
        initialized = true;
        serverTick = serverTime;
        clientTick = clientTime;
        serverEndTick = serverTime + remaining;
        return true;
    }

    public float remaining(long clientTime, float partial) {
        if (!initialized)
            return 0;
        double elapsed = Math.max(0, clientTime - clientTick + partial);
        return (float) Math.max(0, serverEndTick - serverTick - elapsed);
    }

    public float elapsedMilliseconds(long clientTime, float partial) {
        return Math.max(0, DURATION_TICKS - remaining(clientTime, partial)) * 50;
    }

    public void reset() {
        initialized = false;
        serverTick = clientTick = serverEndTick = 0;
    }
}
