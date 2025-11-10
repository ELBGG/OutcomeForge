package pe.elb.outcomememories.net.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.handlers.LMSClientHandler;

import java.util.function.Supplier;

/**
 * Packet para controlar la música de LMS en el cliente
 */
public class LMSMusicPacket {
    
    private final String musicTrack;
    private final boolean play;
    
    public LMSMusicPacket(String musicTrack, boolean play) {
        this.musicTrack = musicTrack;
        this.play = play;
    }
    
    public static void encode(LMSMusicPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.musicTrack);
        buf.writeBoolean(msg.play);
    }
    
    public static LMSMusicPacket decode(FriendlyByteBuf buf) {
        return new LMSMusicPacket(buf.readUtf(), buf.readBoolean());
    }
    
    public static void handle(LMSMusicPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Ejecutar solo en el lado del cliente
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClientSide(msg.musicTrack, msg.play));
        });
        ctx.get().setPacketHandled(true);
    }
    
    /**
     * Maneja el packet en el lado del cliente
     */
    private static void handleClientSide(String track, boolean play) {
        if (play && !track.isEmpty()) {
            LMSClientHandler.playMusic(track);
        } else {
            LMSClientHandler.stopMusic();
        }
    }
    
    public String getMusicTrack() {
        return musicTrack;
    }
    
    public boolean shouldPlay() {
        return play;
    }
}