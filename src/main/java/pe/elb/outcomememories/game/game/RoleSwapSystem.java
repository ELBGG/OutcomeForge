package pe.elb.outcomememories.game.game;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.game.PlayerTypeOM;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de intercambio de roles sin muerte real
 */
@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RoleSwapSystem {

    private static final float LOW_HEALTH_THRESHOLD = 8.0F; // 4 corazones
    private static final long SWAP_COOLDOWN_MS = 2000L; // 2 segundos
    private static final Map<UUID, Long> lastSwapTime = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerTypeOM> previousRoles = new ConcurrentHashMap<>();

    /**
     * PRIMERA LINEA DE DEFENSA: Cancelar muerte completamente
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!GameSystem.isGameActive()) return;

        System.out.println("[RoleSwap] DEATH EVENT for " + player.getName().getString() + " - CANCELING!");

        // CANCELAR MUERTE
        event.setCanceled(true);

        // Restaurar algo de vida
        player.setHealth(10.0F);

        // Buscar quién lo mató
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer killer) {
            System.out.println("[RoleSwap] Killed by " + killer.getName().getString());

            // Verificar que sea exe matando survivor
            PlayerTypeOM killerType = PlayerRegistry.getPlayerType(killer);
            PlayerTypeOM victimType = PlayerRegistry.getPlayerType(player);

            if (killerType == PlayerTypeOM.X2011 && victimType != PlayerTypeOM.X2011) {
                // Hacer swap inmediato
                scheduleSwap(killer, player);
            }
        }
    }

    /**
     * SEGUNDA LINEA DE DEFENSA: Interceptar ataque antes de aplicar daño
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!GameSystem.isGameActive()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        PlayerTypeOM attackerType = PlayerRegistry.getPlayerType(attacker);
        PlayerTypeOM victimType = PlayerRegistry.getPlayerType(victim);

        // Solo exe puede hacer swap
        if (attackerType != PlayerTypeOM.X2011) return;
        if (victimType == PlayerTypeOM.X2011) return;

        UUID victimUUID = victim.getUUID();
        UUID attackerUUID = attacker.getUUID();

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastSwap = lastSwapTime.get(victimUUID);
        if (lastSwap != null && (now - lastSwap) < SWAP_COOLDOWN_MS) {
            return;
        }

        float currentHealth = victim.getHealth();
        float damage = event.getAmount();

        System.out.println("[RoleSwap] Attack: " + attacker.getName().getString() +
                " -> " + victim.getName().getString() +
                " | HP: " + currentHealth + " | Damage: " + damage);

        // Si el golpe lo dejaria bajo o en el threshold, hacer swap
        if (currentHealth - damage <= LOW_HEALTH_THRESHOLD) {
            System.out.println("[RoleSwap] Triggering swap!");

            // CANCELAR EL ATAQUE
            event.setCanceled(true);

            // Hacer swap
            scheduleSwap(attacker, victim);

            lastSwapTime.put(victimUUID, now);
            lastSwapTime.put(attackerUUID, now);
        }
    }

    /**
     * Programa el swap para el siguiente tick (evita problemas de sincronización)
     */
    private static void scheduleSwap(ServerPlayer exe, ServerPlayer survivor) {
        // Ejecutar en el siguiente tick del servidor
        net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().execute(() -> {
            performRoleSwap(exe, survivor);
        });
    }

    /**
     * Realiza el intercambio de roles
     */
    public static void performRoleSwap(ServerPlayer exe, ServerPlayer survivor) {
        UUID exeUUID = exe.getUUID();
        UUID survivorUUID = survivor.getUUID();

        System.out.println("[RoleSwap] === EXECUTING SWAP ===");
        System.out.println("[RoleSwap] Old Exe: " + exe.getName().getString());
        System.out.println("[RoleSwap] New Exe: " + survivor.getName().getString());

        // Guardar tipo del survivor
        PlayerTypeOM survivorOldType = PlayerRegistry.getPlayerType(survivor);

        // Obtener tipo previo del exe
        PlayerTypeOM exeNewType = previousRoles.getOrDefault(exeUUID, getRandomSurvivorType());

        System.out.println("[RoleSwap] Old Exe will become: " + exeNewType);
        System.out.println("[RoleSwap] New Exe was: " + survivorOldType);

        // === TRANSFORMAR EXE -> SURVIVOR ===
        PlayerRegistry.setPlayerType(exe, exeNewType);
        previousRoles.put(exeUUID, exeNewType);

        exe.setHealth(exe.getMaxHealth());
        exe.getFoodData().setFoodLevel(20);
        exe.clearFire();
        exe.setGameMode(GameType.SURVIVAL);

        exe.sendSystemMessage(Component.literal("§a§l¡Ahora eres SURVIVOR!"));
        exe.sendSystemMessage(Component.literal("§7Personaje: §f" + exeNewType.name()));

        // === TRANSFORMAR SURVIVOR -> EXE ===
        PlayerRegistry.setPlayerType(survivor, PlayerTypeOM.X2011);
        previousRoles.put(survivorUUID, survivorOldType);

        survivor.setHealth(survivor.getMaxHealth());
        survivor.getFoodData().setFoodLevel(20);
        survivor.clearFire();
        survivor.setGameMode(GameType.SURVIVAL);

        survivor.sendSystemMessage(Component.literal("§c§l¡Ahora eres EXECUTIONER!"));

        if (LMSSystem.isLMSActive()) {
            survivor.sendSystemMessage(Component.literal("§c§l¡INSTA-SWAP ACTIVADO!"));

            // Actualizar tracking de LMS
            LMSSystem.updateLMSTracking(exeUUID, survivorUUID);
        }

        // Actualizar exe actual en GameSystem
        GameSystem.updateCurrentExecutioner(survivorUUID);

        // Efectos
        spawnSwapEffects(exe, survivor);

        // Mensaje global
        GameSystem.broadcastMessage("§e⚡ " + survivor.getName().getString() + " §7es ahora el §cExecutioner§7!");

        System.out.println("[RoleSwap] === SWAP COMPLETED ===");
    }

    private static void spawnSwapEffects(ServerPlayer newSurvivor, ServerPlayer newExe) {
        if (newSurvivor.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    newSurvivor.getX(), newSurvivor.getY() + 1.0, newSurvivor.getZ(),
                    30, 0.5, 1.0, 0.5, 0.1);

            serverLevel.playSound(null, newSurvivor.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.5F);

            serverLevel.sendParticles(ParticleTypes.FLAME,
                    newExe.getX(), newExe.getY() + 1.0, newExe.getZ(),
                    30, 0.5, 1.0, 0.5, 0.1);

            serverLevel.playSound(null, newExe.blockPosition(),
                    SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
    }

    private static PlayerTypeOM getRandomSurvivorType() {
        PlayerTypeOM[] types = {
                PlayerTypeOM.SONIC, PlayerTypeOM.TAILS, PlayerTypeOM.KNUCKLES,
                PlayerTypeOM.AMY, PlayerTypeOM.CREAM, PlayerTypeOM.EGGMAN
        };
        return types[new Random().nextInt(types.length)];
    }

    public static void savePreviousRole(UUID playerUUID, PlayerTypeOM type) {
        previousRoles.put(playerUUID, type);
    }

    public static void cleanup(UUID playerUUID) {
        lastSwapTime.remove(playerUUID);
        previousRoles.remove(playerUUID);
    }

    public static void cleanupAll() {
        lastSwapTime.clear();
        previousRoles.clear();
    }
}