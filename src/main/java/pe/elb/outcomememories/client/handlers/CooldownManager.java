package pe.elb.outcomememories.client.handlers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor cliente de cooldowns para overlay.
 * Almacena por id (ej: "cream_dash", "amy_hammer") -> endTime y duration.
 */
public class CooldownManager {

    private static class CD {
        final long startMs;
        final long durationMs;
        final long endMs;
        CD(long startMs, long durationMs) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.endMs = startMs + durationMs;
        }
    }

    private static final Map<String, CD> COOLDOWNS = new ConcurrentHashMap<>();

    public static void setCooldown(String id, long durationMs, long startMs) {
        if (id == null) return;
        COOLDOWNS.put(id, new CD(startMs, durationMs));
    }

    /**
     * Obtener ms restantes (>=0). Si no existe devuelve 0.
     */
    public static long getRemainingMs(String id) {
        CD cd = COOLDOWNS.get(id);
        if (cd == null) return 0L;
        long now = System.currentTimeMillis();
        long rem = cd.endMs - now;
        if (rem <= 0) {
            COOLDOWNS.remove(id);
            return 0L;
        }
        return rem;
    }

    /**
     * Fracción 0..1 del cooldown (1 = full remaining, 0 = ready).
     */
    public static double getFraction(String id) {
        CD cd = COOLDOWNS.get(id);
        if (cd == null) return 0.0;
        long rem = getRemainingMs(id);
        if (rem <= 0) return 0.0;
        return Math.min(1.0, (double) rem / (double) cd.durationMs);
    }

    public static boolean isOnCooldown(String id) {
        return getRemainingMs(id) > 0;
    }

    public static void clear(String id) {
        COOLDOWNS.remove(id);
    }

    public static void clearAll() {
        COOLDOWNS.clear();
    }
}