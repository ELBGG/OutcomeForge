package pe.elb.outcomememories.net.skills.metalsonic;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class MetalSonicSyncPacket {
    
    private final UUID playerUUID;
    private final SyncType syncType;
    private final float floatData;
    
    public enum SyncType {
        PROXIMITY_WARNING
    }
    
    public MetalSonicSyncPacket(UUID playerUUID, SyncType syncType, float floatData) {
        this.playerUUID = playerUUID;
        this.syncType = syncType;
        this.floatData = floatData;
    }
    
    public static void encode(MetalSonicSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeEnum(msg.syncType);
        buf.writeFloat(msg.floatData);
    }
    
    public static MetalSonicSyncPacket decode(FriendlyByteBuf buf) {
        return new MetalSonicSyncPacket(
            buf.readUUID(),
            buf.readEnum(SyncType.class),
            buf.readFloat()
        );
    }
    
    public static void handle(MetalSonicSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                if (msg.syncType == SyncType.PROXIMITY_WARNING) {
                    // ClientMetalSonicHandler.playProximityBeep(msg.floatData);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
    
    public UUID getPlayerUUID() { return playerUUID; }
    public SyncType getSyncType() { return syncType; }
    public float getFloatData() { return floatData; }
}