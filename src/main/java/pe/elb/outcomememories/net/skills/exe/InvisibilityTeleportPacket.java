package pe.elb.outcomememories.net.skills.exe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.exe.InvisibilitySkill;

import java.util.function.Supplier;

/**
 * Packet para teleportarse a un survivor durante invisibilidad
 */
public class InvisibilityTeleportPacket {
    
    private final int targetIndex;
    
    public InvisibilityTeleportPacket(int targetIndex) {
        this.targetIndex = targetIndex;
    }
    
    public static void encode(InvisibilityTeleportPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetIndex);
    }
    
    public static InvisibilityTeleportPacket decode(FriendlyByteBuf buf) {
        return new InvisibilityTeleportPacket(buf.readInt());
    }
    
    public static void handle(InvisibilityTeleportPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                InvisibilitySkill.tryTeleportToSurvivor(player, msg.targetIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}