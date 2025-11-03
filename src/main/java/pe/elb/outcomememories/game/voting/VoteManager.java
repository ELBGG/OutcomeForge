package pe.elb.outcomememories.voting;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro global en memoria de votos.
 *
 * - Mantiene un mapa UUID -> count (global)
 * - Mantiene un mapa UUID -> last known name (para mostrar en /votefinish)
 * - Mantiene un mapa chestKey -> (UUID -> count) para conteos por cofre
 *
 * Nota: actualmente es volátil (no persiste entre reinicios). Si quieres persistencia,
 * lo convertimos a SavedData del mundo.
 */
public class VoteManager {

    private static final Map<UUID, Integer> counts = new ConcurrentHashMap<>();
    private static final Map<UUID, String> names = new ConcurrentHashMap<>();

    // Map que guarda por cofre (clave string) un mapa de UUID -> contador
    private static final Map<String, Map<UUID, Integer>> chestCounts = new ConcurrentHashMap<>();

    /**
     * Incrementa el contador de votos global para la UUID dada.
     * @param targetUUID UUID del jugador votado
     * @param targetName Nombre conocido del jugador (se sobrescribe con el último conocido)
     * @return el nuevo total de votos para la UUID (global)
     */
    public static int incrementVote(UUID targetUUID, String targetName) {
        names.put(targetUUID, targetName);
        return counts.merge(targetUUID, 1, Integer::sum);
    }

    /**
     * Incrementa el contador de votos en un cofre identificado por chestKey.
     * También actualiza el registro de nombre conocido para la UUID.
     * @param chestKey clave que identifica el cofre (ej. world@x,y,z)
     * @param targetUUID UUID del jugador votado
     * @param targetName nombre conocido del jugador
     * @return nuevo total de votos para la UUID en ese cofre
     */
    public static int incrementChestVote(String chestKey, UUID targetUUID, String targetName) {
        names.put(targetUUID, targetName);
        Map<UUID, Integer> map = chestCounts.computeIfAbsent(chestKey, k -> new ConcurrentHashMap<>());
        return map.merge(targetUUID, 1, Integer::sum);
    }

    /**
     * Genera un resumen legible de los votos almacenados en un cofre.
     * @param chestKey clave del cofre
     * @return resumen con líneas "nombre count\n"
     */
    public static String getChestSummary(String chestKey) {
        Map<UUID, Integer> map = chestCounts.getOrDefault(chestKey, Collections.emptyMap());
        if (map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        map.forEach((uuid, cnt) -> {
            String name = names.getOrDefault(uuid, uuid.toString());
            sb.append(name).append(" ").append(cnt).append("\n");
        });
        return sb.toString();
    }

    /**
     * Devuelve el número de votos globales para la UUID (0 si no existe).
     */
    public static int getVotes(UUID uuid) {
        return counts.getOrDefault(uuid, 0);
    }

    /**
     * Obtiene el nombre conocido para la UUID, o null si no hay.
     */
    public static String getName(UUID uuid) {
        return names.get(uuid);
    }

    /**
     * Variante que acepta una cadena UUID y devuelve el nombre conocido o null.
     * Útil cuando se trabaja con keys en forma de String.
     */
    public static String getNameSafe(String uuidString) {
        try {
            UUID u = UUID.fromString(uuidString);
            return names.get(u);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Devuelve una vista inmutable del mapa de contadores globales.
     */
    public static Map<UUID, Integer> getAllCounts() {
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Reinicia todos los contadores (útil si quieres reiniciar entre rondas).
     */
    public static void resetAll() {
        counts.clear();
        names.clear();
        chestCounts.clear();
    }
}