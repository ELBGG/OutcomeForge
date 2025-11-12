package pe.elb.outcomememories.client.handlers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import pe.elb.outcomememories.Outcomememories;

import java.util.Objects;

/**
 * Handler unificado del cliente para el sistema Last Man Standing (LMS)
 * 
 * Maneja:
 * - Reproducción de música temática en loop
 * - Zoom pulsante sincronizado con el BPM
 * - Efectos visuales de cámara
 */
@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LMSClientHandler {

    // ============ MÚSICA ============
    private static SoundInstance currentMusic = null;
    private static String currentTrack = "";

    // ============ BEAT ZOOM ============
    private static boolean isZoomActive = false;
    private static float currentBPM = 0.0F;
    private static float zoomIntensity = 0.08F;

    // Estado del beat
    private static long musicStartTime = 0L;
    private static float currentZoomScale = 1.0F; // 1.0 = sin zoom
    private static float targetZoomScale = 1.0F;

    // Constantes de zoom
    private static final float ZOOM_DURATION_FRACTION = 0.35F;
    private static final float LERP_SPEED = 0.18F;

    // ============ MÚSICA - CONTROL ============

    /**
     * Reproduce una pista de música LMS en loop
     */
    public static void playMusic(String trackName) {
        System.out.println("[LMS] ===== PLAY MUSIC REQUEST =====");
        System.out.println("[LMS] Track requested: " + trackName);

        stopMusic();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            System.err.println("[LMS] ERROR: MC components not ready!");
            return;
        }

        try {
            ResourceLocation soundLocation = new ResourceLocation("outcomememories", trackName);
            System.out.println("[LMS] Sound location: " + soundLocation);

            // Crear instancia de sonido AMBIENT que hace loop
            currentMusic = new SimpleSoundInstance(
                    soundLocation,
                    SoundSource.AMBIENT,  // Categoría AMBIENT en lugar de MUSIC
                    0.8F,  // Volumen
                    1.0F,  // Pitch
                    SoundInstance.createUnseededRandom(),
                    false,  // Loop
                    0,
                    SoundInstance.Attenuation.NONE,
                    0.0D, 0.0D, 0.0D,
                    true
            );

            System.out.println("[LMS] Playing with AMBIENT category (loop enabled)");

            mc.getSoundManager().play(currentMusic);
            currentTrack = trackName;

            System.out.println("[LMS] ✓ Music playback started!");

        } catch (Exception e) {
            System.err.println("[LMS] ERROR playing music:");
            e.printStackTrace();
        }
    }

    /**
     * Detiene la música actual
     */
    public static void stopMusic() {
        if (currentMusic != null) {
            System.out.println("[LMS] Stopping music: " + currentTrack);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().stop(currentMusic);
            }
            currentMusic = null;
            currentTrack = "";
        }
    }

    /**
     * Verifica si hay música reproduciéndose
     */
    public static boolean isMusicPlaying() {
        if (currentMusic == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return false;
        return mc.getSoundManager().isActive(currentMusic);
    }

    /**
     * Obtiene el nombre de la pista actual
     */
    public static String getCurrentTrack() {
        return currentTrack;
    }

    // ============ BEAT ZOOM - CONFIGURACIÓN ============

    /**
     * Configura el zoom pulsante basado en la canción
     * Este método es llamado desde LMSBeatZoomPacket
     */
    public static void setupBeatZoom(String songTrack, boolean enable) {
        isZoomActive = enable;

        if (!enable) {
            resetBeatZoom();
            return;
        }

        // Configurar BPM según la canción
        switch (songTrack.toLowerCase()) {
            case "soniclms" -> {
                currentBPM = 174.0F;
                zoomIntensity = 0.1F;
            }
            case "tailslms" -> {
                currentBPM = 188.0F;
                zoomIntensity = 0.1F;
            }
            case "knuckleslms" -> {
                currentBPM = 142.0F;
                zoomIntensity = 0.1F;
            }
            case "amylms", "creamlms" -> {
                currentBPM = 170.0F;
                zoomIntensity = 0.1F;
            }
            case "eggmanlms" -> {
                currentBPM = 178.0F;
                zoomIntensity = 0.1F;
            }
            case "blazelms" -> {
                currentBPM = 168.0F;
                zoomIntensity = 0.1F;
            }
            case "metalsoniclms" -> {
                currentBPM = 154.0F;
                zoomIntensity = 0.1F;
            }
            default -> {
                currentBPM = 140.0F;
                zoomIntensity = 0.1F;
            }
        }

        musicStartTime = System.currentTimeMillis();
        currentZoomScale = 1.0F;
        targetZoomScale = 1.0F;

        Outcomememories.LOGGER.info("[LMS] Beat Zoom activado - BPM: {}, Intensidad: {}%",
                currentBPM, (int)(zoomIntensity * 100));
    }

    /**
     * Resetea el sistema de zoom
     */
    public static void resetBeatZoom() {
        if (!isZoomActive) return;

        isZoomActive = false;
        currentZoomScale = 1.0F;
        targetZoomScale = 1.0F;

        Outcomememories.LOGGER.info("[LMS] Beat Zoom desactivado");
    }

    // ============ BEAT ZOOM - LÓGICA ============

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isZoomActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) return;

        updateBeatZoom();
    }

    /**
     * Actualiza el zoom basado en el BPM
     */
    private static void updateBeatZoom() {
        long now = System.currentTimeMillis();
        long elapsedMs = now - musicStartTime;

        // Calcular el intervalo entre beats (en ms)
        float beatIntervalMs = (60.0F / currentBPM) * 1000.0F;

        // Calcular en qué punto del beat estamos (0.0 a 1.0)
        float beatProgress = (elapsedMs % (long) beatIntervalMs) / beatIntervalMs;

        // Calcular el zoom OBJETIVO
        if (beatProgress < ZOOM_DURATION_FRACTION) {
            float zoomProgress = beatProgress / ZOOM_DURATION_FRACTION;

            // Curva sinusoidal suave (ease-in-out)
            float curve = (float) (Math.sin(zoomProgress * Math.PI));

            // Aplicar una curva de ease adicional para mayor suavidad
            curve = (float) Math.pow(curve, 0.8); // Suaviza los picos

            // Escala: 1.0 + intensidad en el pico
            targetZoomScale = 1.0F + (curve * zoomIntensity);
        } else {
            targetZoomScale = 1.0F;
        }

        // Interpolar SUAVEMENTE hacia el objetivo
        currentZoomScale = Mth.lerp(LERP_SPEED, currentZoomScale, targetZoomScale);
    }

    /**
     * Modifica el FOV de la cámara durante el render
     * Esto es MÁS SUAVE que cambiar la configuración del cliente
     */
    @SubscribeEvent
    public static void onComputeFOV(ViewportEvent.ComputeFov event) {
        if (!isZoomActive) return;

        // Modificar el FOV multiplicando por la escala
        // Nota: FOV más alto = zoom out, FOV más bajo = zoom in
        // Invertimos la escala para que el "zoom in" visual se sienta correcto
        double inversedScale = 2.0 - currentZoomScale; // 1.05 -> 0.95 (zoom in visual)

        double newFOV = event.getFOV() * inversedScale;
        event.setFOV(newFOV);
    }

    // ============ UTILIDADES ============

    /**
     * Resetea completamente el sistema LMS del cliente
     */
    public static void resetAll() {
        stopMusic();
        resetBeatZoom();
        
        Outcomememories.LOGGER.info("[LMS] Sistema del cliente reseteado completamente");
    }

    /**
     * Verifica si el sistema LMS está activo en el cliente
     */
    public static boolean isActive() {
        return isMusicPlaying() || isZoomActive;
    }

    /**
     * Obtiene el BPM actual
     */
    public static float getCurrentBPM() {
        return currentBPM;
    }

    /**
     * Obtiene la intensidad del zoom actual
     */
    public static float getZoomIntensity() {
        return zoomIntensity;
    }

    /**
     * Obtiene la escala de zoom actual (para debug)
     */
    public static float getCurrentZoomScale() {
        return currentZoomScale;
    }

    /**
     * Verifica si el zoom está activo
     */
    public static boolean isZoomActive() {
        return isZoomActive;
    }

    /**
     * Cambia la intensidad del zoom en tiempo real
     */
    public static void setZoomIntensity(float intensity) {
        zoomIntensity = Math.max(0.0F, Math.min(0.15F, intensity));
        Outcomememories.LOGGER.info("[LMS] Intensidad del zoom cambiada a: {}%", 
            (int)(zoomIntensity * 100));
    }

    /**
     * Cambia el BPM en tiempo real (útil para sincronización manual)
     */
    public static void setBPM(float bpm) {
        currentBPM = Math.max(60.0F, Math.min(200.0F, bpm));
        musicStartTime = System.currentTimeMillis(); // Resincronizar
        Outcomememories.LOGGER.info("[LMS] BPM cambiado a: {}", currentBPM);
    }
}