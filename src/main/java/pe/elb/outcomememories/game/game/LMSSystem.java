package pe.elb.outcomememories.game.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.client.handlers.LMSClientHandler;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.LMSBeatZoomPacket;
import pe.elb.outcomememories.net.packets.LMSMusicPacket;
import pe.elb.outcomememories.net.packets.LMSLyricsPacket;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sistema Last Man Standing - Modo TNT Tag por 3 minutos
 *
 * Se activa cuando: Survivors == Executioners
 * Duración: 3 minutos (promedio de todas las canciones)
 * Mecánicas:
 * - Exe hace insta-swap (un golpe = cambio de rol)
 * - Música continua sin detenerse
 * - BossBar para mostrar tiempo restante
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LMSSystem {

    // ============ CONSTANTES ============
    private static final long LMS_DURATION_MS = 3 * 60 * 1000L; // 3 minutos
    private static final long EXIT_OPENING_DURATION_MS = 50_000L; // 50 segundos

    // ============ ESTADO DEL SISTEMA ============
    private static boolean isLMSActive = false;
    private static boolean isExitPhase = false; // Fase final de 50s con salida
    private static long lmsStartedAt = 0L;
    private static long exitPhaseStartedAt = 0L;
    private static Set<UUID> survivorsInLMS = new HashSet<>();
    private static Set<UUID> executionersInLMS = new HashSet<>();

    // ============ BOSSBAR ============
    private static ServerBossEvent lmsBossBar;

    // ============ MÚSICA POR PERSONAJE ============
    private static final Map<PlayerTypeOM, String> LMS_MUSIC_TRACKS = new HashMap<>();

    static {
        // Mapeo de música temática por personaje
        LMS_MUSIC_TRACKS.put(PlayerTypeOM.SONIC, "soniclms");
        LMS_MUSIC_TRACKS.put(PlayerTypeOM.TAILS, "tailslms");
        LMS_MUSIC_TRACKS.put(PlayerTypeOM.KNUCKLES, "knuckleslms");
        LMS_MUSIC_TRACKS.put(PlayerTypeOM.AMY, "amylms");
        LMS_MUSIC_TRACKS.put(PlayerTypeOM.CREAM, "creamlms");
        LMS_MUSIC_TRACKS.put(PlayerTypeOM.EGGMAN, "eggmanlms");
    }

    // ============ VERIFICACIÓN Y ACTIVACIÓN ============

    /**
     * Verifica si se debe activar LMS
     * Condición: Igual cantidad de survivors y executioners
     */
    public static void checkLMSConditions() {
        if (isLMSActive) return;
        if (!GameSystem.isGameActive()) return;

        List<ServerPlayer> allPlayers = getAlivePlayers();

        List<ServerPlayer> executioners = allPlayers.stream()
                .filter(p -> {
                    PlayerTypeOM type = PlayerRegistry.getPlayerType(p);
                    return type == PlayerTypeOM.X2011;
                })
                .collect(Collectors.toList());

        List<ServerPlayer> survivors = allPlayers.stream()
                .filter(p -> {
                    PlayerTypeOM type = PlayerRegistry.getPlayerType(p);
                    return type != null && type != PlayerTypeOM.X2011;
                })
                .collect(Collectors.toList());

        // CONDICIÓN: Igual cantidad y al menos 1 de cada tipo
        if (!executioners.isEmpty() && !survivors.isEmpty()
                && executioners.size() == survivors.size()) {
            activateLMS(executioners, survivors);
        }
    }

    /**
     * Activa el modo Last Man Standing
     */
    private static void activateLMS(List<ServerPlayer> executioners, List<ServerPlayer> survivors) {
        isLMSActive = true;
        isExitPhase = false;
        long now = System.currentTimeMillis();
        lmsStartedAt = now;

        survivorsInLMS.clear();
        executionersInLMS.clear();

        for (ServerPlayer survivor : survivors) {
            survivorsInLMS.add(survivor.getUUID());
        }

        for (ServerPlayer exe : executioners) {
            executionersInLMS.add(exe.getUUID());
        }

        // Crear BossBar
        createBossBar();

        // Anuncio global
        GameSystem.broadcastMessage("§4§l========================================");
        GameSystem.broadcastMessage("§c§l    ⚠ LAST MAN STANDING ACTIVADO ⚠");
        GameSystem.broadcastMessage("§4§l========================================");
        GameSystem.broadcastMessage("§7Modo: §eTNT TAG");
        GameSystem.broadcastMessage("§7Duración: §f3 minutos");
        GameSystem.broadcastMessage("§c§l¡Un golpe = Cambio de rol!");
        GameSystem.broadcastMessage("§4§l========================================");

        GameSystem.playGlobalSound(SoundEvents.WITHER_SPAWN, 1.0F, 0.5F);
        GameSystem.playGlobalSound(SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 0.8F);

        // Efectos a executioners
        for (ServerPlayer exe : executioners) {
            exe.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false, true));
            exe.sendSystemMessage(Component.literal("§c§l¡INSTA-SWAP ACTIVADO!"));
            exe.sendSystemMessage(Component.literal("§7Un golpe intercambia roles"));
            lmsBossBar.addPlayer(exe);
        }

        // Inicializar sistema de letras
        LMSLyricsSystem.initializeLyrics();

        // Reproducir música Y letras para cada survivor
        for (ServerPlayer survivor : survivors) {
            survivor.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false, true));

            PlayerTypeOM type = PlayerRegistry.getPlayerType(survivor);
            String musicTrack = LMS_MUSIC_TRACKS.getOrDefault(type, "soniclms");

            survivor.sendSystemMessage(Component.literal("§e§l¡EVITA SER GOLPEADO!"));
            lmsBossBar.addPlayer(survivor);

            try {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> survivor),
                        new LMSMusicPacket(musicTrack, true)
                );
            } catch (Throwable e) {
                System.err.println("[LMS] Error enviando música a " + survivor.getName().getString());
                e.printStackTrace();
            }

