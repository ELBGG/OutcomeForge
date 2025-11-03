package pe.elb.outcomememories.client.renderer;

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
     * Cancela el crosshair vanilla
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void cancelVanillaCrosshair(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
            event.setCanceled(true); // Siempre cancelar el vanilla
        }
    }
    
    /**
     * Renderiza el crosshair personalizado
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void renderCustomCrosshair(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
            Minecraft mc = Minecraft.getInstance();
            
            // No renderizar si está en opciones o pausado (opcional)
            if (mc.player == null || mc.options.hideGui) {
                return;
            }
            
            GuiGraphics guiGraphics = event.getGuiGraphics();
            
            // Calcular posición central
            int screenWidth = event.getWindow().getGuiScaledWidth();
            int screenHeight = event.getWindow().getGuiScaledHeight();
            int x = screenWidth / 2 - CROSSHAIR_SIZE / 2;
            int y = screenHeight / 2 - CROSSHAIR_SIZE / 2;
            
            // Renderizar
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
        }
    }
}