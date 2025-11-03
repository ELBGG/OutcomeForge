package pe.elb.outcomememories.client;

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
 * Sin usar Mixins - usa polling en cada tick
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KnucklesPunchInputHandler {
    
    private static boolean wasLeftClickPressed = false;
    
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        // Solo procesar para Knuckles
        PlayerTypeOM type = PlayerRegistry.getPlayerType(mc.player);
        if (type != PlayerTypeOM.KNUCKLES) return;
        
        // Solo procesar si está cargando punch
        if (!KnucklesPunchState.isChargingPunch()) {
            wasLeftClickPressed = false;
            return;
        }
        
        // Detectar click izquierdo usando GLFW
        long window = mc.getWindow().getWindow();
        int leftClickState = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        
        // Detectar edge (cambio de no presionado a presionado)
        boolean isPressed = leftClickState == GLFW.GLFW_PRESS;
        
        if (isPressed && !wasLeftClickPressed) {
            // Click detectado!
            boolean isGroundSlam = !mc.player.onGround();
            
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
        }
        
        wasLeftClickPressed = isPressed;
    }
}