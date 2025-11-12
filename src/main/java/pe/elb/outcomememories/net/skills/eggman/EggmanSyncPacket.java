package pe.elb.outcomememories.net.skills.eggman;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.cache.PlayerSkillsClientCache;

import java.util.UUID;
import java.util.function.Supplier;

public class EggmanSyncPacket {
    
    private final UUID playerUUID;
    private final SyncType syncType;
    private final boolean booleanData;
    private final long longData;
    
    public enum SyncType {
        SHIELD_ACTIVE,
        SHIELD_BROKEN
    }
    
    public EggmanSyncPacket(UUID playerUUID, SyncType syncType, boolean booleanData, long longData) {
        this.playerUUID = playerUUID;
        this.syncType = syncType;
        this.booleanData = booleanData;
        this.longData = longData;
    }
    
    public static void encode(EggmanSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeEnum(msg.syncType);
        buf.writeBoolean(msg.booleanData);
        buf.writeLong(msg.longData);
    }
    
    public static EggmanSyncPacket decode(FriendlyByteBuf buf) {
        return new EggmanSyncPacket(
            buf.readUUID(),
            buf.readEnum(SyncType.class),
            buf.readBoolean(),
            buf.readLong()
        );
    }
    
    public static void handle(EggmanSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                switch (msg.syncType) {
                    case SHIELD_ACTIVE -> PlayerSkillsClientCache.setShieldActive(
                        msg.playerUUID, 
                        msg.booleanData, 
                        msg.longData
                    );
                    
                    case SHIELD_BROKEN -> PlayerSkillsClientCache.setShieldActive(
                        msg.playerUUID, 
                        false, 
                        0L
                    );
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}