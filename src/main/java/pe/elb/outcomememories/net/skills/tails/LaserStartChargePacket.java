package pe.elb.outcomememories.net.skills.tails;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.tails.LaserCannonSkill;

import java.util.function.Supplier;

public class LaserStartChargePacket {
    
    public LaserStartChargePacket() {}
    
    public static void encode(LaserStartChargePacket msg, FriendlyByteBuf buf) {}
    
    public static LaserStartChargePacket decode(FriendlyByteBuf buf) {
        return new LaserStartChargePacket();
    }
    
    public static void handle(LaserStartChargePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                LaserCannonSkill.tryUse(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}