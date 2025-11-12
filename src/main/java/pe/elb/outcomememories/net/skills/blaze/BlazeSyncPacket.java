package pe.elb.outcomememories.net.skills.blaze;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.cache.PlayerSkillsClientCache;

import java.util.UUID;
import java.util.function.Supplier;

public class BlazeSyncPacket {
    
    private final UUID playerUUID;
    private final float solMeter;
    
    public BlazeSyncPacket(UUID playerUUID, float solMeter) {
        this.playerUUID = playerUUID;
        this.solMeter = solMeter;
    }
    
    public static void encode(BlazeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeFloat(msg.solMeter);
    }
    
    public static BlazeSyncPacket decode(FriendlyByteBuf buf) {
        return new BlazeSyncPacket(buf.readUUID(), buf.readFloat());
    }
    
    public static void handle(BlazeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                PlayerSkillsClientCache.updateSolMeter(msg.playerUUID, msg.solMeter);
            });
        });
        ctx.get().setPacketHandled(true);
    }
    
    public UUID getPlayerUUID() { return playerUUID; }
    public float getSolMeter() { return solMeter; }
}