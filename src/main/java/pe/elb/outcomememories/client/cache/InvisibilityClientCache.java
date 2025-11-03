package pe.elb.outcomememories.client;

import pe.elb.outcomememories.net.skills.exe.InvisibilityNearbySyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Cache del cliente para mostrar survivors cercanos durante invisibilidad
 */
public class InvisibilityClientCache {
    
    private static List<InvisibilityNearbySyncPacket.SurvivorInfo> nearbySurvivors = new ArrayList<>();
    private static boolean isInvisActive = false;
    
    public static void updateNearbySurvivors(List<InvisibilityNearbySyncPacket.SurvivorInfo> survivors) {
        nearbySurvivors = new ArrayList<>(survivors);
        isInvisActive = !survivors.isEmpty();
    }
    
    public static List<InvisibilityNearbySyncPacket.SurvivorInfo> getNearbySurvivors() {
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