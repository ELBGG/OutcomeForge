package pe.elb.outcomememories.game.game;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/**
 * Sistema principal de juego para Outcome Memories
 *
 * NOTA: El timer/countdown es manejado por plugin externo de servidor.
 * Este sistema solo maneja:
 * - Asignación y rotación de roles
 * - Condiciones de victoria
 * - Estadísticas de jugadores
 * - Integración con LMSSystem
 */
@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GameSystem {

    // ============ ESTADO DEL JUEGO ============
    private static GameState currentState = GameState.WAITING;
    private static long gameStartedAt = 0L;

    // ============ TRACKING DE JUGADORES ============
    private static final Map<UUID, PlayerTypeOM> previousRoles = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerStats> playerStats = new ConcurrentHashMap<>();
    private static UUID currentExecutioner = null;

    // ============ CONFIGURACIÓN EXTERNA ============
    // Estos valores pueden ser modificados por el plugin del timer
    private static long gameDurationMs = 15 * 60 * 1000L; // 15 min por defecto
    private static long lastThreeMinutesThreshold = 3 * 60 * 1000L; // 3 min

    // ============ ENUMS ============
    public enum GameState {
        WAITING,        // Esperando jugadores
        STARTING,       // Countdown pre-inicio
        ACTIVE,         // Partida en curso
        OVERTIME,       // Overtime activado
        ENDING,         // Partida terminando
        FINISHED        // Partida finalizada
    }

    // ============ CLASE DE ESTADÍSTICAS ============
    public static class PlayerStats {
        public int kills = 0;
        public int deaths = 0;
        public int timesAsExe = 0;
        public int timesAsSurvivor = 0;
        public long timeAlive = 0L;
        public long lastSpawnTime = 0L;
    }

    // ============ INICIO DEL JUEGO ============

    /**
     * Inicia una nueva partida
     * Llamado externamente por comando o plugin de timer
     */
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

        // Limpiar datos previos
        playerStats.clear();
        previousRoles.clear();

        // Asignar roles iniciales
        assignInitialRoles(players);

        // Inicializar estadísticas
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

    /**
     * Asigna roles iniciales aleatoriamente
     */
    private static void assignInitialRoles(List<ServerPlayer> players) {
        Collections.shuffle(players);

        // El primero es el Executioner
        ServerPlayer exe = players.get(0);
        currentExecutioner = exe.getUUID();

        // Asignar tipos aleatorios a survivors
        List<PlayerTypeOM> survivorTypes = Arrays.asList(
                PlayerTypeOM.SONIC,
                PlayerTypeOM.TAILS,
                PlayerTypeOM.KNUCKLES,
                PlayerTypeOM.AMY,
                PlayerTypeOM.CREAM,
                PlayerTypeOM.EGGMAN
        );

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            UUID puid = player.getUUID();

            if (i == 0) {
                // Executioner
                PlayerRegistry.setPlayerType(player, PlayerTypeOM.X2011);
                previousRoles.put(puid, getRandomSurvivorType(survivorTypes));

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

    // ============ SISTEMA DE ROTACIÓN ============

    /**
     * Maneja la muerte de un jugador y la rotación de roles
     */
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

        // Verificar si el killer es el Executioner
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            UUID killerUUID = killer.getUUID();

            if (killerUUID.equals(currentExecutioner)) {
                handleExecutionerKill(killer, victim);
            }
        }

        // Verificar condiciones de victoria
        checkWinConditions();
    }

    /**
     * Maneja cuando el Executioner mata a un survivor
     * Implementa la mecánica de intercambio de roles
     */
    private static void handleExecutionerKill(ServerPlayer killer, ServerPlayer victim) {
        UUID killerUUID = killer.getUUID();
        UUID victimUUID = victim.getUUID();

        // Actualizar estadísticas
        PlayerStats killerStats = playerStats.get(killerUUID);
        if (killerStats != null) killerStats.kills++;

        // Guardar el tipo del victim antes de morir
        PlayerTypeOM victimPreviousType = PlayerRegistry.getPlayerType(victim);
        PlayerTypeOM killerPreviousType = previousRoles.get(killerUUID);

        // Si el killer no tiene un rol previo guardado, asignar uno aleatorio
        if (killerPreviousType == null) {
            killerPreviousType = getRandomSurvivorType(Arrays.asList(
                    PlayerTypeOM.SONIC, PlayerTypeOM.TAILS, PlayerTypeOM.KNUCKLES,
                    PlayerTypeOM.AMY, PlayerTypeOM.CREAM, PlayerTypeOM.EGGMAN
            ));
        }

        broadcastMessage("§c§l========================================");
        broadcastMessage("§e§l    ¡CAMBIO DE EXECUTIONER!");
        broadcastMessage("§c" + killer.getName().getString() + " §7eliminó a §a" + victim.getName().getString());
        broadcastMessage("§c§l========================================");

        // Programar el intercambio de roles después de un delay (para que el respawn funcione)
        scheduleRoleSwap(killer, victim, killerPreviousType, victimPreviousType);

        playGlobalSound(SoundEvents.WITHER_DEATH, 1.0F, 0.8F);
    }

    /**
     * Programa el intercambio de roles con un pequeño delay
     */
    private static void scheduleRoleSwap(ServerPlayer oldExe, ServerPlayer newExe,
                                         PlayerTypeOM oldExeNewType, PlayerTypeOM newExePreviousType) {
        UUID oldExeUUID = oldExe.getUUID();
        UUID newExeUUID = newExe.getUUID();

        pendingSwaps.put(System.currentTimeMillis() + 100L, new RoleSwap(
                oldExeUUID, newExeUUID, oldExeNewType, newExePreviousType
        ));
    }

    private static final Map<Long, RoleSwap> pendingSwaps = new ConcurrentHashMap<>();

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

    /**
     * Ejecuta el intercambio de roles
     */
    private static void executeRoleSwap(RoleSwap swap) {
        ServerPlayer oldExe = findPlayerByUUID(swap.oldExeUUID);
        ServerPlayer newExe = findPlayerByUUID(swap.newExeUUID);

        if (oldExe == null || newExe == null) return;

        // El viejo Exe se convierte en survivor
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

        // El nuevo Exe se convierte en Executioner
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

    // ============ TICK EVENT ============

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (currentState != GameState.ACTIVE && currentState != GameState.OVERTIME) return;

        long now = System.currentTimeMillis();

        // Procesar swaps pendientes
        Iterator<Map.Entry<Long, RoleSwap>> swapIt = pendingSwaps.entrySet().iterator();
        while (swapIt.hasNext()) {
            Map.Entry<Long, RoleSwap> entry = swapIt.next();
            if (now >= entry.getKey()) {
                executeRoleSwap(entry.getValue());
                swapIt.remove();
            }
        }

        // Verificar condiciones de LMS cada 5 segundos (100 ticks)
        if ((now / 50) % 100 == 0) {
            LMSSystem.checkLMSConditions();
        }
    }

    // ============ CONDICIONES DE VICTORIA ============

    /**
     * Verifica las condiciones de victoria
     */
    private static void checkWinConditions() {
        int survivorsAlive = countAliveSurvivors();

        if (survivorsAlive == 0) {
            endGameExecutionerWin();
        }
    }

    /**
     * Termina el juego con victoria del Executioner
     */
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

    /**
     * Termina el juego con victoria de los Survivors
     * Llamado externamente cuando el timer se agota
     */
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

    /**
     * Programa el fin del juego y muestra estadísticas
     */
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

    /**
     * Muestra estadísticas finales
     */
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

    /**
     * Resetea el juego para una nueva partida
     */
    private static void resetGame() {
        currentState = GameState.WAITING;
        gameStartedAt = 0L;
        currentExecutioner = null;
        previousRoles.clear();
        playerStats.clear();
        pendingSwaps.clear();

        // Limpiar LMS si estaba activo
        LMSSystem.cleanup();

        broadcastMessage("§7Partida finalizada.");
    }

    // ============ API PÚBLICA ============

    /**
     * Establece la duración del juego (llamado por plugin externo)
     */
    public static void setGameDuration(long durationMs) {
        gameDurationMs = durationMs;
    }

    /**
     * Obtiene el estado actual del juego
     */
    public static GameState getCurrentState() {
        return currentState;
    }

    /**
     * Verifica si hay una partida activa
     */
    public static boolean isGameActive() {
        return currentState == GameState.ACTIVE || currentState == GameState.OVERTIME;
    }

    /**
     * Obtiene estadísticas de un jugador
     */
    public static PlayerStats getPlayerStats(UUID playerUUID) {
        return playerStats.get(playerUUID);
    }

    /**
     * Verifica si un jugador es el executioner actual
     */
    public static boolean isCurrentExecutioner(UUID playerUUID) {
        return currentExecutioner != null && currentExecutioner.equals(playerUUID);
    }

    // ============ UTILIDADES ============

    /**
     * Cuenta survivors vivos
     */
    private static int countAliveSurvivors() {
        return (int) getAlivePlayers().stream()
                .filter(p -> {
                    PlayerTypeOM type = PlayerRegistry.getPlayerType(p);
                    return type != null && type != PlayerTypeOM.X2011;
                })
                .count();
    }

    /**
     * Obtiene lista de survivors vivos
     */
    private static List<ServerPlayer> getAliveSurvivors() {
        return getAlivePlayers().stream()
                .filter(p -> {
                    PlayerTypeOM type = PlayerRegistry.getPlayerType(p);
                    return type != null && type != PlayerTypeOM.X2011;
                })
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los jugadores vivos
     */
    private static List<ServerPlayer> getAlivePlayers() {
        return ServerLifecycleHooks.getCurrentServer()
                .getPlayerList()
                .getPlayers()
                .stream()
                .filter(p -> p.isAlive() && !p.isSpectator())
                .collect(Collectors.toList());
    }

    /**
     * Encuentra un jugador por UUID
     */
    private static ServerPlayer findPlayerByUUID(UUID uuid) {
        if (uuid == null) return null;
        for (ServerPlayer sp : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (sp.getUUID().equals(uuid)) return sp;
        }
        return null;
    }

    /**
     * Envía un mensaje a todos los jugadores
     */
    public static void broadcastMessage(String message) {
        Component comp = Component.literal(message);
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(comp);
        }
    }

    /**
     * Reproduce un sonido global
     */
    public static void playGlobalSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(), sound, SoundSource.MASTER, volume, pitch);
            }
        }
    }

    /**
     * Limpieza al desconectarse un jugador
     */
    public static void cleanup(UUID playerUUID) {
        previousRoles.remove(playerUUID);
        playerStats.remove(playerUUID);

        // Si el executioner se desconecta, elegir uno nuevo
        if (playerUUID.equals(currentExecutioner) && isGameActive()) {
            assignNewExecutioner();
        }
    }

    /**
     * Asigna un nuevo executioner si el actual se desconecta
     */
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

    // Añadir al GameSystem.java



    public static void forceEndGame() {
        if (currentState == GameState.FINISHED || currentState == GameState.WAITING) {
            return;
        }

        broadcastMessage("§7Juego finalizado por administrador");
        resetGame();
    }

    /**
     * Actualiza el executioner actual (público para comandos)
     */
    public static void updateCurrentExecutioner(UUID newExeUUID) {
        currentExecutioner = newExeUUID;
    }

    /**
     * Muestra todas las estadísticas (para comando)
     */
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


// Remover el @SubscribeEvent de onPlayerDeath - ya no se usa
// Los jugadores NO mueren, solo intercambian roles
}