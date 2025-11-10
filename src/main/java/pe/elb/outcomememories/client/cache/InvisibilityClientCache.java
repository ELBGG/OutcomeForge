package pe.elb.outcomememories.client.cache;

import pe.elb.outcomememories.net.skills.exe.ExeSyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Cache del cliente para mostrar survivors cercanos durante invisibilidad
 */
public class InvisibilityClientCache {
    
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
    
    public static void clear() {
        nearbySurvivors.clear();
        isInvisActive = false;
    }
}