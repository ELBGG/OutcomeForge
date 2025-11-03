package pe.elb.outcomememories.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.client.overlays.guis.PlayerHealtBarHud;

import static pe.elb.outcomememories.Outcomememories.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class PlayerHealthBarEvent {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Solo mostrar en tercera persona
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) return;

        // Evitar que se renderice sobre pantallas de menú o pausa
        if (mc.screen != null) return;

        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        int width = 259;
        int height = 73;
        int x = 20;
        int y = screenHeight - height - 20;

        RenderSystem.enableBlend();
        PlayerHealtBarHud.render(event.getGuiGraphics(), x, y, width, height);
        RenderSystem.disableBlend();
    }
}