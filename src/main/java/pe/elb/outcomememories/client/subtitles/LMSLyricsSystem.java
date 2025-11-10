package pe.elb.outcomememories.client.subtitles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pe.elb.outcomememories.game.PlayerTypeOM;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Sistema unificado de gestión de letras LMS
 * 
 * Maneja:
 * - Carga de archivos JSON desde assets
 * - Control de reproducción de letras
 * - Datos de subtítulos individuales
 */
@OnlyIn(Dist.CLIENT)
public class LMSLyricsSystem {

    // ============ SINGLETON ============
    
    private static final LMSLyricsSystem INSTANCE = new LMSLyricsSystem();
    private static final Gson GSON = new GsonBuilder().create();

    public static LMSLyricsSystem getInstance() {
        return INSTANCE;
    }

    // ============ ESTADO DEL SISTEMA ============
    
    private final Map<PlayerTypeOM, String> lyricsFiles = new HashMap<>();
    private final Map<PlayerTypeOM, LyricsFile> loadedLyrics = new HashMap<>();

    private List<LyricEntry> currentLyrics;
    private int currentIndex = 0;
    private boolean isPlaying = false;

    private LMSLyricsSystem() {
        // Mapeo de personajes a archivos
        lyricsFiles.put(PlayerTypeOM.AMY, "amylms");
        lyricsFiles.put(PlayerTypeOM.SONIC, "soniclms");
        lyricsFiles.put(PlayerTypeOM.TAILS, "tailslms");
        lyricsFiles.put(PlayerTypeOM.KNUCKLES, "knuckleslms");
        lyricsFiles.put(PlayerTypeOM.CREAM, "creamlms");
        lyricsFiles.put(PlayerTypeOM.EGGMAN, "eggmanlms");
    }

    // ============ CARGA DE LETRAS ============

    /**
     * Carga los archivos de letras desde resources
     */
    public void loadLyrics(ResourceManager resourceManager) {
        System.out.println("[LMSLyrics] Cargando archivos de letras...");

        int loaded = 0;
        for (Map.Entry<PlayerTypeOM, String> entry : lyricsFiles.entrySet()) {
            PlayerTypeOM character = entry.getKey();
            String fileName = entry.getValue();

            try {
                ResourceLocation location = new ResourceLocation("outcomememories",
                        "subtitles/" + fileName + ".json");

                Optional<Resource> resourceOpt = resourceManager.getResource(location);

                if (resourceOpt.isPresent()) {
                    Resource resource = resourceOpt.get();

                    try (InputStream stream = resource.open();
                         InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                        LyricsFile lyricsFile = GSON.fromJson(reader, LyricsFile.class);

                        if (lyricsFile != null && lyricsFile.getEntries() != null) {
                            loadedLyrics.put(character, lyricsFile);
                            loaded++;
                            System.out.println("[LMSLyrics] ✓ Cargado: " + fileName + ".json (" +
                                    lyricsFile.getEntries().size() + " entradas)");
                        } else {
                            System.err.println("[LMSLyrics] ✗ Archivo inválido: " + fileName + ".json");
                        }
                    }
                } else {
                    System.err.println("[LMSLyrics] ✗ No encontrado: " + location);
                }

            } catch (Exception e) {
                System.err.println("[LMSLyrics] Error cargando " + fileName + ".json:");
                e.printStackTrace();
            }
        }

        System.out.println("[LMSLyrics] Resumen: " + loaded + "/" + lyricsFiles.size() + " archivos cargados");
    }

    // ============ CONTROL DE REPRODUCCIÓN ============

    /**
     * Inicia la reproducción de letras para un personaje
     */
    public void startLyrics(PlayerTypeOM character) {
        LyricsFile lyricsFile = loadedLyrics.get(character);

        if (lyricsFile == null || lyricsFile.getEntries() == null || lyricsFile.getEntries().isEmpty()) {
            System.err.println("[LMSLyrics] No hay letras cargadas para: " + character);
            return;
        }

        currentLyrics = lyricsFile.getEntries();
        currentIndex = 0;
        isPlaying = true;
        showNextLyric();

        System.out.println("[LMSLyrics] Letras iniciadas para: " + character +
                " (" + currentLyrics.size() + " entradas)");
    }

    /**
     * Inicia las letras por nombre de archivo (usado por el packet)
     */
    public void startLyricsByFileName(String fileName) {
        ResourceManager resourceManager = net.minecraft.client.Minecraft.getInstance().getResourceManager();

        try {
            ResourceLocation location = new ResourceLocation("outcomememories",
                    "subtitles/" + fileName + ".json");

            Optional<Resource> resourceOpt = resourceManager.getResource(location);

            if (resourceOpt.isPresent()) {
                Resource resource = resourceOpt.get();

                try (InputStream stream = resource.open();
                     InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

                    LyricsFile lyricsFile = GSON.fromJson(reader, LyricsFile.class);

                    if (lyricsFile != null && lyricsFile.getEntries() != null) {
                        this.currentLyrics = lyricsFile.getEntries();
                        this.currentIndex = 0;
                        this.isPlaying = true;
                        this.showNextLyric();

                        System.out.println("[LMSLyrics] Letras iniciadas: " + fileName +
                                " (" + this.currentLyrics.size() + " entradas)");
                    } else {
                        System.err.println("[LMSLyrics] Archivo inválido: " + fileName);
                    }
                }
            } else {
                System.err.println("[LMSLyrics] Archivo no encontrado: " + location);
            }

        } catch (Exception e) {
            System.err.println("[LMSLyrics] Error cargando " + fileName + ":");
            e.printStackTrace();
        }
    }

