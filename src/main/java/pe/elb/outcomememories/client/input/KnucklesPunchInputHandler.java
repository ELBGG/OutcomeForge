package pe.elb.outcomememories.client.input;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.client.KnucklesPunchState;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.skills.knuckles.PunchExecutePacket;

/**
 * Handler para detectar clicks del mouse durante el Punch de Knuckles
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KnucklesPunchInputHandler {

    private static boolean wasLeftClickPressed = false;
    private static int clickCooldown = 0; // Anti-spam

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Reducir cooldown
        if (clickCooldown > 0) {
            clickCooldown--;
        }

        // Solo procesar para Knuckles
        PlayerTypeOM type = PlayerRegistry.getPlayerType(mc.player);
        if (type != PlayerTypeOM.KNUCKLES) {
            wasLeftClickPressed = false;
            return;
        }

        // Solo procesar si está cargando punch
        if (!KnucklesPunchState.isChargingPunch()) {
            wasLeftClickPressed = false;
            return;
        }

        // Detectar click izquierdo
        long window = mc.getWindow().getWindow();
        int leftClickState = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT);

        boolean isPressed = leftClickState == GLFW.GLFW_PRESS;

        // Detectar edge (cambio de no presionado a presionado)
        if (isPressed && !wasLeftClickPressed && clickCooldown == 0) {
            // Click detectado!
            boolean isGroundSlam = !mc.player.onGround();

            // Debug log
            Outcomememories.LOGGER.info("Knuckles Punch ejecutado - Ground Slam: {}", isGroundSlam);

            // Enviar packet al servidor
            NetworkHandler.CHANNEL.sendToServer(new PunchExecutePacket(isGroundSlam));

            // Resetear estado local
            KnucklesPunchState.setChargingPunch(false);

            // Feedback visual
            mc.player.displayClientMessage(
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
}