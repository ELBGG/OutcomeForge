package pe.elb.outcomememories.client.overlays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.EscapeInputPacket;
import pe.elb.outcomememories.net.packets.TrapStatePacket;

public class TrapClientHandler {

    private static boolean isTrapped = false;
    private static float escapeProgress = 0.0f;
    private static EscapeHudOverlay hudOverlay = null;
    private static double trapX, trapY, trapZ;

    // Configuración del QTE
    private static final float PROGRESS_PER_PRESS = 8.0F; // 8% por cada press perfecto
    private static final float PROGRESS_MISS_PENALTY = 2.0F; // -2% por fallar timing

    /**
     * Maneja la recepción del paquete de estado de atrapamiento.
     */
    public static void handlePacket(TrapStatePacket packet) {
        isTrapped = packet.isTrapped();
        escapeProgress = packet.getEscapeProgress();

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (isTrapped) {
            trapX = packet.getTrapX();
            trapY = packet.getTrapY();
            trapZ = packet.getTrapZ();
        } else {
            // Si ya no está atrapado, reseteamos el estado
            clearTrapState();
        }
    }

    public static boolean isPlayerTrapped() {
        return isTrapped;
    }

    public static float getEscapeProgress() {
        return escapeProgress;
    }

    public static void setEscapeProgress(float progress) {
        escapeProgress = progress;
    }

    public static double getTrapX() { return trapX; }
    public static double getTrapY() { return trapY; }
    public static double getTrapZ() { return trapZ; }

    /**
     * Llamado cuando el jugador presiona ESPACIO durante el QTE
     * @param isPerfectTiming Si el timing fue perfecto (dentro del sweet spot)
     */
    public static void onSpacePressed(boolean isPerfectTiming) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isTrapped) return;

        // Actualiza la animación visual del botón
        if (hudOverlay != null) {
            EscapeHudOverlay.onSpacePressed();
        }

        // Calcular progreso a agregar basado en timing
        float progressToAdd = isPerfectTiming ? PROGRESS_PER_PRESS : (PROGRESS_PER_PRESS / 2.0F);

        // Enviar el paquete al servidor con el progreso
        NetworkHandler.CHANNEL.sendToServer(
                new EscapeInputPacket(progressToAdd)
        );

        // Feedback local inmediato (será sincronizado por el servidor)
        escapeProgress = Math.min(100.0F, escapeProgress + progressToAdd);
    }

    public static void setHudOverlay(EscapeHudOverlay overlay) {
        hudOverlay = overlay;
    }

    /**
     * Reinicia completamente el estado de atrapamiento.
     */
    public static void clearTrapState() {
        isTrapped = false;
        escapeProgress = 0.0f;
        trapX = 0;
        trapY = 0;
        trapZ = 0;
    }
}