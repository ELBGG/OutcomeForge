package pe.elb.outcomememories.net.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.handlers.LMSClientHandler;

import java.util.function.Supplier;

/**
 * Packet para activar/desactivar el sistema de zoom pulsante por BPM
 */
public class LMSBeatZoomPacket {
    
    private final String musicTrack;
    private final boolean enable;
    
    public LMSBeatZoomPacket(String musicTrack, boolean enable) {
        this.musicTrack = musicTrack;
        this.enable = enable;
    }
    
    // Serialización
    public static void encode(LMSBeatZoomPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.musicTrack);
        buffer.writeBoolean(packet.enable);
    }
    
    // Deserialización
    public static LMSBeatZoomPacket decode(FriendlyByteBuf buffer) {
        String musicTrack = buffer.readUtf();
        boolean enable = buffer.readBoolean();
        return new LMSBeatZoomPacket(musicTrack, enable);
    }
    
    // Manejo en el cliente
    public static void handle(LMSBeatZoomPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Este código se ejecuta en el CLIENTE
            LMSClientHandler.setupBeatZoom(packet.musicTrack, packet.enable);
        });
        ctx.get().setPacketHandled(true);
    }
}