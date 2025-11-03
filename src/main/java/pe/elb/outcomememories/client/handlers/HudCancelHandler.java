package pe.elb.outcomememories.client.init;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

@Mod.EventBusSubscriber(modid = "outcomememories", value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class HudCancelHandler {

    @SubscribeEvent
    public static void onRenderHudOverlay(RenderGuiOverlayEvent.Pre event) {
        CameraType cameraType = Minecraft.getInstance().options.getCameraType();

        // Cancela solo si está en 3ra persona
        if (cameraType == CameraType.THIRD_PERSON_BACK || cameraType == CameraType.THIRD_PERSON_FRONT) {
            // Cancela vida
            if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
                event.setCanceled(true);
            }
            // Cancela hambre
            if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
                event.setCanceled(true);
            }
        }
    }
}