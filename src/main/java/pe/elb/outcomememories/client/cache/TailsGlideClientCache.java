package pe.elb.outcomememories.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cache del lado del cliente para el Glide de Tails
 */
public class TailsGlideClientCache {
    
    public static class GlideData {
        public float energyPercent;
        public boolean isGliding;
        
        public GlideData(float energyPercent, boolean isGliding) {
            this.energyPercent = energyPercent;
            this.isGliding = isGliding;
        }
    }
    
    private static final Map<UUID, GlideData> glideCache = new HashMap<>();
    
    public static void updateGlideData(UUID playerUUID, float energyPercent, boolean isGliding) {
        glideCache.put(playerUUID, new GlideData(energyPercent, isGliding));
    }
    
    public static GlideData getGlideData(UUID playerUUID) {
        return glideCache.getOrDefault(playerUUID, new GlideData(1.0F, false));
    }
    
    public static boolean isGliding(UUID playerUUID) {
        GlideData data = glideCache.get(playerUUID);
        return data != null && data.isGliding;
    }
    
    public static float getEnergyPercent(UUID playerUUID) {
        GlideData data = glideCache.get(playerUUID);
        return data != null ? data.energyPercent : 1.0F;
    }
    
    public static void clear() {
        glideCache.clear();
    }
}