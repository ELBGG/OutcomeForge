package pe.elb.outcomememories.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cache del lado del cliente para el Laser Cannon de Tails
 */
public class TailsLaserClientCache {
    
    public static class LaserData {
        public String phase;
        public float progress;
        
        public LaserData(String phase, float progress) {
            this.phase = phase;
            this.progress = progress;
        }
    }
    
    private static final Map<UUID, LaserData> laserCache = new HashMap<>();
    
    public static void updateLaserData(UUID playerUUID, String phase, float progress) {
        laserCache.put(playerUUID, new LaserData(phase, progress));
    }
    
    public static LaserData getLaserData(UUID playerUUID) {
        return laserCache.getOrDefault(playerUUID, new LaserData("NONE", 0.0F));
    }
    
    public static boolean isActive(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null && !"NONE".equals(data.phase);
    }
    
    public static String getPhase(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null ? data.phase : "NONE";
    }
    
    public static float getProgress(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null ? data.progress : 0.0F;
    }
    
    public static void clear() {
        laserCache.clear();
    }
}