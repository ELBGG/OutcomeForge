package pe.elb.outcomememories.net.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.subtitles.LMSLyricsSystem;

import java.util.function.Supplier;

/**
 * Packet para controlar las letras LMS desde el servidor
 */
public class LMSLyricsPacket {

    private final String lyricsFileName;
    private final boolean start;

    public LMSLyricsPacket(String lyricsFileName, boolean start) {
        this.lyricsFileName = lyricsFileName;
        this.start = start;
    }

    public static void encode(LMSLyricsPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.lyricsFileName);
        buf.writeBoolean(msg.start);
    }

    public static LMSLyricsPacket decode(FriendlyByteBuf buf) {
        return new LMSLyricsPacket(buf.readUtf(), buf.readBoolean());
    }

    public static void handle(LMSLyricsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClientSide(msg.lyricsFileName, msg.start));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClientSide(String fileName, boolean start) {
        if (start && !fileName.isEmpty()) {
            System.out.println("[LMSLyrics-Client] Recibido comando para reproducir: " + fileName);
            // Llamar al método de instancia, no al estático
            LMSLyricsSystem.getInstance().startLyricsByFileName(fileName);
        } else {
            System.out.println("[LMSLyrics-Client] Recibido comando para detener");
            LMSLyricsSystem.getInstance().stopLyrics();
        }
    }
}