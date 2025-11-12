package pe.elb.outcomememories.game.game;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import pe.elb.outcomememories.game.PlayerTypeOM;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OutcomeMemoriesGameSystem {

    private static GameState currentState = GameState.WAITING;
    private static long gameStartedAt = 0L;

    private static final Map<UUID, PlayerTypeOM> previousRoles = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();
    private static UUID currentExecutioner = null;

    private static long gameDurationMs = 15 * 60 * 1000L;
    private static long lastThreeMinutesThreshold = 3 * 60 * 1000L;

    private static final List<ExitDoor> exits = new ArrayList<>();
    private static final Map<Long, RoleSwap> pendingSwaps = new ConcurrentHashMap<>();

    public enum GameState {
        WAITING,
        STARTING,
        ACTIVE,
        OVERTIME,
        ENDING,
        FINISHED
    }

    public static class PlayerStats {
        public int kills = 0;
        public int deaths = 0;
        public int timesAsExe = 0;
        public int timesAsSurvivor = 0;
        public long timeAlive = 0L;
        public long lastSpawnTime = 0L;
    }

    public static class ExitDoor {
        public final ServerLevel level;
        public final BlockPos pos;
        public boolean isOpen;

        public ExitDoor(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
            this.isOpen = true;
        }
    }

    private static class RoleSwap {
        public final UUID oldExeUUID;
        public final UUID newExeUUID;
        public final PlayerTypeOM oldExeNewType;
        public final PlayerTypeOM newExePreviousType;

        public RoleSwap(UUID oldExe, UUID newExe, PlayerTypeOM oldExeType, PlayerTypeOM newExeType) {
            this.oldExeUUID = oldExe;
            this.newExeUUID = newExe;
            this.oldExeNewType = oldExeType;
            this.newExePreviousType = newExeType;
        }
    }

    public static boolean startGame() {
        if (currentState == GameState.ACTIVE || currentState == GameState.OVERTIME) {
            return false;
        }

        List<ServerPlayer> players = getAlivePlayers();
        if (players.size() < 2) {
            broadcastMessage("§c¡Se necesitan al menos 2 jugadores para iniciar!");
            return false;
        }

        currentState = GameState.ACTIVE;
        long now = System.currentTimeMillis();
        gameStartedAt = now;

        playerStats.clear();
        previousRoles.clear();

        assignInitialRoles(players);

        for (ServerPlayer player : players) {
            PlayerStats stats = new PlayerStats();
            stats.lastSpawnTime = now;
            playerStats.put(player.getUUID(), stats);
        }

        broadcastMessage("§a§l========================================");
        broadcastMessage("§e§l       OUTCOME MEMORIES INICIADO");
        broadcastMessage("§a§l========================================");
        broadcastMessage("§7Objetivo Survivors: §fSobrevivir el tiempo");
        broadcastMessage("§7Objetivo Exe: §cEliminar a todos");
        broadcastMessage("§a§l========================================");

        playGlobalSound(SoundEvents.END_PORTAL_SPAWN, 1.0F, 1.0F);

        return true;
    }

    private static void assignInitialRoles(List<ServerPlayer> players) {
        Collections.shuffle(players);

        ServerPlayer exe = players.get(0);
        currentExecutioner = exe.getUUID();

        List<PlayerTypeOM> survivorTypes = Arrays.asList(
                PlayerTypeOM.SONIC,
                PlayerTypeOM.TAILS,
                PlayerTypeOM.KNUCKLES,
                PlayerTypeOM.AMY,
                PlayerTypeOM.CREAM,
                PlayerTypeOM.EGGMAN,
                PlayerTypeOM.METAL_SONIC,
                PlayerTypeOM.BLAZE
        );

        PlayerTypeOM exePreviousRole = survivorTypes.get(new Random().nextInt(survivorTypes.size()));
        previousRoles.put(exe.getUUID(), exePreviousRole);

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            UUID puid = player.getUUID();

            if (i == 0) {
                // Executioner (ya tiene rol previo asignado arriba)
                PlayerRegistry.setPlayerType(player, PlayerTypeOM.X2011);
                PlayerRegistry.savePreviousRole(puid, exePreviousRole);

                PlayerStats stats = playerStats.computeIfAbsent(puid, k -> new PlayerStats());
                stats.timesAsExe++;

                player.sendSystemMessage(Component.literal("§c§l========================================"));
                player.sendSystemMessage(Component.literal("§4§l        ¡Eres el EXECUTIONER!"));
                player.sendSystemMessage(Component.literal("§c§l========================================"));
                player.sendSystemMessage(Component.literal("§7Objetivo: §cElimina a todos los survivors"));
                player.sendSystemMessage(Component.literal("§c§l========================================"));
            } else {
                // Survivors
                PlayerTypeOM type = survivorTypes.get((i - 1) % survivorTypes.size());
                PlayerRegistry.setPlayerType(player, type);
                previousRoles.put(puid, type);
                PlayerRegistry.savePreviousRole(puid, type);

                PlayerStats stats = playerStats.computeIfAbsent(puid, k -> new PlayerStats());
                stats.timesAsSurvivor++;

                player.sendSystemMessage(Component.literal("§a§l========================================"));
                player.sendSystemMessage(Component.literal("§2§l        ¡Eres un SURVIVOR!"));
                player.sendSystemMessage(Component.literal("§a§l========================================"));
                player.sendSystemMessage(Component.literal("§7Personaje: §f" + type.name()));
                player.sendSystemMessage(Component.literal("§7Objetivo: §aSobrevive hasta el final"));
                player.sendSystemMessage(Component.literal("§a§l========================================"));
            }
        }
    }

    private static PlayerTypeOM getRandomSurvivorType(List<PlayerTypeOM> types) {
        return types.get(new Random().nextInt(types.size()));
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (currentState != GameState.ACTIVE && currentState != GameState.OVERTIME) return;

        UUID victimUUID = victim.getUUID();
        PlayerStats victimStats = playerStats.get(victimUUID);
        if (victimStats != null) {
            victimStats.deaths++;
            victimStats.timeAlive += System.currentTimeMillis() - victimStats.lastSpawnTime;
        }

        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            UUID killerUUID = killer.getUUID();

            if (killerUUID.equals(currentExecutioner)) {
                handleExecutionerKill(killer, victim);
            }
        }

        checkWinConditions();
    }

    private static void handleExecutionerKill(ServerPlayer killer, ServerPlayer victim) {
        UUID killerUUID = killer.getUUID();
        UUID victimUUID = victim.getUUID();

        PlayerStats killerStats = playerStats.get(killerUUID);
        if (killerStats != null) killerStats.kills++;

        PlayerTypeOM victimPreviousType = PlayerRegistry.getPlayerType(victim);
        PlayerTypeOM killerPreviousType = previousRoles.get(killerUUID);

        if (killerPreviousType == null) {
            killerPreviousType = getRandomSurvivorType(Arrays.asList(
                    PlayerTypeOM.SONIC, PlayerTypeOM.TAILS, PlayerTypeOM.KNUCKLES,
                    PlayerTypeOM.AMY, PlayerTypeOM.CREAM, PlayerTypeOM.EGGMAN,
                    PlayerTypeOM.METAL_SONIC, PlayerTypeOM.BLAZE
            ));
        }

        broadcastMessage("§c§l========================================");
        broadcastMessage("§e§l    ¡CAMBIO DE EXECUTIONER!");
        broadcastMessage("§c" + killer.getName().getString() + " §7eliminó a §a" + victim.getName().getString());
        broadcastMessage("§c§l========================================");

        scheduleRoleSwap(killer, victim, killerPreviousType, victimPreviousType);

        playGlobalSound(SoundEvents.WITHER_DEATH, 1.0F, 0.8F);
    }

    private static void scheduleRoleSwap(ServerPlayer oldExe, ServerPlayer newExe,
                                         PlayerTypeOM oldExeNewType, PlayerTypeOM newExePreviousType) {
        UUID oldExeUUID = oldExe.getUUID();
        UUID newExeUUID = newExe.getUUID();

        pendingSwaps.put(System.currentTimeMillis() + 100L, new RoleSwap(
                oldExeUUID, newExeUUID, oldExeNewType, newExePreviousType
        ));
    }

    private static void executeRoleSwap(RoleSwap swap) {
        ServerPlayer oldExe = findPlayerByUUID(swap.oldExeUUID);
        ServerPlayer newExe = findPlayerByUUID(swap.newExeUUID);

        if (oldExe == null || newExe == null) return;

        PlayerRegistry.setPlayerType(oldExe, swap.oldExeNewType);
        previousRoles.put(swap.oldExeUUID, swap.oldExeNewType);

        PlayerStats oldExeStats = playerStats.get(swap.oldExeUUID);
        if (oldExeStats != null) {
            oldExeStats.timesAsSurvivor++;
            oldExeStats.lastSpawnTime = System.currentTimeMillis();
        }

        oldExe.setGameMode(GameType.SURVIVAL);
        oldExe.setHealth(oldExe.getMaxHealth());
        oldExe.sendSystemMessage(Component.literal("§a§l¡Ahora eres un SURVIVOR!"));
        oldExe.sendSystemMessage(Component.literal("§7Personaje: §f" + swap.oldExeNewType.name()));

        PlayerRegistry.setPlayerType(newExe, PlayerTypeOM.X2011);
        previousRoles.put(swap.newExeUUID, swap.newExePreviousType);
        currentExecutioner = swap.newExeUUID;

        PlayerStats newExeStats = playerStats.get(swap.newExeUUID);
        if (newExeStats != null) {
            newExeStats.timesAsExe++;
            newExeStats.lastSpawnTime = System.currentTimeMillis();
        }

        newExe.setGameMode(GameType.SURVIVAL);
        newExe.setHealth(newExe.getMaxHealth());
        newExe.sendSystemMessage(Component.literal("§c§l¡Ahora eres el EXECUTIONER!"));
        newExe.sendSystemMessage(Component.literal("§7Elimina a todos los survivors"));

        playGlobalSound(SoundEvents.END_PORTAL_SPAWN, 1.0F, 1.5F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (currentState != GameState.ACTIVE && currentState != GameState.OVERTIME) return;

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<Long, RoleSwap>> swapIt = pendingSwaps.entrySet().iterator();
        while (swapIt.hasNext()) {
            Map.Entry<Long, RoleSwap> entry = swapIt.next();
            if (now >= entry.getKey()) {
                executeRoleSwap(entry.getValue());
                swapIt.remove();
            }
        }

        if ((now / 50) % 100 == 0) {
            LMSSystem.checkLMSConditions();
        }
    }

    private static void checkWinConditions() {
        int survivorsAlive = countAliveSurvivors();

        if (survivorsAlive == 0) {
            endGameExecutionerWin();
        }
    }

    public static void endGameExecutionerWin() {
        if (currentState == GameState.ENDING || currentState == GameState.FINISHED) return;

        currentState = GameState.ENDING;

        ServerPlayer exe = findPlayerByUUID(currentExecutioner);
        String exeName = exe != null ? exe.getName().getString() : "Executioner";

        broadcastMessage("§c§l========================================");
        broadcastMessage("§4§l      ¡EXECUTIONER GANA!");
        broadcastMessage("§c" + exeName + " §7eliminó a todos los survivors");
        broadcastMessage("§c§l========================================");

        playGlobalSound(SoundEvents.WITHER_DEATH, 1.0F, 1.0F);

        scheduleGameEnd();
    }

    public static void endGameSurvivorsWin() {
        if (currentState == GameState.ENDING || currentState == GameState.FINISHED) return;

        currentState = GameState.ENDING;

        List<ServerPlayer> survivors = getAliveSurvivors();

        broadcastMessage("§a§l========================================");
        broadcastMessage("§2§l     ¡SURVIVORS GANAN!");

        if (survivors.size() == 1) {
            broadcastMessage("§aÚltimo superviviente: §f" + survivors.get(0).getName().getString());
        } else {
            broadcastMessage("§aSobrevivientes restantes: §f" + survivors.size());
        }

        broadcastMessage("§a§l========================================");

        playGlobalSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);

        scheduleGameEnd();
    }

    private static void scheduleGameEnd() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                showFinalStatistics();
                currentState = GameState.FINISHED;
                resetGame();
            }
        }, 5000L);
    }

    private static void showFinalStatistics() {
        broadcastMessage("§e§l========================================");
        broadcastMessage("§e§l        ESTADÍSTICAS FINALES");
        broadcastMessage("§e§l========================================");

        List<Map.Entry<UUID, PlayerStats>> sortedStats = playerStats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().kills, a.getValue().kills))
                .collect(Collectors.toList());

        for (Map.Entry<UUID, PlayerStats> entry : sortedStats) {
            ServerPlayer player = findPlayerByUUID(entry.getKey());
            if (player == null) continue;

            PlayerStats stats = entry.getValue();
            broadcastMessage(String.format(
                    "§f%s §7- K: §c%d §7D: §c%d §7Exe: §e%d §7Surv: §a%d",
                    player.getName().getString(),
                    stats.kills,
                    stats.deaths,
                    stats.timesAsExe,
                    stats.timesAsSurvivor
            ));
        }

        broadcastMessage("§e§l========================================");
    }

    private static void resetGame() {
        currentState = GameState.WAITING;
        gameStartedAt = 0L;
        currentExecutioner = null;
        previousRoles.clear();
        playerStats.clear();
        pendingSwaps.clear();

        LMSSystem.cleanup();

        broadcastMessage("§7Partida finalizada.");
    }

    public static void registerExit(ServerLevel level, BlockPos pos) {
        exits.add(new ExitDoor(level, pos));
    }

    public static void closeAllExits() {
        for (ExitDoor exit : exits) {
            exit.isOpen = false;
        }
        System.out.println("[ExitSystem] All exits closed");
    }

    public static void openRandomExit() {
        if (exits.isEmpty()) return;

        ExitDoor randomExit = exits.get(new Random().nextInt(exits.size()));
        randomExit.isOpen = true;

        System.out.println("[ExitSystem] Random exit opened at " + randomExit.pos);
    }

    public static void openAllExits() {
        for (ExitDoor exit : exits) {
            exit.isOpen = true;
        }
        System.out.println("[ExitSystem] All exits opened");
    }

    public static boolean isPlayerAtExit(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        for (ExitDoor exit : exits) {
            if (!exit.isOpen) continue;
            if (!exit.level.equals(player.level())) continue;

            if (exit.pos.distSqr(playerPos) <= 4.0) {
                return true;
            }
        }

        return false;
    }

    public static int getExitCount() {
        return exits.size();
    }

    public static void listExits(CommandSourceStack source) {
        int index = 1;
        for (ExitDoor exit : exits) {
            String status = exit.isOpen ? "§aAbierta" : "§cCerrada";
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "§7%d. §f%s §7[%s§7]",
                            index,
                            exit.pos.toShortString(),
                            status
                    )),
                    false
            );
        }
    }

    public static int clearExits() {
        int count = exits.size();
        exits.clear();
        return count;
    }

    public static void setGameDuration(long durationMs) {
        gameDurationMs = durationMs;
    }

    public static GameState getCurrentState() {
        return currentState;
    }

    public static boolean isGameActive() {
        return currentState == GameState.ACTIVE || currentState == GameState.OVERTIME;
    }

    public static PlayerStats getPlayerStats(UUID playerUUID) {
        return playerStats.get(playerUUID);
    }

    public static boolean isCurrentExecutioner(UUID playerUUID) {
        return currentExecutioner != null && currentExecutioner.equals(playerUUID);
    }

    public static void forceEndGame() {
        if (currentState == GameState.FINISHED || currentState == GameState.WAITING) {
            return;
        }

        broadcastMessage("§7Juego finalizado por administrador");
        resetGame();
    }

    public static void updateCurrentExecutioner(UUID newExeUUID) {
        currentExecutioner = newExeUUID;
    }

    public static void showAllStats(CommandSourceStack source) {
        if (playerStats.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("§7No hay estadísticas disponibles"),
                    false
            );
            return;
        }

        List<Map.Entry<UUID, PlayerStats>> sortedStats = playerStats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().kills, a.getValue().kills))
                .collect(Collectors.toList());

        for (Map.Entry<UUID, PlayerStats> entry : sortedStats) {
            ServerPlayer player = findPlayerByUUID(entry.getKey());
            if (player == null) continue;

            PlayerStats stats = entry.getValue();
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "§f%s §7- K: §c%d §7D: §c%d §7Exe: §e%d §7Surv: §a%d",
                            player.getName().getString(),
                            stats.kills,
                            stats.deaths,
                            stats.timesAsExe,
                            stats.timesAsSurvivor
                    )),
                    false
            );
        }
    }

    private static int countAliveSurvivors() {
        return (int) getAlivePlayers().stream()
                .filter(p -> {
                    PlayerTypeOM type = PlayerRegistry.getPlayerType(p);
                    return type != null && type != PlayerTypeOM.X2011;
                })
                .count();
    }

    private static List<ServerPlayer> getAliveSurvivors() {
        return getAlivePlayers().stream()
                .filter(p -> {
                    PlayerTypeOM type = PlayerRegistry.getPlayerType(p);
                    return type != null && type != PlayerTypeOM.X2011;
                })
                .collect(Collectors.toList());
    }

    private static List<ServerPlayer> getAlivePlayers() {
        return ServerLifecycleHooks.getCurrentServer()
                .getPlayerList()
                .getPlayers()
                .stream()
                .filter(p -> p.isAlive() && !p.isSpectator())
                .collect(Collectors.toList());
    }

    private static ServerPlayer findPlayerByUUID(UUID uuid) {
        if (uuid == null) return null;
        for (ServerPlayer sp : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (sp.getUUID().equals(uuid)) return sp;
        }
        return null;
    }

    public static void broadcastMessage(String message) {
        Component comp = Component.literal(message);
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(comp);
        }
    }

    public static void playGlobalSound(SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(), sound, SoundSource.MASTER, volume, pitch);
            }
        }
    }

    public static void cleanup(UUID playerUUID) {
        previousRoles.remove(playerUUID);
        playerStats.remove(playerUUID);

        if (playerUUID.equals(currentExecutioner) && isGameActive()) {
            assignNewExecutioner();
        }
    }

    private static void assignNewExecutioner() {
        List<ServerPlayer> survivors = getAliveSurvivors();
        if (survivors.isEmpty()) {
            endGameExecutionerWin();
            return;
        }

        ServerPlayer newExe = survivors.get(new Random().nextInt(survivors.size()));
        PlayerTypeOM previousType = PlayerRegistry.getPlayerType(newExe);

        PlayerRegistry.setPlayerType(newExe, PlayerTypeOM.X2011);
        previousRoles.put(newExe.getUUID(), previousType);
        currentExecutioner = newExe.getUUID();

        broadcastMessage("§c¡Nuevo Executioner asignado: " + newExe.getName().getString() + "!");
    }
}