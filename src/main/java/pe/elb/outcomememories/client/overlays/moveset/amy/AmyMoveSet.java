package pe.elb.outcomememories.client.overlays.moveset.amy;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;


public enum AmyMoveSet {
    HEAL("heal", KeyType.SECONDARY, "Heal"),
    DASH("dash", KeyType.PRIMARY, "Dash");

    private final String textureName;
    private final KeyType keyType;
    private final String displayName;

    AmyMoveSet(String textureName, KeyType keyType, String displayName) {
        this.textureName = textureName;
        this.keyType = keyType;
        this.displayName = displayName;
    }

    public ResourceLocation getTexture() {
        return new ResourceLocation("outcomememories", "textures/moveset/cream/" + textureName + ".png");
    }

    /**
     * Devuelve la tecla asociada como texto (por ejemplo "Q" o "SPACE").
     * Intenta resolver el KeyMapping en el cliente; si no está disponible devuelve un fallback.
     */
    public String getKeybind() {
        // Solo acceder a KeyMappings en tiempo de ejecución del cliente
        if (FMLEnvironment.dist.isClient()) {
            try {
                return switch (this.keyType) {
                    case PRIMARY ->
                            pe.elb.outcomememories.client.KeyBindings.ABILITY_PRIMARY.getTranslatedKeyMessage().getString();
                    case SECONDARY ->
                            pe.elb.outcomememories.client.KeyBindings.ABILITY_SECONDARY.getTranslatedKeyMessage().getString();
                    case SPECIAL ->
                            pe.elb.outcomememories.client.KeyBindings.ABILITY_SPECIAL.getTranslatedKeyMessage().getString();
                };
            } catch (Throwable t) {
            }
        }

        return switch (this.keyType) {
            case PRIMARY, SECONDARY, SPECIAL -> "";
        };
    }

    public String getDisplayName() {
        return displayName;
    }

    private enum KeyType {
        PRIMARY,
        SECONDARY,
        SPECIAL
    }
}