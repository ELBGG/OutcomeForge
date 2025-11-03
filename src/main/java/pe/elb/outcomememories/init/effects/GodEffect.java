package pe.elb.shadersowlsafio.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class HyperspaceEffect extends MobEffect {
    public HyperspaceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00FFFF); // Color cian
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false; // No necesita tickeo extra
    }
}