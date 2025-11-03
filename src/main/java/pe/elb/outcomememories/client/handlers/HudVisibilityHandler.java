package pe.elb.outcomememories.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.Outcomememories;

/**
 * Oculta elementos del HUD en tercera persona usando eventos
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HudVisibilityHandler {
    
    /**
     * Oculta la hotbar en tercera persona
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderHotbar(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
            Minecraft mc = Minecraft.getInstance();
            CameraType camera = mc.options.getCameraType();
            
            if (camera == CameraType.THIRD_PERSON_BACK || camera == CameraType.THIRD_PERSON_FRONT) {
                event.setCanceled(true);
            }
        }
    }
    
    /**
     * Oculta la barra de experiencia en tercera persona
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderExperience(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            Minecraft mc = Minecraft.getInstance();
            CameraType camera = mc.options.getCameraType();
            
            if (camera == CameraType.THIRD_PERSON_BACK || camera == CameraType.THIRD_PERSON_FRONT) {
                event.setCanceled(true);
            }
        }
    }
    
    /**
     * Oculta la barra de vida en tercera persona
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderHealth(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
            Minecraft mc = Minecraft.getInstance();
            CameraType camera = mc.options.getCameraType();
            
            if (camera == CameraType.THIRD_PERSON_BACK || camera == CameraType.THIRD_PERSON_FRONT) {
                event.setCanceled(true);
            }
        }
    }
    
    /**
     * Oculta la barra de comida en tercera persona
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderFood(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
            Minecraft mc = Minecraft.getInstance();
            CameraType camera = mc.options.getCameraType();
            
            if (camera == CameraType.THIRD_PERSON_BACK || camera == CameraType.THIRD_PERSON_FRONT) {
                event.setCanceled(true);
            }
        }
    }
    
    /**
     * Oculta la barra de armadura en tercera persona
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderArmor(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.ARMOR_LEVEL.type()) {
            Minecraft mc = Minecraft.getInstance();
            CameraType camera = mc.options.getCameraType();
            
            if (camera == CameraType.THIRD_PERSON_BACK || camera == CameraType.THIRD_PERSON_FRONT) {
                event.setCanceled(true);
            }
        }
    }
    
    /**
     * Oculta el aire (burbujas) en tercera persona
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderAir(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.AIR_LEVEL.type()) {
            Minecraft mc = Minecraft.getInstance();
            CameraType camera = mc.options.getCameraType();
            
            if (camera == CameraType.THIRD_PERSON_BACK || camera == CameraType.THIRD_PERSON_FRONT) {
                event.setCanceled(true);
            }
        }
    }
}