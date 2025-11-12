package pe.elb.outcomememories.game.skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import pe.elb.outcomememories.client.input.KeyBindings;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.CooldownSyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema unificado de habilidades de X2011 (Executioner)
 * 
 * Habilidades:
 * - Attack (M1): Ataque básico (42 daño)
 * - Charge (Q): Dash con grab/choke
 * - Invisibility (E): Invisibilidad con teleport
 * - God's Trickery (X): Clones y confusión
 * 
 * Sistemas:
 * - Control de ataques e interacciones durante estados especiales
 * - Detección de colisiones para grab
 * - Sistema de escape para víctimas
 */
@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class X2011SkillsSystem {

    // ============ CONSTANTES - ATTACK ============
    private static final long ATTACK_COOLDOWN_MS = 1_000L; // 1 segundo
    private static final float ATTACK_DAMAGE_NORMAL = 42.0F;
    private static final float ATTACK_DAMAGE_RAGE = 50.0F;

    // ============ CONSTANTES - CHARGE ============
    private static final long CHARGE_COOLDOWN_MISS_MS = 30_000L; // 30s si falla
    private static final long CHARGE_COOLDOWN_LAND_MS = 35_000L; // 35s si conecta
    private static final int CHARGE_FLASH_TICKS = 20; // 1 segundo de flashes
    private static final double CHARGE_SPEED = 2.5D;
    private static final double CHARGE_DETECTION_RANGE = 1.5D;
    private static final int GRAB_DAMAGE_PER_SECOND = 2;
    private static final long GRAB_DAMAGE_INTERVAL_MS = 1_000L;
    private static final float GRAB_ESCAPE_THRESHOLD = 100.0F;
    private static final float GRAB_KILL_HP_THRESHOLD = 15.0F;

    // ============ CONSTANTES - INVISIBILITY ============
    private static final long INVIS_DURATION_MS = 20_000L; // 20 segundos
    private static final long INVIS_COOLDOWN_MS = 10_000L;
    private static final double INVIS_HIGHLIGHT_RADIUS = 15.0D;
    private static final double INVIS_TELEPORT_RADIUS = 8.0D;
    private static final long INVIS_TELEPORT_ENDLAG_MS = 2_000L;
    private static final double INVIS_GRAVITY_REDUCTION = 0.6D;

    // ============ CONSTANTES - GOD'S TRICKERY ============
    private static final long TRICK_COOLDOWN_MS = 44_000L; // 44 segundos
    private static final double TRICK_RANGE = 60.0D;
    private static final int TRICK_REAL_DAMAGE = 25;
    private static final int TRICK_INVERT_DURATION = 20 * 8; // 8 segundos
    private static final int TRICK_VISION_DISTORT_DURATION = 20 * 8;

    // ============ ESTADO - ATTACK ============
    private static final Map<UUID, Long> lastAttackAt = new ConcurrentHashMap<>();
    private static final Random random = new Random();

    // ============ ESTADO - CHARGE ============
    private static final Map<UUID, Long> lastChargeAt = new ConcurrentHashMap<>();
    private static final Map<UUID, ChargeData> activeCharges = new ConcurrentHashMap<>();
    private static final Map<UUID, GrabData> activeGrabs = new ConcurrentHashMap<>();

    // ============ ESTADO - INVISIBILITY ============
    private static final Map<UUID, InvisData> activeInvis = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastInvisAt = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> immobilizedUntil = new ConcurrentHashMap<>();

    // ============ ESTADO - GOD'S TRICKERY ============
    private static final Map<UUID, Long> lastTrickAt = new ConcurrentHashMap<>();
    private static final Map<UUID, TrickData> activeTricks = new ConcurrentHashMap<>();

    // ============ ATTACK - MÉTODOS PÚBLICOS ============

    /**
     * Maneja el ataque básico de X2011 (cancelado si está en estado especial)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(attacker);
        if (type != PlayerTypeOM.X2011) return;

        UUID attackerUUID = attacker.getUUID();
        
        // Verificar estado bloqueante
        String blockReason = getAttackBlockReason(attackerUUID, attacker);
        if (blockReason != null) {
            event.setCanceled(true);
            attacker.displayClientMessage(Component.literal(blockReason), true);
            
            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, attacker.blockPosition(),
                    SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.2F, 0.8F);
            }
            return;
        }

        // Cooldown de ataque
        long now = System.currentTimeMillis();
        Long last = lastAttackAt.get(attackerUUID);
        if (last != null && (now - last) < ATTACK_COOLDOWN_MS) {
            event.setCanceled(true);
            return;
        }

        lastAttackAt.put(attackerUUID, now);

        if (event.getTarget() instanceof LivingEntity target) {
            event.setCanceled(true);

            // Determinar daño
            float damage = ATTACK_DAMAGE_NORMAL;
            // TODO: if (hasRageActive(attacker)) damage = ATTACK_DAMAGE_RAGE;

            // Elegir animación aleatoria
            AttackAnimation anim = random.nextBoolean() 
                ? AttackAnimation.DOUBLE_SWIPE 
                : AttackAnimation.RIGHT_HOOK;

            // Aplicar daño
            DamageSource source = attacker.damageSources().playerAttack(attacker);
            boolean hurt = target.hurt(source, damage);

            if (hurt && attacker.level() instanceof ServerLevel serverLevel) {
                if (anim == AttackAnimation.DOUBLE_SWIPE) {
                    serverLevel.playSound(null, target.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.9F);
                    serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        12, 0.5, 0.5, 0.5, 0.1);
                } else {
                    serverLevel.playSound(null, target.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        8, 0.3, 0.3, 0.3, 0.05);
                }

                target.knockback(0.4,
                    attacker.getX() - target.getX(),
                    attacker.getZ() - target.getZ());
            }
        }
    }

    // ============ CHARGE - MÉTODOS PÚBLICOS ============

    /**
     * Intenta usar Charge
     */
    public static boolean tryUseCharge(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.X2011) return false;

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = lastChargeAt.get(puid);
        if (last != null && (now - last) < CHARGE_COOLDOWN_MISS_MS) {
            return false;
        }

        // Iniciar charge
        ChargeData data = new ChargeData();
        activeCharges.put(puid, data);

        return true;
    }

    /**
     * Enviar progreso de escape (llamado desde el cliente)
     */
    public static boolean submitEscapeProgress(UUID victimUUID, float progressToAdd) {
        if (progressToAdd < 0 || progressToAdd > 20.0F) return false;

        for (Map.Entry<UUID, GrabData> entry : activeGrabs.entrySet()) {
            GrabData data = entry.getValue();
            if (data.victimUUID.equals(victimUUID)) {
                data.escapeProgress += progressToAdd;

                if (data.escapeProgress >= GRAB_ESCAPE_THRESHOLD) {
                    ServerPlayer attacker = findPlayerByUUID(entry.getKey());
                    ServerPlayer victim = findPlayerByUUID(victimUUID);
                    
                    if (attacker != null && victim != null) {
                        releaseGrab(attacker, data, false);
                    }
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Forzar liberación (por stun externo)
     */
    public static void forceReleaseGrab(UUID attackerUUID) {
        GrabData data = activeGrabs.get(attackerUUID);
        if (data != null) {
            ServerPlayer attacker = findPlayerByUUID(attackerUUID);
            if (attacker != null) {
                releaseGrab(attacker, data, false);
            }
        }
    }

    // ============ INVISIBILITY - MÉTODOS PÚBLICOS ============

    /**
     * Toggle invisibilidad
     */
    public static boolean tryToggleInvisibility(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        var def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.X2011) return false;

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Si está activo, desactivar
        if (activeInvis.containsKey(puid)) {
            InvisData data = activeInvis.get(puid);

            // Verificar que no esté cerca de un survivor
            List<ServerPlayer> nearby = collectSurvivorsExcluding(player, INVIS_TELEPORT_RADIUS);
            if (!nearby.isEmpty()) return false;

            deactivateInvis(player, data, false);
            return true;
        }

        // Verificar cooldown
        Long last = lastInvisAt.get(puid);
        if (last != null && (now - last) < INVIS_COOLDOWN_MS) {
            return false;
        }

        // Activar invisibilidad
        InvisData data = new InvisData(now + INVIS_DURATION_MS);
        activeInvis.put(puid, data);

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
            (int)(INVIS_DURATION_MS / 50L), 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
            (int)(INVIS_DURATION_MS / 50L), 1, false, false, true));

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.8F, 1.0F);
        }

        return true;
    }

    /**
     * Teleport a un survivor cercano
     */
    public static boolean tryTeleportToSurvivor(ServerPlayer player, int targetIndex) {
        if (player == null || player.level().isClientSide) return false;

        UUID puid = player.getUUID();
        InvisData data = activeInvis.get(puid);
        if (data == null) return false;

        List<ServerPlayer> nearby = collectSurvivorsExcluding(player, INVIS_TELEPORT_RADIUS);
        if (targetIndex < 0 || targetIndex >= nearby.size()) return false;

        ServerPlayer target = nearby.get(targetIndex);
        if (!target.isAlive()) return false;

        // Teleportar detrás
        Vec3 behind = target.getLookAngle().scale(-1.5).normalize();
        player.teleportTo(
            target.getX() + behind.x,
            target.getY(),
            target.getZ() + behind.z
        );

        // Aplicar endlag
        long now = System.currentTimeMillis();
        immobilizedUntil.put(puid, now + INVIS_TELEPORT_ENDLAG_MS);

        deactivateInvis(player, data, true);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        return true;
    }

    // ============ GOD'S TRICKERY - MÉTODOS PÚBLICOS ============

    /**
     * Intenta usar God's Trickery
     */
    public static boolean tryUseGodsTrickery(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.X2011) return false;

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = lastTrickAt.get(puid);
        if (last != null && (now - last) < TRICK_COOLDOWN_MS) {
            return false;
        }

        // Buscar survivors
        List<ServerPlayer> survivors = collectSurvivorsExcluding(player, TRICK_RANGE);
        if (survivors.isEmpty()) return false;

        // Elegir objetivo real + clones
        Collections.shuffle(survivors);
        ServerPlayer realTarget = survivors.get(0);
        TrickColor realColor = TrickColor.values()[random.nextInt(TrickColor.values().length)];

        TrickData trickData = new TrickData(puid, realTarget.getUUID(), realColor);

        // Asignar clones
        for (int i = 1; i < Math.min(4, survivors.size()); i++) {
            ServerPlayer cloneTarget = survivors.get(i);
            TrickColor cloneColor = TrickColor.values()[random.nextInt(TrickColor.values().length)];

            CloneTarget ct = new CloneTarget(cloneTarget.getUUID(), cloneColor, cloneTarget.position());
            trickData.cloneTargets.put(cloneTarget.getUUID(), ct);
        }

        activeTricks.put(puid, trickData);

        // Volverse invisible
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));

        // Teleportar detrás del real
        Vec3 behind = realTarget.getLookAngle().scale(-2.0).normalize();
        player.teleportTo(
            realTarget.getX() + behind.x,
            realTarget.getY(),
            realTarget.getZ() + behind.z
        );

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + 1.0, player.getZ(),
                30, 0.5, 1.0, 0.5, 0.1);
        }

        // Glowing a targets
        realTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, true, true));
        for (CloneTarget ct : trickData.cloneTargets.values()) {
            ServerPlayer cloneTarget = findPlayerByUUID(ct.survivorUUID);
            if (cloneTarget != null) {
                cloneTarget.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, true, true));
            }
        }

        lastTrickAt.put(puid, now);
        syncCooldown(player, "exe_god", TRICK_COOLDOWN_MS, now);

        return true;
    }

    // ============ CONTROL DE INTERACCIONES ============

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);
        if (type != PlayerTypeOM.X2011) return;

        if (isInNonAttackableState(player.getUUID(), player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);
        if (type != PlayerTypeOM.X2011) return;

        if (isInNonAttackableState(player.getUUID(), player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);
        if (type != PlayerTypeOM.X2011) return;

        UUID playerUUID = player.getUUID();

        if (isInvisActive(player) || isTrickActive(playerUUID)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Prevenir salto durante invisibilidad
        if (activeInvis.containsKey(player.getUUID())) {
            player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        }
    }

    // ============ TICK DEL SERVIDOR ============

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = System.currentTimeMillis();
        immobilizedUntil.entrySet().removeIf(e -> now >= e.getValue());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        UUID puid = player.getUUID();

        // Tick de charge
        ChargeData chargeData = activeCharges.get(puid);
        if (chargeData != null) {
            handleCharge(player, chargeData);
        }

        // Tick de grab
        GrabData grabData = activeGrabs.get(puid);
        if (grabData != null) {
            handleGrab(player, grabData);
        }

        // Tick de invisibilidad
        InvisData invisData = activeInvis.get(puid);
        if (invisData != null) {
            handleInvisibility(player, invisData);
        }

        // Tick de God's Trickery
        TrickData trickData = activeTricks.get(puid);
        if (trickData != null) {
            handleGodsTrickery(player, trickData);
        }
    }

    // ============ LÓGICA DE CHARGE ============

    private static void handleCharge(ServerPlayer player, ChargeData data) {
        UUID puid = player.getUUID();
        data.ticksInPhase++;

        switch (data.phase) {
            case FLASHING -> {
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;

                if (data.ticksInPhase % 10 == 0 && data.flashCount < 2) {
                    data.flashCount++;

                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 2.0F);
                        serverLevel.sendParticles(ParticleTypes.FLAME,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            15, 0.5, 0.8, 0.5, 0.05);
                    }
                }

                if (data.ticksInPhase >= CHARGE_FLASH_TICKS) {
                    data.phase = ChargePhase.DASHING;
                    data.ticksInPhase = 0;
                    data.dashDirection = player.getLookAngle().normalize();

                    player.setDeltaMovement(data.dashDirection.scale(CHARGE_SPEED).add(0, 0.5, 0));
                    player.hurtMarked = true;

                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 0.8F);
                    }
                }
            }

            case DASHING -> {
                if (data.dashDirection != null) {
                    player.setDeltaMovement(data.dashDirection.scale(CHARGE_SPEED).add(0, -0.1, 0));
                    player.hurtMarked = true;
                }

                // Detectar colisión
                AABB hitbox = player.getBoundingBox().inflate(CHARGE_DETECTION_RANGE);
                List<LivingEntity> hits = player.level().getEntitiesOfClass(
                    LivingEntity.class, hitbox,
                    e -> e != player && e.isAlive() && e instanceof ServerPlayer
                );

                for (LivingEntity hit : hits) {
                    if (hit instanceof ServerPlayer victim) {
                        PlayerRegistry.PlayerDefinition victimDef = PlayerRegistry.get(victim);
                        if (victimDef != null && victimDef.getType() == PlayerTypeOM.X2011) {
                            continue;
                        }

                        grabVictim(player, victim);
                        activeCharges.remove(puid);

                        long now = System.currentTimeMillis();
                        lastChargeAt.put(puid, now);
                        syncCooldown(player, "exe_charge", CHARGE_COOLDOWN_LAND_MS, now);
                        return;
                    }
                }

                if (player.onGround() || data.ticksInPhase >= 60) {
                    activeCharges.remove(puid);
                    long now = System.currentTimeMillis();
                    lastChargeAt.put(puid, now);
                    syncCooldown(player, "exe_charge", CHARGE_COOLDOWN_MISS_MS, now);
                }
            }
        }
    }

    private static void grabVictim(ServerPlayer attacker, ServerPlayer victim) {
        UUID attackerUUID = attacker.getUUID();
        UUID victimUUID = victim.getUUID();

        GrabData grabData = new GrabData(attackerUUID, victimUUID);
        activeGrabs.put(attackerUUID, grabData);

        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;

        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, victim.blockPosition(),
                SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.0F, 0.8F);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                victim.getX(), victim.getY() + 1.0, victim.getZ(),
                20, 0.5, 0.8, 0.5, 0.05);
        }
    }

    private static void handleGrab(ServerPlayer attacker, GrabData data) {
        ServerPlayer victim = findPlayerByUUID(data.victimUUID);

        if (victim == null || !victim.isAlive()) {
            releaseGrab(attacker, data, false);
            return;
        }

        long now = System.currentTimeMillis();

        // Mantener en posición
        victim.teleportTo(attacker.getX(), attacker.getY(), attacker.getZ());
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;

        // Daño periódico
        if (now >= data.nextDamageTick) {
            DamageSource source = attacker.damageSources().playerAttack(attacker);
            victim.hurt(source, GRAB_DAMAGE_PER_SECOND);

            data.nextDamageTick = now + GRAB_DAMAGE_INTERVAL_MS;

            // Ejecutar si HP <= 15
            if (victim.getHealth() <= GRAB_KILL_HP_THRESHOLD) {
                victim.hurt(source, victim.getHealth());

                if (attacker.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, victim.blockPosition(),
                        SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 1.0F, 1.0F);
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        victim.getX(), victim.getY() + 1.0, victim.getZ(),
                        1, 0.0, 0.0, 0.0, 0.0);
                }

                releaseGrab(attacker, data, true);
                return;
            }

            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    5, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    private static void releaseGrab(ServerPlayer attacker, GrabData data, boolean wasKill) {
        activeGrabs.remove(attacker.getUUID());

        if (!wasKill) {
            long now = System.currentTimeMillis();
            lastChargeAt.put(attacker.getUUID(), 
                now - CHARGE_COOLDOWN_MISS_MS + CHARGE_COOLDOWN_LAND_MS);
            syncCooldown(attacker, "exe_charge", CHARGE_COOLDOWN_LAND_MS, now);
        }
    }

    // ============ LÓGICA DE INVISIBILITY ============

    private static void handleInvisibility(ServerPlayer player, InvisData data) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar expiración
        if (now >= data.until) {
            deactivateInvis(player, data, false);
            return;
        }

        // Actualizar lista de survivors
        if (player.tickCount % 10 == 0) {
            List<ServerPlayer> nearby = collectSurvivorsExcluding(player, INVIS_HIGHLIGHT_RADIUS);
            data.nearbySurvivors.clear();
            
            for (ServerPlayer sp : nearby) {
                data.nearbySurvivors.add(sp.getUUID());
                sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, 25, 0, false, false, false));
            }
        }

        // Reducir gravedad
        if (!player.onGround()) {
            Vec3 vel = player.getDeltaMovement();
            player.setDeltaMovement(vel.x, vel.y * INVIS_GRAVITY_REDUCTION, vel.z);
            player.hurtMarked = true;
        }
    }

    private static void deactivateInvis(ServerPlayer player, InvisData data, boolean withEndlag) {
        UUID puid = player.getUUID();

        activeInvis.remove(puid);
        long now = System.currentTimeMillis();
        lastInvisAt.put(puid, now);

        syncCooldown(player, "exe_invis", INVIS_COOLDOWN_MS, now);

        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6F, 0.8F);
        }
    }

    // ============ LÓGICA DE GOD'S TRICKERY ============

    private static void handleGodsTrickery(ServerPlayer player, TrickData data) {
        data.ticksActive++;

        checkRealTarget(data);
        checkCloneTargets(data);

        if (data.ticksActive >= 60) {
            endTrick(player, data);
            activeTricks.remove(player.getUUID());
        }
    }

    private static void checkRealTarget(TrickData data) {
        ServerPlayer target = findPlayerByUUID(data.realTargetUUID);
        ServerPlayer executioner = findPlayerByUUID(data.executionerUUID);

        if (target == null || executioner == null) return;

        boolean shouldDamage = false;

        switch (data.realColor) {
            case BLUE, DISTORTED -> {
                Vec3 vel = target.getDeltaMovement();
                if (vel.horizontalDistance() > 0.01) shouldDamage = true;
            }
            case BLACK -> {
                if (target.onGround() || target.getDeltaMovement().y <= 0) {
                    shouldDamage = true;
                }
            }
        }

        if (shouldDamage && data.ticksActive % 20 == 0) {
            DamageSource source = executioner.damageSources().playerAttack(executioner);
            target.hurt(source, TRICK_REAL_DAMAGE);

            if (executioner.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    10, 0.5, 0.5, 0.5, 0.1);
            }
        }
    }

    private static void checkCloneTargets(TrickData data) {
        for (CloneTarget ct : data.cloneTargets.values()) {
            ServerPlayer target = findPlayerByUUID(ct.survivorUUID);
            if (target == null) continue;

            boolean shouldApplyEffect = false;

            switch (ct.color) {
                case ORANGE -> {
                    Vec3 currentPos = target.position();
                    if (currentPos.distanceTo(ct.lastPosition) < 0.01) shouldApplyEffect = true;
                    ct.lastPosition = currentPos;
                }
                case BLUE, DISTORTED -> {
                    Vec3 vel = target.getDeltaMovement();
                    if (vel.horizontalDistance() > 0.01) shouldApplyEffect = true;
                }
                case BLACK -> {
                    if (!target.onGround() && target.getDeltaMovement().y > 0) {
                        ct.hasJumped = true;
                    } else if (!ct.hasJumped) {
                        shouldApplyEffect = true;
                    }
                }
            }

            if (shouldApplyEffect && data.ticksActive % 20 == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 
                    TRICK_INVERT_DURATION, 2, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 
                    TRICK_VISION_DISTORT_DURATION, 1, false, true, true));

                if (target.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.WITCH,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        15, 0.5, 0.8, 0.5, 0.1);
                }
            }
        }
    }

    private static void endTrick(ServerPlayer executioner, TrickData data) {
        executioner.removeEffect(MobEffects.INVISIBILITY);

        if (executioner.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, executioner.blockPosition(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    // ============ UTILIDADES ============

    private static String getAttackBlockReason(UUID playerUUID, ServerPlayer player) {
        if (isGrabbing(playerUUID)) {
            return "§c✗ Ya estás agarrando a alguien";
        }
        if (isCharging(playerUUID)) {
            return "§c✗ No puedes atacar durante Charge";
        }
        if (isTrickActive(playerUUID)) {
            return "§c✗ No puedes atacar durante God's Trickery";
        }
        if (isImmobilized(player)) {
            return "§c✗ Inmobilizado después de teleport";
        }
        if (isInvisActive(player)) {
            return "§c✗ No puedes atacar mientras estás invisible";
        }
        return null;
    }

    private static boolean isInNonAttackableState(UUID playerUUID, ServerPlayer player) {
        return isInvisActive(player) || isImmobilized(player) || 
               isTrickActive(playerUUID) || isCharging(playerUUID) || isGrabbing(playerUUID);
    }

    private static List<ServerPlayer> collectSurvivorsExcluding(ServerPlayer self, double radius) {
        if (self == null) return Collections.emptyList();
        
        List<ServerPlayer> res = new ArrayList<>();
        Level level = self.level();
        AABB area = self.getBoundingBox().inflate(radius);
        List<Player> found = level.getEntitiesOfClass(Player.class, area, 
            e -> e != self && e.isAlive());
        
        for (Player p : found) {
            if (!(p instanceof ServerPlayer sp)) continue;
            var def = PlayerRegistry.get(sp);
            if (def == null) continue;
            if (def.getType() != PlayerTypeOM.X2011) res.add(sp);
        }
        return res;
    }

    private static ServerPlayer findPlayerByUUID(UUID uuid) {
        if (uuid == null) return null;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.getUUID().equals(uuid)) return sp;
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

    public static boolean canExecutionerAttack(ServerPlayer player) {
        if (player == null) return false;
        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);
        if (type != PlayerTypeOM.X2011) return true;
        return !isInNonAttackableState(player.getUUID(), player);
    }

    public static boolean hasActiveGrab(UUID victimUUID) {
        return activeGrabs.values().stream()
            .anyMatch(data -> data.victimUUID.equals(victimUUID));
    }

    public static boolean isCharging(UUID playerUUID) {
        return activeCharges.containsKey(playerUUID);
    }

    public static boolean isGrabbing(UUID playerUUID) {
        return activeGrabs.containsKey(playerUUID);
    }

    public static boolean isInvisActive(ServerPlayer player) {
        return player != null && activeInvis.containsKey(player.getUUID());
    }

    public static boolean isImmobilized(ServerPlayer player) {
        if (player == null) return false;
        Long until = immobilizedUntil.get(player.getUUID());
        return until != null && System.currentTimeMillis() < until;
    }

    public static boolean isTrickActive(UUID playerUUID) {
        return activeTricks.containsKey(playerUUID);
    }

    public static List<UUID> getNearbySurvivors(UUID executionerUuid) {
        InvisData data = activeInvis.get(executionerUuid);
        return data != null ? new ArrayList<>(data.nearbySurvivors) : Collections.emptyList();
    }

    // ============ GETTERS DE COOLDOWN ============

    public static long getAttackCooldownRemaining(UUID playerUUID) {
        Long last = lastAttackAt.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (ATTACK_COOLDOWN_MS - (System.currentTimeMillis() - last)));
    }

    public static long getChargeCooldownRemaining(UUID playerUUID) {
        Long last = lastChargeAt.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (CHARGE_COOLDOWN_MISS_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    public static long getInvisCooldownRemaining(UUID playerUUID) {
        Long last = lastInvisAt.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (INVIS_COOLDOWN_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    public static long getTrickCooldownRemaining(UUID playerUUID) {
        Long last = lastTrickAt.get(playerUUID);
        if (last == null) return 0;
        return Math.max(0, (TRICK_COOLDOWN_MS - (System.currentTimeMillis() - last)) / 1000);
    }

    // ============ LIMPIEZA ============

    public static void cleanup(UUID playerUUID) {
        lastAttackAt.remove(playerUUID);
        lastChargeAt.remove(playerUUID);
        activeCharges.remove(playerUUID);
        activeGrabs.remove(playerUUID);
        activeInvis.remove(playerUUID);
        lastInvisAt.remove(playerUUID);
        immobilizedUntil.remove(playerUUID);
        lastTrickAt.remove(playerUUID);
        activeTricks.remove(playerUUID);
    }

    // ============ CLASES INTERNAS ============

    private enum AttackAnimation {
        DOUBLE_SWIPE,
        RIGHT_HOOK
    }

    private enum ChargePhase {
        FLASHING,
        DASHING
    }

    private static class ChargeData {
        public ChargePhase phase;
        public int ticksInPhase;
        public int flashCount;
        public Vec3 dashDirection;

        public ChargeData() {
            this.phase = ChargePhase.FLASHING;
            this.ticksInPhase = 0;
            this.flashCount = 0;
            this.dashDirection = null;
        }
    }

    private static class GrabData {
        public final UUID attackerUUID;
        public final UUID victimUUID;
        public float escapeProgress;
        public long nextDamageTick;
        public final long grabbedAt;

        public GrabData(UUID attackerUUID, UUID victimUUID) {
            this.attackerUUID = attackerUUID;
            this.victimUUID = victimUUID;
            this.escapeProgress = 0.0F;
            this.nextDamageTick = System.currentTimeMillis() + GRAB_DAMAGE_INTERVAL_MS;
            this.grabbedAt = System.currentTimeMillis();
        }
    }

    private static class InvisData {
        public final long until;
        public final List<UUID> nearbySurvivors;
        public boolean hasRevealed;

        public InvisData(long until) {
            this.until = until;
            this.nearbySurvivors = new ArrayList<>();
            this.hasRevealed = false;
        }
    }

    private enum TrickColor {
        ORANGE,
        BLUE,
        BLACK,
        DISTORTED
    }

    private static class TrickData {
        public final UUID executionerUUID;
        public final Map<UUID, CloneTarget> cloneTargets;
        public final UUID realTargetUUID;
        public final TrickColor realColor;
        public int ticksActive;
        public final long startedAt;

        public TrickData(UUID executionerUUID, UUID realTargetUUID, TrickColor realColor) {
            this.executionerUUID = executionerUUID;
            this.cloneTargets = new HashMap<>();
            this.realTargetUUID = realTargetUUID;
            this.realColor = realColor;
            this.ticksActive = 0;
            this.startedAt = System.currentTimeMillis();
        }
    }

    private static class CloneTarget {
        public final UUID survivorUUID;
        public final TrickColor color;
        public Vec3 lastPosition;
        public boolean hasJumped;

        public CloneTarget(UUID survivorUUID, TrickColor color, Vec3 initialPos) {
            this.survivorUUID = survivorUUID;
            this.color = color;
            this.lastPosition = initialPos;
            this.hasJumped = false;
        }
    }

    // ============ ENUM: MOVESET (para UI) ============

    public enum ExeMoveSet {
        INVISIBILITY("exe_invis", "inv", KeyType.SECONDARY, "Invisibility"),
        CHARGE("exe_charge", "charge", KeyType.PRIMARY, "Charge"),
        GODS_TRICKERY("exe_god", "god", KeyType.SPECIAL, "God's Trickery");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        ExeMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/x2011/" + textureName + ".png");
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