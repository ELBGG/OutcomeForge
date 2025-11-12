package pe.elb.outcomememories.game.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import pe.elb.outcomememories.game.PlayerTypeOM;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRegistry {

    private static final Map<UUID, PlayerDefinition> registry = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerTypeOM> previousRoles = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastSwapTime = new ConcurrentHashMap<>();

    private static final float LOW_HEALTH_THRESHOLD = 8.0F;
    private static final long SWAP_COOLDOWN_MS = 2000L;

    public static void setPlayerType(ServerPlayer player, PlayerTypeOM type) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");

        PlayerDefinition def = new PlayerDefinition(player, type);
        registry.put(player.getUUID(), def);
    }

    public static void setPlayerType(Player player, PlayerTypeOM type) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");

        PlayerDefinition def = PlayerDefinition.fromPlayer(player, type);
        registry.put(player.getUUID(), def);
    }

    public static void setPlayerTypeByUUID(UUID playerUUID, PlayerTypeOM type) {
        Objects.requireNonNull(playerUUID, "playerUUID");
        Objects.requireNonNull(type, "type");

        PlayerDefinition def = PlayerDefinition.forClient(playerUUID, type);
        registry.put(playerUUID, def);
    }

    public static PlayerTypeOM getPlayerType(ServerPlayer player) {
        return player != null ? getPlayerType(player.getUUID()) : null;
    }

    public static PlayerTypeOM getPlayerType(Player player) {
        return player != null ? getPlayerType(player.getUUID()) : null;
    }

    public static PlayerTypeOM getPlayerType(UUID playerUUID) {
        PlayerDefinition def = registry.get(playerUUID);
        return def != null ? def.getType() : null;
    }

    public static PlayerDefinition get(ServerPlayer player) {
        return player != null ? registry.get(player.getUUID()) : null;
    }

    public static PlayerDefinition get(UUID playerUUID) {
        return registry.get(playerUUID);
    }

    public static void remove(ServerPlayer player) {
        if (player != null) {
            remove(player.getUUID());
        }
    }

    public static void remove(UUID playerUUID) {
        if (playerUUID != null) {
            registry.remove(playerUUID);
            previousRoles.remove(playerUUID);
            lastSwapTime.remove(playerUUID);
        }
    }

    public static boolean has(ServerPlayer player) {
        return player != null && registry.containsKey(player.getUUID());
    }

    public static boolean has(UUID playerUUID) {
        return playerUUID != null && registry.containsKey(playerUUID);
    }

    public static void clear() {
        registry.clear();
        previousRoles.clear();
        lastSwapTime.clear();
    }

    public static int size() {
        return registry.size();
    }

    public static boolean isEmpty() {
        return registry.isEmpty();
    }

    public static void savePreviousRole(UUID playerUUID, PlayerTypeOM type) {
        previousRoles.put(playerUUID, type);
    }

    public static PlayerTypeOM getPreviousRole(UUID playerUUID) {
        return previousRoles.get(playerUUID);
    }

    public static void cleanup(UUID playerUUID) {
        remove(playerUUID);
    }

    public static void cleanupAll() {
        clear();
    }

    public static class PlayerDefinition {
        private final ServerPlayer serverPlayer;
        private final UUID playerUUID;
        private final PlayerTypeOM type;

        public PlayerDefinition(ServerPlayer player, PlayerTypeOM type) {
            this.serverPlayer = Objects.requireNonNull(player, "player");
            this.playerUUID = player.getUUID();
            this.type = Objects.requireNonNull(type, "type");
        }

        private PlayerDefinition(UUID playerUUID, PlayerTypeOM type) {
            this.serverPlayer = null;
            this.playerUUID = Objects.requireNonNull(playerUUID, "playerUUID");
            this.type = Objects.requireNonNull(type, "type");
        }

        public static PlayerDefinition forAmy(ServerPlayer player) {
            return new PlayerDefinition(player, PlayerTypeOM.AMY);
        }

        public static PlayerDefinition forClient(UUID playerUUID, PlayerTypeOM type) {
            return new PlayerDefinition(playerUUID, type);
        }

        public static PlayerDefinition fromPlayer(Player player, PlayerTypeOM type) {
            if (player instanceof ServerPlayer) {
                return new PlayerDefinition((ServerPlayer) player, type);
            } else {
                return new PlayerDefinition(player.getUUID(), type);
            }
        }

        public ServerPlayer getPlayer() {
            return serverPlayer;
        }

        public PlayerTypeOM getType() {
            return type;
        }

        public UUID getUuid() {
            return playerUUID;
        }

        public boolean isServerSide() {
            return serverPlayer != null;
        }

        public boolean isClientSide() {
            return serverPlayer == null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PlayerDefinition that = (PlayerDefinition) o;
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

            return "PlayerDefinition{" +
                    "player=" + playerName +
                    ", type=" + type +
                    ", side=" + (isServerSide() ? "SERVER" : "CLIENT") +
                    '}';
        }
    }
}