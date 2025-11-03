package pe.elb.shadersowlsafio.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            HyperspaceEffectShaderHandler.updateShader();
            MomentoRevillEffectShaderHandler.updateShader();
            NutriaEffectShaderHandler.updateShader();
            SapoEffectShaderHandler.updateShader();
        }
    }
}