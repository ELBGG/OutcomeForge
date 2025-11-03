package pe.elb.outcomememories.game.skills.exe.shader;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.lodestar.lodestone.systems.postprocess.PostProcessor;

@OnlyIn(Dist.CLIENT)
public class SapoShader extends PostProcessor {
    public static SapoShader INSTANCE;

    public static void initialize() {
        if (INSTANCE == null) {
            INSTANCE = new SapoShader();
            INSTANCE.setActive(false);
        }
    }

    public static SapoShader get() {
        if (INSTANCE == null) {
            INSTANCE = new SapoShader();
        }
        return INSTANCE;
    }

    private SapoShader() {}

    public ResourceLocation getPostChainLocation() {
        return new ResourceLocation("cosmicsafio", "sapo");
    }

    public void beforeProcess(PoseStack poseStack) {
        // Actualiza el uniform de tiempo para animar el shader
        float time = (float) (System.currentTimeMillis() % 100000) / 1000.0F;
        this.effects[0].safeGetUniform("frameTimeCounter").set(time);
    }

    public void afterProcess() {}

    public void updateShaderState() {}
}