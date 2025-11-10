package pe.elb.outcomememories.net.skills.sonic;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.cache.DodgeMeterClientCache;

import java.util.UUID;
import java.util.function.Supplier;

public class SonicSyncPacket {
    
    private final UUID playerUUID;
    private final float dodgeHP;
    
    public SonicSyncPacket(UUID playerUUID, float dodgeHP) {
        this.playerUUID = playerUUID;
        this.dodgeHP = dodgeHP;
    }
    
    public static void encode(SonicSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeFloat(msg.dodgeHP);
    }
    
    public static SonicSyncPacket decode(FriendlyByteBuf buf) {
        return new SonicSyncPacket(buf.readUUID(), buf.readFloat());
    }
    
    public static void handle(SonicSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().execute(() -> {
                DodgeMeterClientCache.updateDodgeHP(msg.playerUUID, msg.dodgeHP);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}