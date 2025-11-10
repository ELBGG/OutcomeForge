package pe.elb.outcomememories;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.elb.outcomememories.init.SoundRegister;
import pe.elb.outcomememories.net.NetworkHandler;

@Mod(Outcomememories.MODID)
public class Outcomememories {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "outcomememories";
    public static final Logger LOGGER = LoggerFactory.getLogger(Outcomememories.class);

    public Outcomememories() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        SoundRegister.SOUND_EVENTS.register(modEventBus);
        NetworkHandler.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Comandos registrados para {}", MODID);
    }

}
