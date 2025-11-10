package pe.elb.outcomememories.client.cache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import pe.elb.outcomememories.game.PlayerTypeOM;

/**
 * Guarda información del jugador cliente (recibida del servidor)
 * y controla el estado especial del "Executioner Mode".
 */
public class ClientPlayerData {

    private static PlayerTypeOM type = null;
    private static boolean executionerMode = false;

    public static void setType(PlayerTypeOM t) {
        type = t;
    }

    public static PlayerTypeOM getType() {
        return type;
    }

    public static boolean hasType() {
        return type != null;
    }

    public static void clear() {
        type = null;
        executionerMode = false;
    }

    /** Indica si el jugador local está en modo verdugo. */
    public static boolean isExecutionerMode() {
        return executionerMode;
    }

    /** Activa el modo verdugo (Executioner) localmente. */
    public static void enableExecutionerMode(LocalPlayer player) {
        if (executionerMode) return;
        executionerMode = true;

        var mc = Minecraft.getInstance();


        // Forzar cámara 3ª persona
        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);

        // Efecto audiovisual (opcional)
        if (player != null && player.level() != null) {
            player.level().playLocalSound(
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS,
                    1.0f, 0.6f, false
            );
        }
    }

    /** Desactiva el modo verdugo y restaura los controles. */
    public static void disableExecutionerMode() {
        if (!executionerMode) return;
        executionerMode = false;


        Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
    }
}
