package pe.elb.outcomememories.game.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import pe.elb.outcomememories.game.PlayerTypeOM;

import java.util.Objects;
import java.util.UUID;

/**
 * Clase que representa la definición de un jugador para el sistema de juego.
 * Soporta tanto ServerPlayer (servidor) como Player genérico (cliente).
 */
public class PlayerDefineSuvivor {

    private final ServerPlayer serverPlayer; // Para servidor
    private final UUID playerUUID; // Para cliente (cuando no hay ServerPlayer)
    private final PlayerTypeOM type;

    /**
     * Constructor para SERVIDOR (con ServerPlayer completo)
     */
    public PlayerDefineSuvivor(ServerPlayer player, PlayerTypeOM type) {
        this.serverPlayer = Objects.requireNonNull(player, "player");
        this.playerUUID = player.getUUID();
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * ✅ NUEVO: Constructor para CLIENTE (solo con UUID y tipo)
     * Útil cuando sincronizamos desde RoleUpdatePacket
     */
    private PlayerDefineSuvivor(UUID playerUUID, PlayerTypeOM type) {
        this.serverPlayer = null;
        this.playerUUID = Objects.requireNonNull(playerUUID, "playerUUID");
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Factory helper: crear definición para Amy
     */
    public static PlayerDefineSuvivor forAmy(ServerPlayer player) {
        return new PlayerDefineSuvivor(player, PlayerTypeOM.AMY);
    }

    /**
     * ✅ NUEVO: Factory para cliente (solo tipo y UUID)
     */
    public static PlayerDefineSuvivor forClient(UUID playerUUID, PlayerTypeOM type) {
        return new PlayerDefineSuvivor(playerUUID, type);
    }

    /**
     * ✅ NUEVO: Factory desde Player genérico
     */
    public static PlayerDefineSuvivor fromPlayer(Player player, PlayerTypeOM type) {
        if (player instanceof ServerPlayer) {
            return new PlayerDefineSuvivor((ServerPlayer) player, type);
        } else {
            return new PlayerDefineSuvivor(player.getUUID(), type);
        }
    }

    /**
     * Obtiene el ServerPlayer (puede ser null en cliente)
     */
    public ServerPlayer getPlayer() {
        return serverPlayer;
    }

    public PlayerTypeOM getType() {
        return type;
    }

    public UUID getUuid() {
        return playerUUID;
    }

    /**
     * Verifica si tiene un ServerPlayer válido (está en servidor)
     */
    public boolean isServerSide() {
        return serverPlayer != null;
    }

    /**
     * Verifica si es solo un registro de cliente (solo UUID)
     */
    public boolean isClientSide() {
        return serverPlayer == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PlayerDefineSuvivor that = (PlayerDefineSuvivor) o;
        return Objects.equals(playerUUID, that.playerUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUUID);
    }

    @Override
    public String toString() {
        String playerName = serverPlayer != null
                ? serverPlayer.getGameProfile().getName()
                : playerUUID.toString();

        return "PlayerDefineSuvivor{" +
                "player=" + playerName +
                ", type=" + type +
                ", side=" + (isServerSide() ? "SERVER" : "CLIENT") +
                '}';
    }
}