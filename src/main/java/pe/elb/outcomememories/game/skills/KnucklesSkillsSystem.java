package pe.elb.outcomememories.game.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import pe.elb.outcomememories.client.input.KeyBindings;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerDefineSuvivor;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.CooldownSyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema unificado de habilidades de Knuckles
 * 
 * Habilidades:
 * - Punch (E): Puñetazo cargable con 3 fases + Ground Slam
 * - Counter (Q): Counter defensivo que hereda fase del Punch
 * - Wall Cling (Pasivo): Agarrarse a paredes
 * - Glide (Pasivo): Planeo rápido después de Wall Cling o en aire
 */
@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KnucklesSkillsSystem {

    // ============ CONSTANTES - PUNCH ============
    private static final long PUNCH_COOLDOWN_MS = 24_000L; // 24 segundos
    private static final int PUNCH_PHASE_1_TICKS = 20 * 1; // 1 segundo
    private static final int PUNCH_PHASE_2_TICKS = 20 * 3; // 3 segundos
    private static final int PUNCH_PHASE_3_TICKS = 20 * 5; // 5 segundos (Burning Fury)
    private static final int PUNCH_PHASE_1_STUN = 20 * 5; // 5 segundos
    private static final int PUNCH_PHASE_2_STUN = 20 * 6; // 6 segundos
    private static final int PUNCH_PHASE_3_STUN = 20 * 8; // 8 segundos
    private static final double PUNCH_LUNGE_SPEED = 3.0D;
    private static final double PUNCH_GROUND_SLAM_SPEED = 2.5D;
    private static final float PUNCH_DAMAGE_REDUCTION = 0.36F; // 64% reducción

    // ============ CONSTANTES - COUNTER ============
    private static final long COUNTER_COOLDOWN_SUCCESS_MS = 24_000L; // 24s si conecta
    private static final long COUNTER_COOLDOWN_MISS_MS = 29_000L; // 29s si falla
    private static final int COUNTER_DURATION_TICKS = 20 * 3; // 3 segundos
    private static final double COUNTER_KNOCKBACK_FORCE = 2.5D;

    // ============ CONSTANTES - GLIDE ============
    private static final int GLIDE_DURATION_TICKS = 20 * 4; // 4 segundos
    private static final double GLIDE_SPEED = 1.5D;
    private static final double GLIDE_FALL_SPEED = 0.15D;
    private static final double WALL_CLING_DISTANCE = 0.6D;

    // ============ ESTADO - PUNCH ============
    private static final Map<UUID, Long> punchLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, PunchData> activePunches = new ConcurrentHashMap<>();

    // ============ ESTADO - COUNTER ============
    private static final Map<UUID, Long> counterLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, CounterData> activeCounters = new ConcurrentHashMap<>();

    // ============ ESTADO - GLIDE ============
    private static final Map<UUID, GlideData> activeGlides = new ConcurrentHashMap<>();
    private static final Map<UUID, WallClingData> wallClings = new ConcurrentHashMap<>();

    // ============ ESTADO - STUN ============
    private static final Map<UUID, Long> stunSchedule = new ConcurrentHashMap<>();

    // ============ PUNCH - MÉTODOS PÚBLICOS ============

    /**
     * Intenta iniciar el Punch cargable
     */
    public static boolean tryUsePunch(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerDefineSuvivor def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.KNUCKLES) return false;

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = punchLastUsed.get(puid);
        if (last != null && (now - last) < PUNCH_COOLDOWN_MS) {
            return false;
        }

        // Iniciar carga
        PunchData data = new PunchData();
        activePunches.put(puid, data);

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.5F);
        }

        return true;
    }

    /**
     * Ejecuta el puñetazo (lunge o ground slam)
     */
    public static boolean executePunch(ServerPlayer player, boolean isGroundSlam) {
        UUID puid = player.getUUID();
        PunchData data = activePunches.get(puid);

        if (data == null) return false;

        // Guardar fase de carga antes de ejecutar
        if (data.phase == PunchPhase.CHARGING_1 ||
                data.phase == PunchPhase.CHARGING_2 ||
                data.phase == PunchPhase.CHARGING_3) {
            data.chargePhaseBeforeAttack = data.phase;
        } else {
            return false;
        }

        // Determinar tipo de ataque
        if (isGroundSlam && !player.onGround()) {
            // Ground Slam
            data.usedGroundSlam = true;
            data.phase = PunchPhase.SLAMMING;
            data.ticksInPhase = 0;

            player.setDeltaMovement(0, -PUNCH_GROUND_SLAM_SPEED, 0);
            player.hurtMarked = true;

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.8F);
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        20, 0.3, 0.1, 0.3, 0.05);
            }
        } else {
            // Lunge normal
            data.phase = PunchPhase.LUNGING;
            data.ticksInPhase = 0;

            Vec3 look = player.getLookAngle().normalize();
            Vec3 lunge = new Vec3(look.x * PUNCH_LUNGE_SPEED, 0.2, look.z * PUNCH_LUNGE_SPEED);
            player.setDeltaMovement(lunge);
            player.hurtMarked = true;

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.9F);
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        10, 0.5, 0.3, 0.5, 0.05);
            }
        }

        return true;
    }

    // ============ COUNTER - MÉTODOS PÚBLICOS ============

    /**
     * Intenta usar Counter
     */
    public static boolean tryUseCounter(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerDefineSuvivor def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.KNUCKLES) return false;

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = counterLastUsed.get(puid);
        if (last != null && (now - last) < COUNTER_COOLDOWN_MISS_MS) {
            return false;
        }

        // Verificar si está cargando punch
        PunchData punchData = activePunches.get(puid);
        PunchPhase inheritedPhase = null;

        if (punchData != null) {
            inheritedPhase = punchData.phase;
            cancelPunchWithoutCooldown(puid);
        }

        // Activar counter
        CounterData data = new CounterData(inheritedPhase);
        activeCounters.put(puid, data);

        // Inmovilizar stance
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.5, 0.8, 0.5, 0.1);
        }

        return true;
    }

    // ============ GLIDE - MÉTODOS PÚBLICOS ============

    /**
     * Intenta agarrarse a una pared
     */
    public static boolean tryWallCling(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerDefineSuvivor def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.KNUCKLES) return false;

        if (player.onGround()) return false;
        if (wallClings.containsKey(player.getUUID())) return false;

        // Buscar pared cercana
        Vec3 wallNormal = findNearbyWall(player);
        if (wallNormal == null) return false;

        // Agarrarse
        UUID puid = player.getUUID();
        wallClings.put(puid, new WallClingData(wallNormal));

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F, 1.2F);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    10, 0.3, 0.5, 0.3, 0.05);
        }

        return true;
    }

    /**
     * Salta de la pared e inicia glide
     */
    public static boolean startGlideFromWall(ServerPlayer player) {
        UUID puid = player.getUUID();
        WallClingData cling = wallClings.get(puid);

        if (cling == null) return false;

        wallClings.remove(puid);
        activeGlides.put(puid, new GlideData());

        // Impulso alejándose de la pared
        Vec3 jumpDir = cling.wallNormal.scale(0.5).add(0, 0.3, 0);
        player.setDeltaMovement(jumpDir);
        player.hurtMarked = true;

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 1.3F);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    15, 0.4, 0.3, 0.4, 0.1);
        }

        return true;
    }

    /**
     * Inicia glide directamente (sin pared)
     */
    public static boolean startGlide(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerDefineSuvivor def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.KNUCKLES) return false;

        if (player.onGround()) return false;

        UUID puid = player.getUUID();
        if (activeGlides.containsKey(puid)) return false;

        activeGlides.put(puid, new GlideData());
        return true;
    }

    // ============ EVENTOS ============

    /**
     * Intercepta ataques cuando Counter está activo
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        UUID puid = player.getUUID();
        CounterData counter = activeCounters.get(puid);

        if (counter == null || counter.wasTriggered) return;

        // Verificar que sea Executioner
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (!isExecutioner(attacker)) return;

        // ¡COUNTER EXITOSO!
        counter.wasTriggered = true;
        event.setCanceled(true);

        performCounter(player, attacker, counter);
        activeCounters.remove(puid);

        long now = System.currentTimeMillis();
        counterLastUsed.put(puid, now);
        syncCooldown(player, "knuckles_counter", COUNTER_COOLDOWN_SUCCESS_MS, now);
    }

    /**
     * Reducir daño mientras carga Punch
     */
    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        UUID puid = player.getUUID();
        PunchData data = activePunches.get(puid);

        if (data == null) return;

        // Solo reducir durante carga
        if (data.phase == PunchPhase.CHARGING_1 ||
                data.phase == PunchPhase.CHARGING_2 ||
                data.phase == PunchPhase.CHARGING_3) {

            float originalDamage = event.getAmount();
            float reducedDamage = originalDamage * PUNCH_DAMAGE_REDUCTION;
            event.setAmount(reducedDamage);
        }
    }

    // ============ TICK DEL SERVIDOR ============

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickStuns();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID puid = player.getUUID();

        // Tick de punch
        PunchData punchData = activePunches.get(puid);
        if (punchData != null) {
            tickPunch(player, punchData);
        }

        // Tick de counter
        CounterData counterData = activeCounters.get(puid);
        if (counterData != null) {
            tickCounter(player, counterData);
        }

        // Tick de wall cling
        if (wallClings.containsKey(puid)) {
            tickWallCling(player, puid);
        }

        // Tick de glide
        if (activeGlides.containsKey(puid)) {
            tickGlide(player, puid);
        }
    }

    // ============ LÓGICA DE PUNCH ============

    private static void tickPunch(ServerPlayer player, PunchData data) {
        UUID puid = player.getUUID();
        data.ticksInPhase++;

        switch (data.phase) {
            case CHARGING_1, CHARGING_2, CHARGING_3 -> handlePunchCharging(player, data);
            case LUNGING -> handlePunchLunging(player, data);
            case SLAMMING -> handlePunchSlamming(player, data);
        }
    }

    private static void handlePunchCharging(ServerPlayer player, PunchData data) {
        // Ralentizar durante carga
        player.setDeltaMovement(player.getDeltaMovement().scale(0.5));
        player.hurtMarked = true;

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && data.ticksInPhase % 5 == 0) {
            SimpleParticleType particle = data.phase == PunchPhase.CHARGING_3 ?
                    ParticleTypes.FLAME : ParticleTypes.CRIT;
            int count = data.phase == PunchPhase.CHARGING_3 ? 10 : 5;

            serverLevel.sendParticles(particle,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    count, 0.3, 0.3, 0.3, 0.05);
        }

        // Avanzar a siguiente fase
        PunchPhase currentPhase = data.phase;
        if (data.ticksInPhase >= currentPhase.maxChargeTicks) {
            PunchPhase nextPhase = switch (currentPhase) {
                case CHARGING_1 -> PunchPhase.CHARGING_2;
                case CHARGING_2 -> PunchPhase.CHARGING_3;
                default -> null;
            };

            if (nextPhase != null) {
                data.phase = nextPhase;
                data.ticksInPhase = 0;

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 1.8F);

                    if (nextPhase == PunchPhase.CHARGING_3) {
                        serverLevel.sendParticles(ParticleTypes.FLAME,
                                player.getX(), player.getY() + 1.0, player.getZ(),
                                50, 0.5, 0.8, 0.5, 0.15);
                        serverLevel.playSound(null, player.blockPosition(),
                                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                }
            }
        }
    }

    private static void handlePunchLunging(ServerPlayer player, PunchData data) {
        UUID puid = player.getUUID();

        // Mantener velocidad
        Vec3 look = player.getLookAngle().normalize();
        Vec3 lunge = new Vec3(look.x * PUNCH_LUNGE_SPEED, 0.1, look.z * PUNCH_LUNGE_SPEED);
        player.setDeltaMovement(lunge);
        player.hurtMarked = true;

        // Detectar impactos
        AABB hitBox = player.getBoundingBox().inflate(1.5);
        List<LivingEntity> enemies = player.level().getEntitiesOfClass(LivingEntity.class, hitBox,
                e -> e != player && e.isAlive() && isExecutioner(e));

        for (LivingEntity enemy : enemies) {
            PunchPhase chargePhase = data.chargePhaseBeforeAttack;
            int stunTicks = chargePhase.stunTicks;
            boolean hasFire = chargePhase.hasFire;

            applyStun(enemy, stunTicks);

            Vec3 knockback = player.getLookAngle().normalize().scale(1.5);
            enemy.push(knockback.x, 0.4, knockback.z);
            enemy.hurtMarked = true;

            if (hasFire) {
                enemy.setSecondsOnFire(5);
            }

            // Efectos
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, enemy.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        enemy.getX(), enemy.getY() + 1.0, enemy.getZ(),
                        3, 0.3, 0.5, 0.3, 0.0);

                if (hasFire) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            enemy.getX(), enemy.getY() + 1.0, enemy.getZ(),
                            20, 0.4, 0.5, 0.4, 0.1);
                }
            }

            endPunch(player, data);
            return;
        }

        // Terminar si toca suelo o timeout
        if (data.ticksInPhase >= 10 || player.onGround()) {
            endPunch(player, data);
        }
    }

    private static void handlePunchSlamming(ServerPlayer player, PunchData data) {
        // Mantener velocidad hacia abajo
        if (!player.onGround()) {
            player.setDeltaMovement(0, -PUNCH_GROUND_SLAM_SPEED, 0);
            player.hurtMarked = true;
        }

        // Impacto al tocar suelo
        if (player.onGround()) {
            PunchPhase chargePhase = data.chargePhaseBeforeAttack;
            int stunTicks = chargePhase.stunTicks;
            boolean hasFire = chargePhase.hasFire;

            double radius = 3.0;
            AABB impactArea = player.getBoundingBox().inflate(radius);
            List<LivingEntity> enemies = player.level().getEntitiesOfClass(LivingEntity.class, impactArea,
                    e -> e != player && e.isAlive() && isExecutioner(e));

            for (LivingEntity enemy : enemies) {
                applyStun(enemy, stunTicks);

                Vec3 awayFromPlayer = enemy.position().subtract(player.position()).normalize();
                double distance = enemy.position().distanceTo(player.position());
                double force = Math.max(0.5, 2.5 - (distance / radius));

                enemy.push(awayFromPlayer.x * force, 0.8, awayFromPlayer.z * force);
                enemy.hurtMarked = true;

                if (hasFire) {
                    enemy.setSecondsOnFire(5);
                }
            }

            // Efectos del impacto
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5F, 0.8F);

                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        player.getX(), player.getY() + 0.1, player.getZ(),
                        5, 0.0, 0.0, 0.0, 0.0);

                // Onda expansiva
                for (int i = 0; i < 360; i += 20) {
                    double angle = Math.toRadians(i);
                    double offsetX = Math.cos(angle) * radius;
                    double offsetZ = Math.sin(angle) * radius;

                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            player.getX() + offsetX, player.getY() + 0.1, player.getZ() + offsetZ,
                            3, 0.1, 0.1, 0.1, 0.05);
                }

                if (hasFire) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            player.getX(), player.getY() + 0.5, player.getZ(),
                            40, radius * 0.5, 0.2, radius * 0.5, 0.15);
                }
            }

            endPunch(player, data);
            return;
        }

        // Timeout
        if (data.ticksInPhase >= 40) {
            endPunch(player, data);
        }
    }

    private static void endPunch(ServerPlayer player, PunchData data) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        activePunches.remove(puid);
        punchLastUsed.put(puid, now);

        syncCooldown(player, "knuckles_punch", PUNCH_COOLDOWN_MS, now);
    }

    // ============ LÓGICA DE COUNTER ============

    private static void tickCounter(ServerPlayer player, CounterData counter) {
        UUID puid = player.getUUID();

        // Mantener stance
        player.setDeltaMovement(player.getDeltaMovement().scale(0.1));
        player.hurtMarked = true;

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 10 == 0) {
            SimpleParticleType particle = counter.inheritedPunchPhase == PunchPhase.CHARGING_3 ?
                    ParticleTypes.FLAME : ParticleTypes.ENCHANTED_HIT;

            serverLevel.sendParticles(particle,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    3, 0.3, 0.5, 0.3, 0.02);
        }

        // Flash visual
        if (counter.ticksRemaining % 10 == 0 && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    10, 0.4, 0.6, 0.4, 0.05);
        }

        // Advertencia sonora
        if (counter.ticksRemaining == 20) {
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6F, 2.0F);
            }
        }

        counter.ticksRemaining--;

        if (counter.ticksRemaining <= 0) {
            endCounter(player, counter);
        }
    }

    private static void performCounter(ServerPlayer defender, LivingEntity attacker, CounterData counter) {
        // Determinar stun según fase heredada
        int stunTicks = PUNCH_PHASE_1_STUN;
        boolean applyFire = false;

        if (counter.inheritedPunchPhase != null) {
            switch (counter.inheritedPunchPhase) {
                case CHARGING_1 -> stunTicks = PUNCH_PHASE_1_STUN;
                case CHARGING_2 -> stunTicks = PUNCH_PHASE_2_STUN;
                case CHARGING_3 -> {
                    stunTicks = PUNCH_PHASE_3_STUN;
                    applyFire = true;
                }
            }
        }

        applyStun(attacker, stunTicks);

        // Knockback
        Vec3 knockbackDir = attacker.position().subtract(defender.position()).normalize();
        attacker.push(
                knockbackDir.x * COUNTER_KNOCKBACK_FORCE,
                0.5,
                knockbackDir.z * COUNTER_KNOCKBACK_FORCE
        );
        attacker.hurtMarked = true;

        if (applyFire) {
            attacker.setSecondsOnFire(5);
        }

        // Efectos
        if (defender.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, attacker.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);

            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
                    30, 0.5, 0.8, 0.5, 0.2);

            if (applyFire) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
                        25, 0.5, 0.8, 0.5, 0.15);

                serverLevel.playSound(null, attacker.blockPosition(),
                        SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
    }

    private static void endCounter(ServerPlayer player, CounterData counter) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        activeCounters.remove(puid);

        long cooldown = counter.wasTriggered ? COUNTER_COOLDOWN_SUCCESS_MS : COUNTER_COOLDOWN_MISS_MS;
        counterLastUsed.put(puid, now);

        syncCooldown(player, "knuckles_counter", cooldown, now);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5F, 0.8F);
        }
    }

    // ============ LÓGICA DE GLIDE ============

    private static void tickWallCling(ServerPlayer player, UUID puid) {
        WallClingData cling = wallClings.get(puid);
        if (cling == null) return;

        if (player.onGround()) {
            wallClings.remove(puid);
            return;
        }

        // Caída lenta
        player.setDeltaMovement(0, -0.05, 0);
        player.hurtMarked = true;

        // Verificar que aún haya pared
        Vec3 wallNormal = findNearbyWall(player);
        if (wallNormal == null) {
            wallClings.remove(puid);
        }

        // Partículas
        if (player.tickCount % 10 == 0 && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private static void tickGlide(ServerPlayer player, UUID puid) {
        GlideData glide = activeGlides.get(puid);
        if (glide == null) return;

        if (player.onGround()) {
            activeGlides.remove(puid);
            return;
        }

        // Física del glide
        Vec3 look = player.getLookAngle().normalize();
        Vec3 horizontalDir = new Vec3(look.x, 0, look.z).normalize();

        double newX = horizontalDir.x * GLIDE_SPEED;
        double newZ = horizontalDir.z * GLIDE_SPEED;
        double newY = -GLIDE_FALL_SPEED;

        player.setDeltaMovement(newX, newY, newZ);
        player.hurtMarked = true;

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 3 == 0) {
            Vec3 behind = look.scale(-0.5);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + behind.x, player.getY() + 0.3, player.getZ() + behind.z,
                    2, 0.2, 0.1, 0.2, 0.01);
        }

        glide.ticksRemaining--;

        if (glide.ticksRemaining <= 0) {
            activeGlides.remove(puid);

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 0.8F);
            }
        }
    }

    private static Vec3 findNearbyWall(ServerPlayer player) {
        Vec3 playerPos = player.position();
        Vec3[] directions = {
            new Vec3(1, 0, 0),
            new Vec3(-1, 0, 0),
            new Vec3(0, 0, 1),
            new Vec3(0, 0, -1)
        };

        for (Vec3 dir : directions) {
            Vec3 checkPos = playerPos.add(dir.scale(WALL_CLING_DISTANCE));
            BlockState block = player.level().getBlockState(
                new BlockPos((int)checkPos.x, (int)checkPos.y, (int)checkPos.z)
            );

            if (!block.isAir()) {
                return dir.scale(-1).normalize();
            }
        }

        return null;
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

    private static boolean isExecutioner(LivingEntity e) {
        if (e instanceof ServerPlayer sp) {
            var def = PlayerRegistry.get(sp);
            return def != null && def.getType() == PlayerTypeOM.X2011;
        }
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

    public static boolean isPunchActive(UUID playerUUID) {
        return activePunches.containsKey(playerUUID);
    }

    public static PunchData getPunchData(UUID playerUUID) {
        return activePunches.get(playerUUID);
    }

    public static boolean isCounterActive(UUID playerUUID) {
        return activeCounters.containsKey(playerUUID);
    }

    public static CounterData getCounterData(UUID playerUUID) {
        return activeCounters.get(playerUUID);
    }

    public static boolean isGliding(UUID playerUUID) {
        return activeGlides.containsKey(playerUUID);
    }

    public static boolean isWallClinging(UUID playerUUID) {
        return wallClings.containsKey(playerUUID);
    }

    public static void stopGlide(UUID playerUUID) {
        activeGlides.remove(playerUUID);
    }

    public static void stopWallCling(UUID playerUUID) {
        wallClings.remove(playerUUID);
    }

    public static void cancelPunchWithoutCooldown(UUID playerUUID) {
        activePunches.remove(playerUUID);
    }

    // ============ GETTERS DE COOLDOWN ============

    public static long getPunchCooldownRemaining(UUID playerUUID) {
        Long last = punchLastUsed.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (PUNCH_COOLDOWN_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    public static long getCounterCooldownRemaining(UUID playerUUID) {
        Long last = counterLastUsed.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (COUNTER_COOLDOWN_MISS_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    // ============ LIMPIEZA ============

    public static void cleanup(UUID playerUUID) {
        punchLastUsed.remove(playerUUID);
        activePunches.remove(playerUUID);
        counterLastUsed.remove(playerUUID);
        activeCounters.remove(playerUUID);
        activeGlides.remove(playerUUID);
        wallClings.remove(playerUUID);
    }

    // ============ CLASES INTERNAS ============

    public enum PunchPhase {
        CHARGING_1(PUNCH_PHASE_1_TICKS, PUNCH_PHASE_1_STUN, "§c⚡ Fase 1", false),
        CHARGING_2(PUNCH_PHASE_2_TICKS, PUNCH_PHASE_2_STUN, "§6⚡ Fase 2", false),
        CHARGING_3(PUNCH_PHASE_3_TICKS, PUNCH_PHASE_3_STUN, "§4🔥 BURNING FURY!", true),
        LUNGING(10, 0, "", false),
        SLAMMING(20, 0, "", false);

        public final int maxChargeTicks;
        public final int stunTicks;
        public final String displayName;
        public final boolean hasFire;

        PunchPhase(int maxChargeTicks, int stunTicks, String displayName, boolean hasFire) {
            this.maxChargeTicks = maxChargeTicks;
            this.stunTicks = stunTicks;
            this.displayName = displayName;
            this.hasFire = hasFire;
        }
    }

    public static class PunchData {
        public PunchPhase phase;
        public int ticksInPhase;
        public boolean usedGroundSlam;
        public final long startedAt;
        public PunchPhase chargePhaseBeforeAttack;

        public PunchData() {
            this.phase = PunchPhase.CHARGING_1;
            this.ticksInPhase = 0;
            this.usedGroundSlam = false;
            this.startedAt = System.currentTimeMillis();
            this.chargePhaseBeforeAttack = PunchPhase.CHARGING_1;
        }
    }

    private static class CounterData {
        public int ticksRemaining;
        public boolean wasTriggered;
        public PunchPhase inheritedPunchPhase;
        public final long startedAt;

        public CounterData(PunchPhase punchPhase) {
            this.ticksRemaining = COUNTER_DURATION_TICKS;
            this.wasTriggered = false;
            this.inheritedPunchPhase = punchPhase;
            this.startedAt = System.currentTimeMillis();
        }
    }

    private static class GlideData {
        public int ticksRemaining;
        public final long startedAt;

        public GlideData() {
            this.ticksRemaining = GLIDE_DURATION_TICKS;
            this.startedAt = System.currentTimeMillis();
        }
    }

    private static class WallClingData {
        public final Vec3 wallNormal;
        public final long clungAt;

        public WallClingData(Vec3 wallNormal) {
            this.wallNormal = wallNormal;
            this.clungAt = System.currentTimeMillis();
        }
    }

    // ============ ENUM: MOVESET (para UI) ============

    public enum KnucklesMoveSet {
        PUNCH("knuckles_punch", "punch", KeyType.SECONDARY, "Punch"),
        COUNTER("knuckles_counter", "counter", KeyType.PRIMARY, "Counter");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        KnucklesMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/knuckles/" + textureName + ".png");
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

        public String getDisplayName() {
            return displayName;
        }

        private enum KeyType {
            PRIMARY,
            SECONDARY,
            SPECIAL
        }
    }
}