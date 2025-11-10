package pe.elb.outcomememories.client.handlers;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.skills.tails.TailsSkillPacket;

@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TailsClientControls {

    private static boolean wasLeftClickPressed = false;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(mc.player);
        if (type != PlayerTypeOM.TAILS) return;

        if (event.getKey() == GLFW.GLFW_KEY_SPACE) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                NetworkHandler.CHANNEL.sendToServer(
                        new TailsSkillPacket(TailsSkillPacket.SkillType.GLIDE_ASCEND, true)
                );
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                NetworkHandler.CHANNEL.sendToServer(
                        new TailsSkillPacket(TailsSkillPacket.SkillType.GLIDE_ASCEND, false)
                );
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(mc.player);
        if (type != PlayerTypeOM.TAILS) {
            wasLeftClickPressed = false;
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean isPressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isPressed && !wasLeftClickPressed) {
            NetworkHandler.CHANNEL.sendToServer(
                    new TailsSkillPacket(TailsSkillPacket.SkillType.LASER_EXECUTE)
            );
        }

        wasLeftClickPressed = isPressed;
    }

    public static void reset() {
        wasLeftClickPressed = false;
    }
}