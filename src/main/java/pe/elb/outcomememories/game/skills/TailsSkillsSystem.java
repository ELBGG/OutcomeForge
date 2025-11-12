package pe.elb.outcomememories.game.skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.client.input.KeyBindings;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.CooldownSyncPacket;
import pe.elb.outcomememories.net.skills.tails.TailsSyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema unificado de habilidades de Tails
 * 
 * Habilidades:
 * - Glide (E): Planeo controlado con energía
 * - Laser Cannon (Q): Láser cargable que stunnea
 * - Animal Sense (Pasivo): Detecta Executioners (Glowing)
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TailsSkillsSystem {

    // ============ CONSTANTES - GLIDE ============
    private static final long GLIDE_COOLDOWN_COMPLETED_MS = 35_000L; // 35s si completa
    private static final long GLIDE_COOLDOWN_CANCELLED_MS = 25_000L; // 25s si cancela
    private static final long GLIDE_COOLDOWN_AFTER_LASER_MS = 6_500L; // 6.5s después de Laser
    private static final int GLIDE_MAX_DURATION_TICKS = 20 * 10; // 10 segundos
    private static final int GLIDE_MIN_CANCEL_TIME_TICKS = 20 * 5; // 5 segundos mínimo
    private static final double GLIDE_SPEED = 0.6D;
    private static final double GLIDE_DESCENT_SPEED = 0.08D;
    private static final double GLIDE_ASCENT_SPEED = 0.12D;

    // ============ CONSTANTES - LASER CANNON ============
    private static final long LASER_COOLDOWN_NORMAL_MS = 45_000L; // 45s normal
    private static final long LASER_COOLDOWN_SOLO_MS = 15_000L; // 15s solo/últimos 3 min
    private static final long LASER_COOLDOWN_CANCELLED_MS = 15_000L; // 15s si cancela
    private static final int LASER_CHARGE_NORMAL_TICKS = 20 * 5; // 5s normal
    private static final int LASER_CHARGE_SOLO_TICKS = 20 * 2; // 2s solo
    private static final int LASER_READY_WINDOW_TICKS = 20 * 2; // 2s para disparar
    private static final int LASER_DURATION_TICKS = 20 * 4; // 4s disparando
    private static final int LASER_STUN_TICKS = 20 * 6; // 6s stun
    private static final double LASER_RANGE = 50.0D;
    private static final double LASER_WIDTH = 1.5D;
    private static final double LASER_KNOCKBACK_FORCE = 0.3D;

    // ============ CONSTANTES - ANIMAL SENSE ============
    private static final double ANIMAL_SENSE_RADIUS = 45.0D; // 45 bloques
    private static final int ANIMAL_SENSE_GLOW_TICKS = 21; // Ligeramente más de 1s

    // ============ ESTADO - GLIDE ============
    private static final Map<UUID, Long> glideLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, GlideData> activeGlides = new ConcurrentHashMap<>();

    // ============ ESTADO - LASER CANNON ============
    private static final Map<UUID, Long> laserLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, LaserData> activeLasers = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> stunSchedule = new ConcurrentHashMap<>();

    // ============ GLIDE - MÉTODOS PÚBLICOS ============

    /**
     * Intenta usar/cancelar Glide
     */
    public static boolean tryUseGlide(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.TAILS) return false;

        UUID puid = player.getUUID();

        // Si está glideando, intentar cancelar
        if (activeGlides.containsKey(puid)) {
            return tryGlideCancel(player);
        }

        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = glideLastUsed.get(puid);
        if (last != null && (now - last) < GLIDE_COOLDOWN_COMPLETED_MS) {
            return false;
        }

        // Debe estar en el aire
        if (player.onGround()) return false;

        // Iniciar glide
        GlideData data = new GlideData();
        activeGlides.put(puid, data);

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.0F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY(), player.getZ(),
                    10, 0.3, 0.1, 0.3, 0.05);
        }

        syncGlideToClient(player, data);
        return true;
    }

    /**
     * Intenta cancelar Glide
     */
    public static boolean tryGlideCancel(ServerPlayer player) {
        UUID puid = player.getUUID();
        GlideData data = activeGlides.get(puid);

        if (data == null) return false;
        if (!data.canCancel()) return false;

        endGlide(player, data, true);
        return true;
    }

    /**
     * Establece si está ascendiendo (manteniendo space)
     */
    public static void setGlideAscending(ServerPlayer player, boolean ascending) {
        UUID puid = player.getUUID();
        GlideData data = activeGlides.get(puid);

        if (data != null) {
            data.isAscending = ascending;
        }
    }

    // ============ LASER CANNON - MÉTODOS PÚBLICOS ============

    /**
     * Intenta usar Laser Cannon (iniciar carga o manejar acción)
     */
    public static boolean tryUseLaser(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.TAILS) return false;

        UUID puid = player.getUUID();

        // Si ya está activo, manejar acción (cancelar/disparar)
        if (activeLasers.containsKey(puid)) {
            return handleLaserAction(player);
        }

        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = laserLastUsed.get(puid);
        boolean isSolo = isInLastThreeMinutes(player);
        long cooldownToUse = isSolo ? LASER_COOLDOWN_SOLO_MS : LASER_COOLDOWN_NORMAL_MS;
        
        if (last != null && (now - last) < cooldownToUse) {
            return false;
        }

        // Iniciar laser
        LaserData data = new LaserData(isSolo);
        activeLasers.put(puid, data);

        syncLaserToClient(player, "CHARGING", 0.0F);

        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new CooldownSyncPacket("tails_laser", cooldownToUse, now)
            );
        } catch (Throwable ignored) {}

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.5F);
        }

        return true;
    }

    /**
     * Maneja acción del laser según fase (cancelar/disparar)
     */
    public static boolean handleLaserAction(ServerPlayer player) {
        UUID puid = player.getUUID();
        LaserData data = activeLasers.get(puid);
        if (data == null) return false;

        switch (data.phase) {
            case CHARGING -> {
                // Solo cancelar si ya terminó de cargar
                if (data.ticksInPhase >= data.getChargeTime()) {
                    cancelLaser(player, data);
                    return true;
                }
                return false;
            }
            case READY_TO_FIRE -> {
                fireLaser(player, data);
                return true;
            }
            case FIRING -> {
                // No puede cancelar mientras dispara
                return false;
            }
        }
        return false;
    }

    // ============ EVENTOS ============

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickStuns();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.TAILS) return;

        UUID puid = player.getUUID();

        // Tick de Glide
        GlideData glideData = activeGlides.get(puid);
        if (glideData != null) {
            tickGlide(player, glideData);
        }

        // Tick de Laser
        LaserData laserData = activeLasers.get(puid);
        if (laserData != null) {
            tickLaser(player, laserData);
        }

        // Animal Sense (cada segundo)
        if (player.tickCount % 20 == 0) {
            tickAnimalSense(player);
        }
    }

    // ============ LÓGICA DE GLIDE ============

    private static void tickGlide(ServerPlayer player, GlideData data) {
        UUID puid = player.getUUID();

        // Si toca el suelo, terminar
        if (player.onGround()) {
            endGlide(player, data, false);
            return;
        }

        // Física del glide
        Vec3 look = player.getLookAngle().normalize();
        Vec3 horizontalDir = new Vec3(look.x, 0, look.z).normalize();

        double newX = horizontalDir.x * GLIDE_SPEED;
        double newZ = horizontalDir.z * GLIDE_SPEED;
        double newY = data.isAscending ? GLIDE_ASCENT_SPEED : -GLIDE_DESCENT_SPEED;

        player.setDeltaMovement(newX, newY, newZ);
        player.hurtMarked = true;

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && data.ticksActive % 5 == 0) {
            Vec3 behind = look.scale(-0.5);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + behind.x, player.getY() + 0.5, player.getZ() + behind.z,
                    2, 0.1, 0.1, 0.1, 0.01);
        }

        data.ticksActive++;

        // Advertencia 1s antes
        if (data.ticksActive == GLIDE_MAX_DURATION_TICKS - 20) {
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
            }
        }

        // Sync energía periódicamente
        if (data.ticksActive % 10 == 0) {
            syncGlideToClient(player, data);
        }

        // Terminar si se agota
        if (data.ticksActive >= GLIDE_MAX_DURATION_TICKS) {
            endGlide(player, data, false);
        }
    }

    private static void endGlide(ServerPlayer player, GlideData data, boolean wasCancelled) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        activeGlides.remove(puid);

        long cooldown = wasCancelled ? GLIDE_COOLDOWN_CANCELLED_MS : GLIDE_COOLDOWN_COMPLETED_MS;
        glideLastUsed.put(puid, now);

        syncCooldown(player, "tails_glide", cooldown, now);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
        }

        // Notificar cliente que glide terminó
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new TailsSyncPacket(puid, 1.0F, false)
        );
    }

    private static void syncGlideToClient(ServerPlayer player, GlideData data) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new TailsSyncPacket(player.getUUID(), data.getEnergyPercent(), true)
        );
    }

    // ============ LÓGICA DE LASER CANNON ============

    private static void tickLaser(ServerPlayer player, LaserData data) {
        data.ticksInPhase++;

        switch (data.phase) {
            case CHARGING -> handleLaserCharging(player, data);
            case READY_TO_FIRE -> handleLaserReady(player, data);
            case FIRING -> handleLaserFiring(player, data);
        }
    }

    private static void handleLaserCharging(ServerPlayer player, LaserData data) {
        // Ralentizar durante carga
        player.setDeltaMovement(player.getDeltaMovement().scale(0.5));
        player.hurtMarked = true;

        // Sync progreso
        if (data.ticksInPhase % 10 == 0) {
            float progress = (float) data.ticksInPhase / data.getChargeTime();
            syncLaserToClient(player, "CHARGING", Math.min(1.0F, progress));
        }

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && data.ticksInPhase % 5 == 0) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.5, player.getZ(),
                    5, 0.3, 0.3, 0.3, 0.05);
        }

        // Transición a READY
        if (data.ticksInPhase >= data.getChargeTime()) {
            data.phase = LaserPhase.READY_TO_FIRE;
            data.ticksInPhase = 0;

            syncLaserToClient(player, "READY_TO_FIRE", 1.0F);

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
            }
        }
    }

    private static void handleLaserReady(ServerPlayer player, LaserData data) {
        // Sync estado
        if (data.ticksInPhase % 5 == 0) {
            syncLaserToClient(player, "READY_TO_FIRE", 1.0F);
        }

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && data.ticksInPhase % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.5, player.getZ(),
                    10, 0.4, 0.4, 0.4, 0.1);
        }

        // Auto-cancelar si expira ventana
        if (data.ticksInPhase >= LASER_READY_WINDOW_TICKS) {
            cancelLaser(player, data);
        }
    }

    private static void handleLaserFiring(ServerPlayer player, LaserData data) {
        UUID puid = player.getUUID();

        // Sync progreso
        if (data.ticksInPhase % 5 == 0) {
            float progress = (float) data.ticksInPhase / LASER_DURATION_TICKS;
            syncLaserToClient(player, "FIRING", Math.min(1.0F, progress));
        }

        // Inmovilizar
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        // Raycast del láser
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = start.add(look.scale(LASER_RANGE));

        AABB laserBox = new AABB(start, end).inflate(LASER_WIDTH);
        List<LivingEntity> hitEntities = player.level().getEntitiesOfClass(
                LivingEntity.class, laserBox,
                e -> e != player && e.isAlive() && isExecutioner(e) && 
                !data.hitEntities.contains(e.getUUID())
        );

        for (LivingEntity hit : hitEntities) {
            data.hitEntities.add(hit.getUUID());
            applyStun(hit, LASER_STUN_TICKS);

            Vec3 knockbackDir = look.scale(LASER_KNOCKBACK_FORCE);
            hit.push(knockbackDir.x, 0.1, knockbackDir.z);
            hit.hurtMarked = true;
        }

        // Partículas del rayo
        if (player.level() instanceof ServerLevel serverLevel) {
            for (double d = 0; d < LASER_RANGE; d += 0.5) {
                Vec3 particlePos = start.add(look.scale(d));
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        particlePos.x, particlePos.y, particlePos.z,
                        2, 0.1, 0.1, 0.1, 0.0);
            }
        }

        // Sonido
        if (data.ticksInPhase % 10 == 0 && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5F, 2.0F);
        }

        // Terminar
        if (data.ticksInPhase >= LASER_DURATION_TICKS) {
            endLaser(player, data);
        }
    }

    private static void cancelLaser(ServerPlayer player, LaserData data) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        activeLasers.remove(puid);
        laserLastUsed.put(puid, now - (LASER_COOLDOWN_NORMAL_MS - LASER_COOLDOWN_CANCELLED_MS));

        syncLaserToClient(player, "NONE", 0.0F);
        syncCooldown(player, "tails_laser", LASER_COOLDOWN_CANCELLED_MS, now);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    private static void fireLaser(ServerPlayer player, LaserData data) {
        data.phase = LaserPhase.FIRING;
        data.ticksInPhase = 0;

        syncLaserToClient(player, "FIRING", 0.0F);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 2.0F);
        }
    }

    private static void endLaser(ServerPlayer player, LaserData data) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        activeLasers.remove(puid);

        syncLaserToClient(player, "NONE", 0.0F);

        long cooldown = data.isSolo ? LASER_COOLDOWN_SOLO_MS : LASER_COOLDOWN_NORMAL_MS;
        laserLastUsed.put(puid, now);

        syncCooldown(player, "tails_laser", cooldown, now);

        // Reducir cooldown de Glide
        applyGlideCooldownReduction(puid);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static void syncLaserToClient(ServerPlayer player, String phase, float progress) {
        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new TailsSyncPacket(player.getUUID(), phase, progress)
        );
    }

    /**
     * Reduce cooldown de Glide después de usar Laser
     */
    private static void applyGlideCooldownReduction(UUID playerUUID) {
        long now = System.currentTimeMillis();
        glideLastUsed.put(playerUUID, 
            now - (GLIDE_COOLDOWN_COMPLETED_MS - GLIDE_COOLDOWN_AFTER_LASER_MS));

        // Sync al cliente
        for (ServerLevel lvl : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            net.minecraft.world.entity.Entity ent = lvl.getEntity(playerUUID);
            if (ent instanceof ServerPlayer sp) {
                syncCooldown(sp, "tails_glide", GLIDE_COOLDOWN_AFTER_LASER_MS, now);
                break;
            }
        }
    }

    // ============ LÓGICA DE ANIMAL SENSE ============

    private static void tickAnimalSense(ServerPlayer player) {
        // Buscar Executioners cercanos
        AABB searchArea = player.getBoundingBox().inflate(ANIMAL_SENSE_RADIUS);
        List<LivingEntity> nearbyExecutioners = player.level().getEntitiesOfClass(
            LivingEntity.class, searchArea,
            entity -> entity != player && entity.isAlive() && isExecutioner(entity)
        );

        // Aplicar Glowing
        for (LivingEntity executioner : nearbyExecutioners) {
            executioner.addEffect(new MobEffectInstance(
                MobEffects.GLOWING, 
                ANIMAL_SENSE_GLOW_TICKS, 
                0, 
                false, false, false
            ));
        }
    }

    // ============ SISTEMA DE STUN ============

    private static void applyStun(LivingEntity target, int ticks) {
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            scheduleAiReactivation(mob, ticks);
        }

        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false, true
        ));
    }

    private static void scheduleAiReactivation(Mob mob, int ticks) {
        UUID mobId = mob.getUUID();
        long enableAt = System.currentTimeMillis() + (ticks * 50L);
        stunSchedule.put(mobId, enableAt);
    }

    private static void tickStuns() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = stunSchedule.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now >= entry.getValue()) {
                UUID mobId = entry.getKey();
                LivingEntity mob = findEntityByUUID(mobId);
                if (mob instanceof Mob m) {
                    m.setNoAi(false);
                }
                it.remove();
            }
        }
    }

    // ============ UTILIDADES ============

    private static boolean isExecutioner(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
            return def != null && def.getType() == PlayerTypeOM.X2011;
        }
        return false;
    }

    private static boolean isInLastThreeMinutes(ServerPlayer player) {
        return false;
    }

    private static LivingEntity findEntityByUUID(UUID uuid) {
        for (ServerLevel level : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity le) return le;
        }
        return null;
    }

    private static void syncCooldown(ServerPlayer player, String skillId, long cooldownMs, long now) {
        try {
            NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new CooldownSyncPacket(skillId, cooldownMs, now)
            );
        } catch (Throwable ignored) {}
    }

    // ============ API PÚBLICA ============

    public static boolean isGliding(UUID playerUUID) {
        return activeGlides.containsKey(playerUUID);
    }

    public static float getGlideEnergy(UUID playerUUID) {
        GlideData data = activeGlides.get(playerUUID);
        return data != null ? data.getEnergyPercent() : 1.0F;
    }

    public static boolean isLaserActive(UUID playerUUID) {
        return activeLasers.containsKey(playerUUID);
    }

    public static LaserPhase getLaserPhase(UUID playerUUID) {
        LaserData data = activeLasers.get(playerUUID);
        return data != null ? data.phase : null;
    }

    // ============ GETTERS DE COOLDOWN ============

    public static long getGlideCooldownRemaining(UUID playerUUID) {
        Long last = glideLastUsed.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (GLIDE_COOLDOWN_COMPLETED_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    public static long getLaserCooldownRemaining(UUID playerUUID) {
        Long last = laserLastUsed.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (LASER_COOLDOWN_NORMAL_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    // ============ LIMPIEZA ============

    public static void cleanup(UUID playerUUID) {
        glideLastUsed.remove(playerUUID);
        activeGlides.remove(playerUUID);
        laserLastUsed.remove(playerUUID);
        activeLasers.remove(playerUUID);
    }

    // ============ CLASES INTERNAS ============

    private static class GlideData {
        public int ticksActive;
        public boolean isAscending;
        public final long startedAt;

        public GlideData() {
            this.ticksActive = 0;
            this.isAscending = false;
            this.startedAt = System.currentTimeMillis();
        }

        public float getEnergyPercent() {
            return 1.0F - ((float) ticksActive / GLIDE_MAX_DURATION_TICKS);
        }

        public boolean canCancel() {
            return ticksActive >= GLIDE_MIN_CANCEL_TIME_TICKS;
        }
    }

    public enum LaserPhase {
        CHARGING,
        READY_TO_FIRE,
        FIRING
    }

    private static class LaserData {
        public LaserPhase phase;
        public int ticksInPhase;
        public final boolean isSolo;
        public final long startedAt;
        public final Set<UUID> hitEntities;

        public LaserData(boolean isSolo) {
            this.phase = LaserPhase.CHARGING;
            this.ticksInPhase = 0;
            this.isSolo = isSolo;
            this.startedAt = System.currentTimeMillis();
            this.hitEntities = new HashSet<>();
        }

        public int getChargeTime() {
            return isSolo ? LASER_CHARGE_SOLO_TICKS : LASER_CHARGE_NORMAL_TICKS;
        }
    }

    // ============ ENUM: MOVESET (para UI) ============

    public enum TailsMoveSet {
        GLIDE("tails_glide", "glide", KeyType.SECONDARY, "Glide"),
        LASER("tails_laser", "laser", KeyType.PRIMARY, "Laser Cannon");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        TailsMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() { return id; }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/tails/" + textureName + ".png");
        }

        public String getKeybind() {
            if (FMLEnvironment.dist.isClient()) {
                try {
                    return switch (this.keyType) {
                        case PRIMARY -> KeyBindings.ABILITY_PRIMARY.getTranslatedKeyMessage().getString();
                        case SECONDARY -> KeyBindings.ABILITY_SECONDARY.getTranslatedKeyMessage().getString();
                        case SPECIAL -> KeyBindings.ABILITY_SPECIAL.getTranslatedKeyMessage().getString();
                    };
                } catch (Throwable ignored) {}
            }
            return switch (this.keyType) {
                case PRIMARY -> "Q";
                case SECONDARY -> "E";
                case SPECIAL -> "X";
            };
        }

        public String getDisplayName() { return displayName; }

        private enum KeyType {
            PRIMARY,
            SECONDARY,
            SPECIAL
        }
    }
}