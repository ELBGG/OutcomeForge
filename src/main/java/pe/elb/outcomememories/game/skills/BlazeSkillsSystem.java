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
import pe.elb.outcomememories.client.input.KeyBindings;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.CooldownSyncPacket;
import pe.elb.outcomememories.net.skills.blaze.BlazeSyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlazeSkillsSystem {

    private static final long ROUNDHOUSE_COOLDOWN_MS = 24_000L;
    private static final long SOL_FLAME_COOLDOWN_MS = 24_000L;

    private static final float SOL_METER_MAX = 100.0F;
    private static final float SOL_METER_HIT_GAIN = 25.0F;
    private static final float SOL_METER_ROUNDHOUSE_COST = 20.0F;
    private static final float SOL_METER_MISS_DRAIN = 20.0F;
    private static final float SOL_METER_BOOST_DRAIN_PER_TICK = 2.0F;
    private static final float SOL_METER_FLAME_DRAIN_PER_TICK = 5.0F;
    private static final float SOL_METER_JAVELIN_THRESHOLD = 35.0F;

    private static final double ROUNDHOUSE_RANGE = 3.0D;
    private static final double ROUNDHOUSE_KNOCKBACK = 7.0D;
    private static final int ROUNDHOUSE_STUN_TICKS = 20 * 2;
    private static final int ROUNDHOUSE_RAGDOLL_TICKS = 20 * 1;

    private static final double SOL_BOOST_SPEED = 1.5D;
    private static final int SOL_BOOST_CHARGE_TICKS = 10;

    private static final double SOL_FLAME_RANGE = 15.0D;
    private static final double SOL_FLAME_WIDTH = 2.0D;
    private static final int SOL_FLAME_DURATION_TICKS = 20 * 3;
    private static final int SOL_FLAME_INTERVAL_TICKS = 5;

    private static final double JAVELIN_SPEED = 2.0D;
    private static final double JAVELIN_RANGE = 20.0D;

    private static final int BURN_DURATION_TICKS = 20 * 4;
    private static final int BURN_SLOW_AMPLIFIER = 1;

    private static final Map<UUID, Long> roundhouseLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> solFlameLastUsed = new ConcurrentHashMap<>();

    private static final Map<UUID, Float> solMeter = new ConcurrentHashMap<>();
    private static final Map<UUID, RoundhouseData> activeRoundhouse = new ConcurrentHashMap<>();
    private static final Map<UUID, SolBoostData> activeSolBoost = new ConcurrentHashMap<>();
    private static final Map<UUID, SolFlameData> activeSolFlame = new ConcurrentHashMap<>();
    private static final Map<UUID, JavelinProjectile> activeJavelins = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> burnedEntities = new ConcurrentHashMap<>();

    public static boolean tryUseRoundhouse(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        Long last = roundhouseLastUsed.get(puid);
        if (last != null && (now - last) < ROUNDHOUSE_COOLDOWN_MS) {
            return false;
        }

        if (player.onGround()) {
            return executeGroundRoundhouse(player);
        } else {
            return executeAirSolBoost(player);
        }
    }

    private static boolean executeGroundRoundhouse(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        float currentMeter = getSolMeter(puid);
        if (currentMeter < SOL_METER_ROUNDHOUSE_COST) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cSol Meter insuficiente!"));
            return false;
        }

        consumeSolMeter(puid, SOL_METER_ROUNDHOUSE_COST);

        Vec3 look = player.getLookAngle().normalize();
        Vec3 launch = look.scale(1.2D).add(0, 0.3D, 0);
        player.setDeltaMovement(launch);
        player.hurtMarked = true;

        RoundhouseData data = new RoundhouseData(now);
        activeRoundhouse.put(puid, data);

        roundhouseLastUsed.put(puid, now);
        syncCooldown(player, "blaze_roundhouse", ROUNDHOUSE_COOLDOWN_MS, now);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    15, 0.3, 0.5, 0.3, 0.1);
        }

        return true;
    }

    private static boolean executeAirSolBoost(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        float currentMeter = getSolMeter(puid);
        if (currentMeter <= 0) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cSol Meter vacío!"));
            return false;
        }

        SolBoostData data = new SolBoostData(now);
        activeSolBoost.put(puid, data);

        roundhouseLastUsed.put(puid, now);
        syncCooldown(player, "blaze_roundhouse", ROUNDHOUSE_COOLDOWN_MS, now);

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.5F);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.3, 0.5, 0.3, 0.15);
        }

        return true;
    }

    public static boolean tryUseSolFlame(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        Long last = solFlameLastUsed.get(puid);
        if (last != null && (now - last) < SOL_FLAME_COOLDOWN_MS) {
            return false;
        }

        float currentMeter = getSolMeter(puid);

        if (currentMeter < SOL_METER_JAVELIN_THRESHOLD) {
            return executeBurningJavelin(player);
        } else {
            return executeSolFlame(player);
        }
    }

    private static boolean executeSolFlame(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        SolFlameData data = new SolFlameData(now);
        activeSolFlame.put(puid, data);

        solFlameLastUsed.put(puid, now);
        syncCooldown(player, "blaze_flame", SOL_FLAME_COOLDOWN_MS, now);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1.5, player.getZ(),
                    30, 0.3, 0.3, 0.3, 0.2);
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6🔥 Sol Flame activado!"));

        return true;
    }

    private static boolean executeBurningJavelin(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        JavelinProjectile javelin = new JavelinProjectile(puid, start, look, now);
        activeJavelins.put(UUID.randomUUID(), javelin);

        solFlameLastUsed.put(puid, now);
        syncCooldown(player, "blaze_flame", SOL_FLAME_COOLDOWN_MS, now);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.2F);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1.5, player.getZ(),
                    15, 0.2, 0.2, 0.2, 0.1);
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c🔥 Burning Javelin lanzada!"));

        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID puid = player.getUUID();

        tickRoundhouse(player);
        tickSolBoost(player);
        tickSolFlame(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickJavelins();
        tickBurns();
    }

    private static void tickRoundhouse(ServerPlayer player) {
        UUID puid = player.getUUID();
        RoundhouseData data = activeRoundhouse.get(puid);
        if (data == null) return;

        data.ticksActive++;

        AABB hitbox = player.getBoundingBox().inflate(ROUNDHOUSE_RANGE);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, hitbox,
                e -> e != player && e.isAlive() && isExecutioner(e));

        if (!targets.isEmpty()) {
            LivingEntity target = targets.get(0);

            applyStun(target, ROUNDHOUSE_STUN_TICKS);
            applyBurn(target);
            applyKnockback(target, player.getLookAngle().normalize(), ROUNDHOUSE_KNOCKBACK);

            replenishSolMeter(puid, SOL_METER_HIT_GAIN);

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, target.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        20, 0.3, 0.5, 0.3, 0.1);
            }

            activeRoundhouse.remove(puid);
        } else if (player.onGround() || data.ticksActive > 30) {
            if (data.ticksActive > 30) {
                drainSolMeter(puid, SOL_METER_MISS_DRAIN);
                applyRagdoll(player, ROUNDHOUSE_RAGDOLL_TICKS);

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c¡Roundhouse falló!"));
            }

            activeRoundhouse.remove(puid);
        }
    }

    private static void tickSolBoost(ServerPlayer player) {
        UUID puid = player.getUUID();
        SolBoostData data = activeSolBoost.get(puid);
        if (data == null) return;

        data.ticksActive++;

        float currentMeter = getSolMeter(puid);
        if (currentMeter <= 0 || player.onGround()) {
            activeSolBoost.remove(puid);
            return;
        }

        if (data.ticksActive <= SOL_BOOST_CHARGE_TICKS) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;

            if (player.level() instanceof ServerLevel serverLevel && data.ticksActive % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        5, 0.2, 0.2, 0.2, 0.05);
            }
        } else {
            Vec3 look = player.getLookAngle().normalize();
            Vec3 boost = look.scale(SOL_BOOST_SPEED);
            player.setDeltaMovement(boost);
            player.hurtMarked = true;

            drainSolMeter(puid, SOL_METER_BOOST_DRAIN_PER_TICK);

            AABB hitbox = player.getBoundingBox().inflate(1.5);
            List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, hitbox,
                    e -> e != player && e.isAlive() && isExecutioner(e));

            if (!targets.isEmpty()) {
                LivingEntity target = targets.get(0);

                applyStun(target, ROUNDHOUSE_STUN_TICKS);
                replenishSolMeter(puid, SOL_METER_HIT_GAIN);

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, target.blockPosition(),
                            SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.2F);
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            target.getX(), target.getY() + 1.0, target.getZ(),
                            15, 0.3, 0.5, 0.3, 0.1);
                }

                activeSolBoost.remove(puid);
            }

            if (player.level() instanceof ServerLevel serverLevel && data.ticksActive % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        3, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    private static void tickSolFlame(ServerPlayer player) {
        UUID puid = player.getUUID();
        SolFlameData data = activeSolFlame.get(puid);
        if (data == null) return;

        data.ticksActive++;

        float currentMeter = getSolMeter(puid);
        if (currentMeter <= 0 || data.ticksActive > SOL_FLAME_DURATION_TICKS) {
            activeSolFlame.remove(puid);
            
            if (data.ticksActive > SOL_FLAME_DURATION_TICKS && !data.hitTarget) {
                drainSolMeter(puid, 100.0F);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c¡Sol Flame falló completamente!"));
            }
            
            return;
        }

        drainSolMeter(puid, SOL_METER_FLAME_DRAIN_PER_TICK);

        if (data.ticksActive % SOL_FLAME_INTERVAL_TICKS == 0) {
            Vec3 start = player.getEyePosition();
            Vec3 look = player.getLookAngle().normalize();
            Vec3 end = start.add(look.scale(SOL_FLAME_RANGE));

            AABB flameBox = new AABB(start, end).inflate(SOL_FLAME_WIDTH);
            List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, flameBox,
                    e -> e != player && e.isAlive() && isExecutioner(e));

            for (LivingEntity target : targets) {
                applyKnockback(target, look, 0.5D);
                data.hitTarget = true;

                if (data.ticksActive >= SOL_FLAME_DURATION_TICKS - SOL_FLAME_INTERVAL_TICKS) {
                    applyBurn(target);
                }
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                for (double d = 0; d < SOL_FLAME_RANGE; d += 0.5) {
                    Vec3 particlePos = start.add(look.scale(d));
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            particlePos.x, particlePos.y, particlePos.z,
                            3, 0.2, 0.2, 0.2, 0.02);
                }

                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.0F);
            }
        }
    }

    private static void tickJavelins() {
        if (activeJavelins.isEmpty()) return;

        Iterator<Map.Entry<UUID, JavelinProjectile>> it = activeJavelins.entrySet().iterator();
        
        while (it.hasNext()) {
            Map.Entry<UUID, JavelinProjectile> entry = it.next();
            JavelinProjectile javelin = entry.getValue();

            javelin.ticksAlive++;
            javelin.position = javelin.position.add(javelin.velocity.scale(JAVELIN_SPEED / 20.0));

            ServerPlayer owner = findPlayerByUUID(javelin.ownerUUID);
            if (owner == null) {
                it.remove();
                continue;
            }

            AABB hitbox = new AABB(javelin.position, javelin.position).inflate(1.0);
            List<LivingEntity> targets = owner.level().getEntitiesOfClass(LivingEntity.class, hitbox,
                    e -> e.isAlive() && isExecutioner(e));

            if (!targets.isEmpty()) {
                LivingEntity target = targets.get(0);

                applyKnockback(target, javelin.velocity.normalize(), 1.0D);
                applyBurn(target);

                if (owner.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, target.blockPosition(),
                            SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            target.getX(), target.getY() + 1.0, target.getZ(),
                            15, 0.3, 0.5, 0.3, 0.1);
                }

                it.remove();
                continue;
            }

            double traveledDistance = javelin.position.distanceTo(javelin.spawnPosition);
            if (traveledDistance > JAVELIN_RANGE || javelin.ticksAlive > 100) {
                it.remove();
                continue;
            }

            if (owner.level() instanceof ServerLevel serverLevel && javelin.ticksAlive % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        javelin.position.x, javelin.position.y, javelin.position.z,
                        2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    private static void tickBurns() {
        if (burnedEntities.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = burnedEntities.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now >= entry.getValue()) {
                it.remove();
            }
        }
    }

    private static void applyStun(LivingEntity target, int ticks) {
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            scheduleAiReactivation(mob, ticks);
        }

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false, true));
    }

    private static final Map<UUID, Long> stunSchedule = new ConcurrentHashMap<>();

    private static void scheduleAiReactivation(Mob mob, int ticks) {
        stunSchedule.put(mob.getUUID(), System.currentTimeMillis() + (ticks * 50L));
    }

    private static void applyBurn(LivingEntity target) {
        UUID tid = target.getUUID();
        long now = System.currentTimeMillis();

        if (burnedEntities.containsKey(tid)) {
            Long burnEnd = burnedEntities.get(tid);
            if (now < burnEnd) {
                return;
            }
        }

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 
            BURN_DURATION_TICKS, BURN_SLOW_AMPLIFIER, false, false, true));
        
        target.setSecondsOnFire(BURN_DURATION_TICKS / 20);

        burnedEntities.put(tid, now + (BURN_DURATION_TICKS * 50L));
    }

    private static void applyKnockback(LivingEntity target, Vec3 direction, double force) {
        Vec3 knockback = direction.scale(force);
        target.push(knockback.x, knockback.y * 0.5, knockback.z);
        target.hurtMarked = true;
    }

    private static void applyRagdoll(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false, true));
        
        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(vel.x * 0.1, -0.5, vel.z * 0.1);
        player.hurtMarked = true;
    }

    public static float getSolMeter(UUID playerUUID) {
        return solMeter.getOrDefault(playerUUID, 0.0F);
    }

    public static void setSolMeter(UUID playerUUID, float value) {
        float clamped = Math.max(0.0F, Math.min(SOL_METER_MAX, value));
        solMeter.put(playerUUID, clamped);
        syncSolMeterToClient(playerUUID, clamped);
    }

    public static void consumeSolMeter(UUID playerUUID, float amount) {
        float current = getSolMeter(playerUUID);
        setSolMeter(playerUUID, current - amount);
    }

    public static void replenishSolMeter(UUID playerUUID, float amount) {
        float current = getSolMeter(playerUUID);
        setSolMeter(playerUUID, current + amount);
    }

    public static void drainSolMeter(UUID playerUUID, float amount) {
        consumeSolMeter(playerUUID, amount);
    }

    public static void initializeSolMeter(UUID playerUUID) {
        setSolMeter(playerUUID, 0.0F);
    }

    private static void syncSolMeterToClient(UUID playerUUID, float value) {
        ServerPlayer player = findPlayerByUUID(playerUUID);
        if (player != null) {
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new BlazeSyncPacket(playerUUID, value)
            );
        }
    }

    private static boolean isExecutioner(LivingEntity e) {
        if (e instanceof ServerPlayer sp) {
            var pt = PlayerRegistry.getPlayerType(sp);
            return pt == PlayerTypeOM.X2011;
        }
        return false;
    }

    private static ServerPlayer findPlayerByUUID(UUID uuid) {
        for (ServerPlayer sp : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
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

    public static void cleanup(UUID playerUUID) {
        roundhouseLastUsed.remove(playerUUID);
        solFlameLastUsed.remove(playerUUID);
        solMeter.remove(playerUUID);
        activeRoundhouse.remove(playerUUID);
        activeSolBoost.remove(playerUUID);
        activeSolFlame.remove(playerUUID);
    }

    private static class RoundhouseData {
        public final long startedAt;
        public int ticksActive;

        public RoundhouseData(long startedAt) {
            this.startedAt = startedAt;
            this.ticksActive = 0;
        }
    }

    private static class SolBoostData {
        public final long startedAt;
        public int ticksActive;

        public SolBoostData(long startedAt) {
            this.startedAt = startedAt;
            this.ticksActive = 0;
        }
    }

    private static class SolFlameData {
        public final long startedAt;
        public int ticksActive;
        public boolean hitTarget;

        public SolFlameData(long startedAt) {
            this.startedAt = startedAt;
            this.ticksActive = 0;
            this.hitTarget = false;
        }
    }

    private static class JavelinProjectile {
        public final UUID ownerUUID;
        public Vec3 position;
        public final Vec3 spawnPosition;
        public final Vec3 velocity;
        public final long spawnedAt;
        public int ticksAlive;

        public JavelinProjectile(UUID ownerUUID, Vec3 position, Vec3 velocity, long spawnedAt) {
            this.ownerUUID = ownerUUID;
            this.position = position;
            this.spawnPosition = position;
            this.velocity = velocity;
            this.spawnedAt = spawnedAt;
            this.ticksAlive = 0;
        }
    }

    public enum BlazeMoveSet {
        ROUNDHOUSE("blaze_roundhouse", "roundhouse", KeyType.SECONDARY, "Roundhouse Kick / Sol Boost"),
        SOL_FLAME("blaze_flame", "flame", KeyType.PRIMARY, "Sol Flame / Burning Javelin");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        BlazeMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() { return id; }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/blaze/" + textureName + ".png");
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
            return "";
        }

        public String getDisplayName() { return displayName; }

        private enum KeyType {
            PRIMARY,
            SECONDARY,
            SPECIAL
        }
    }
}