package pe.elb.outcomememories.client.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cache unificado del lado del cliente para todas las habilidades de Tails
 * 
 * Maneja:
 * - Glide (planeo): energía y estado
 * - Laser Cannon: fase y progreso de carga
 */
public class TailsClientCache {

    // ============ GLIDE DATA ============
    
    public static class GlideData {
        public float energyPercent;
        public boolean isGliding;
        
        public GlideData(float energyPercent, boolean isGliding) {
            this.energyPercent = energyPercent;
            this.isGliding = isGliding;
        }
    }
    
    private static final Map<UUID, GlideData> glideCache = new HashMap<>();
    
    // ============ LASER DATA ============
    
    public static class LaserData {
        public String phase;
        public float progress;
        
        public LaserData(String phase, float progress) {
            this.phase = phase;
            this.progress = progress;
        }
    }
    
    private static final Map<UUID, LaserData> laserCache = new HashMap<>();
    
    // ============ GLIDE - MÉTODOS ============
    
    /**
     * Actualiza los datos del Glide de un jugador
     */
    public static void updateGlideData(UUID playerUUID, float energyPercent, boolean isGliding) {
        glideCache.put(playerUUID, new GlideData(energyPercent, isGliding));
    }
    
    /**
     * Obtiene los datos completos del Glide
     */
    public static GlideData getGlideData(UUID playerUUID) {
        return glideCache.getOrDefault(playerUUID, new GlideData(1.0F, false));
    }
    
    /**
     * Verifica si el jugador está planeando
     */
    public static boolean isGliding(UUID playerUUID) {
        GlideData data = glideCache.get(playerUUID);
        return data != null && data.isGliding;
    }
    
    /**
     * Obtiene el porcentaje de energía del Glide
     */
    public static float getGlideEnergyPercent(UUID playerUUID) {
        GlideData data = glideCache.get(playerUUID);
        return data != null ? data.energyPercent : 1.0F;
    }
    
    /**
     * Limpia todos los datos de Glide
     */
    public static void clearGlideCache() {
        glideCache.clear();
    }
    
    /**
     * Limpia los datos de Glide de un jugador específico
     */
    public static void clearGlideData(UUID playerUUID) {
        glideCache.remove(playerUUID);
    }
    
    // ============ LASER - MÉTODOS ============
    
    /**
     * Actualiza los datos del Laser Cannon de un jugador
     */
    public static void updateLaserData(UUID playerUUID, String phase, float progress) {
        laserCache.put(playerUUID, new LaserData(phase, progress));
    }
    
    /**
     * Obtiene los datos completos del Laser
     */
    public static LaserData getLaserData(UUID playerUUID) {
        return laserCache.getOrDefault(playerUUID, new LaserData("NONE", 0.0F));
    }
    
    /**
     * Verifica si el Laser está activo (cualquier fase excepto NONE)
     */
    public static boolean isLaserActive(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null && !"NONE".equals(data.phase);
    }
    
    /**
     * Obtiene la fase actual del Laser
     */
    public static String getLaserPhase(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null ? data.phase : "NONE";
    }
    
    /**
     * Obtiene el progreso de carga del Laser (0.0 a 1.0)
     */
    public static float getLaserProgress(UUID playerUUID) {
        LaserData data = laserCache.get(playerUUID);
        return data != null ? data.progress : 0.0F;
    }
    
    /**
     * Limpia todos los datos de Laser
     */
    public static void clearLaserCache() {
        laserCache.clear();
    }
    
    /**
     * Limpia los datos de Laser de un jugador específico
     */
    public static void clearLaserData(UUID playerUUID) {
        laserCache.remove(playerUUID);
    }
    
    // ============ UTILIDADES GENERALES ============
    
    /**
     * Limpia todos los cachés de Tails (Glide y Laser)
     */
    public static void clearAll() {
        glideCache.clear();
        laserCache.clear();
    }
    
    /**
     * Limpia todos los datos de un jugador específico
     */
    public static void clearPlayerData(UUID playerUUID) {
        glideCache.remove(playerUUID);
        laserCache.remove(playerUUID);
    }
    
    /**
     * Obtiene el número de jugadores con datos de Glide cacheados
     */
    public static int getGlideCacheSize() {
        return glideCache.size();
    }
    
    /**
     * Obtiene el número de jugadores con datos de Laser cacheados
     */
    public static int getLaserCacheSize() {
        return laserCache.size();
    }
    
    /**
     * Debug: Imprime información del caché
     */
    public static void printDebugInfo() {
        System.out.println("[TailsCache] Glide entries: " + glideCache.size());
        System.out.println("[TailsCache] Laser entries: " + laserCache.size());
    }
}