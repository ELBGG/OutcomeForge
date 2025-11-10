package pe.elb.outcomememories.client.overlays;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.client.overlays.guis.PlayerHealtBarHud;
import pe.elb.outcomememories.client.overlays.guis.PlayerAttacksSetHud;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;

import static pe.elb.outcomememories.Outcomememories.MODID;

/**
 * Maneja el registro y renderizado de overlays personalizados
 * - Health Bar: Solo se muestra cuando el jugador tiene un tipo definido
 * - Moveset: Solo se muestra cuando el jugador tiene un tipo definido
 */
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class PlayerHealthBarEvent {

    // ========================================
    // REGISTRADOR DE OVERLAYS (MOD BUS)
    // ========================================
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class OverlayRegistrar {

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            // Registrar Health Bar personalizada
            event.registerAbove(
                    VanillaGuiOverlay.HOTBAR.id(),
                    "player_health_bar",
                    new PlayerHealtBarHud()
            );

            // Registrar Moveset
            event.registerAbove(
                    VanillaGuiOverlay.HOTBAR.id(),
                    "player_moveset",
                    new PlayerAttacksSetHud()
            );

            System.out.println("[OutcomeMemories] Overlays registrados: Health Bar y Moveset");
        }
    }

    // ========================================
    // CONTROL DE VISIBILIDAD (FORGE BUS)
    // ========================================

    /**
     * Oculta la barra de vida vanilla cuando el jugador tiene un tipo definido
     */
    @SubscribeEvent
    public static void onRenderVanillaHealth(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) return;

        PlayerTypeOM playerType = PlayerRegistry.getPlayerType(mc.player);

        if (playerType != null && playerType != PlayerTypeOM.NONE) {
            // Ocultar barra de vida vanilla
            if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) {
                event.setCanceled(true);
            }

            // Ocultar nivel de armadura
            if (event.getOverlay().id().equals(VanillaGuiOverlay.ARMOR_LEVEL.id())) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Controla si el Health Bar personalizado debe renderizarse
     */
    @SubscribeEvent
    public static void onRenderCustomHealthBar(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().getPath().equals("player_health_bar")) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Si no hay jugador, cancelar renderizado
        if (mc.player == null) {
            event.setCanceled(true);
            return;
        }

        // Verificar si el jugador tiene un tipo definido
        PlayerTypeOM playerType = PlayerRegistry.getPlayerType(mc.player);

        // Solo mostrar si tiene un tipo válido (no NONE)
        if (playerType == null || playerType == PlayerTypeOM.NONE) {
            event.setCanceled(true);
            return;
        }

        // No mostrar en modo espectador
        if (mc.player.isSpectator()) {
            event.setCanceled(true);
            return;
        }

        // No mostrar si la UI está oculta (F1)
        if (mc.options.hideGui) {
            event.setCanceled(true);
            return;
        }

        // No mostrar en modo debug (F3) - usando el campo correcto
        if (mc.options.renderDebug) {
            event.setCanceled(true);
            return;
        }
    }

    /**
     * Controla si el Moveset debe renderizarse
     */
    @SubscribeEvent
    public static void onRenderMoveset(RenderGuiOverlayEvent.Pre event) {
        // Verificar si es nuestro overlay de moveset
        if (!event.getOverlay().id().getPath().equals("player_moveset")) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Si no hay jugador, cancelar
        if (mc.player == null) {
            event.setCanceled(true);
            return;
        }

        // Verificar si tiene tipo definido
        PlayerTypeOM playerType = PlayerRegistry.getPlayerType(mc.player);

        // Solo mostrar si tiene un tipo válido
        if (playerType == null || playerType == PlayerTypeOM.NONE) {
            event.setCanceled(true);
            return;
        }

        // No mostrar en modo espectador
        if (mc.player.isSpectator()) {
            event.setCanceled(true);
            return;
        }

        // No mostrar si la UI está oculta
        if (mc.options.hideGui) {
            event.setCanceled(true);
            return;
        }

        // No mostrar en modo debug (F3)
        if (mc.options.renderDebug) {
            event.setCanceled(true);
            return;
        }
    }
}