// ✨ NUEVO: Enviar packet para activar beat zoom
            try {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> survivor),
                        new LMSBeatZoomPacket(musicTrack, true)
                );
            } catch (Throwable e) {
                System.err.println("[LMS] Error enviando beat zoom a " + survivor.getName().getString());
                e.printStackTrace();
            }

            // Reproducir letras (nuestro sistema integrado)
            try {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> survivor),
                        new LMSLyricsPacket(musicTrack, true)
                );

                if (survivor.level().isClientSide()) {
                    LMSClientHandler.setupBeatZoom(musicTrack, true);
                }

            } catch (Throwable e) {
                System.err.println("[LMS] Error enviando letras a " + survivor.getName().getString());
                e.printStackTrace();
            }
        }

        // COMENTADO PARA TESTING
        // ExitSystem.closeAllExits();

        System.out.println("[LMS] Activado con " + executioners.size() + " exes y " + survivors.size() + " survivors");
    }

    /**
     * Crea la BossBar para el timer
     */
    private static void createBossBar() {
        if (lmsBossBar != null) {
            lmsBossBar.removeAllPlayers();
        }

        lmsBossBar = new ServerBossEvent(
                Component.literal("§c§lLAST MAN STANDING"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );

        lmsBossBar.setProgress(1.0F);
        lmsBossBar.setVisible(true);
    }

    /**
     * Actualiza la BossBar
     */
    private static void updateBossBar() {
        if (lmsBossBar == null) return;

        long remaining = getLMSTimeRemaining();
        float progress = 0.0F;

        if (isExitPhase) {
            progress = (float) remaining / (float) EXIT_OPENING_DURATION_MS;
            lmsBossBar.setName(Component.literal("§a§lFASE DE ESCAPE: " + formatTime(remaining)));
            lmsBossBar.setColor(BossEvent.BossBarColor.GREEN);
        } else {
            progress = (float) remaining / (float) LMS_DURATION_MS;
            lmsBossBar.setName(Component.literal("§c§l" + formatTime(remaining)));
            lmsBossBar.setColor(BossEvent.BossBarColor.RED);
        }

        lmsBossBar.setProgress(Math.max(0.0F, Math.min(1.0F, progress)));
    }

    // ============ INSTA-SWAP MECHANIC ============

    /**
     * Durante LMS, cualquier golpe de exe causa swap inmediato
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!isLMSActive) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        UUID attackerUUID = attacker.getUUID();
        UUID victimUUID = victim.getUUID();

        // Verificar que sea exe atacando a survivor
        if (!executionersInLMS.contains(attackerUUID)) return;
        if (!survivorsInLMS.contains(victimUUID)) return;

        // CANCELAR DAÑO - no matar al jugador
        event.setCanceled(true);

        // Realizar el swap manual de tipos
        PlayerTypeOM victimType = PlayerRegistry.getPlayerType(victim);

        // El attacker (exe) se convierte en el tipo del victim
        PlayerRegistry.setPlayerType(attacker, victimType);

        // El victim se convierte en X2011 (exe)
        PlayerRegistry.setPlayerType(victim, PlayerTypeOM.X2011);

        // Actualizar tracking
        executionersInLMS.remove(attackerUUID);
        executionersInLMS.add(victimUUID);

        survivorsInLMS.remove(victimUUID);
        survivorsInLMS.add(attackerUUID);

        // Efectos visuales del swap
        attacker.sendSystemMessage(Component.literal("§a§l¡AHORA ERES SURVIVOR!"));
        victim.sendSystemMessage(Component.literal("§c§l¡AHORA ERES EXECUTIONER!"));

        // Sonido de swap
        attacker.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.5F);
        victim.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);

        System.out.println("[LMS] Swap exitoso: " + attacker.getName().getString() + " (exe->surv) <-> " + victim.getName().getString() + " (surv->exe)");

        // LA MÚSICA Y EFECTOS CONTINÚAN
    }

    // ============ SISTEMA DE TIEMPO ============

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isLMSActive) return;

        long now = System.currentTimeMillis();
        long elapsed = now - lmsStartedAt;
        long remaining = LMS_DURATION_MS - elapsed;

        // Actualizar BossBar cada tick
        updateBossBar();

        // INICIAR FASE DE SALIDA (50 segundos)
        if (remaining <= 0 && !isExitPhase) {
            startExitPhase();
        }

        // Manejar fase de salida
        if (isExitPhase) {
            handleExitPhase();
        }
    }

    /**
     * Inicia la fase final de 50 segundos con una salida abierta
     */
    private static void startExitPhase() {
        isExitPhase = true;
        exitPhaseStartedAt = System.currentTimeMillis();

        GameSystem.broadcastMessage("§a§l========================================");
        GameSystem.broadcastMessage("§2§l   ¡FASE FINAL - 50 SEGUNDOS!");
        GameSystem.broadcastMessage("§a§l========================================");

        GameSystem.playGlobalSound(SoundEvents.END_PORTAL_SPAWN, 1.0F, 1.0F);

        // COMENTADO PARA TESTING
        // ExitSystem.openRandomExit();

        System.out.println("[LMS] Fase de salida iniciada");
    }

    /**
     * Maneja la fase de salida
     */
    private static void handleExitPhase() {
        long now = System.currentTimeMillis();
        long elapsed = now - exitPhaseStartedAt;
        long remaining = EXIT_OPENING_DURATION_MS - elapsed;

        // Tiempo agotado - Executioners ganan
        if (remaining <= 0) {
            endLMSExecutionersWin();
        }
    }

    // ============ CONDICIONES DE VICTORIA ============

    /**
     * Termina LMS con victoria de Executioners
     */
    private static void endLMSExecutionersWin() {
        GameSystem.broadcastMessage("§c§l========================================");
        GameSystem.broadcastMessage("§4§l   ⚠ LMS - EXECUTIONERS GANAN ⚠");
        GameSystem.broadcastMessage("§c§l========================================");

        GameSystem.playGlobalSound(SoundEvents.WITHER_DEATH, 1.0F, 0.8F);

        deactivateLMS();
        GameSystem.endGameExecutionerWin();
    }

    /**
     * Un survivor escapó - Survivors ganan
     */
    public static void onSurvivorEscaped(ServerPlayer survivor) {
        if (!isLMSActive || !isExitPhase) return;

        GameSystem.broadcastMessage("§a§l========================================");
        GameSystem.broadcastMessage("§2§l   ✓ LMS - SURVIVORS GANAN ✓");
        GameSystem.broadcastMessage("§a" + survivor.getName().getString() + " §7logró escapar!");
        GameSystem.broadcastMessage("§a§l========================================");

        GameSystem.playGlobalSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);

        deactivateLMS();
        GameSystem.endGameSurvivorsWin();
    }

    /**
     * Desactiva el modo LMS
     */
    private static void deactivateLMS() {
        isLMSActive = false;
        isExitPhase = false;

        // Remover BossBar
        if (lmsBossBar != null) {
            lmsBossBar.removeAllPlayers();
            lmsBossBar.setVisible(false);
            lmsBossBar = null;
        }

        // Detener música y letras para todos los jugadores involucrados
        Set<UUID> allLMSPlayers = new HashSet<>();
        allLMSPlayers.addAll(survivorsInLMS);
        allLMSPlayers.addAll(executionersInLMS);

        for (UUID uuid : allLMSPlayers) {
            ServerPlayer player = findPlayerByUUID(uuid);
            if (player != null) {
                player.removeEffect(MobEffects.GLOWING);

                // Detener música
                try {
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new LMSMusicPacket("", false)
                    );
                } catch (Throwable ignored) {}

                // Detener letras (nuestro sistema)
                try {
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new LMSLyricsPacket("", false)
                    );
                } catch (Throwable ignored) {}

                try {
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new LMSBeatZoomPacket("", false)
                    );
                } catch (Throwable ignored) {}
            }
        }

        survivorsInLMS.clear();
        executionersInLMS.clear();
        lmsStartedAt = 0L;
        exitPhaseStartedAt = 0L;

        // COMENTADO PARA TESTING
        // ExitSystem.openAllExits();

        System.out.println("[LMS] Desactivado");
    }

    // ============ UTILIDADES ============

    public static boolean isLMSActive() {
        return isLMSActive;
    }

    public static boolean isExitPhaseActive() {
        return isExitPhase;
    }

    public static long getLMSTimeRemaining() {
        if (!isLMSActive) return 0L;

        if (isExitPhase) {
            long elapsed = System.currentTimeMillis() - exitPhaseStartedAt;
            return Math.max(0L, EXIT_OPENING_DURATION_MS - elapsed);
        } else {
            long elapsed = System.currentTimeMillis() - lmsStartedAt;
            return Math.max(0L, LMS_DURATION_MS - elapsed);
        }
    }

    private static String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static ServerPlayer findPlayerByUUID(UUID uuid) {
        if (uuid == null) return null;
        for (ServerPlayer sp : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (sp.getUUID().equals(uuid)) return sp;
        }
        return null;
    }

    private static List<ServerPlayer> getAlivePlayers() {
        return ServerLifecycleHooks.getCurrentServer()
                .getPlayerList()
                .getPlayers()
                .stream()
                .filter(p -> !p.isSpectator())
                .collect(Collectors.toList());
    }

    public static void cleanup() {
        if (isLMSActive) {
            deactivateLMS();
        }
    }

    /**
     * Fuerza la activación de LMS (para comandos)
     */
    public static void forceActivateLMS() {
        List<ServerPlayer> allPlayers = getAlivePlayers();

        List<ServerPlayer> executioners = allPlayers.stream()
                .filter(p -> PlayerRegistry.getPlayerType(p) == PlayerTypeOM.X2011)
                .collect(Collectors.toList());

        List<ServerPlayer> survivors = allPlayers.stream()
                .filter(p -> {
                    PlayerTypeOM type = PlayerRegistry.getPlayerType(p);
                    return type != null && type != PlayerTypeOM.X2011;
                })
                .collect(Collectors.toList());

        activateLMS(executioners, survivors);
    }

    /**
     * Fuerza la fase de salida (para comandos)
     */
    public static void forceExitPhase() {
        if (!isLMSActive || isExitPhase) return;
        startExitPhase();
    }

    /**
     * Fuerza el fin de LMS (para comandos)
     */
    public static void forceEndLMS() {
        if (!isLMSActive) return;

        GameSystem.broadcastMessage("§7LMS finalizado por administrador");
        deactivateLMS();
    }

    /**
     * Actualiza el tracking después de un swap
     */
    public static void updateLMSTracking(UUID oldExeUUID, UUID newExeUUID) {
        if (!isLMSActive) return;

        executionersInLMS.remove(oldExeUUID);
        survivorsInLMS.add(oldExeUUID);

        survivorsInLMS.remove(newExeUUID);
        executionersInLMS.add(newExeUUID);

        System.out.println("[LMS] Tracking updated after swap");
    }
}