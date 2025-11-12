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
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.LMSBeatZoomPacket;
import pe.elb.outcomememories.net.packets.LMSMusicPacket;
import pe.elb.outcomememories.net.packets.LMSLyricsPacket;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LMSSystem {

    private static final long LMS_DURATION_MS = 3 * 60 * 1000L;
    private static final long EXIT_OPENING_DURATION_MS = 50_000L;

    private static boolean isLMSActive = false;
    private static boolean isExitPhase = false;
    private static long lmsStartedAt = 0L;
    private static long exitPhaseStartedAt = 0L;
    private static Set<UUID> survivorsInLMS = new HashSet<>();
    private static Set<UUID> executionersInLMS = new HashSet<>();

    private static ServerBossEvent lmsBossBar;

    private static final Map<PlayerTypeOM, String> LMS_TRACKS = new HashMap<>();
    private static boolean lyricsInitialized = false;

    static {
        LMS_TRACKS.put(PlayerTypeOM.SONIC, "soniclms");
        LMS_TRACKS.put(PlayerTypeOM.TAILS, "tailslms");
        LMS_TRACKS.put(PlayerTypeOM.KNUCKLES, "knuckleslms");
        LMS_TRACKS.put(PlayerTypeOM.AMY, "amylms");
        LMS_TRACKS.put(PlayerTypeOM.CREAM, "creamlms");
        LMS_TRACKS.put(PlayerTypeOM.EGGMAN, "eggmanlms");
        LMS_TRACKS.put(PlayerTypeOM.METAL_SONIC, "metalsoniclms");
        LMS_TRACKS.put(PlayerTypeOM.BLAZE, "blazelms");
    }

    public static void checkLMSConditions() {
        if (isLMSActive) return;
        if (!OutcomeMemoriesGameSystem.isGameActive()) return;

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

        if (!executioners.isEmpty() && !survivors.isEmpty()
                && executioners.size() == survivors.size()) {
            activateLMS(executioners, survivors);
        }
    }

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

        createBossBar();

        OutcomeMemoriesGameSystem.broadcastMessage("§4§l========================================");
        OutcomeMemoriesGameSystem.broadcastMessage("§c§l    ⚠ LAST MAN STANDING ACTIVADO ⚠");
        OutcomeMemoriesGameSystem.broadcastMessage("§4§l========================================");
        OutcomeMemoriesGameSystem.broadcastMessage("§7Modo: §eTNT TAG");
        OutcomeMemoriesGameSystem.broadcastMessage("§7Duración: §f3 minutos");
        OutcomeMemoriesGameSystem.broadcastMessage("§c§l¡Un golpe = Cambio de rol!");
        OutcomeMemoriesGameSystem.broadcastMessage("§4§l========================================");

        OutcomeMemoriesGameSystem.playGlobalSound(SoundEvents.WITHER_SPAWN, 1.0F, 0.5F);
        OutcomeMemoriesGameSystem.playGlobalSound(SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 0.8F);

        for (ServerPlayer exe : executioners) {
            exe.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false, true));
            exe.sendSystemMessage(Component.literal("§c§l¡INSTA-SWAP ACTIVADO!"));
            exe.sendSystemMessage(Component.literal("§7Un golpe intercambia roles"));
            lmsBossBar.addPlayer(exe);
        }

        initializeLyrics();

        for (ServerPlayer survivor : survivors) {
            survivor.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false, true));

            PlayerTypeOM type = PlayerRegistry.getPlayerType(survivor);
            String musicTrack = LMS_TRACKS.getOrDefault(type, "soniclms");

            survivor.sendSystemMessage(Component.literal("§e§l¡EVITA SER GOLPEADO!"));
            lmsBossBar.addPlayer(survivor);

            sendMusicPacket(survivor, musicTrack, true);
            sendBeatZoomPacket(survivor, musicTrack, true);
            sendLyricsPacket(survivor, musicTrack, true);
        }

        OutcomeMemoriesGameSystem.closeAllExits();

        System.out.println("[LMS] Activado con " + executioners.size() + " exes y " + survivors.size() + " survivors");
    }

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!isLMSActive) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        UUID attackerUUID = attacker.getUUID();
        UUID victimUUID = victim.getUUID();

        if (!executionersInLMS.contains(attackerUUID)) return;
        if (!survivorsInLMS.contains(victimUUID)) return;

        event.setCanceled(true);

        PlayerTypeOM victimType = PlayerRegistry.getPlayerType(victim);

        PlayerRegistry.setPlayerType(attacker, victimType);
        PlayerRegistry.setPlayerType(victim, PlayerTypeOM.X2011);

        executionersInLMS.remove(attackerUUID);
        executionersInLMS.add(victimUUID);

        survivorsInLMS.remove(victimUUID);
        survivorsInLMS.add(attackerUUID);

        attacker.sendSystemMessage(Component.literal("§a§l¡AHORA ERES SURVIVOR!"));
        victim.sendSystemMessage(Component.literal("§c§l¡AHORA ERES EXECUTIONER!"));

        attacker.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.5F);
        victim.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);

        System.out.println("[LMS] Swap exitoso: " + attacker.getName().getString() + " (exe->surv) <-> " + victim.getName().getString() + " (surv->exe)");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isLMSActive) return;

        long now = System.currentTimeMillis();
        long elapsed = now - lmsStartedAt;
        long remaining = LMS_DURATION_MS - elapsed;

        updateBossBar();

        if (remaining <= 0 && !isExitPhase) {
            startExitPhase();
        }

        if (isExitPhase) {
            handleExitPhase();
        }
    }

    private static void startExitPhase() {
        isExitPhase = true;
        exitPhaseStartedAt = System.currentTimeMillis();

        OutcomeMemoriesGameSystem.broadcastMessage("§a§l========================================");
        OutcomeMemoriesGameSystem.broadcastMessage("§2§l   ¡FASE FINAL - 50 SEGUNDOS!");
        OutcomeMemoriesGameSystem.broadcastMessage("§a§l========================================");

        OutcomeMemoriesGameSystem.playGlobalSound(SoundEvents.END_PORTAL_SPAWN, 1.0F, 1.0F);

        OutcomeMemoriesGameSystem.openRandomExit();

        System.out.println("[LMS] Fase de salida iniciada");
    }

    private static void handleExitPhase() {
        long now = System.currentTimeMillis();
        long elapsed = now - exitPhaseStartedAt;
        long remaining = EXIT_OPENING_DURATION_MS - elapsed;

        if (remaining <= 0) {
            endLMSExecutionersWin();
        }
    }

    private static void endLMSExecutionersWin() {
        OutcomeMemoriesGameSystem.broadcastMessage("§c§l========================================");
        OutcomeMemoriesGameSystem.broadcastMessage("§4§l   ⚠ LMS - EXECUTIONERS GANAN ⚠");
        OutcomeMemoriesGameSystem.broadcastMessage("§c§l========================================");

        OutcomeMemoriesGameSystem.playGlobalSound(SoundEvents.WITHER_DEATH, 1.0F, 0.8F);

        deactivateLMS();
        OutcomeMemoriesGameSystem.endGameExecutionerWin();
    }

    public static void onSurvivorEscaped(ServerPlayer survivor) {
        if (!isLMSActive || !isExitPhase) return;

        OutcomeMemoriesGameSystem.broadcastMessage("§a§l========================================");
        OutcomeMemoriesGameSystem.broadcastMessage("§2§l   ✓ LMS - SURVIVORS GANAN ✓");
        OutcomeMemoriesGameSystem.broadcastMessage("§a" + survivor.getName().getString() + " §7logró escapar!");
        OutcomeMemoriesGameSystem.broadcastMessage("§a§l========================================");

        OutcomeMemoriesGameSystem.playGlobalSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);

        deactivateLMS();
        OutcomeMemoriesGameSystem.endGameSurvivorsWin();
    }

    private static void deactivateLMS() {
        isLMSActive = false;
        isExitPhase = false;

        if (lmsBossBar != null) {
            lmsBossBar.removeAllPlayers();
            lmsBossBar.setVisible(false);
            lmsBossBar = null;
        }

        Set<UUID> allLMSPlayers = new HashSet<>();
        allLMSPlayers.addAll(survivorsInLMS);
        allLMSPlayers.addAll(executionersInLMS);

        for (UUID uuid : allLMSPlayers) {
            ServerPlayer player = findPlayerByUUID(uuid);
            if (player != null) {
                player.removeEffect(MobEffects.GLOWING);

                sendMusicPacket(player, "", false);
                sendLyricsPacket(player, "", false);
                sendBeatZoomPacket(player, "", false);
            }
        }

        survivorsInLMS.clear();
        executionersInLMS.clear();
        lmsStartedAt = 0L;
        exitPhaseStartedAt = 0L;

        OutcomeMemoriesGameSystem.openAllExits();

        System.out.println("[LMS] Desactivado");
    }

    private static void initializeLyrics() {
        if (lyricsInitialized) {
            System.out.println("[LMS] Sistema de letras ya inicializado");
            return;
        }

        System.out.println("[LMS] Inicializando sistema de letras...");
        System.out.println("[LMS] Archivos JSON ubicados en: assets/outcomememories/subtitles/");

        for (Map.Entry<PlayerTypeOM, String> entry : LMS_TRACKS.entrySet()) {
            System.out.println("[LMS] - " + entry.getValue() + ".json -> " + entry.getKey());
        }

        lyricsInitialized = true;
        System.out.println("[LMS] ✓ Sistema de letras inicializado");
    }

    private static void sendMusicPacket(ServerPlayer player, String track, boolean start) {
        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new LMSMusicPacket(track, start)
            );
        } catch (Throwable e) {
            System.err.println("[LMS] Error enviando música a " + player.getName().getString());
        }
    }

    private static void sendBeatZoomPacket(ServerPlayer player, String track, boolean start) {
        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new LMSBeatZoomPacket(track, start)
            );
        } catch (Throwable e) {
            System.err.println("[LMS] Error enviando beat zoom a " + player.getName().getString());
        }
    }

    private static void sendLyricsPacket(ServerPlayer player, String track, boolean start) {
        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new LMSLyricsPacket(track, start)
            );
        } catch (Throwable e) {
            System.err.println("[LMS] Error enviando letras a " + player.getName().getString());
        }
    }

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

    public static void forceExitPhase() {
        if (!isLMSActive || isExitPhase) return;
        startExitPhase();
    }

    public static void forceEndLMS() {
        if (!isLMSActive) return;

        OutcomeMemoriesGameSystem.broadcastMessage("§7LMS finalizado por administrador");
        deactivateLMS();
    }

    public static void updateLMSTracking(UUID oldExeUUID, UUID newExeUUID) {
        if (!isLMSActive) return;

        executionersInLMS.remove(oldExeUUID);
        survivorsInLMS.add(oldExeUUID);

        survivorsInLMS.remove(newExeUUID);
        executionersInLMS.add(newExeUUID);

        System.out.println("[LMS] Tracking updated after swap");
    }

    public static String getLyricsFileName(PlayerTypeOM character) {
        return LMS_TRACKS.get(character);
    }

    public static boolean isLyricsAvailable() {
        return lyricsInitialized;
    }

    public static void resetLyrics() {
        lyricsInitialized = false;
        System.out.println("[LMS] Sistema de letras reseteado");
    }
}
