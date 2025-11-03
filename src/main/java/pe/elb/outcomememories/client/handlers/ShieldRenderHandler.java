package pe.elb.outcomememories.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.client.renderer.EnergyShieldRenderer;
import pe.elb.outcomememories.game.skills.eggman.EnergyShieldSkill;

@Mod.EventBusSubscriber(modid = Outcomememories.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShieldRenderHandler {
    
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        
        // Verificar si el jugador tiene escudo activo
        if (!EnergyShieldSkill.hasShieldActive(player.getUUID())) {
            return;
        }
        
        PoseStack poseStack = event.getPoseStack();
        
        poseStack.pushPose();
        
        // Centrar el escudo en el jugador
        poseStack.translate(0.0, player.getBbHeight() / 2.0, 0.0);
        
        // Calcular fase de advertencia (últimos 2 segundos)
        float warningPhase = EnergyShieldSkill.getWarningPhase(player.getUUID());
        
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
}