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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
import pe.elb.outcomememories.net.skills.eggman.EggmanSyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EggmanSkillsSystem {

    // ============ CONSTANTES - ENERGY SHIELD ============
    private static final long SHIELD_COOLDOWN_SUCCESS_MS = 35_000L; // 35s si se usa correctamente
    private static final long SHIELD_COOLDOWN_FAIL_MS = 25_000L; // 25s si no se golpea
    private static final int SHIELD_DURATION_TICKS = 20 * 3; // 3 segundos
    private static final int SHIELD_STUN_NORMAL_TICKS = 20 * 2; // 2 segundos
    private static final int SHIELD_STUN_LAST_TICKS = 20 * 5; // 5 segundos en últimos 3 min

    // ============ CONSTANTES - JETPACK BOOST ============
    private static final long BOOST_COOLDOWN_MS = 22_000L; // 22 segundos
    private static final int BOOST_DURATION_TICKS = 20 * 6; // 6 segundos
    private static final int BOOST_SPEED_AMPLIFIER = 6;
    private static final double BOOST_INITIAL_IMPULSE = 1.5D;

    // ============ CONSTANTES - DOUBLE JUMP ============
    private static final long DOUBLE_JUMP_COOLDOWN_MS = 10_000L; // 10 segundos
    private static final double DOUBLE_JUMP_STRENGTH = 0.90D;

    // ============ ESTADO - ENERGY SHIELD ============
    private static final Map<UUID, Long> shieldLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, ShieldData> activeShields = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> stunSchedule = new ConcurrentHashMap<>();

    // ============ ESTADO - JETPACK BOOST ============
    private static final Map<UUID, Long> boostLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> boostActiveTicks = new ConcurrentHashMap<>();

    // ============ ESTADO - DOUBLE JUMP ============
    private static final Map<UUID, Long> doubleJumpLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> hasDoubleJumped = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> wasOnGround = new ConcurrentHashMap<>();

    // ============ ENERGY SHIELD - MÉTODOS PÚBLICOS ============

    /**
     * Intenta activar el Energy Shield
     */
    public static boolean tryUseShield(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.EGGMAN) {
            return false;
        }

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = shieldLastUsed.get(puid);
        if (last != null) {
            long elapsed = now - last;
            if (elapsed < SHIELD_COOLDOWN_SUCCESS_MS) {
                return false;
            }
        }

        // Activar escudo
        long expiresAt = now + (SHIELD_DURATION_TICKS * 50L);
        activeShields.put(puid, new ShieldData(now, expiresAt));

        syncCooldown(player, "eggman_shield", SHIELD_COOLDOWN_SUCCESS_MS, now);

        // ✅ Enviar packet de activación del escudo a todos los clientes cercanos
        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new EggmanSyncPacket(puid, EggmanSyncPacket.SyncType.SHIELD_ACTIVE, true, now)
            );
        } catch (Throwable e) {
            System.err.println("[EggmanSkills] Error enviando packet de escudo: " + e.getMessage());
        }

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    30, 0.5, 0.8, 0.5, 0.05);
        }

        return true;
    }

    /**
     * Verifica si el jugador tiene escudo activo
     */
    public static boolean hasShieldActive(UUID playerUUID) {
        return activeShields.containsKey(playerUUID);
    }

    /**
     * Obtiene el tiempo restante del escudo
     */
    public static long getShieldTimeRemaining(UUID playerUUID) {
        ShieldData shield = activeShields.get(playerUUID);
        if (shield == null) return 0L;
        long now = System.currentTimeMillis();
        return Math.max(0L, shield.expiresAt - now);
    }

    /**
     * Obtiene la fase de advertencia del escudo (0.0 a 1.0)
     */
    public static float getWarningPhase(UUID playerUUID) {
        ShieldData shield = activeShields.get(playerUUID);
        if (shield == null) return 0.0F;

        long now = System.currentTimeMillis();
        long timeLeft = shield.expiresAt - now;

        // Si quedan menos de 2 segundos, empezar advertencia
        if (timeLeft < 2000L) {
            return 1.0F - (timeLeft / 2000.0F);
        }

        return 0.0F;
    }

    // ============ JETPACK BOOST - MÉTODOS PÚBLICOS ============

    /**
     * Intenta activar el Jetpack Boost
     */
    public static boolean tryUseBoost(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.EGGMAN) {
            return false;
        }

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar cooldown
        Long last = boostLastUsed.get(puid);
        if (last != null && (now - last) < BOOST_COOLDOWN_MS) {
            return false;
        }

        boostLastUsed.put(puid, now);
        syncCooldown(player, "eggman_boost", BOOST_COOLDOWN_MS, now);

        // Aplicar speed boost
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                BOOST_DURATION_TICKS,
                BOOST_SPEED_AMPLIFIER,
                false, true, true
        ));

        // Impulso inicial hacia adelante
        Vec3 look = player.getLookAngle().normalize();
        Vec3 horizontalDir = new Vec3(look.x, 0, look.z).normalize();
        Vec3 boost = horizontalDir.scale(BOOST_INITIAL_IMPULSE);
        player.setDeltaMovement(player.getDeltaMovement().add(boost));

        try {
            player.hurtMarked = true;
        } catch (Throwable ignored) {}

        boostActiveTicks.put(puid, BOOST_DURATION_TICKS);

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.9F);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    20, 0.3, 0.2, 0.3, 0.1);
        }

        return true;
    }

    /**
     * Verifica si el boost está activo
     */
    public static boolean isBoostActive(UUID playerUUID) {
        Integer ticks = boostActiveTicks.get(playerUUID);
        return ticks != null && ticks > 0;
    }

    // ============ DOUBLE JUMP - MÉTODOS PÚBLICOS ============

    /**
     * Intenta realizar un doble salto
     */
    public static boolean tryUseDoubleJump(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.EGGMAN) {
            return false;
        }

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        // Verificar que no esté en el suelo
        if (player.onGround()) {
            return false;
        }

        // Verificar que no haya usado ya el doble salto
        if (hasDoubleJumped.getOrDefault(puid, false)) {
            return false;
        }

        // Verificar cooldown
        Long last = doubleJumpLastUsed.get(puid);
        if (last != null && (now - last) < DOUBLE_JUMP_COOLDOWN_MS) {
            return false;
        }

        // Realizar doble salto
        Vec3 motion = player.getDeltaMovement();
        double newY = Math.max(motion.y, DOUBLE_JUMP_STRENGTH);
        player.setDeltaMovement(motion.x, newY, motion.z);
        player.hurtMarked = true;

        hasDoubleJumped.put(puid, true);
        doubleJumpLastUsed.put(puid, now);

        // Efectos
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5F, 1.5F);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY(), player.getZ(),
                    15, 0.3, 0.1, 0.3, 0.05);
        }

        return true;
    }

    // ============ EVENTOS ============

    /**
     * Interceptar daño cuando el jugador tiene escudo activo
     */
    @SubscribeEvent
    public static void onPlayerAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        UUID puid = player.getUUID();
        ShieldData shield = activeShields.get(puid);

        if (shield == null || shield.wasHit) return;

        // Cancelar el daño
        event.setCanceled(true);
        shield.wasHit = true;

        // Stunear al atacante
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            stunAttacker(player, attacker);
        }

        // Romper el escudo
        activeShields.remove(puid);
        shieldLastUsed.put(puid, System.currentTimeMillis());

        // ✅ Enviar packet de ruptura del escudo
        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new EggmanSyncPacket(puid, EggmanSyncPacket.SyncType.SHIELD_BROKEN, false, 0L)
            );
        } catch (Throwable e) {
            System.err.println("[EggmanSkills] Error enviando packet de ruptura: " + e.getMessage());
        }

        // Efectos de ruptura
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.5, 0.5, 0.5, 0.1);
        }
    }

    // ============ TICK DEL SERVIDOR ============

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickShields();
        tickStuns();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.EGGMAN) return;

        tickBoost(player);
        tickDoubleJump(player);
    }

    /**
     * Tick de escudos activos
     */
    private static void tickShields() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, ShieldData>> it = activeShields.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, ShieldData> entry = it.next();
            UUID puid = entry.getKey();
            ShieldData shield = entry.getValue();

            ServerPlayer player = findPlayerByUUID(puid);
            if (player == null || !player.isAlive()) {
                it.remove();

                // ✅ Enviar packet de desactivación si el jugador se desconecta/muere
                try {
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.ALL.noArg(),
                            new EggmanSyncPacket(puid, EggmanSyncPacket.SyncType.SHIELD_BROKEN, false, 0L)
                    );
                } catch (Throwable ignored) {}

                continue;
            }

            long timeLeft = shield.expiresAt - now;

            // Advertencias sonoras
            if (timeLeft <= 1000 && timeLeft > 950) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F, 1.8F);
                    serverLevel.sendParticles(ParticleTypes.END_ROD,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            15, 0.4, 0.6, 0.4, 0.05);
                }
            }

            if (timeLeft <= 500 && timeLeft > 450) {
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F, 1.8F);
                }
            }

            // Expirar shield
            if (now >= shield.expiresAt) {
                it.remove();

                // ✅ Enviar packet de expiración
                try {
                    NetworkHandler.CHANNEL.send(
                            PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                            new EggmanSyncPacket(puid, EggmanSyncPacket.SyncType.SHIELD_BROKEN, false, 0L)
                    );
                } catch (Throwable e) {
                    System.err.println("[EggmanSkills] Error enviando packet de expiración: " + e.getMessage());
                }

                // Si no fue golpeado, cooldown reducido
                if (!shield.wasHit) {
                    shieldLastUsed.put(puid, now - (SHIELD_COOLDOWN_SUCCESS_MS - SHIELD_COOLDOWN_FAIL_MS));
                    syncCooldown(player, "eggman_shield", SHIELD_COOLDOWN_FAIL_MS, now);
                }

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
                }
            } else {
                // Partículas del escudo activo
                if (player.level() instanceof ServerLevel serverLevel && player.tickCount % 5 == 0) {
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            2, 0.4, 0.6, 0.4, 0.01);
                }
            }
        }
    }

    /**
     * Tick de mobs stuneados
     */
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

    /**
     * Tick del boost
     */
    private static void tickBoost(ServerPlayer player) {
        UUID puid = player.getUUID();
        Integer ticksLeft = boostActiveTicks.get(puid);

        if (ticksLeft == null || ticksLeft <= 0) {
            boostActiveTicks.remove(puid);
            return;
        }

        // Partículas durante el boost
        if (player.level() instanceof ServerLevel serverLevel) {
            Vec3 behind = player.getLookAngle().scale(-0.5);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    player.getX() + behind.x,
                    player.getY() + 0.2,
                    player.getZ() + behind.z,
                    2, 0.1, 0.05, 0.1, 0.01);

            if (ticksLeft % 10 == 0) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        player.getX() + behind.x,
                        player.getY() + 0.5,
                        player.getZ() + behind.z,
                        5, 0.2, 0.1, 0.2, 0.05);
            }
        }

        // Advertencia sonora (1 segundo restante)
        if (ticksLeft == 20) {
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5F, 1.8F);
            }
        }

        boostActiveTicks.put(puid, ticksLeft - 1);
    }

    /**
     * Tick del doble salto (resetear al tocar suelo)
     */
    private static void tickDoubleJump(ServerPlayer player) {
        UUID puid = player.getUUID();
        boolean currentlyOnGround = player.onGround();
        boolean previouslyOnGround = wasOnGround.getOrDefault(puid, true);

        // Si el jugador acaba de aterrizar, resetear double jump
        if (currentlyOnGround && !previouslyOnGround) {
            hasDoubleJumped.put(puid, false);
        }

        wasOnGround.put(puid, currentlyOnGround);
    }

    // ============ UTILIDADES ============

    private static void stunAttacker(ServerPlayer defender, LivingEntity attacker) {
        // Determinar duración del stun
        int stunTicks = isInLastThreeMinutes(defender.level())
                ? SHIELD_STUN_LAST_TICKS
                : SHIELD_STUN_NORMAL_TICKS;

        // Aplicar stun
        if (attacker instanceof Mob mob) {
            mob.setNoAi(true);
            stunSchedule.put(mob.getUUID(), System.currentTimeMillis() + (stunTicks * 50L));
        }

        // Aplicar slowness extremo
        attacker.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, stunTicks, 10, false, false, true
        ));

        // Efectos visuales
        if (attacker.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, attacker.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.8F);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
                    15, 0.3, 0.5, 0.3, 0.05);
        }
    }

    private static boolean isInLastThreeMinutes(Level lvl) {
        // TODO: Integrar con tu sistema de timer de partida
        return false;
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

    // ============ LIMPIEZA ============

    public static void cleanup(UUID playerUUID) {
        // Limpiar datos del servidor
        shieldLastUsed.remove(playerUUID);
        activeShields.remove(playerUUID);
        boostLastUsed.remove(playerUUID);
        boostActiveTicks.remove(playerUUID);
        doubleJumpLastUsed.remove(playerUUID);
        hasDoubleJumped.remove(playerUUID);
        wasOnGround.remove(playerUUID);

        // ✅ Enviar packet para limpiar en todos los clientes
        try {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new EggmanSyncPacket(playerUUID, EggmanSyncPacket.SyncType.SHIELD_BROKEN, false, 0L)
            );
        } catch (Throwable ignored) {}
    }

    // ============ GETTERS DE COOLDOWN ============

    public static long getShieldCooldownRemaining(UUID playerUUID) {
        Long last = shieldLastUsed.get(playerUUID);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, (SHIELD_COOLDOWN_SUCCESS_MS - elapsed) / 1000);
    }

    public static long getBoostCooldownRemaining(UUID playerUUID) {
        Long last = boostLastUsed.get(playerUUID);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, (BOOST_COOLDOWN_MS - elapsed) / 1000);
    }

    public static long getDoubleJumpCooldownRemaining(UUID playerUUID) {
        Long last = doubleJumpLastUsed.get(playerUUID);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, (DOUBLE_JUMP_COOLDOWN_MS - elapsed) / 1000);
    }

    // ============ CLASE INTERNA: SHIELD DATA ============

    private static class ShieldData {
        public final long activatedAt;
        public final long expiresAt;
        public boolean wasHit = false;

        public ShieldData(long activatedAt, long expiresAt) {
            this.activatedAt = activatedAt;
            this.expiresAt = expiresAt;
        }
    }

    // ============ ENUM: MOVESET (para UI) ============

    public enum EggmanMoveSet {
        BOOST("eggman_boost", "boost", KeyType.SECONDARY, "Jetpack Boost"),
        SHIELD("eggman_shield", "shield", KeyType.PRIMARY, "Energy Shield");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        EggmanMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/eggman/" + textureName + ".png");
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