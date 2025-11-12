package pe.elb.outcomememories.net.skills.metalsonic;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.MetalSonicSkillsSystem;

import java.util.function.Supplier;

public class MetalSonicSkillPacket {
    
    private final SkillType skillType;
    
    public enum SkillType {
        CHARGE,
        REPAIR
    }
    
    public MetalSonicSkillPacket(SkillType skillType) {
        this.skillType = skillType;
    }

    public static void encode(MetalSonicSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
    }

    public static MetalSonicSkillPacket decode(FriendlyByteBuf buf) {
        return new MetalSonicSkillPacket(buf.readEnum(SkillType.class));
    }

    public static void handle(MetalSonicSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case CHARGE -> MetalSonicSkillsSystem.tryUseCharge(player);
                case REPAIR -> MetalSonicSkillsSystem.tryUseRepair(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}