package pe.elb.outcomememories.net.skills.tails;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.cache.PlayerSkillsClientCache;

import java.util.UUID;
import java.util.function.Supplier;

public class TailsSyncPacket {
    
    private final SyncType syncType;
    private final UUID playerUUID;
    
    private final float floatData;
    private final boolean booleanData;
    private final String stringData;
    
    public enum SyncType {
        GLIDE_ENERGY,
        LASER_STATE
    }
    
    public TailsSyncPacket(UUID playerUUID, float energyPercent, boolean isGliding) {
        this.syncType = SyncType.GLIDE_ENERGY;
        this.playerUUID = playerUUID;
        this.floatData = energyPercent;
        this.booleanData = isGliding;
        this.stringData = "";
    }
    
    public TailsSyncPacket(UUID playerUUID, String phase, float progress) {
        this.syncType = SyncType.LASER_STATE;
        this.playerUUID = playerUUID;
        this.floatData = progress;
        this.booleanData = false;
        this.stringData = phase;
    }
    
    private TailsSyncPacket(SyncType syncType, UUID playerUUID, float floatData, 
                            boolean booleanData, String stringData) {
        this.syncType = syncType;
        this.playerUUID = playerUUID;
        this.floatData = floatData;
        this.booleanData = booleanData;
        this.stringData = stringData;
    }
    
    public static void encode(TailsSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.syncType);
        buf.writeUUID(msg.playerUUID);
        buf.writeFloat(msg.floatData);
        buf.writeBoolean(msg.booleanData);
        buf.writeUtf(msg.stringData);
    }
    
    public static TailsSyncPacket decode(FriendlyByteBuf buf) {
        SyncType syncType = buf.readEnum(SyncType.class);
        UUID playerUUID = buf.readUUID();
        float floatData = buf.readFloat();
        boolean booleanData = buf.readBoolean();
        String stringData = buf.readUtf();
        
        return new TailsSyncPacket(syncType, playerUUID, floatData, booleanData, stringData);
    }
    
    public static void handle(TailsSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                switch (msg.syncType) {
                    case GLIDE_ENERGY -> PlayerSkillsClientCache.updateGlideData(
                        msg.playerUUID, msg.floatData, msg.booleanData
                    );
                    
                    case LASER_STATE -> PlayerSkillsClientCache.updateLaserData(
                        msg.playerUUID, msg.stringData, msg.floatData
                    );
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}