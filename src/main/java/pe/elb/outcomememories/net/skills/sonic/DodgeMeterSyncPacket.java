package pe.elb.outcomememories.game.skills.sonic;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.client.DodgeMeterClientCache;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet para sincronizar el Dodge Meter del servidor al cliente
 */
public class DodgeMeterSyncPacket {
    
    private final UUID playerUUID;
    private final float dodgeHP;
    
    public DodgeMeterSyncPacket(UUID playerUUID, float dodgeHP) {
        this.playerUUID = playerUUID;
        this.dodgeHP = dodgeHP;
    }
    
    public static void encode(DodgeMeterSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeFloat(msg.dodgeHP);
    }
    
    public static DodgeMeterSyncPacket decode(FriendlyByteBuf buf) {
        return new DodgeMeterSyncPacket(buf.readUUID(), buf.readFloat());
    }
    
    public static void handle(DodgeMeterSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Este código se ejecuta en el cliente
            Minecraft.getInstance().execute(() -> {
                DodgeMeterClientCache.updateDodgeHP(msg.playerUUID, msg.dodgeHP);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}