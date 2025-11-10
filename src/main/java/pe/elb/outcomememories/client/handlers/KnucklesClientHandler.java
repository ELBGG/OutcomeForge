package pe.elb.outcomememories.client.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.skills.knuckles.KnucklesSkillPacket;

/**
 * Handler unificado del cliente para todas las mecánicas de Knuckles:
 * - Detección de clicks durante Punch
 * - Estado de carga del Punch
 * - Detección automática de paredes para Wall Cling
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KnucklesClientHandler {

    // ============ PUNCH STATE ============
    private static boolean isChargingPunch = false;

    public static void setChargingPunch(boolean charging) {
        isChargingPunch = charging;
    }

    public static boolean isChargingPunch() {
        return isChargingPunch;
    }

    public static void resetPunchState() {
        isChargingPunch = false;
    }

    // ============ PUNCH INPUT DETECTION ============
    private static boolean wasLeftClickPressed = false;
    private static int clickCooldown = 0; // Anti-spam

    // ============ WALL CLING DETECTION ============
    private static boolean wasCollidingHorizontally = false;
    private static int ticksSinceLastWallCling = 0;
    private static final int WALL_CLING_COOLDOWN_TICKS = 10; // 0.5 segundos entre intentos

    // ============ MAIN TICK HANDLER ============

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);

        // Solo procesar para Knuckles
        if (type != PlayerTypeOM.KNUCKLES) {
            wasLeftClickPressed = false;
            wasCollidingHorizontally = false;
            return;
        }

        // Procesar mecánicas de Knuckles
        handlePunchInput(mc, player);
        handleWallDetection(player);
    }

    // ============ PUNCH INPUT HANDLING ============

    /**
     * Detecta clicks del mouse durante la carga del Punch
     */
    private static void handlePunchInput(Minecraft mc, LocalPlayer player) {
        // Reducir cooldown
        if (clickCooldown > 0) {
            clickCooldown--;
        }

        // Solo procesar si está cargando punch
        if (!isChargingPunch) {
            wasLeftClickPressed = false;
            return;
        }

        // Detectar click izquierdo usando GLFW
        long window = mc.getWindow().getWindow();
        int leftClickState = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT);

        boolean isPressed = leftClickState == GLFW.GLFW_PRESS;

        // Detectar edge (cambio de no presionado a presionado)
        if (isPressed && !wasLeftClickPressed && clickCooldown == 0) {
            // Click detectado!
            boolean isGroundSlam = !player.onGround();

            // Debug log
            Outcomememories.LOGGER.info("Knuckles Punch ejecutado - Ground Slam: {}", isGroundSlam);

            // Enviar packet al servidor
            NetworkHandler.CHANNEL.sendToServer(new KnucklesSkillPacket(KnucklesSkillPacket.SkillType.PUNCH_EXECUTE));

            // Resetear estado local
            isChargingPunch = false;

            // Feedback visual
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            isGroundSlam ? "§4💥 GROUND SLAM!" : "§c👊 PUNCH!"
                    ),
                    true
            );

            // Cooldown anti-spam
            clickCooldown = 5; // 5 ticks = 250ms
        }

        wasLeftClickPressed = isPressed;
    }

    // ============ WALL CLING DETECTION ============

    /**
     * Detecta automáticamente cuando Knuckles colisiona con una pared
     */
    private static void handleWallDetection(LocalPlayer player) {
        ticksSinceLastWallCling++;

        // Detectar colisión horizontal con pared
        boolean isCollidingNow = player.horizontalCollision;
        boolean justHitWall = isCollidingNow && !wasCollidingHorizontally;

        // Si acaba de golpear una pared y no está en el suelo
        if (justHitWall && !player.onGround() && ticksSinceLastWallCling >= WALL_CLING_COOLDOWN_TICKS) {
            // Enviar packet para intentar wall cling
            NetworkHandler.CHANNEL.sendToServer(new KnucklesSkillPacket(KnucklesSkillPacket.SkillType.WALL_CLING));
            ticksSinceLastWallCling = 0;

            Outcomememories.LOGGER.debug("Knuckles Wall Cling detectado");
        }

        wasCollidingHorizontally = isCollidingNow;
    }

    // ============ UTILIDADES ============

    /**
     * Resetea todo el estado de Knuckles (útil al cambiar de personaje)
     */
    public static void resetAll() {
        isChargingPunch = false;
        wasLeftClickPressed = false;
        clickCooldown = 0;
        wasCollidingHorizontally = false;
        ticksSinceLastWallCling = 0;
    }
}