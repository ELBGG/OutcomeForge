package pe.elb.outcomememories.client.cache;

import pe.elb.outcomememories.net.skills.exe.ExeSyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache unificado del lado del cliente para todas las habilidades de personajes
 * 
 * Personajes soportados:
 * - Sonic: Dodge Meter
 * - Blaze: Sol Meter
 * - Eggman: Energy Shield
 * - Tails: Glide y Laser Cannon
 * - Executioner: Invisibility
 */
public class PlayerSkillsClientCache {

    // ============ SONIC - DODGE METER ============
    
    private static final Map<UUID, Float> dodgeHPCache = new ConcurrentHashMap<>();
    
    public static void updateDodgeHP(UUID playerUUID, float dodgeHP) {
        dodgeHPCache.put(playerUUID, dodgeHP);
    }
    
    public static float getDodgeHP(UUID playerUUID) {
        return dodgeHPCache.getOrDefault(playerUUID, 50.0F);
    }
    
    public static void clearDodgeCache() {
        dodgeHPCache.clear();
    }

    // ============ BLAZE - SOL METER ============
    
    private static final Map<UUID, Float> solMeterCache = new ConcurrentHashMap<>();
    
    public static void updateSolMeter(UUID playerUUID, float solMeter) {
        solMeterCache.put(playerUUID, solMeter);
    }
    
    public static float getSolMeter(UUID playerUUID) {
        return solMeterCache.getOrDefault(playerUUID, 0.0F);
    }
    
    public static void clearSolMeterCache() {
        solMeterCache.clear();
    }

    // ============ EGGMAN - ENERGY SHIELD ============
    
    public static class ShieldState {
        public boolean active;
        public long startedAt;
        public long durationMs;
        
        public ShieldState(boolean active, long startedAt, long durationMs) {
            this.active = active;
            this.startedAt = startedAt;
            this.durationMs = durationMs;
        }
    }
    
    private static final Map<UUID, ShieldState> shieldCache = new ConcurrentHashMap<>();
    
    public static void setShieldActive(UUID playerUUID, boolean active, long startedAt) {
        if (active) {
            shieldCache.put(playerUUID, new ShieldState(true, startedAt, 10000L));
        } else {
            shieldCache.remove(playerUUID);
        }
    }
    
    public static boolean hasShieldActive(UUID playerUUID) {
        ShieldState state = shieldCache.get(playerUUID);
        if (state == null) return false;
        
        long now = System.currentTimeMillis();
        long elapsed = now - state.startedAt;
        
        if (elapsed >= state.durationMs) {
            shieldCache.remove(playerUUID);
            return false;
        }
        
        return state.active;
    }
    
    public static float getWarningPhase(UUID playerUUID) {
        ShieldState state = shieldCache.get(playerUUID);
        if (state == null) return 0.0F;
        
        long now = System.currentTimeMillis();
        long elapsed = now - state.startedAt;
        long remaining = state.durationMs - elapsed;
        
        if (remaining <= 2000L) {
            return 1.0F - (remaining / 2000.0F);
        }
        
        return 0.0F;
    }
    
    public static void clearShieldCache() {
        shieldCache.clear();
    }

    // ============ TAILS - GLIDE ============
    
    public static class GlideData {
        public float energyPercent;
        public boolean isGliding;
        
        public GlideData(float energyPercent, boolean isGliding) {
            this.energyPercent = energyPercent;
            this.isGliding = isGliding;
        }
    }
    
    private static final Map<UUID, GlideData> glideCache = new ConcurrentHashMap<>();
    
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
    
    public static float getGlideEnergyPercent(UUID playerUUID) {
        GlideData data = glideCache.get(playerUUID);
        return data != null ? data.energyPercent : 1.0F;
    }
    
    public static void clearGlideCache() {
        glideCache.clear();
    }

    // ============ TAILS - LASER CANNON ============
    
    public static class LaserData {
        public String phase;
        public float progress;
        
        public LaserData(String phase, float progress) {
            this.phase = phase;
            this.progress = progress;
        }
    }
    
    private static final Map<UUID, LaserData> laserCache = new ConcurrentHashMap<>();
    
    public static void updateLaserData(UUID playerUUID, String phase, float progress) {
        laserCache.put(playerUUID, new LaserData(phase, progress));
    }
    
    public static LaserData getLaserData(UUID playerUUID) {
        return laserCache.getOrDefault(playerUUID, new LaserData("NONE", 0.0F));
    }
    
    public static boolean isLaserActive(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null && !"NONE".equals(data.phase);
    }
    
    public static String getLaserPhase(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null ? data.phase : "NONE";
    }
    
    public static float getLaserProgress(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null ? data.progress : 0.0F;
    }
    
    public static void clearLaserCache() {
        laserCache.clear();
    }

    // ============ EXECUTIONER - INVISIBILITY ============
    
    private static List<ExeSyncPacket.SurvivorInfo> nearbySurvivors = new ArrayList<>();
    private static boolean isInvisActive = false;
    
    public static void updateNearbySurvivors(List<ExeSyncPacket.SurvivorInfo> survivors) {
        nearbySurvivors = new ArrayList<>(survivors);
        isInvisActive = !survivors.isEmpty();
    }
    
