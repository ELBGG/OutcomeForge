package pe.elb.outcomememories.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SoundRegister {

    private static final String MODID = "outcomememories";

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    private static RegistryObject<SoundEvent> registerSound(String name) {
        ResourceLocation loc = new ResourceLocation(MODID, name);
        System.out.println("[SoundRegister] Registering sound: " + loc);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(loc));
    }

    public static final RegistryObject<SoundEvent> AMY_LMS = registerSound("amylms");
    public static final RegistryObject<SoundEvent> CREAM_LMS = registerSound("creamlms");
    public static final RegistryObject<SoundEvent> TAILS_LMS = registerSound("tailslms");
    public static final RegistryObject<SoundEvent> KNUCKLES_LMS = registerSound("knuckleslms");
    public static final RegistryObject<SoundEvent> EGGMAN_LMS = registerSound("eggmanlms");
    public static final RegistryObject<SoundEvent> SONIC_LSM = registerSound("soniclms");

    private SoundRegister() {}
}