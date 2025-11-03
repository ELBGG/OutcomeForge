package pe.elb.outcomememories.net;

import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;
import pe.elb.outcomememories.game.skills.amy.HammerSkills;
import java.util.function.Supplier;

/**
 * Paquete enviado cuando el cliente (Amy) presiona Q para atacar.
 */
public class AttackKeyPacket {
    public AttackKeyPacket() {}

    public static void encode(AttackKeyPacket msg, net.minecraft.network.FriendlyByteBuf buf) {}
    public static AttackKeyPacket decode(net.minecraft.network.FriendlyByteBuf buf) { return new AttackKeyPacket(); }

    public static void handle(AttackKeyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                HammerSkills.tryUse(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
