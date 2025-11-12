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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
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
 * Sistema unificado de habilidades de Cream
 * <p>
 * Habilidades:
 * - Dash (E): Dash rápido con knockback
 * - Heal (Q): Curación AOE o Speed Mode en últimos 3 minutos
 * - Glide (Pasivo): Planeo suave al caer
 */
@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CreamSkillsSystem {

    private static final long DASH_COOLDOWN_MS = 15_000L;
    private static final double DASH_DISTANCE = 3.0D;
    private static final double DASH_KNOCKBACK_EXEC = 5.0D;
    private static final double DASH_KNOCKBACK_PLAYER = DASH_KNOCKBACK_EXEC * 2.0D;
    private static final float DASH_SELF_DAMAGE = 2.0F;
    private static final int DASH_SPEED_BOOST_TICKS = 20 * 4;
    private static final long HEAL_COOLDOWN_MS = 15_000L;
    private static final long HEAL_COOLDOWN_IF_AIR_MS = 5_000L;
    private static final long HEAL_DURATION_MS = 7_000L;
    private static final long HEAL_INTERVAL_MS = 1_000L;
    private static final double HEAL_RADIUS = 4.0D;
    private static final int HEAL_AMOUNT = 5;
    private static final int SPEED_MODE_DURATION_TICKS = 20 * 10;
    private static final int SPEED_MODE_AMPLIFIER = 4;
    private static final double GLIDE_BASE_FALL_MULTIPLIER = 0.55D;
    private static final double GLIDE_FORWARD_ACCEL = 0.05D;
    private static final double GLIDE_MAX_FORWARD_SPEED = 0.9D;
    private static final double GLIDE_MIN_FALL_DISTANCE = 0.8D;
    private static final double GLIDE_MIN_AIR_GAP = 1.2D;

    private static final Map<UUID, Long> dashLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> dashActiveTicks = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> healLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, HealChannelInfo> healActive = new ConcurrentHashMap<>();
    private static final Set<UUID> gliding = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static boolean tryUseDash(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        var def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.CREAM) {
            return false;
        }

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = dashLastUsed.get(puid);
        if (last != null && (now - last) < DASH_COOLDOWN_MS) {
            return false;
        }

        dashLastUsed.put(puid, now);
        syncCooldown(player, "cream_dash", DASH_COOLDOWN_MS, now);

        Vec3 look = player.getLookAngle().normalize();
        Vec3 impulse = new Vec3(look.x * DASH_DISTANCE, 0.6D, look.z * DASH_DISTANCE);
        player.setDeltaMovement(impulse);
        
        try { 
            player.hurtMarked = true; 
        } catch (Throwable ignored) {}

        dashActiveTicks.put(puid, 6);

        Level lvl = player.level();
        if (lvl instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, 
                SoundSource.PLAYERS, 0.9F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, 
                player.getZ(), 6, 0.4, 0.1, 0.4, 0.0);
        }

        return true;
    }

    public static boolean tryUseHeal(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        var def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.CREAM) {
            return false;
        }

        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        Long last = healLastUsed.get(puid);
        if (last != null && (now - last) < HEAL_COOLDOWN_MS) {
            return false;
        }

        if (!player.onGround()) {
            healLastUsed.put(puid, now + HEAL_COOLDOWN_IF_AIR_MS - HEAL_COOLDOWN_MS);
            syncCooldown(player, "cream_heal", HEAL_COOLDOWN_MS, now);
            return false;
        }

        healLastUsed.put(puid, now);

        if (isInLastThreeMinutes(player.level())) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 
                SPEED_MODE_DURATION_TICKS, SPEED_MODE_AMPLIFIER, false, true, true));

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(), 
                    SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.9F, 1.0F);
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, 
                    player.getX(), player.getY() + 0.5, player.getZ(), 16, 0.2, 0.4, 0.2, 0.01);
            }

            syncCooldown(player, "cream_heal", HEAL_COOLDOWN_MS, now);
            return true;
        }

        List<Player> nearbyOthers = player.level().getEntitiesOfClass(Player.class,
            player.getBoundingBox().inflate(HEAL_RADIUS),
            p -> p.isAlive() && !p.getUUID().equals(puid));

        if (nearbyOthers.isEmpty()) {
            try {
                player.heal(HEAL_AMOUNT);
            } catch (Throwable ignored) {}

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0, 
                    player.getZ(), 8, 0.4, 0.6, 0.4, 0.03);
                serverLevel.playSound(null, player.blockPosition(), 
                    SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.0F);
            }

            syncCooldown(player, "cream_heal", HEAL_COOLDOWN_MS, now);
            return true;
        }

        long until = now + HEAL_DURATION_MS;
        long nextHeal = now + HEAL_INTERVAL_MS;
        HealChannelInfo ci = new HealChannelInfo(now, until, nextHeal);

        try {
            player.heal(HEAL_AMOUNT);
            ci.selfHealed = true;
        } catch (Throwable ignored) {}

        healActive.put(puid, ci);

        Level lvl = player.level();
        if (lvl instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.blockPosition(), 
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, 
                player.getX(), player.getY() + 0.5, player.getZ(), 24, 0.5, 0.6, 0.5, 0.02);
        }

        syncCooldown(player, "cream_heal", HEAL_COOLDOWN_MS, now);
        return true;
    }

    private static void cancelHealChannel(UUID puid, String reason) {
        HealChannelInfo ci = healActive.remove(puid);
        if (ci == null) return;
        
        ServerPlayer caster = findPlayerByUUID(puid);
        if (caster != null) {
            try {
                caster.hurtMarked = true;
            } catch (Throwable ignored) {}
        }
    }

    public static boolean startGlide(ServerPlayer player) {
        if (player == null || player.level().isClientSide) return false;

        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def == null || def.getType() != PlayerTypeOM.CREAM) return false;

        if (player.onGround()) return false;
        if (player.getDeltaMovement().y >= 0) return false;
        if (!isHighEnough(player, GLIDE_MIN_AIR_GAP)) return false;
        if (player.fallDistance < GLIDE_MIN_FALL_DISTANCE) return false;

        gliding.add(player.getUUID());

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.3, 0.1, 0.3, 0.01);
        }

        return true;
    }

    public static void stopGlide(ServerPlayer player) {
        if (player == null) return;
        gliding.remove(player.getUUID());
    }

    public static boolean isGliding(ServerPlayer player) {
        return player != null && gliding.contains(player.getUUID());
    }

    private static boolean isHighEnough(ServerPlayer player, double minGap) {
        AABB box = player.getBoundingBox();
        Level level = player.level();
        AABB below = box.move(0, -minGap, 0);
        return level.noCollision(player, below);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickDash();
        tickHeal();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        tickGlide(player);
    }

    private static void tickDash() {
        if (dashActiveTicks.isEmpty()) return;

        Iterator<Map.Entry<UUID, Integer>> it = dashActiveTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            UUID puid = e.getKey();
            int ticksLeft = e.getValue();

            ServerPlayer player = findPlayerByUUID(puid);
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            Vec3 look = player.getLookAngle().normalize();
            AABB area = player.getBoundingBox().expandTowards(look.scale(1.2)).inflate(0.6, 0.6, 0.6);

            List<LivingEntity> hits = player.level().getEntitiesOfClass(LivingEntity.class, area,
                ent -> ent != player && ent.isAlive());

            for (LivingEntity hit : hits) {
                if (isExecutioner(hit)) {
                    applyKnockback(hit, player, DASH_KNOCKBACK_EXEC);
                    player.hurt(player.damageSources().generic(), DASH_SELF_DAMAGE);
                    giveSpeedBoost(player, DASH_SPEED_BOOST_TICKS);

                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(null, hit.blockPosition(), 
                            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.9F);
                        serverLevel.sendParticles(ParticleTypes.CRIT, 
                            hit.getX(), hit.getY() + 1.0, hit.getZ(), 8, 0.3, 0.3, 0.3, 0.03);
                    }
                } else if (hit instanceof Player otherPlayer) {
                    applyKnockback(otherPlayer, player, DASH_KNOCKBACK_PLAYER);
                    player.hurt(player.damageSources().generic(), DASH_SELF_DAMAGE);
                    giveSpeedBoost(player, DASH_SPEED_BOOST_TICKS);

                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(null, otherPlayer.blockPosition(), 
                            SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 1.0F);
                        serverLevel.sendParticles(ParticleTypes.POOF, 
                            otherPlayer.getX(), otherPlayer.getY() + 0.5, otherPlayer.getZ(), 
                            6, 0.3, 0.2, 0.3, 0.02);
                    }
                }
            }

            ticksLeft--;
            if (ticksLeft <= 0) {
                it.remove();
            } else {
                e.setValue(ticksLeft);
            }
        }
    }

    private static void tickHeal() {
        if (healActive.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, HealChannelInfo> e : healActive.entrySet()) {
            UUID puid = e.getKey();
            HealChannelInfo ci = e.getValue();

            if (now >= ci.until) {
                toRemove.add(puid);
                continue;
            }

            ServerPlayer caster = findPlayerByUUID(puid);
            if (caster == null || !caster.isAlive()) {
                toRemove.add(puid);
                continue;
            }

            if (caster.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT, 
                    caster.getX(), caster.getY() + 0.6, caster.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
            }

            if (now >= ci.nextHealAt) {
                List<Player> nearby = caster.level().getEntitiesOfClass(Player.class,
                    caster.getBoundingBox().inflate(HEAL_RADIUS),
                    p -> p.isAlive());

                if (nearby.isEmpty() || (nearby.size() == 1 && nearby.get(0).getUUID().equals(puid))) {
                    toRemove.add(puid);
                    continue;
                }

                for (Player p : nearby) {
                    if (p.getUUID().equals(puid)) continue;
                    try {
                        p.heal(HEAL_AMOUNT);
                    } catch (Throwable ignored) {}
                }

                if (caster.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HEART, 
                        caster.getX(), caster.getY() + 1.0, caster.getZ(), 10, 0.4, 0.6, 0.4, 0.03);
                    serverLevel.playSound(null, caster.blockPosition(), 
                        SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.0F);
                }

                ci.nextHealAt = now + HEAL_INTERVAL_MS;
            }
        }

        for (UUID r : toRemove) {
            healActive.remove(r);
            ServerPlayer caster = findPlayerByUUID(r);
            if (caster != null) {
                try {
                    caster.hurtMarked = true;
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void tickGlide(ServerPlayer player) {
        UUID puid = player.getUUID();
        if (!gliding.contains(puid)) return;

        if (player.onGround() || !player.isAlive()) {
            gliding.remove(puid);
            return;
        }

        Vec3 vel = player.getDeltaMovement();
        Vec3 look = player.getLookAngle();
        Vec3 horizontalDir = new Vec3(look.x, 0, look.z).normalize();

        double newY = vel.y;
        if (vel.y < 0) {
            newY = vel.y * GLIDE_BASE_FALL_MULTIPLIER;
        }

        double newX = vel.x + horizontalDir.x * GLIDE_FORWARD_ACCEL;
        double newZ = vel.z + horizontalDir.z * GLIDE_FORWARD_ACCEL;

        double horizontalSpeed = Math.sqrt(newX * newX + newZ * newZ);
        if (horizontalSpeed > GLIDE_MAX_FORWARD_SPEED) {
            double scale = GLIDE_MAX_FORWARD_SPEED / horizontalSpeed;
            newX *= scale;
            newZ *= scale;
        }

        player.setDeltaMovement(newX, newY, newZ);
        player.hurtMarked = true;

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.5, player.getZ(),
                2, 0.1, 0.05, 0.1, 0.01);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            UUID puid = sp.getUUID();
            if (healActive.containsKey(puid)) {
                cancelHealChannel(puid, "damage");
            }
        }
    }

    private static void applyKnockback(LivingEntity target, ServerPlayer source, double distance) {
        Vec3 dir = new Vec3(target.getX() - source.getX(), 0.0, target.getZ() - source.getZ()).normalize();
        if (Double.isNaN(dir.x) || Double.isNaN(dir.z)) {
            dir = source.getLookAngle().normalize();
        }
        double strength = distance * 0.6D;
        target.push(dir.x * strength, 0.5D, dir.z * strength);
        try { 
            target.hurtMarked = true; 
        } catch (Throwable ignored) {}
    }

    private static void giveSpeedBoost(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, true, true));
    }

    private static boolean isExecutioner(LivingEntity e) {
        if (e instanceof ServerPlayer sp) {
            var pt = PlayerRegistry.getPlayerType(sp);
            return pt == PlayerTypeOM.X2011;
        }
        return false;
    }

    private static boolean isInLastThreeMinutes(Level lvl) {
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

    private static class HealChannelInfo {
        final long startedAt;
        final long until;
        long nextHealAt;
        boolean selfHealed;

        HealChannelInfo(long startedAt, long until, long nextHealAt) {
            this.startedAt = startedAt;
            this.until = until;
            this.nextHealAt = nextHealAt;
            this.selfHealed = false;
        }
    }

    public enum CreamMoveSet {
        DASH("cream_dash", "dash", KeyType.SECONDARY, "Dash"),
        HEAL("cream_heal", "heal", KeyType.PRIMARY, "Heal");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        CreamMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/cream/" + textureName + ".png");
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