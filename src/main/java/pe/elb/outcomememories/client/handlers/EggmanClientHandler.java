package pe.elb.outcomememories.client.handlers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.client.renderer.EnergyShieldRenderer;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.game.skills.EggmanSkillsSystem;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.skills.eggman.EggmanSkillPacket;

/**
 * Handler unificado del cliente para todas las mecánicas de Eggman
 * 
 * Maneja:
 * - Controles: SPACE para Double Jump (jetpack)
 * - Renderizado: Energy Shield visual con arcos eléctricos
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EggmanClientHandler {

    // ============ CONTROLES ============
    
    /**
     * Maneja la entrada de teclado para Eggman
     */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(mc.player);
        if (type != PlayerTypeOM.EGGMAN) return;

        // Detectar SPACE para double jump/jetpack
        if (event.getKey() == GLFW.GLFW_KEY_SPACE) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                NetworkHandler.CHANNEL.sendToServer(new EggmanSkillPacket(EggmanSkillPacket.SkillType.DOUBLE_JUMP));
            }
        }
    }

    // ============ RENDERIZADO ============
    
    /**
     * Renderiza el Energy Shield de Eggman sobre cualquier jugador que lo tenga activo
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        
        // Verificar si el jugador tiene escudo activo
        if (!EggmanSkillsSystem.hasShieldActive(player.getUUID())) {
            return;
        }
        
        PoseStack poseStack = event.getPoseStack();
        
        poseStack.pushPose();
        
        // Centrar el escudo en el jugador
        poseStack.translate(0.0, player.getBbHeight() / 2.0, 0.0);
        
        // Calcular fase de advertencia (últimos 2 segundos)
        float warningPhase = EggmanSkillsSystem.getWarningPhase(player.getUUID());
        
        // Renderizar esfera del escudo
        EnergyShieldRenderer.renderEnergyShield(
            poseStack, 
            event.getMultiBufferSource(), 
            event.getPackedLight(), 
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            warningPhase
        );
        
        // Renderizar arcos eléctricos (solo si no está en advertencia o cada cierto tiempo)
        if (warningPhase < 0.5F || (System.currentTimeMillis() % 400) < 200) {
            EnergyShieldRenderer.renderElectricArcs(
                poseStack, 
                event.getMultiBufferSource(), 
                event.getPackedLight(), 
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
            );
        }
        
        poseStack.popPose();
    }

    // ============ UTILIDADES ============
    
    /**
     * Resetea el estado del cliente de Eggman (útil al cambiar de personaje)
     */
    public static void reset() {
        // Por ahora no hay estado persistente, pero útil para futuro
    }
}