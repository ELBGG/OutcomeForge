package pe.elb.outcomememories.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.LMSSystem;
import pe.elb.outcomememories.game.game.OutcomeMemoriesGameSystem;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.game.skills.SonicSkillsSystem;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.skills.sonic.SonicSyncPacket;

/**
 * Handler unificado de eventos del servidor
 * 
 * Maneja:
 * - Conexión de jugadores: Sincronización de tipos y estados
 * - Detección de salidas durante LMS
 * - Tick de jugadores
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerEventHandler");

    // ============ CONEXIÓN Y LOGIN ============

    /**
     * Cuando un jugador se conecta al servidor
     * Sincroniza tipos de personajes entre todos los jugadores
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LOGGER.info("[ServerEvents] Jugador {} se conectó, sincronizando...",
                player.getGameProfile().getName());

        // 1️⃣ Sincronizar el tipo de ESTE jugador a TODOS
        PlayerTypeOM thisPlayerType = PlayerRegistry.getPlayerType(player);
        if (thisPlayerType != null) {
            LOGGER.info("[ServerEvents] {} tiene tipo {}, enviando a todos",
                    player.getGameProfile().getName(), thisPlayerType);
            NetworkHandler.sendRoleUpdate(player, thisPlayerType, false, false, false, 1);
        }

        // 2️⃣ Enviar los tipos de TODOS los demás jugadores a ESTE jugador
        for (ServerPlayer otherPlayer : player.getServer().getPlayerList().getPlayers()) {
            if (otherPlayer.getUUID().equals(player.getUUID())) continue;

            PlayerTypeOM otherType = PlayerRegistry.getPlayerType(otherPlayer);
            if (otherType != null) {
                LOGGER.info("[ServerEvents] Enviando tipo de {} ({}) a {}",
                        otherPlayer.getGameProfile().getName(), otherType, player.getGameProfile().getName());

                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new NetworkHandler.RoleUpdatePacket(
                                otherPlayer.getUUID(),
                                otherType,
                                false, false, false, 1
                        )
                );
            }
        }

        // 3️⃣ Asignar NONE si no tiene tipo
        if (thisPlayerType == null) {
            PlayerRegistry.setPlayerType(player, PlayerTypeOM.NONE);
            LOGGER.info("[ServerEvents] {} no tenía tipo, asignado NONE", player.getGameProfile().getName());
        }

        // 4️⃣ Si es Sonic, sincronizar Dodge Meter
        PlayerRegistry.PlayerDefinition def = PlayerRegistry.get(player);
        if (def != null && def.getType() == PlayerTypeOM.SONIC) {
            float dodgeHP = SonicSkillsSystem.getDodgeHP(player.getUUID());
            NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SonicSyncPacket(player.getUUID(), dodgeHP)
            );
            LOGGER.info("[ServerEvents] Dodge Meter de {} sincronizado: {}", 
                player.getGameProfile().getName(), dodgeHP);
        }
    }

    // ============ DETECCIÓN DE SALIDAS (LMS) ============

    /**
     * Detecta cuando un survivor llega a una salida durante la fase de escape de LMS
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        
        // Solo procesar durante fase de escape de LMS
        if (!LMSSystem.isExitPhaseActive()) return;
        
        // Solo survivors pueden escapar
        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);
        if (type == PlayerTypeOM.X2011) return;
        
        // Verificar si está en una salida
        if (OutcomeMemoriesGameSystem.isPlayerAtExit(player)) {
            LOGGER.info("[ServerEvents] Survivor {} llegó a la salida durante LMS!", 
                player.getGameProfile().getName());
            LMSSystem.onSurvivorEscaped(player);
        }
    }

    // ============ UTILIDADES ============

    /**
     * Limpia el estado de un jugador al desconectarse
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        LOGGER.info("[ServerEvents] Jugador {} se desconectó",
                player.getGameProfile().getName());
        
        // Aquí puedes agregar limpieza de estados si es necesario
        // Por ejemplo: limpiar cachés, detener habilidades activas, etc.
    }
}