    /**
     * Detiene las letras
     */
    public void stopLyrics() {
        isPlaying = false;
        currentLyrics = null;
        currentIndex = 0;
        LMSLyricsRenderer.getInstance().clearSubtitle();

        System.out.println("[LMSLyrics] Letras detenidas");
    }

    /**
     * Tick del sistema
     */
    public void tick() {
        if (!isPlaying || currentLyrics == null) {
            return;
        }

        LMSLyricsRenderer.getInstance().tick();

        // Si el subtítulo actual terminó, mostrar el siguiente
        if (LMSLyricsRenderer.getInstance().getCurrentSubtitle() == null) {
            showNextLyric();
        }
    }

    /**
     * Muestra la siguiente lírica
     */
    private void showNextLyric() {
        if (currentLyrics == null || currentLyrics.isEmpty()) {
            return;
        }

        if (currentIndex >= currentLyrics.size()) {
            // Si llegamos al final, verificar si la última entrada es un stop
            if (!currentLyrics.isEmpty()) {
                LyricEntry lastEntry = currentLyrics.get(currentLyrics.size() - 1);
                if (lastEntry.isStop()) {
                    stopLyrics();
                    System.out.println("[LMSLyrics] Secuencia finalizada (stop encontrado)");
                    return;
                }
            }

            // Loop: volver al inicio
            currentIndex = 0;
        }

        LyricEntry entry = currentLyrics.get(currentIndex);
        currentIndex++;

        // Si es un stop, detener la reproducción
        if (entry.isStop()) {
            stopLyrics();
            System.out.println("[LMSLyrics] Reproducción detenida (stop)");
            return;
        }

        // Si es una pausa, crear subtítulo invisible
        if (entry.isPause()) {
            Subtitle pauseSubtitle = new Subtitle("", entry.getDuration());
            LMSLyricsRenderer.getInstance().showSubtitle(pauseSubtitle);
            return;
        }

        // Crear y configurar subtítulo
        Subtitle subtitle = new Subtitle(entry.getText(), entry.getDuration());
        subtitle.setMetadata("textScale", entry.getScale());
        subtitle.setMetadata("position", entry.getPosition());

        if (entry.getBackgroundColor() != null) {
            subtitle.setMetadata("backgroundColor", entry.getBackgroundColor());
        }

        LMSLyricsRenderer.getInstance().showSubtitle(subtitle);
    }

    // ============ UTILIDADES ============

    public boolean isPlaying() {
        return isPlaying;
    }

    public void clearLoadedLyrics() {
        loadedLyrics.clear();
        stopLyrics();
    }

    // ============ CLASE INTERNA: SUBTITLE ============

    /**
     * Representa un subtítulo LMS con metadata
     */
    public static class Subtitle {
        private final String text;
        private final int durationTicks;
        private int ticksRemaining;
        private final Map<String, Object> metadata;

        public Subtitle(String text, int durationTicks) {
            this.text = text;
            this.durationTicks = durationTicks;
            this.ticksRemaining = durationTicks;
            this.metadata = new HashMap<>();
        }

        public String getText() {
            return text;
        }

        public int getDurationTicks() {
            return durationTicks;
        }

        public boolean tick() {
            if (ticksRemaining > 0) {
                ticksRemaining--;
                return ticksRemaining > 0;
            }
            return false;
        }

        public void reset() {
            ticksRemaining = durationTicks;
        }

        public void setMetadata(String key, Object value) {
            metadata.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T getMetadata(String key, T defaultValue) {
            return (T) metadata.getOrDefault(key, defaultValue);
        }

        public boolean hasMetadata(String key) {
            return metadata.containsKey(key);
        }
    }

    // ============ CLASE INTERNA: LYRICS FILE ============

    /**
     * Estructura de archivo JSON de letras
     */
    public static class LyricsFile {
        @SerializedName("entries")
        private List<LyricEntry> entries;

        public List<LyricEntry> getEntries() {
            return entries;
        }
    }

    /**
     * Entrada individual de letra
     */
    public static class LyricEntry {
        @SerializedName("text")
        private String text;

        @SerializedName("duration")
        private int duration;

        @SerializedName("scale")
        private Float scale;

        @SerializedName("backgroundColor")
        private String backgroundColor;

        @SerializedName("position")
        private String position;

        @SerializedName("pause")
        private Boolean pause;

        @SerializedName("stop")
        private Boolean stop;

        public String getText() {
            return text != null ? text : "";
        }

        public int getDuration() {
            return duration;
        }

        public float getScale() {
            return scale != null ? scale : 1.0f;
        }

        public String getBackgroundColor() {
            return backgroundColor;
        }

        public String getPosition() {
            return position != null ? position : "bottom";
        }

        public boolean isPause() {
            return pause != null && pause;
        }

        public boolean isStop() {
            return stop != null && stop;
        }
    }
}