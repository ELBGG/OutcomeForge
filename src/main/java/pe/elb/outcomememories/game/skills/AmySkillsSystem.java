package pe.elb.outcomememories.game.skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
import pe.elb.outcomememories.net.skills.amy.AmySyncPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AmySkillsSystem {

    private static final long HAMMER_COOLDOWN_MS = 24_000L;
    private static final long HAMMER_STANCE_MS = 2_000L;
    private static final double HAMMER_BOUNCE_VELOCITY = 1.0D;
    private static final double HAMMER_CONTACT_RADIUS = 1.5D;

    private static final long THROW_COOLDOWN_MS = 28_000L;
    private static final long THROW_ABSENCE_MS = 8_000L;
    private static final long THROW_PICKUP_ALLOW_MS = 1_000L;
    private static final double THROW_INITIAL_SPEED = 1.8D;
    private static final double THROW_RETURN_SPEED = 1.6D;
    private static final double THROW_HIT_RADIUS = 0.7D;
    private static final double THROW_COLLECT_DISTANCE = 1.2D;

    private static final long REROLL_COOLDOWN_MS = 15_000L;

    private static final double SUN_HEAL_RADIUS = 5.0D;
    private static final int SUN_HEAL_INTERVAL_TICKS = 40;
    private static final float SUN_MAX_HEAL = 75.0F;

    private static final Map<UUID, Long> hammerLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> hammerStanceUntil = new ConcurrentHashMap<>();
    private static final Set<UUID> hammerAwaitingBounce = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<UUID, Set<UUID>> hammerStunnedDuringStance = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> mobNoAiUntil = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> throwLastUsed = new ConcurrentHashMap<>();
    private static final Map<UUID, ThrownInfo> thrownMap = new ConcurrentHashMap<>();

    private static final Map<UUID, Long> rerollLastUsed = new ConcurrentHashMap<>();

    private static final Map<UUID, List<TarotCard>> playerCards = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> sunHealTicks = new ConcurrentHashMap<>();

    public static boolean tryUseHammer(ServerPlayer player) {
        UUID puid = player.getUUID();

        if (isHammerAbsent(puid)) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long last = hammerLastUsed.get(puid);
        if (last != null && (now - last) < HAMMER_COOLDOWN_MS) {
            return false;
        }

        long stunDuration = getStunDuration(puid);

        hammerLastUsed.put(puid, now);
        hammerStanceUntil.put(puid, now + HAMMER_STANCE_MS);
        hammerStunnedDuringStance.put(puid, Collections.newSetFromMap(new ConcurrentHashMap<>()));

        syncCooldown(player, "amy_hammer", HAMMER_COOLDOWN_MS, now);

        if (!player.onGround()) {
            hammerAwaitingBounce.add(puid);
        }

        Level lvl = player.level();
        if (lvl instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_BELL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(),
                    10, 0.5, 0.5, 0.5, 0.02);
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(),
                    4, 0.5, 0.2, 0.5, 0.0);
        }

        return true;
    }

    public static boolean tryThrowHammer(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        if (thrownMap.containsKey(puid)) {
            return false;
        }

        Long last = throwLastUsed.get(puid);
        if (last != null && (now - last) < THROW_COOLDOWN_MS) {
            return false;
        }

        throwLastUsed.put(puid, now);
        syncCooldown(player, "amy_throw", THROW_COOLDOWN_MS, now);

        ItemStack hammerStack = new ItemStack(Items.IRON_AXE);
        ServerLevel lvl = (ServerLevel) player.level();
        ItemEntity itemEnt = new ItemEntity(lvl, player.getX(), player.getY() + 1.2, player.getZ(), hammerStack);

        itemEnt.setPickUpDelay(32767);
        itemEnt.setInvulnerable(true);

        Vec3 look = player.getLookAngle().normalize();
        Vec3 vel = look.scale(THROW_INITIAL_SPEED);
        itemEnt.setDeltaMovement(vel);
        lvl.addFreshEntity(itemEnt);

        long allowPickupAt = now + THROW_PICKUP_ALLOW_MS;
        long returnAt = now + THROW_ABSENCE_MS;

        thrownMap.put(puid, new ThrownInfo(itemEnt.getId(), now, allowPickupAt, returnAt));

        lvl.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
        lvl.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(),
                6, 0.4, 0.2, 0.4, 0.0);

        return true;
    }

    public static boolean attemptPickupHammer(ServerPlayer player, ItemEntity itemEntity) {
        UUID puid = player.getUUID();
        ThrownInfo info = thrownMap.get(puid);
        if (info == null) return false;
        if (itemEntity.getId() != info.entityId) return false;

        long now = System.currentTimeMillis();
        if (now < info.allowPickupAt) {
            return false;
        }

        itemEntity.remove(Entity.RemovalReason.DISCARDED);
        thrownMap.remove(puid);

        return true;
    }

    public static boolean tryReroll(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        Long last = rerollLastUsed.get(puid);
        if (last != null && (now - last) < REROLL_COOLDOWN_MS) {
            return false;
        }

        rerollLastUsed.put(puid, now);
        syncCooldown(player, "amy_reroll", REROLL_COOLDOWN_MS, now);

        rollNewTarotCards(puid);

        Level lvl = player.level();
        if (lvl instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.5, 0.5, 0.5, 0.1);
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d✦ Tarot Cards Rerolled!"));

        return true;
    }

    private static void rollNewTarotCards(UUID playerUUID) {
        List<TarotCard> allCards = Arrays.asList(TarotCard.values());
        Collections.shuffle(allCards);

        List<TarotCard> selected = new ArrayList<>();
        selected.add(allCards.get(0));
        selected.add(allCards.get(1));

        playerCards.put(playerUUID, selected);

        syncFullToClient(playerUUID);

        System.out.println("[Amy] Cartas rolleadas para " + playerUUID + ": " + selected);
    }

    public static List<TarotCard> getCards(UUID playerUUID) {
        return playerCards.getOrDefault(playerUUID, new ArrayList<>());
    }

    public static boolean hasCard(UUID playerUUID, TarotCard card) {
        return getCards(playerUUID).contains(card);
    }

    public static float getSpeedModifier(UUID playerUUID) {
        float modifier = 0.0F;
        for (TarotCard card : getCards(playerUUID)) {
            modifier += card.getSpeedModifier();
        }
        if (hasCard(playerUUID, TarotCard.THE_MOON)) {
            modifier += (new Random().nextFloat() * 3.0F - 1.5F);
        }
        return modifier;
    }

    public static float getDamageModifier(UUID playerUUID) {
        float modifier = 0.0F;
        for (TarotCard card : getCards(playerUUID)) {
            modifier += card.getDamageModifier();
        }
        if (hasCard(playerUUID, TarotCard.THE_MOON)) {
            modifier += (new Random().nextFloat() * 0.3F - 0.15F);
        }
        return modifier;
    }

    private static long getStunDuration(UUID playerUUID) {
        if (hasCard(playerUUID, TarotCard.STRENGTH)) {
            return 5_000L;
        } else if (hasCard(playerUUID, TarotCard.THE_FOOL)) {
            return 2_000L;
        } else {
            return 3_000L;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickHammer();
        tickThrowHammer();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        tickHammerStance(player);
        tickHammerBounce(player);
        tickSunHeal(player);
    }

    private static void tickHammer() {
        long now = System.currentTimeMillis();

        if (!mobNoAiUntil.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = mobNoAiUntil.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                if (now >= e.getValue()) {
                    UUID mobUuid = e.getKey();
                    for (ServerLevel level : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
                        Entity ent = level.getEntity(mobUuid);
                        if (ent instanceof Mob mob) {
                            mob.setNoAi(false);
                        }
                    }
                    it.remove();
                }
            }
        }
    }

    private static void tickHammerStance(ServerPlayer player) {
        UUID puid = player.getUUID();
        long now = System.currentTimeMillis();

        Long stanceEnd = hammerStanceUntil.get(puid);
        if (stanceEnd == null) return;

        if (now >= stanceEnd) {
            hammerStanceUntil.remove(puid);
            hammerStunnedDuringStance.remove(puid);
        } else {
            AABB area = player.getBoundingBox().inflate(HAMMER_CONTACT_RADIUS);
            List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != player && e.isAlive());
            Set<UUID> already = hammerStunnedDuringStance.computeIfAbsent(puid,
                    k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

            long stunDuration = getStunDuration(puid);

            for (LivingEntity target : nearby) {
                UUID tid = target.getUUID();
                if (already.contains(tid)) continue;
                if (isExecutioner(target)) {
                    applyStunTo(target, stunDuration);
                    already.add(tid);

                    Level lvl = player.level();
                    if (lvl instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.9F);
                        serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(),
                                8, 0.3, 0.3, 0.3, 0.05);
                    }
                }
            }
        }
    }

    private static void tickHammerBounce(ServerPlayer player) {
        UUID puid = player.getUUID();

        if (hammerAwaitingBounce.contains(puid)) {
            if (player.onGround()) {
                applyBounce(player);
                hammerAwaitingBounce.remove(puid);
            }
        }
    }

    private static void tickThrowHammer() {
        if (thrownMap.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<Map.Entry<UUID, ThrownInfo>> entries = new ArrayList<>(thrownMap.entrySet());

        for (Map.Entry<UUID, ThrownInfo> e : entries) {
            UUID owner = e.getKey();
            ThrownInfo info = e.getValue();

            ItemEntity ent = findItemEntityById(info.entityId);
            if (ent == null || ent.isRemoved()) {
                thrownMap.remove(owner);
                continue;
            }

            if (now >= info.allowPickupAt) {
                ent.setPickUpDelay(0);
                ent.setInvulnerable(false);
            }

            if (!info.hitSomething) {
                List<LivingEntity> hits = ent.level().getEntitiesOfClass(LivingEntity.class,
                        ent.getBoundingBox().inflate(THROW_HIT_RADIUS),
                        le -> le.isAlive() && isExecutioner(le));

                if (!hits.isEmpty()) {
                    LivingEntity target = hits.get(0);
                    long stunDuration = getStunDuration(owner);
                    applyStunTo(target, stunDuration);
                    info.hitSomething = true;

                    if (ent.level() instanceof ServerLevel lvl) {
                        lvl.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 0.95F);
                        lvl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(),
                                10, 0.3, 0.3, 0.3, 0.03);
                    }
                }
            }

            if (!info.returning && now >= info.returnAt) {
                ServerPlayer ownerPlayer = ent.level().getServer().getPlayerList().getPlayer(owner);
                if (ownerPlayer != null) {
                    Vec3 toPlayer = new Vec3(ownerPlayer.getX() - ent.getX(),
                            ownerPlayer.getY() + 1.0 - ent.getY(),
                            ownerPlayer.getZ() - ent.getZ());
                    Vec3 dir = toPlayer.normalize();
                    ent.setDeltaMovement(dir.scale(THROW_RETURN_SPEED));
                    info.returning = true;
                } else {
                    ent.remove(Entity.RemovalReason.DISCARDED);
                    thrownMap.remove(owner);
                    continue;
                }
            }

            if (info.returning) {
                ServerPlayer ownerPlayer = ent.level().getServer().getPlayerList().getPlayer(owner);
                if (ownerPlayer == null) {
                    ent.remove(Entity.RemovalReason.DISCARDED);
                    thrownMap.remove(owner);
                    continue;
                }

                double dist = ent.distanceToSqr(ownerPlayer);
                if (dist <= (THROW_COLLECT_DISTANCE * THROW_COLLECT_DISTANCE)) {
                    ent.remove(Entity.RemovalReason.DISCARDED);
                    thrownMap.remove(owner);

                    if (ownerPlayer.level() instanceof ServerLevel lvl) {
                        lvl.playSound(null, ownerPlayer.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                                SoundSource.PLAYERS, 0.9F, 1.0F);
                        lvl.sendParticles(ParticleTypes.CLOUD, ownerPlayer.getX(), ownerPlayer.getY() + 1.0,
                                ownerPlayer.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
                    }
                } else {
                    Vec3 toPlayer = new Vec3(ownerPlayer.getX() - ent.getX(),
                            ownerPlayer.getY() + 1.0 - ent.getY(),
                            ownerPlayer.getZ() - ent.getZ());
                    Vec3 dir = toPlayer.normalize();
                    Vec3 newVel = ent.getDeltaMovement().scale(0.6).add(dir.scale(0.4 * THROW_RETURN_SPEED));
                    ent.setDeltaMovement(newVel);
                }
            }
        }
    }

    private static void tickSunHeal(ServerPlayer player) {
        UUID puid = player.getUUID();

        if (!hasCard(puid, TarotCard.THE_SUN)) return;

        int ticks = sunHealTicks.getOrDefault(puid, 0);
        ticks++;

        if (ticks >= SUN_HEAL_INTERVAL_TICKS) {
            ticks = 0;

            if (player.getHealth() < SUN_MAX_HEAL) {
                player.heal(1.0F);
            }

            AABB healArea = player.getBoundingBox().inflate(SUN_HEAL_RADIUS);
            List<ServerPlayer> nearby = player.level().getEntitiesOfClass(ServerPlayer.class, healArea,
                    p -> p != player && p.isAlive());

            for (ServerPlayer nearbyPlayer : nearby) {
                PlayerTypeOM type = PlayerRegistry.getPlayerType(nearbyPlayer);

                if (type == PlayerTypeOM.X2011) continue;

                if (nearbyPlayer.getHealth() < SUN_MAX_HEAL) {
                    nearbyPlayer.heal(1.0F);
                }

                nearbyPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, true));
            }

            if (player.level() instanceof ServerLevel lvl) {
                lvl.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(),
                        3, 0.3, 0.3, 0.3, 0.05);
            }
        }

        sunHealTicks.put(puid, ticks);
    }

    private static void applyBounce(ServerPlayer player) {
        Vec3 prev = player.getDeltaMovement();
        player.setDeltaMovement(new Vec3(prev.x, HAMMER_BOUNCE_VELOCITY, prev.z));

        try {
            player.hurtMarked = true;
        } catch (Throwable ignored) {}

        Level lvl = player.level();
        if (lvl instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(),
                    10, 0.4, 0.2, 0.4, 0.02);
        }
    }

    private static void applyStunTo(LivingEntity target, long msDuration) {
        long now = System.currentTimeMillis();
        long until = now + msDuration;

        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mobNoAiUntil.put(mob.getUUID(), until);
        }

        int ticks = (int) Math.max(1, (msDuration / 50));
        if (target instanceof ServerPlayer sp) {
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 10, false, false, true));
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 5, false, false, true));
        }
    }

    private static boolean isExecutioner(LivingEntity e) {
        if (e instanceof ServerPlayer sp) {
            var pt = PlayerRegistry.getPlayerType(sp);
            return pt == PlayerTypeOM.X2011;
        }
        return false;
    }

    private static ItemEntity findItemEntityById(int id) {
        for (ServerLevel lvl : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            Entity ent = lvl.getEntity(id);
            if (ent instanceof ItemEntity) return (ItemEntity) ent;
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

    public static boolean isHammerAbsent(UUID playerUuid) {
        return thrownMap.containsKey(playerUuid);
    }

    public static long getHammerReturnRemaining(UUID playerUuid) {
        ThrownInfo info = thrownMap.get(playerUuid);
        if (info == null) return 0;
        long remain = info.returnAt - System.currentTimeMillis();
        return Math.max(0, remain);
    }

    private static void syncTarotCardsToClient(UUID playerUUID) {
        ServerPlayer player = findPlayerByUUID(playerUUID);
        if (player != null) {
            List<TarotCard> cards = getCards(playerUUID);

            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new AmySyncPacket(playerUUID, cards)
            );
        }
    }

    private static void syncStatsToClient(UUID playerUUID) {
        ServerPlayer player = findPlayerByUUID(playerUUID);
        if (player != null) {
            float speedMod = getSpeedModifier(playerUUID);
            float damageMod = getDamageModifier(playerUUID);
            boolean hasWorld = hasCard(playerUUID, TarotCard.THE_WORLD);
            boolean hasSun = hasCard(playerUUID, TarotCard.THE_SUN);
            boolean hasMoon = hasCard(playerUUID, TarotCard.THE_MOON);

            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new AmySyncPacket(playerUUID, speedMod, damageMod, hasWorld, hasSun, hasMoon)
            );
        }
    }

    public static void syncFullToClient(UUID playerUUID) {
        ServerPlayer player = findPlayerByUUID(playerUUID);
        if (player != null) {
            List<TarotCard> cards = getCards(playerUUID);
            float speedMod = getSpeedModifier(playerUUID);
            float damageMod = getDamageModifier(playerUUID);
            boolean hasWorld = hasCard(playerUUID, TarotCard.THE_WORLD);
            boolean hasSun = hasCard(playerUUID, TarotCard.THE_SUN);
            boolean hasMoon = hasCard(playerUUID, TarotCard.THE_MOON);

            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new AmySyncPacket(playerUUID, cards, speedMod, damageMod, hasWorld, hasSun, hasMoon)
            );
        }
    }

    public static void cleanup(UUID playerUUID) {
        hammerLastUsed.remove(playerUUID);
        hammerStanceUntil.remove(playerUUID);
        hammerAwaitingBounce.remove(playerUUID);
        hammerStunnedDuringStance.remove(playerUUID);
        throwLastUsed.remove(playerUUID);
        thrownMap.remove(playerUUID);
        rerollLastUsed.remove(playerUUID);
        playerCards.remove(playerUUID);
        sunHealTicks.remove(playerUUID);
    }

    private static class ThrownInfo {
        public final int entityId;
        public final long thrownAt;
        public final long allowPickupAt;
        public final long returnAt;
        public boolean returning = false;
        public boolean hitSomething = false;

        public ThrownInfo(int entityId, long thrownAt, long allowPickupAt, long returnAt) {
            this.entityId = entityId;
            this.thrownAt = thrownAt;
            this.allowPickupAt = allowPickupAt;
            this.returnAt = returnAt;
        }
    }

    public enum TarotCard {
        THE_WORLD("The World", -2.0F, 0.0F, "All entities highlighted globally. Speed -2"),
        THE_FOOL("The Fool", 1.5F, 0.0F, "Speed +1.5. Stun duration 2s"),
        STRENGTH("Strength", -3.0F, 0.10F, "Stun duration 5s. Speed -3, +10% damage taken"),
        THE_EMPEROR("The Emperor", 0.5F, -0.15F, "Speed +0.5, 15% damage resistance"),
        THE_SUN("The Sun", 0.0F, 0.0F, "Passive AOE heal (1 HP/2s up to 75 HP)"),
        THE_MOON("The Moon", 0.0F, 0.0F, "Random speed/damage. Environmental fog");

        private final String displayName;
        private final float speedModifier;
        private final float damageModifier;
        private final String description;

        TarotCard(String displayName, float speedModifier, float damageModifier, String description) {
            this.displayName = displayName;
            this.speedModifier = speedModifier;
            this.damageModifier = damageModifier;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public float getSpeedModifier() { return speedModifier; }
        public float getDamageModifier() { return damageModifier; }
        public String getDescription() { return description; }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/tarot/" + name().toLowerCase() + ".png");
        }
    }

    public enum AmyMoveSet {
        HAMMER("amy_hammer", "hammer", KeyType.SECONDARY, "Hammer"),
        THROW("amy_throw", "throw", KeyType.PRIMARY, "Throw Hammer"),
        REROLL("amy_reroll", "reroll", KeyType.SPECIAL, "Reroll Tarot Cards");

        private final String id;
        private final String textureName;
        private final KeyType keyType;
        private final String displayName;

        AmyMoveSet(String id, String textureName, KeyType keyType, String displayName) {
            this.id = id;
            this.textureName = textureName;
            this.keyType = keyType;
            this.displayName = displayName;
        }

        public String getId() { return id; }

        public ResourceLocation getTexture() {
            return new ResourceLocation("outcomememories", "textures/moveset/amy/" + textureName + ".png");
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