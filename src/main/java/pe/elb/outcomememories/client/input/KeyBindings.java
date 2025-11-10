package pe.elb.outcomememories.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyBindings {
    
    // Categoría para las habilidades
    public static final String CATEGORY_ABILITIES = "key.categories.outcomememories.abilities";
    
    // Habilidades principales
    public static final KeyMapping ABILITY_PRIMARY = new KeyMapping(
        "key.outcomememories.ability.primary",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Q,
        CATEGORY_ABILITIES
    );
    
    public static final KeyMapping ABILITY_SECONDARY = new KeyMapping(
        "key.outcomememories.ability.secondary",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_E,
        CATEGORY_ABILITIES
    );
    
    public static final KeyMapping ABILITY_SPECIAL = new KeyMapping(
        "key.outcomememories.ability.special",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_X,
        CATEGORY_ABILITIES
    );
    
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ABILITY_PRIMARY);
        event.register(ABILITY_SECONDARY);
        event.register(ABILITY_SPECIAL);
    }
}