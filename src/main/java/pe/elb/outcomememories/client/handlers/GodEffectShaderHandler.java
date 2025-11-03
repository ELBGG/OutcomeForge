package pe.elb.outcomememories.client.shaders;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.game.skills.exe.shader.GodsTrickeryShader;
import pe.elb.outcomememories.init.EffectsRegister;


@Mod.EventBusSubscriber(
        value = {Dist.CLIENT},
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public class GodEffectShaderHandler {
    public static void updateShader() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null && player.hasEffect(EffectsRegister.GOD.get())) {
            GodsTrickeryShader.get().setActive(true);
        } else {
            GodsTrickeryShader.get().setActive(false);
        }
    }
}