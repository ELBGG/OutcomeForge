package pe.elb.outcomememories.game.skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.client.input.KeyBindings;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerDefineSuvivor;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.CooldownSyncPacket;
import pe.elb.outcomememories.net.skills.sonic.SonicSyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema unificado de habilidades de Sonic
 *
 * Habilidades:
 * - Dropdash (E): Spin-dash aéreo con stun
 * - Peelout (Q): Dash cargable que puede llevar aliados
 * - Dodge Meter (Pasivo): 50 HP extra que absorbe daño
 * - Guilt (Pasivo): +15% daño como último sobreviviente
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SonicSkillsSystem {

    // ============ CONSTANTES - DROPDASH ============
    private static final long DROPDASH_COOLDOWN_MISS_MS = 25_000L; // 25s si no golpea
    private static final long DROPDASH_COOLDOWN_HIT_MS = 35_000L; // 35s si golpea 3 veces
    private static final int DROPDASH_MAX_HITS = 3;
    private static final int DROPDASH_STUN_FIRST_TICKS = 20 * 1; // 1 segundo
    private static final int DROPDASH_STUN_THIRD_TICKS = 20 * 3; // 3 segundos
    private static final int DROPDASH_STUN_THIRD_NO_INITIAL_TICKS = 20 * 2; // 2 segundos
    private static final double DROPDASH_DIVE_SPEED = 2.0D;
    private static final double DROPDASH_SPIN_SPEED = 1.2D;
    private static final double DROPDASH_BOUNCE_FORCE = 1.5D;

    // ============ CONSTANTES - PEELOUT ============
    private static final long PEELOUT_COOLDOWN_NO_CARRY_MS = 40_000L; // 40s sin cargar
    private static final long PEELOUT_COOLDOWN_WITH_CARRY_MS = 25_000L; // 25s cargando
    private static final long PEELOUT_DROPDASH_LOCKOUT_MS = 5_000L; // 5s lockout después de Dropdash
    private static final int PEELOUT_CHARGE_TICKS = 20 * 3; // 3 segundos
    private static final int PEELOUT_DASH_TICKS = 20 * 7; // 7 segundos
    private static final double PEELOUT_DASH_SPEED = 2.5D;
    private static final double PEELOUT_PICKUP_RADIUS = 2.0D;

    // ============ CONSTANTES - DODGE METER ============
    private static final float DODGE_METER_MAX_HP = 50.0F;
    private static final float DODGE_METER_REPLENISH_PER_HIT = 10.0F;
    private static final float GUILT_DAMAGE_MULTIPLIER = 1.15F; // +15% daño

    // ============ ESTADO - DROPDASH ============
    private static final Map<UUID, Long> dropdashLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, DropdashData> activeDropdash = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> stunSchedule = new ConcurrentHashMap<>();

    // ============ ESTADO - PEELOUT ============
    private static final Map<UUID, Long> peeloutLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, PeeloutState> activePeelouts = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> dropdashUsedAt = new ConcurrentHashMap<>();

    // ============ ESTADO - CARRY SYSTEM ============
    private static final Map<UUID, UUID> carriedPlayers = new ConcurrentHashMap<>();

    // ============ ESTADO - DODGE METER ============
    private static final Map<UUID, Float> dodgeHP = new ConcurrentHashMap<>();

    // ============ DROPDASH - MÉTODOS PÚBLICOS ============

    /**
     * Intenta usar Dropdash (salto automático + spin-dash)
     */
    public static boolean tryUseDropdash(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerDefineSuvivor def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.SONIC) return false;

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = dropdashLastUsed.get(puid);
        if (last != null && (now - last) < DROPDASH_COOLDOWN_MISS_MS) {
            return false;
        }

        // Iniciar Dropdash con salto automático
        DropdashData data = new DropdashData();
        activeDropdash.put(puid, data);

        // Salto automático (sin necesidad de input del jugador)
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, 1.5D, motion.z);
        player.hurtMarked = true;

        // Invulnerabilidad inmediata
        player.invulnerableTime = 10;

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.5F);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY(), player.getZ(),
                    10, 0.3, 0.1, 0.3, 0.05);
        }

        // Notify Peelout lockout
        dropdashUsedAt.put(puid, now);

        return true;
    }

    // ============ PEELOUT - MÉTODOS PÚBLICOS ============

    /**
     * Intenta usar Peelout (carga + dash)
     */
    public static boolean tryUsePeelout(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerDefineSuvivor def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.SONIC) return false;

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar lockout por Dropdash (excepto si es último sobreviviente)
        if (!isLastSurvivor(player)) {
            Long lastDropdash = dropdashUsedAt.get(puid);
            if (lastDropdash != null && (now - lastDropdash) < PEELOUT_DROPDASH_LOCKOUT_MS) {
                return false;
            }
        }

        // Verificar cooldown
        Long last = peeloutLastUsed.get(puid);
        if (last != null && (now - last) < PEELOUT_COOLDOWN_NO_CARRY_MS) {
            return false;
        }

        // Iniciar Peelout
        PeeloutState state = new PeeloutState(puid);
        activePeelouts.put(puid, state);

        // Inmovilizar durante carga
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.5F);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    20, 0.3, 0.3, 0.3, 0.05);
        }

        return true;
    }

    /**
     * Permite al jugador cargado saltar para liberarse
     */
    public static void tryJumpOutFromCarry(ServerPlayer player) {
        UUID puid = player.getUUID();
        if (!carriedPlayers.containsKey(puid)) return;

        UUID carrierUUID = carriedPlayers.get(puid);
        PeeloutState state = activePeelouts.get(carrierUUID);

        if (state != null && state.getCarriedPlayerUUID() != null &&
                state.getCarriedPlayerUUID().equals(puid)) {

            state.releaseCarriedPlayer();
            stopCarrying(puid);

            // Impulso al saltar
            Vec3 vel = player.getDeltaMovement();
            player.setDeltaMovement(vel.x, 0.5, vel.z);
            player.hurtMarked = true;

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    // ============ DODGE METER - MÉTODOS PÚBLICOS ============

    /**
     * Inicializa el Dodge Meter
     */
    public static void initializeDodgeMeter(UUID playerUUID) {
        dodgeHP.put(playerUUID, DODGE_METER_MAX_HP);
        syncDodgeMeterToClient(playerUUID);
    }

    /**
     * Obtiene el HP del Dodge Meter
     */
    public static float getDodgeHP(UUID playerUUID) {
        return dodgeHP.getOrDefault(playerUUID, DODGE_METER_MAX_HP);
    }

    /**
     * Establece el HP del Dodge Meter
     */
    public static void setDodgeHP(UUID playerUUID, float hp) {
        dodgeHP.put(playerUUID, Math.max(0.0F, Math.min(DODGE_METER_MAX_HP, hp)));
        syncDodgeMeterToClient(playerUUID);
    }

    /**
     * Repone HP del Dodge Meter
     */
    public static void replenishDodgeHP(UUID playerUUID, float amount) {
        float current = getDodgeHP(playerUUID);
        float newAmount = Math.min(DODGE_METER_MAX_HP, current + amount);
        setDodgeHP(playerUUID, newAmount);
    }

    /**
     * Verifica si tiene HP en el Dodge Meter
     */
    public static boolean hasDodgeHP(UUID playerUUID) {
        return getDodgeHP(playerUUID) > 0.0F;
    }

    /**
     * Obtiene el porcentaje del Dodge Meter (0.0 - 1.0)
     */
    public static float getDodgePercentage(UUID playerUUID) {
        return getDodgeHP(playerUUID) / DODGE_METER_MAX_HP;
    }

    // ============ EVENTOS ============

    /**
     * Intercept damage para Dodge Meter y Guilt
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        PlayerDefineSuvivor def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.SONIC) return;

        UUID puid = player.getUUID();
        float currentDodgeHP = getDodgeHP(puid);

        // Si tiene Dodge HP, absorber daño
        if (currentDodgeHP > 0.0F) {
            float damage = event.getAmount();
            float newDodgeHP = Math.max(0.0F, currentDodgeHP - damage);
            setDodgeHP(puid, newDodgeHP);

            event.setCanceled(true);

            player.sendSystemMessage(Component.literal(
                    String.format("§6⚡ Dodge Meter absorbió %.1f daño! (%.0f/%.0f HP restante)",
                            damage, newDodgeHP, DODGE_METER_MAX_HP)
            ));

            player.level().broadcastEntityEvent(player, (byte) 2);

            if (newDodgeHP <= 0.0F) {
                player.sendSystemMessage(Component.literal(
                        "§c⚠ Dodge Meter agotado! Recupera HP golpeando con Dropdash!"));
            }
        }
        // Si no tiene Dodge HP y es último sobreviviente, aplicar Guilt
        else if (isLastSurvivor(player)) {
            float baseDamage = event.getAmount();
            float finalDamage = baseDamage * GUILT_DAMAGE_MULTIPLIER;
            event.setAmount(finalDamage);

            if (player.tickCount % 100 == 0) { // Cada 5 segundos
                player.sendSystemMessage(Component.literal(
                        "§c💔 GUILT: Recibes +15% de daño como último sobreviviente"));
            }
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

        // Tick de Dropdash
        DropdashData dropdashData = activeDropdash.get(puid);
        if (dropdashData != null) {
            tickDropdash(player, dropdashData);
        }

        // Tick de Peelout
        PeeloutState peeloutState = activePeelouts.get(puid);
        if (peeloutState != null) {
            tickPeelout(player, peeloutState);
        }
    }

    // ============ LÓGICA DE DROPDASH ============

    private static void tickDropdash(ServerPlayer player, DropdashData data) {
        data.ticksInPhase++;

        switch (data.phase) {
            case JUMPING -> handleDropdashJumping(player, data);
            case DIVING -> handleDropdashDiving(player, data);
            case SPINNING -> handleDropdashSpinning(player, data);
        }
    }

    private static void handleDropdashJumping(ServerPlayer player, DropdashData data) {
        // Esperar a que empiece a caer
        if (player.getDeltaMovement().y < 0) {
            data.phase = DropdashPhase.DIVING;
            data.ticksInPhase = 0;

            // Impulso hacia abajo
            Vec3 look = player.getLookAngle().normalize();
            Vec3 dive = new Vec3(look.x * DROPDASH_DIVE_SPEED, -DROPDASH_DIVE_SPEED,
                    look.z * DROPDASH_DIVE_SPEED);
            player.setDeltaMovement(dive);
            player.hurtMarked = true;

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
        }

        // Partículas durante el salto
        if (player.level() instanceof ServerLevel serverLevel && data.ticksInPhase % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY(), player.getZ(),
                    2, 0.2, 0.1, 0.2, 0.01);
        }
    }

    private static void handleDropdashDiving(ServerPlayer player, DropdashData data) {
        UUID puid = player.getUUID();

        // Invulnerabilidad durante la caída
        player.invulnerableTime = 10;

        // Detectar impactos
        AABB hitBox = player.getBoundingBox().inflate(1.0);
        List<LivingEntity> enemies = player.level().getEntitiesOfClass(LivingEntity.class, hitBox,
                e -> e != player && e.isAlive() && isExecutioner(e) &&
                        !data.hitEntities.contains(e.getUUID()));

        for (LivingEntity enemy : enemies) {
            if (data.hitCount >= DROPDASH_MAX_HITS) break;

            data.hitEntities.add(enemy.getUUID());
            data.hitCount++;

            // Determinar stun (BYPASS I-FRAMES)
            int stunTicks;
            if (data.hitCount == 1) {
                stunTicks = DROPDASH_STUN_FIRST_TICKS;
                data.hasStunnedInitially = true;
            } else if (data.hitCount == 3) {
                stunTicks = data.hasStunnedInitially ?
                        DROPDASH_STUN_THIRD_TICKS : DROPDASH_STUN_THIRD_NO_INITIAL_TICKS;
            } else {
                stunTicks = DROPDASH_STUN_FIRST_TICKS;
            }

            // Aplicar stun (bypasses I-frames)
            applyStunBypassIframes(enemy, stunTicks);

            // Reponer Dodge Meter
            replenishDodgeHP(puid, DODGE_METER_REPLENISH_PER_HIT);

            // Rebote
            Vec3 awayFromEnemy = player.position().subtract(enemy.position()).normalize();
            Vec3 bounce = awayFromEnemy.scale(DROPDASH_BOUNCE_FORCE).add(0, 0.5, 0);
            player.setDeltaMovement(bounce);
            player.hurtMarked = true;

            // Efectos
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, enemy.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.2F);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        enemy.getX(), enemy.getY() + 1.0, enemy.getZ(),
                        15, 0.3, 0.5, 0.3, 0.1);
            }

            // Si alcanzó 3 golpes, terminar
            if (data.hitCount >= DROPDASH_MAX_HITS) {
                endDropdash(player, data, true);
                return;
            }
        }

        // Si toca el suelo, empezar spin
        if (player.onGround()) {
            data.phase = DropdashPhase.SPINNING;
            data.ticksInPhase = 0;

            Vec3 look = player.getLookAngle().normalize();
            Vec3 spin = new Vec3(look.x * DROPDASH_SPIN_SPEED, 0, look.z * DROPDASH_SPIN_SPEED);
            player.setDeltaMovement(spin);
            player.hurtMarked = true;

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
            }
        }

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && data.ticksInPhase % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    3, 0.3, 0.2, 0.3, 0.02);
        }
    }

    private static void handleDropdashSpinning(ServerPlayer player, DropdashData data) {
        UUID puid = player.getUUID();

        // Mantener velocidad de spin
        if (player.onGround()) {
            Vec3 look = player.getLookAngle().normalize();
            Vec3 current = player.getDeltaMovement();
            double currentSpeed = Math.sqrt(current.x * current.x + current.z * current.z);

            if (currentSpeed < DROPDASH_SPIN_SPEED * 0.5) {
                Vec3 spin = new Vec3(look.x * DROPDASH_SPIN_SPEED, current.y,
                        look.z * DROPDASH_SPIN_SPEED);
                player.setDeltaMovement(spin);
                player.hurtMarked = true;
            }
        }

        // Invulnerabilidad
        player.invulnerableTime = 10;

        // Detectar impactos durante spin
        AABB hitBox = player.getBoundingBox().inflate(0.8);
        List<LivingEntity> enemies = player.level().getEntitiesOfClass(LivingEntity.class, hitBox,
                e -> e != player && e.isAlive() && isExecutioner(e) &&
                        !data.hitEntities.contains(e.getUUID()));

        for (LivingEntity enemy : enemies) {
            if (data.hitCount >= DROPDASH_MAX_HITS) break;

            data.hitEntities.add(enemy.getUUID());
            data.hitCount++;

            int stunTicks;
            if (data.hitCount == 1) {
                stunTicks = DROPDASH_STUN_FIRST_TICKS;
                data.hasStunnedInitially = true;
            } else if (data.hitCount == 3) {
                stunTicks = data.hasStunnedInitially ?
                        DROPDASH_STUN_THIRD_TICKS : DROPDASH_STUN_THIRD_NO_INITIAL_TICKS;
            } else {
                stunTicks = DROPDASH_STUN_FIRST_TICKS;
            }

            applyStunBypassIframes(enemy, stunTicks);
            replenishDodgeHP(puid, DODGE_METER_REPLENISH_PER_HIT);

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, enemy.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.2F);
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        enemy.getX(), enemy.getY() + 1.0, enemy.getZ(),
                        15, 0.3, 0.5, 0.3, 0.1);
            }

            if (data.hitCount >= DROPDASH_MAX_HITS) {
                endDropdash(player, data, true);
                return;
            }
        }

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && data.ticksInPhase % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    player.getX(), player.getY() + 0.2, player.getZ(),
                    2, 0.2, 0.1, 0.2, 0.01);
        }

        // Terminar después de 3 segundos o si no está en el suelo
        if (data.ticksInPhase > 60 || !player.onGround()) {
            endDropdash(player, data, data.hitCount > 0);
        }
    }

    private static void endDropdash(ServerPlayer player, DropdashData data, boolean hitSomething) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        long cooldown = hitSomething ? DROPDASH_COOLDOWN_HIT_MS : DROPDASH_COOLDOWN_MISS_MS;
        dropdashLastUsed.put(puid, now);

        syncCooldown(player, "sonic_drop", cooldown, now);

        activeDropdash.remove(puid);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    15, 0.4, 0.2, 0.4, 0.05);
        }
    }

    // ============ LÓGICA DE PEELOUT ============

    private static void tickPeelout(ServerPlayer player, PeeloutState state) {
        state.incrementTicks();

        switch (state.getPhase()) {
            case CHARGING -> handlePeeloutCharging(player, state);
            case DASHING -> handlePeeloutDashing(player, state);
        }
    }

    private static void handlePeeloutCharging(ServerPlayer player, PeeloutState state) {
        // Mantener quieto (pero permitir salto)
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0, motion.y, 0);
        player.hurtMarked = true;

        // Partículas crecientes
        if (player.level() instanceof ServerLevel serverLevel && state.getTicksInPhase() % 5 == 0) {
            int particleCount = (int)(state.getPhaseProgress() * 30);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    particleCount, 0.4, 0.5, 0.4, 0.1);
        }

        // Sonido a mitad de carga
        if (state.getTicksInPhase() == PEELOUT_CHARGE_TICKS / 2) {
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
            }
        }

        // Transición a DASHING
        if (state.isPhaseComplete()) {
            state.setPhase(PeeloutState.Phase.DASHING);

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.5F);
                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        player.getX(), player.getY(), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void handlePeeloutDashing(ServerPlayer player, PeeloutState state) {
        UUID puid = player.getUUID();

        // Aplicar velocidad
        Vec3 look = player.getLookAngle().normalize();
        Vec3 dash = new Vec3(look.x * PEELOUT_DASH_SPEED, 0, look.z * PEELOUT_DASH_SPEED);
        player.setDeltaMovement(dash);
        player.hurtMarked = true;

        // Detectar jugadores para recoger
        AABB scanArea = player.getBoundingBox().inflate(PEELOUT_PICKUP_RADIUS);
        List<ServerPlayer> nearbyPlayers = player.level().getEntitiesOfClass(ServerPlayer.class, scanArea,
                p -> p != player && p.isAlive() && canBeCarried(p));

        for (ServerPlayer nearby : nearbyPlayers) {
            // Si ya está cargando a alguien, soltar al anterior
            if (state.isCarryingPlayer()) {
                UUID previousCarried = state.getCarriedPlayerUUID();
                ServerPlayer previous = findPlayerByUUID(previousCarried);
                if (previous != null) {
                    stopCarrying(previousCarried);
                }
            }

            // Recoger al nuevo
            pickupPlayer(player, state, nearby);
            break;
        }

        // Sincronizar jugador cargado
        if (state.isCarryingPlayer()) {
            ServerPlayer carried = findPlayerByUUID(state.getCarriedPlayerUUID());
            if (carried != null && carried.isAlive()) {
                syncCarriedPlayerPosition(player, carried);
            } else {
                state.releaseCarriedPlayer();
                if (carried != null) {
                    stopCarrying(carried.getUUID());
                }
            }
        }

        // Partículas
        if (player.level() instanceof ServerLevel serverLevel && state.getTicksInPhase() % 2 == 0) {
            Vec3 behind = look.scale(-0.5);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + behind.x, player.getY() + 0.3, player.getZ() + behind.z,
                    3, 0.2, 0.1, 0.2, 0.02);
        }

        // Terminar
        if (state.isPhaseComplete()) {
            endPeelout(player, state);
        }
    }

    private static void pickupPlayer(ServerPlayer carrier, PeeloutState state, ServerPlayer toPickup) {
        UUID toPickupUUID = toPickup.getUUID();

        if (startCarrying(carrier, toPickup)) {
            state.setCarriedPlayer(toPickupUUID);

            if (carrier.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, toPickup.blockPosition(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.2F);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        toPickup.getX(), toPickup.getY() + 1.0, toPickup.getZ(),
                        10, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    private static void endPeelout(ServerPlayer player, PeeloutState state) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        boolean wasCarrying = state.isCarryingPlayer();
        if (wasCarrying) {
            UUID carriedUUID = state.getCarriedPlayerUUID();
            ServerPlayer carried = findPlayerByUUID(carriedUUID);
            if (carried != null) {
                stopCarrying(carriedUUID);
            }
        }

        long cooldown = wasCarrying ? PEELOUT_COOLDOWN_WITH_CARRY_MS : PEELOUT_COOLDOWN_NO_CARRY_MS;
        peeloutLastUsed.put(puid, now);

        syncCooldown(player, "sonic_peel", cooldown, now);

        activePeelouts.remove(puid);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 0.8F);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    20, 0.5, 0.3, 0.5, 0.05);
        }
    }

    // ============ CARRY SYSTEM ============

    private static boolean startCarrying(ServerPlayer carrier, ServerPlayer carried) {
        UUID carrierUUID = carrier.getUUID();
        UUID carriedUUID = carried.getUUID();

        if (carriedPlayers.containsKey(carriedUUID)) return false;
        if (!carried.isAlive()) return false;

        carriedPlayers.put(carriedUUID, carrierUUID);

        carried.sendSystemMessage(Component.literal(
                "§b⚡ Sonic te está cargando! Presiona ESPACIO para saltar."
        ));

        return true;
    }

    private static void stopCarrying(UUID carriedUUID) {
        carriedPlayers.remove(carriedUUID);
    }

    private static void syncCarriedPlayerPosition(ServerPlayer carrier, ServerPlayer carried) {
        Vec3 carrierPos = carrier.position();
        Vec3 carrierLook = carrier.getLookAngle();

        Vec3 offset = carrierLook.scale(-0.5).add(0, 0.5, 0);
        Vec3 targetPos = carrierPos.add(offset);

        carried.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        carried.setDeltaMovement(carrier.getDeltaMovement());
        carried.hurtMarked = true;
    }

    private static boolean canBeCarried(ServerPlayer player) {
        PlayerDefineSuvivor def = PlayerRegistry.get(player);
        if (def == null) return false;

        PlayerTypeOM type = def.getType();
        return type != PlayerTypeOM.EGGMAN && type != PlayerTypeOM.X2011;
    }

    // ============ SISTEMA DE STUN ============

    /**
     * Aplica stun que BYPASS I-FRAMES (aumenta stun existente)
     */
    private static void applyStunBypassIframes(LivingEntity target, int ticks) {
        if (target instanceof Mob mob) {
            mob.setNoAi(true);

            // Si ya está stuneado, EXTENDER el stun
            Long existingStunEnd = stunSchedule.get(mob.getUUID());
            long now = System.currentTimeMillis();
            long newStunEnd = now + (ticks * 50L);

            if (existingStunEnd != null && existingStunEnd > now) {
                // Extender el stun actual
                newStunEnd = Math.max(existingStunEnd, newStunEnd);
            }

            stunSchedule.put(mob.getUUID(), newStunEnd);
        }

        // Remover efecto existente de slowness y aplicar nuevo
        target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false, true
        ));
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
            var pt = PlayerRegistry.getPlayerType(sp);
            return pt == PlayerTypeOM.X2011;
        }
        return false;
    }

    private static boolean isLastSurvivor(ServerPlayer player) {
        long survivorCount = player.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> {
                    PlayerDefineSuvivor def = PlayerRegistry.get(p);
                    return def != null && def.getType() != PlayerTypeOM.X2011 && p.isAlive();
                })
                .count();

        return survivorCount == 1;
    }

    private static ServerPlayer findPlayerByUUID(UUID uuid) {
        for (ServerPlayer sp : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (sp.getUUID().equals(uuid)) return sp;
        }
        return null;
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

    private static void syncDodgeMeterToClient(UUID playerUUID) {
        ServerPlayer player = findPlayerByUUID(playerUUID);
        if (player != null) {
            float currentDodgeHP = getDodgeHP(playerUUID);
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SonicSyncPacket(playerUUID, currentDodgeHP)
            );
        }
    }

    public static boolean isDropdashActive(UUID playerUUID) {
        return activeDropdash.containsKey(playerUUID);
    }

    public static boolean isPeeloutActive(UUID playerUUID) {
        return activePeelouts.containsKey(playerUUID);
    }

    public static PeeloutState getPeeloutState(UUID playerUUID) {
        return activePeelouts.get(playerUUID);
    }

    public static boolean isBeingCarried(UUID playerUUID) {
        return carriedPlayers.containsKey(playerUUID);
    }

    public static UUID getCarrier(UUID carriedUUID) {
        return carriedPlayers.get(carriedUUID);
    }

    public static long getDropdashCooldownRemaining(UUID playerUUID) {
        Long last = dropdashLastUsed.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (DROPDASH_COOLDOWN_MISS_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    public static long getPeeloutCooldownRemaining(UUID playerUUID) {
        Long last = peeloutLastUsed.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (PEELOUT_COOLDOWN_NO_CARRY_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    public static void cleanup(UUID playerUUID) {
        dropdashLastUsed.remove(playerUUID);
        activeDropdash.remove(playerUUID);
        peeloutLastUsed.remove(playerUUID);
        activePeelouts.remove(playerUUID);
        dropdashUsedAt.remove(playerUUID);
        carriedPlayers.remove(playerUUID);
        carriedPlayers.values().removeIf(carrierUUID -> carrierUUID.equals(playerUUID));
        dodgeHP.remove(playerUUID);
    }

    private enum DropdashPhase {
        JUMPING,
        DIVING,
        SPINNING
    }

    private static class DropdashData {
        public DropdashPhase phase;
        public int ticksInPhase;
        public int hitCount;
        public boolean hasStunnedInitially;
        public final Set<UUID> hitEntities;
        public final long startedAt;

        public DropdashData() {
            this.phase = DropdashPhase.JUMPING;
            this.ticksInPhase = 0;
            this.hitCount = 0;
            this.hasStunnedInitially = false;
            this.hitEntities = new HashSet<>();
            this.startedAt = System.currentTimeMillis();
        }
    }

    public static class PeeloutState {
        public enum Phase {
            CHARGING,
            DASHING
        }

        private final UUID ownerUUID;
        private Phase phase;
        private int ticksInPhase;
        private final long startedAt;
        private UUID carriedPlayerUUID;

        public PeeloutState(UUID ownerUUID) {
            this.ownerUUID = ownerUUID;
            this.phase = Phase.CHARGING;
            this.ticksInPhase = 0;
            this.startedAt = System.currentTimeMillis();
            this.carriedPlayerUUID = null;
        }

        public UUID getOwnerUUID() { return ownerUUID; }
        public Phase getPhase() { return phase; }
        public void setPhase(Phase phase) { this.phase = phase; this.ticksInPhase = 0; }
        public int getTicksInPhase() { return ticksInPhase; }
        public void incrementTicks() { this.ticksInPhase++; }
        public long getStartedAt() { return startedAt; }
        public UUID getCarriedPlayerUUID() { return carriedPlayerUUID; }
        public void setCarriedPlayer(UUID playerUUID) { this.carriedPlayerUUID = playerUUID; }
        public boolean isCarryingPlayer() { return carriedPlayerUUID != null; }
        public void releaseCarriedPlayer() { this.carriedPlayerUUID = null; }

        public float getPhaseProgress() {
            int maxTicks = phase == Phase.CHARGING ? PEELOUT_CHARGE_TICKS : PEELOUT_DASH_TICKS;
            return Math.min(1.0F, (float) ticksInPhase / maxTicks);
        }

        public boolean isPhaseComplete() {
            int maxTicks = phase == Phase.CHARGING ? PEELOUT_CHARGE_TICKS : PEELOUT_DASH_TICKS;
            return ticksInPhase >= maxTicks;
        }

        public float getRemainingSeconds() {
            int maxTicks = phase == Phase.CHARGING ? PEELOUT_CHARGE_TICKS : PEELOUT_DASH_TICKS;
            int remaining = Math.max(0, maxTicks - ticksInPhase);
            return remaining / 20.0F;
        }
    }

    // ============ ENUM: MOVESET (para UI) ============

    public enum SonicMoveSet {
        DROPDASH("sonic_drop", "drop", KeyType.SECONDARY, "Dropdash"),
        PEELOUT("sonic_peel", "peel", KeyType.PRIMARY, "Peelout");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        SonicMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() { return id; }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/sonic/" + textureName + ".png");
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