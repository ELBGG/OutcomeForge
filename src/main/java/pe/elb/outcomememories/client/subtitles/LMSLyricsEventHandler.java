package pe.elb.outcomememories.client.subtitles;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handler unificado de eventos para el sistema de letras LMS
 * 
 * Maneja:
 * - Renderizado de subtítulos en GUI
 * - Tick del sistema
 * - Recarga de recursos
 */
public class LMSLyricsEventHandler {

    /**
     * Eventos de Forge (render y tick)
     */
    @Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ForgeEvents {
        
        /**
         * Renderiza los subtítulos en la GUI
         */
        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            LMSLyricsRenderer.getInstance().render(event.getGuiGraphics());
        }
        
        /**
         * Tick del cliente para actualizar letras
         */
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                LMSLyricsSystem.getInstance().tick();
            }
        }
    }

    /**
     * Eventos del Mod (registro de listeners)
     */
    @Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        
        /**
         * Registra el listener para recargar letras cuando se recargan los resource packs
         */
        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((preparationBarrier, resourceManager, preparationsProfiler, 
                                         reloadProfiler, backgroundExecutor, gameExecutor) -> {
                return preparationBarrier.wait(null).thenRunAsync(() -> {
                    LMSLyricsSystem.getInstance().loadLyrics(resourceManager);
                }, gameExecutor);
            });
        }
    }
}