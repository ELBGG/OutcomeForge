package pe.elb.outcomememories.events;

import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.game.game.GameSystem;
import pe.elb.outcomememories.game.game.LMSSystem;

/**
 * Sistema de integración con FujiTimer (plugin de Bukkit)
 * 
 * El plugin envía comandos especiales que este sistema interpreta:
 * - [TIMER_START] -> Inicia el juego
 * - [TIMER_FINISH] -> Termina el juego (survivors ganan)
 * - [TIMER_3MIN] -> Notifica que quedan 3 minutos (activa buffs)
 * - [TIMER_LMS] -> Fuerza activación de LMS
 */
@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimerIntegration {
    
    // Marcadores que el plugin enviará
    private static final String MARKER_START = "[OM_TIMER_START]";
    private static final String MARKER_FINISH = "[OM_TIMER_FINISH]";
    private static final String MARKER_3MIN = "[OM_TIMER_3MIN]";
    private static final String MARKER_LMS = "[OM_TIMER_LMS]";
    
    private static boolean threeMinutesNotified = false;
    
    /**
     * Escucha comandos del servidor (enviados por el plugin)
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        
        // Verificar si es un comando del timer
        if (message.startsWith("[OM_TIMER_")) {
            // Cancelar el mensaje para que no se vea en chat
            event.setCanceled(true);
            
            handleTimerCommand(message);
        }
    }
    
    /**
     * Maneja los comandos recibidos del plugin
     */
    private static void handleTimerCommand(String command) {
        switch (command) {
            case MARKER_START -> {
                // Iniciar el juego
                boolean started = GameSystem.startGame();
                if (started) {
                    System.out.println("[OutcomeMemories] Game started by FujiTimer");
                    threeMinutesNotified = false;
                }
            }
            
            case MARKER_FINISH -> {
                // Terminar el juego - survivors ganan
                GameSystem.endGameSurvivorsWin();
                System.out.println("[OutcomeMemories] Game ended by FujiTimer - Survivors Win");
                threeMinutesNotified = false;
            }
            
            case MARKER_3MIN -> {
                // Notificar últimos 3 minutos
                if (!threeMinutesNotified) {
                    threeMinutesNotified = true;
                    notifyLastThreeMinutes();
                    System.out.println("[OutcomeMemories] Last 3 minutes notification");
                }
            }
            
            case MARKER_LMS -> {
                // Forzar LMS si no está activo
                if (!LMSSystem.isLMSActive()) {
                    LMSSystem.checkLMSConditions();
                    System.out.println("[OutcomeMemories] LMS check forced by timer");
                }
            }
        }
    }
    
    /**
     * Notifica a todos los jugadores sobre los últimos 3 minutos
     */
    private static void notifyLastThreeMinutes() {
        GameSystem.broadcastMessage("§c§l========================================");
        GameSystem.broadcastMessage("§e§l   ¡ÚLTIMOS 3 MINUTOS!");
        GameSystem.broadcastMessage("§7Las habilidades ahora tienen buffs especiales");
        GameSystem.broadcastMessage("§c§l========================================");
        GameSystem.playGlobalSound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 1.0F);
    }
    
    /**
     * Resetea el estado cuando termina una partida
     */
    public static void reset() {
        threeMinutesNotified = false;
    }
}