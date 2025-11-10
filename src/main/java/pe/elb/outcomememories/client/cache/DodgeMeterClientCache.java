package pe.elb.outcomememories.client.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cache del lado del cliente para el Dodge Meter
 */
public class DodgeMeterClientCache {
    
    private static final Map<UUID, Float> dodgeHPCache = new HashMap<>();
    
    public static void updateDodgeHP(UUID playerUUID, float dodgeHP) {
        dodgeHPCache.put(playerUUID, dodgeHP);
    }
    
    public static float getDodgeHP(UUID playerUUID) {
        return dodgeHPCache.getOrDefault(playerUUID, 50.0F);
    }
    
    public static void clear() {
        dodgeHPCache.clear();
    }
}