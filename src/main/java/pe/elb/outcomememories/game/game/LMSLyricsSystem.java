package pe.elb.outcomememories.game.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.packets.LMSLyricsPacket;

import java.util.HashMap;
import java.util.Map;

/**
 * Sistema de letras LMS integrado
 * Usa nuestro propio renderer en el cliente
 */
public class LMSLyricsSystem {

    private static final Map<PlayerTypeOM, String> LYRICS_FILES = new HashMap<>();
    private static boolean lyricsInitialized = false;

    static {
        // Mapeo de personajes a archivos JSON en assets
        LYRICS_FILES.put(PlayerTypeOM.SONIC, "soniclms");
        LYRICS_FILES.put(PlayerTypeOM.TAILS, "tailslms");
        LYRICS_FILES.put(PlayerTypeOM.KNUCKLES, "knuckleslms");
        LYRICS_FILES.put(PlayerTypeOM.AMY, "amylms");
        LYRICS_FILES.put(PlayerTypeOM.CREAM, "creamlms");
        LYRICS_FILES.put(PlayerTypeOM.EGGMAN, "eggmanlms");
    }

    /**
     * Inicializa el sistema de letras
     * Los archivos JSON están en assets/outcomememories/subtitles/
     */
    public static void initializeLyrics() {
        if (lyricsInitialized) {
            System.out.println("[LMSLyrics] Sistema ya inicializado");
            return;
        }

        System.out.println("[LMSLyrics] Inicializando sistema de letras...");
        System.out.println("[LMSLyrics] Archivos JSON ubicados en: assets/outcomememories/subtitles/");

        // Verificar que los archivos existan (se carga en cliente)
        for (Map.Entry<PlayerTypeOM, String> entry : LYRICS_FILES.entrySet()) {
            System.out.println("[LMSLyrics] - " + entry.getValue() + ".json -> " + entry.getKey());
        }

        lyricsInitialized = true;
        System.out.println("[LMSLyrics] ✓ Sistema inicializado");
    }

    /**
     * Reproduce letras para un jugador según su personaje
     * Envía un packet al cliente para que cargue y reproduzca las letras
     */
    public static void playLyricsForPlayer(ServerPlayer player, PlayerTypeOM characterType) {
        String fileName = LYRICS_FILES.get(characterType);

        if (fileName == null) {
            System.err.println("[LMSLyrics] No hay archivo de letras para: " + characterType);
            return;
        }

        System.out.println("[LMSLyrics] Enviando comando de reproducción: " + fileName +
                " para " + player.getName().getString());

        try {
            // Enviar packet al cliente con el nombre del archivo y comando de inicio
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new LMSLyricsPacket(fileName, true)
            );

            System.out.println("[LMSLyrics] ✓ Packet enviado correctamente");

        } catch (Exception e) {
            System.err.println("[LMSLyrics] Error enviando packet de letras:");
            e.printStackTrace();
        }
    }

    /**
     * Detiene las letras de un jugador
     */
    public static void stopLyricsForPlayer(ServerPlayer player) {
        System.out.println("[LMSLyrics] Deteniendo letras para " + player.getName().getString());

        try {
            // Enviar packet al cliente con comando de detener
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new LMSLyricsPacket("", false)
            );

            System.out.println("[LMSLyrics] ✓ Letras detenidas");

        } catch (Exception e) {
            System.err.println("[LMSLyrics] Error deteniendo letras:");
            e.printStackTrace();
        }
    }

    /**
     * Verifica si el sistema está disponible
     */
    public static boolean isAvailable() {
        return lyricsInitialized;
    }

    /**
     * Resetea el sistema (útil para recargar)
     */
    public static void reset() {
        lyricsInitialized = false;
        System.out.println("[LMSLyrics] Sistema reseteado");
    }

    /**
     * Obtiene el nombre del archivo de letras para un personaje
     */
    public static String getLyricsFileName(PlayerTypeOM character) {
        return LYRICS_FILES.get(character);
    }
}