package pe.elb.outcomememories.net.skills.exe;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.cache.InvisibilityClientCache;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class ExeSyncPacket {
    
    private final SyncType syncType;
    private final List<SurvivorInfo> nearbySurvivors;
    
    public enum SyncType {
        INVISIBILITY_NEARBY
    }
    
    public ExeSyncPacket(List<SurvivorInfo> nearbySurvivors) {
        this.syncType = SyncType.INVISIBILITY_NEARBY;
        this.nearbySurvivors = nearbySurvivors;
    }
    
    public static class SurvivorInfo {
        public final UUID uuid;
        public final String name;
        public final int index;
        
        public SurvivorInfo(UUID uuid, String name, int index) {
            this.uuid = uuid;
            this.name = name;
            this.index = index;
        }
    }
    
    public static void encode(ExeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.syncType);
        buf.writeInt(msg.nearbySurvivors.size());
        for (SurvivorInfo info : msg.nearbySurvivors) {
            buf.writeUUID(info.uuid);
            buf.writeUtf(info.name);
            buf.writeInt(info.index);
        }
    }
    
    public static ExeSyncPacket decode(FriendlyByteBuf buf) {
        SyncType syncType = buf.readEnum(SyncType.class);
        int size = buf.readInt();
        List<SurvivorInfo> survivors = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readUtf();
            int index = buf.readInt();
            survivors.add(new SurvivorInfo(uuid, name, index));
        }
        return new ExeSyncPacket(survivors);
    }
    
    public static void handle(ExeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                if (msg.syncType == SyncType.INVISIBILITY_NEARBY) {
                    InvisibilityClientCache.updateNearbySurvivors(msg.nearbySurvivors);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}