package pe.elb.outcomememories.client.overlays;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.Outcomememories;

@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SimpleCrosshairRenderer {

    private static final ResourceLocation CUSTOM_CROSSHAIR = new ResourceLocation("outcomememories", "textures/gui/custom_crosshair.png");
    private static final int CROSSHAIR_SIZE = 32;

    /**
     * Renderiza el crosshair personalizado ANTES de que se renderice el vanilla
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void renderCustomCrosshair(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();

        // No renderizar si está en opciones o pausado
        if (mc.player == null || mc.options.hideGui) {
            event.setCanceled(true); // Cancelar también el vanilla
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        // Calcular posición central
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 - CROSSHAIR_SIZE / 2;
        int y = screenHeight / 2 - CROSSHAIR_SIZE / 2;

        // Renderizar crosshair personalizado
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        guiGraphics.blit(
                CUSTOM_CROSSHAIR,
                x, y,
                0, 0,
                CROSSHAIR_SIZE, CROSSHAIR_SIZE,
                CROSSHAIR_SIZE, CROSSHAIR_SIZE
        );

        RenderSystem.disableBlend();

        // Cancelar el vanilla
        event.setCanceled(true);
    }
}