    public static List<ExeSyncPacket.SurvivorInfo> getNearbySurvivors() {
        return new ArrayList<>(nearbySurvivors);
    }
    
    public static boolean isInvisActive() {
        return isInvisActive;
    }
    
    public static void clearInvisibilityCache() {
        nearbySurvivors.clear();
        isInvisActive = false;
    }

    // ============ UTILIDADES GENERALES - LIMPIEZA POR JUGADOR ============
    
    /**
     * Limpia todos los datos de un jugador específico
     */
    public static void clearPlayerData(UUID playerUUID) {
        dodgeHPCache.remove(playerUUID);
        solMeterCache.remove(playerUUID);
        shieldCache.remove(playerUUID);
        glideCache.remove(playerUUID);
        laserCache.remove(playerUUID);
    }
    
    /**
     * Limpia solo los datos de Sonic de un jugador
     */
    public static void clearSonicData(UUID playerUUID) {
        dodgeHPCache.remove(playerUUID);
    }
    
    /**
     * Limpia solo los datos de Blaze de un jugador
     */
    public static void clearBlazeData(UUID playerUUID) {
        solMeterCache.remove(playerUUID);
    }
    
    /**
     * Limpia solo los datos de Eggman de un jugador
     */
    public static void clearEggmanData(UUID playerUUID) {
        shieldCache.remove(playerUUID);
    }
    
    /**
     * Limpia solo los datos de Tails de un jugador
     */
    public static void clearTailsData(UUID playerUUID) {
        glideCache.remove(playerUUID);
        laserCache.remove(playerUUID);
    }

    // ============ UTILIDADES GENERALES - LIMPIEZA TOTAL ============
    
    /**
     * Limpia TODOS los cachés de TODOS los personajes
     */
    public static void clearAll() {
        dodgeHPCache.clear();
        solMeterCache.clear();
        shieldCache.clear();
        glideCache.clear();
        laserCache.clear();
        nearbySurvivors.clear();
        isInvisActive = false;
    }

    // ============ UTILIDADES GENERALES - DEBUG ============
    
    /**
     * Obtiene el tamaño total de todos los cachés
     */
    public static int getTotalCacheSize() {
        return dodgeHPCache.size() 
            + solMeterCache.size() 
            + shieldCache.size() 
            + glideCache.size() 
            + laserCache.size();
    }
    
    /**
     * Debug: Imprime información detallada de todos los cachés
     */
    public static void printDebugInfo() {
        System.out.println("========== PlayerSkillsClientCache Debug ==========");
        System.out.println("[Sonic]  Dodge HP entries:    " + dodgeHPCache.size());
        System.out.println("[Blaze]  Sol Meter entries:   " + solMeterCache.size());
        System.out.println("[Eggman] Shield entries:      " + shieldCache.size());
        System.out.println("[Tails]  Glide entries:       " + glideCache.size());
        System.out.println("[Tails]  Laser entries:       " + laserCache.size());
        System.out.println("[Exe]    Invis active:        " + isInvisActive);
        System.out.println("[Exe]    Nearby survivors:    " + nearbySurvivors.size());
        System.out.println("Total cache entries: " + getTotalCacheSize());
        System.out.println("===================================================");
    }
    
    /**
     * Verifica si un jugador tiene algún dato cacheado
     */
    public static boolean hasAnyData(UUID playerUUID) {
        return dodgeHPCache.containsKey(playerUUID)
            || solMeterCache.containsKey(playerUUID)
            || shieldCache.containsKey(playerUUID)
            || glideCache.containsKey(playerUUID)
            || laserCache.containsKey(playerUUID);
    }
    
    /**
     * Obtiene información de debug de un jugador específico
     */
    public static String getPlayerDebugInfo(UUID playerUUID) {
        StringBuilder sb = new StringBuilder();
        sb.append("Player Cache Info for ").append(playerUUID).append(":\n");
        
        if (dodgeHPCache.containsKey(playerUUID)) {
            sb.append("  - Dodge HP: ").append(dodgeHPCache.get(playerUUID)).append("\n");
        }
        
        if (solMeterCache.containsKey(playerUUID)) {
            sb.append("  - Sol Meter: ").append(solMeterCache.get(playerUUID)).append("\n");
        }
        
        if (shieldCache.containsKey(playerUUID)) {
            ShieldState shield = shieldCache.get(playerUUID);
            sb.append("  - Shield: Active=").append(shield.active)
              .append(", Started=").append(shield.startedAt).append("\n");
        }
        
        if (glideCache.containsKey(playerUUID)) {
            GlideData glide = glideCache.get(playerUUID);
            sb.append("  - Glide: Energy=").append(glide.energyPercent)
              .append(", Active=").append(glide.isGliding).append("\n");
        }
        
        if (laserCache.containsKey(playerUUID)) {
            LaserData laser = laserCache.get(playerUUID);
            sb.append("  - Laser: Phase=").append(laser.phase)
              .append(", Progress=").append(laser.progress).append("\n");
        }
        
        if (!hasAnyData(playerUUID)) {
            sb.append("  (No cached data)\n");
        }
        
        return sb.toString();
    }
}