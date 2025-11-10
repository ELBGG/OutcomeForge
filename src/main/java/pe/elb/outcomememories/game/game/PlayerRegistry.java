package pe.elb.outcomememories.game.game;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import pe.elb.outcomememories.game.PlayerTypeOM;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro optimizado de definiciones de jugador.
 * Funciona tanto en servidor como en cliente.
 */
public class PlayerRegistry {

    private static final Map<UUID, PlayerDefineSuvivor> registry = new ConcurrentHashMap<>();

    // ============================================
    // MÉTODOS DE ESCRITURA (SET)
    // ============================================

    /**
     * Asigna un tipo al jugador (SERVIDOR)
     */
    public static void setPlayerType(ServerPlayer player, PlayerTypeOM type) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");

        PlayerDefineSuvivor def = (type == PlayerTypeOM.AMY)
                ? PlayerDefineSuvivor.forAmy(player)
                : new PlayerDefineSuvivor(player, type);

        registry.put(player.getUUID(), def);
    }

    /**
     * ✅ Asigna un tipo desde Player genérico (CLIENTE)
     */
    public static void setPlayerType(Player player, PlayerTypeOM type) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");

        // Usar el factory method que acepta Player genérico
        PlayerDefineSuvivor def = PlayerDefineSuvivor.fromPlayer(player, type);
        registry.put(player.getUUID(), def);
    }

    /**
     * ✅ Asigna un tipo solo por UUID (cuando el jugador no está cargado en cliente)
     */
    public static void setPlayerTypeByUUID(UUID playerUUID, PlayerTypeOM type) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(type, "type");

        PlayerDefineSuvivor def = PlayerDefineSuvivor.forClient(playerUUID, type);
        registry.put(playerUUID, def);
    }

    // ============================================
    // MÉTODOS DE LECTURA (GET TYPE)
    // ============================================

    /**
     * Obtiene el tipo de jugador (sobrecarga para todos los casos)
     */
    public static PlayerTypeOM getPlayerType(ServerPlayer player) {
        return player != null ? getPlayerType(player.getUUID()) : null;
    }

    public static PlayerTypeOM getPlayerType(LocalPlayer player) {
        return player != null ? getPlayerType(player.getUUID()) : null;
    }

    public static PlayerTypeOM getPlayerType(Player player) {
        return player != null ? getPlayerType(player.getUUID()) : null;
    }

    /**
     * ✅ Método base: obtiene tipo por UUID (todos los demás llaman a este)
     */
    public static PlayerTypeOM getPlayerType(UUID playerUUID) {
        PlayerDefineSuvivor def = registry.get(playerUUID);
        return def != null ? def.getType() : null;
    }

    // ============================================
    // MÉTODOS DE ACCESO COMPLETO (GET DEFINITION)
    // ============================================

    /**
     * Obtiene la definición completa (solo útil en servidor)
     */
    public static PlayerDefineSuvivor get(ServerPlayer player) {
        return player != null ? registry.get(player.getUUID()) : null;
    }

    /**
     * ✅ Obtiene la definición por UUID
     */
    public static PlayerDefineSuvivor get(UUID playerUUID) {
        return registry.get(playerUUID);
    }

    // ============================================
    // MÉTODOS DE ELIMINACIÓN (REMOVE)
    // ============================================

    /**
     * Elimina la definición de un jugador
     */
    public static void remove(ServerPlayer player) {
        if (player != null) {
            registry.remove(player.getUUID());
        }
    }

    /**
     * ✅ Elimina por UUID
     */
    public static void remove(UUID playerUUID) {
        if (playerUUID != null) {
            registry.remove(playerUUID);
        }
    }

    // ============================================
    // MÉTODOS DE VERIFICACIÓN (HAS/EXISTS)
    // ============================================

    /**
     * Verifica si existe una definición para el jugador
     */
    public static boolean has(ServerPlayer player) {
        return player != null && registry.containsKey(player.getUUID());
    }

    /**
     * ✅ Verifica por UUID
     */
    public static boolean has(UUID playerUUID) {
        return playerUUID != null && registry.containsKey(playerUUID);
    }

    // ============================================
    // MÉTODOS DE UTILIDAD
    // ============================================

    /**
     * ✅ Limpia todo el registro
     */
    public static void clear() {
        registry.clear();
    }

    /**
     * ✅ Tamaño del registro (para debug)
     */
    public static int size() {
        return registry.size();
    }

    /**
     * ✅ Verifica si el registro está vacío
     */
    public static boolean isEmpty() {
        return registry.isEmpty();
    }
}