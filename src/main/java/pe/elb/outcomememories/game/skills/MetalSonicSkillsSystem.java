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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import pe.elb.outcomememories.client.input.KeyBindings;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.LMSSystem;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.CooldownSyncPacket;
import pe.elb.outcomememories.net.skills.metalsonic.MetalSonicSyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MetalSonicSkillsSystem {

    private static final long CHARGE_COOLDOWN_MS = 25_000L;
    private static final long REPAIR_COOLDOWN_MS = 25_000L;

    private static final float DAMAGE_RESISTANCE = 0.5F;

    private static final int CHARGE_WINDUP_TICKS = 20;
    private static final double CHARGE_SPEED = 2.5D;
    private static final int CHARGE_SELF_DAMAGE_INTERVAL_TICKS = 5;
    private static final float CHARGE_SELF_DAMAGE_PER_TICK = 4.0F;
    private static final float CHARGE_TOTAL_DAMAGE = 40.0F;
    private static final float CHARGE_LOW_HEALTH_THRESHOLD = 0.3F;
    private static final float CHARGE_WALL_DAMAGE_NORMAL = 39.0F;
    private static final float CHARGE_WALL_DAMAGE_LMS = 15.0F;
    private static final int CHARGE_WALL_STUN_TICKS = 20 * 5;
    private static final int CHARGE_NORMAL_STUN_TICKS = 20 * 3;
    private static final int CHARGE_WALL_BOUNCE_STUN_TICKS = 20 * 4;
    private static final int CHARGE_MAX_DURATION_TICKS = 20 * 7;

    private static final float REPAIR_HEAL_AMOUNT = 7.0F;
    private static final int REPAIR_HEAL_INTERVAL_TICKS = 40;
    private static final float REPAIR_MAX_HP = 150.0F;
    private static final int REPAIR_MAX_TICKS = 200;

    private static final double PROXIMITY_WARNING_MAX_DISTANCE = 30.0D;
    private static final double PROXIMITY_WARNING_MIN_DISTANCE = 5.0D;

    private static final Map<UUID, Long> chargeLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> repairLastUsed = new ConcurrentHashMap<>();

    private static final Map<UUID, ChargeData> activeCharges = new ConcurrentHashMap<>();
    private static final Map<UUID, RepairData> activeRepairs = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> accumulatedSelfDamage = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> stunSchedule = new ConcurrentHashMap<>();
    private static final Set<UUID> processingDamage = ConcurrentHashMap.newKeySet();

    public static boolean tryUseCharge(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        Long last = chargeLastUsed.get(puid);
        if (last != null && (now - last) < CHARGE_COOLDOWN_MS) {
            return false;
        }

        ChargeData data = new ChargeData(now);
        activeCharges.put(puid, data);
        accumulatedSelfDamage.put(puid, 0.0F);

        chargeLastUsed.put(puid, now);
        syncCooldown(player, "metalsonic_charge", CHARGE_COOLDOWN_MS, now);

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.8F);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    10, 0.3, 0.5, 0.3, 0.1);
        }

        return true;
    }

    public static boolean tryUseRepair(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        if (player.getHealth() >= REPAIR_MAX_HP) {
            player.sendSystemMessage(Component.literal("§c¡HP máximo alcanzado!"));
            return false;
        }

        Long last = repairLastUsed.get(puid);
        if (last != null && (now - last) < REPAIR_COOLDOWN_MS) {
            return false;
        }

        RepairData data = new RepairData(now);
        activeRepairs.put(puid, data);

        repairLastUsed.put(puid, now);
        syncCooldown(player, "metalsonic_repair", REPAIR_COOLDOWN_MS, now);

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5F, 1.5F);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    15, 0.3, 0.5, 0.3, 0.1);
        }

        player.sendSystemMessage(Component.literal("§a⚙ Self Repair iniciado..."));

        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        tickCharge(player);
        tickRepair(player);
        tickProximityWarning(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickStuns();
    }

    private static void tickCharge(ServerPlayer player) {
        UUID puid = player.getUUID();
        ChargeData data = activeCharges.get(puid);
        if (data == null) return;

        data.ticksActive++;

        if (data.phase == ChargePhase.WINDUP) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;

            if (player.level() instanceof ServerLevel serverLevel) {
                if (data.ticksActive % 5 == 0) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            5, 0.2, 0.3, 0.2, 0.05);
                }

                if (data.ticksActive % 10 == 0) {
                    serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5F, 1.5F);
                }
            }

            if (data.ticksActive >= CHARGE_WINDUP_TICKS) {
                data.phase = ChargePhase.CHARGING;

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.5F);
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                            player.getX(), player.getY(), player.getZ(),
                            1, 0.0, 0.0, 0.0, 0.0);
                }
            }

        } else if (data.phase == ChargePhase.CHARGING) {
            if (data.ticksActive >= CHARGE_MAX_DURATION_TICKS) {
                endCharge(player, data, true);
                player.sendSystemMessage(Component.literal("§c⚡ ¡Destructive Charge agotado!"));
                return;
            }

            Vec3 look = player.getLookAngle().normalize();
            Vec3 currentVel = player.getDeltaMovement();
            Vec3 charge = new Vec3(look.x * CHARGE_SPEED, currentVel.y, look.z * CHARGE_SPEED);
            player.setDeltaMovement(charge);
            player.hurtMarked = true;

            if (data.carriedExecutioner == null) {
                AABB hitbox = player.getBoundingBox().inflate(1.5);
                List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, hitbox,
                        e -> e != player && e.isAlive() && isExecutioner(e));

                if (!targets.isEmpty()) {
                    LivingEntity exe = targets.get(0);
                    data.carriedExecutioner = exe.getUUID();

                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(null, exe.blockPosition(),
                                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
                        serverLevel.sendParticles(ParticleTypes.CRIT,
                                exe.getX(), exe.getY() + 1.0, exe.getZ(),
                                20, 0.3, 0.5, 0.3, 0.1);
                    }

                    player.sendSystemMessage(Component.literal("§c⚡ Executioner capturado!"));
                }
            }

            if (data.carriedExecutioner != null) {
                LivingEntity exe = findEntityByUUID(data.carriedExecutioner);
                if (exe != null && exe.isAlive()) {
                    Vec3 targetPos = player.position();
                    exe.teleportTo(targetPos.x, targetPos.y, targetPos.z);
                    exe.setDeltaMovement(player.getDeltaMovement());
                    exe.hurtMarked = true;

                    if (data.ticksActive % CHARGE_SELF_DAMAGE_INTERVAL_TICKS == 0) {
                        float damageToApply = CHARGE_SELF_DAMAGE_PER_TICK;

                        if (player.getHealth() / player.getMaxHealth() < CHARGE_LOW_HEALTH_THRESHOLD) {
                            damageToApply *= 0.5F;
                        }

                        if (LMSSystem.isLMSActive()) {
                            damageToApply *= 0.3F;
                        }

                        player.hurt(player.damageSources().generic(), damageToApply);

                        float accumulated = accumulatedSelfDamage.getOrDefault(puid, 0.0F);
                        accumulated += damageToApply;
                        accumulatedSelfDamage.put(puid, accumulated);

                        if (accumulated >= CHARGE_TOTAL_DAMAGE) {
                            endCharge(player, data, false);
                            return;
                        }
                    }
                } else {
                    data.carriedExecutioner = null;
                }
            }

            if (isAgainstWall(player)) {
                if (data.carriedExecutioner != null) {
                    handleWallSmash(player, data);
                } else {
                    handleWallBounce(player, data);
                }
            }

            if (player.level() instanceof ServerLevel serverLevel && data.ticksActive % 2 == 0) {
                Vec3 behind = look.scale(-0.5);
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        player.getX() + behind.x, player.getY() + 0.3, player.getZ() + behind.z,
                        3, 0.2, 0.1, 0.2, 0.02);
            }
        }
    }

    private static void handleWallSmash(ServerPlayer player, ChargeData data) {
        UUID puid = player.getUUID();
        LivingEntity exe = findEntityByUUID(data.carriedExecutioner);

        if (exe != null) {
            float wallDamage = LMSSystem.isLMSActive() ? CHARGE_WALL_DAMAGE_LMS : CHARGE_WALL_DAMAGE_NORMAL;
            player.hurt(player.damageSources().generic(), wallDamage);

            applyStun(exe, CHARGE_WALL_STUN_TICKS);

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.8F);
                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        5, 0.5, 0.5, 0.5, 0.0);
            }

            player.sendSystemMessage(Component.literal("§c💥 ¡Executioner aplastado contra la pared!"));
        }

        activeCharges.remove(puid);
        accumulatedSelfDamage.remove(puid);
    }

    private static void handleWallBounce(ServerPlayer player, ChargeData data) {
        UUID puid = player.getUUID();

        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(vel.scale(-0.5).add(0, 0.3, 0));
        player.hurtMarked = true;

        applyStun(player, CHARGE_WALL_BOUNCE_STUN_TICKS);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    15, 0.4, 0.2, 0.4, 0.05);
        }

        activeCharges.remove(puid);
        accumulatedSelfDamage.remove(puid);
    }

    private static void endCharge(ServerPlayer player, ChargeData data, boolean timeout) {
        UUID puid = player.getUUID();

        if (data.carriedExecutioner != null) {
            LivingEntity exe = findEntityByUUID(data.carriedExecutioner);
            if (exe != null) {
                applyStun(exe, CHARGE_NORMAL_STUN_TICKS);

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, exe.blockPosition(),
                            SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            exe.getX(), exe.getY() + 1.0, exe.getZ(),
                            10, 0.3, 0.5, 0.3, 0.05);
                }
            }
        }

        activeCharges.remove(puid);
        accumulatedSelfDamage.remove(puid);

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    private static void tickRepair(ServerPlayer player) {
        UUID puid = player.getUUID();
        RepairData data = activeRepairs.get(puid);
        if (data == null) return;

        data.ticksActive++;

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;

        if (data.ticksActive % REPAIR_HEAL_INTERVAL_TICKS == 0) {
            float currentHealth = player.getHealth();
            if (currentHealth < REPAIR_MAX_HP) {
                float newHealth = Math.min(REPAIR_MAX_HP, currentHealth + REPAIR_HEAL_AMOUNT);
                player.setHealth(newHealth);

                data.totalHealed += REPAIR_HEAL_AMOUNT;

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HEART,
                            player.getX(), player.getY() + 1.5, player.getZ(),
                            3, 0.3, 0.3, 0.3, 0.05);
                    serverLevel.playSound(null, player.blockPosition(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3F, 1.5F);
                }
            }
        }

        if (player.level() instanceof ServerLevel serverLevel && data.ticksActive % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    5, 0.3, 0.5, 0.3, 0.05);
        }

        if (player.getHealth() >= REPAIR_MAX_HP || data.totalHealed >= 70.0F || data.ticksActive >= REPAIR_MAX_TICKS) {
            activeRepairs.remove(puid);

            player.sendSystemMessage(Component.literal("§a⚙ Self Repair completado!"));

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.5F);
            }
        }
    }

    private static void tickProximityWarning(ServerPlayer player) {
        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);
        if (type != PlayerTypeOM.METAL_SONIC) return;

        List<ServerPlayer> executioners = player.level().getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(PROXIMITY_WARNING_MAX_DISTANCE),
                p -> p != player && isExecutioner(p));

        if (executioners.isEmpty()) return;

        ServerPlayer closestExe = null;
        double closestDist = Double.MAX_VALUE;

        for (ServerPlayer exe : executioners) {
            double dist = player.distanceTo(exe);
            if (dist < closestDist) {
                closestDist = dist;
                closestExe = exe;
            }
        }

        if (closestExe != null && closestDist <= PROXIMITY_WARNING_MAX_DISTANCE
                && closestDist >= PROXIMITY_WARNING_MIN_DISTANCE) {

            float healthPercent = player.getHealth() / player.getMaxHealth();
            float pitch = 1.0F + (1.0F - healthPercent) * 0.5F;

            float distancePercent = (float) (closestDist / PROXIMITY_WARNING_MAX_DISTANCE);
            int interval = Math.max(5, (int) (distancePercent * 40));

            if (player.tickCount % interval == 0) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new MetalSonicSyncPacket(player.getUUID(),
                                MetalSonicSyncPacket.SyncType.PROXIMITY_WARNING, pitch)
                );
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);
        if (type != PlayerTypeOM.METAL_SONIC) return;

        UUID puid = player.getUUID();

        if (processingDamage.contains(puid)) {
            return;
        }

        if (activeCharges.containsKey(puid)) {
            ChargeData data = activeCharges.get(puid);
            if (data.phase == ChargePhase.WINDUP) {
                activeCharges.remove(puid);

                long reducedCooldown = CHARGE_COOLDOWN_MS / 2;
                chargeLastUsed.put(puid, System.currentTimeMillis() - (CHARGE_COOLDOWN_MS - reducedCooldown));

                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));

                player.sendSystemMessage(Component.literal("§c⚡ Destructive Charge cancelado! Cooldown reducido."));

                return;
            }
        }

        if (activeRepairs.containsKey(puid)) {
            activeRepairs.remove(puid);
            player.sendSystemMessage(Component.literal("§c⚙ Self Repair interrumpido!"));
            return;
        }

        float originalDamage = event.getAmount();
        float reducedDamage = originalDamage * (1.0F - DAMAGE_RESISTANCE);

        event.setCanceled(true);

        processingDamage.add(puid);

        try {
            player.hurt(event.getSource(), reducedDamage);
        } finally {
            processingDamage.remove(puid);
        }
    }

    private static void applyStun(LivingEntity target, int ticks) {
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            stunSchedule.put(mob.getUUID(), System.currentTimeMillis() + (ticks * 50L));
        }

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false, true));
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

    private static boolean isExecutioner(LivingEntity e) {
        if (e instanceof ServerPlayer sp) {
            var pt = PlayerRegistry.getPlayerType(sp);
            return pt == PlayerTypeOM.X2011;
        }
        return false;
    }

    private static boolean isAgainstWall(ServerPlayer player) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(look.scale(0.8));

        net.minecraft.world.level.ClipContext context = new net.minecraft.world.level.ClipContext(
                start,
                end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        );

        net.minecraft.world.phys.BlockHitResult result = player.level().clip(context);

        if (result.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.core.Vec3i normalVec = result.getDirection().getNormal();
            Vec3 hitNormal = new Vec3(normalVec.getX(), normalVec.getY(), normalVec.getZ());

            double dotProduct = look.dot(hitNormal);

            return dotProduct < -0.7;
        }

        return false;
    }

    private static LivingEntity findEntityByUUID(UUID uuid) {
        for (net.minecraft.server.level.ServerLevel level : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity le) return le;
        }
        return null;
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
        chargeLastUsed.remove(playerUUID);
        repairLastUsed.remove(playerUUID);
        activeCharges.remove(playerUUID);
        activeRepairs.remove(playerUUID);
        accumulatedSelfDamage.remove(playerUUID);
        processingDamage.remove(playerUUID);
    }

    private enum ChargePhase {
        WINDUP,
        CHARGING
    }

    private static class ChargeData {
        public final long startedAt;
        public int ticksActive;
        public ChargePhase phase;
        public UUID carriedExecutioner;

        public ChargeData(long startedAt) {
            this.startedAt = startedAt;
            this.ticksActive = 0;
            this.phase = ChargePhase.WINDUP;
            this.carriedExecutioner = null;
        }
    }

    private static class RepairData {
        public final long startedAt;
        public int ticksActive;
        public float totalHealed;

        public RepairData(long startedAt) {
            this.startedAt = startedAt;
            this.ticksActive = 0;
            this.totalHealed = 0.0F;
        }
    }

    public enum MetalSonicMoveSet {
        CHARGE("metalsonic_charge", "charge", KeyType.SECONDARY, "Destructive Charge"),
        REPAIR("metalsonic_repair", "repair", KeyType.PRIMARY, "Self Repair");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        MetalSonicMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() { return id; }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/metalsonic/" + textureName + ".png");
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