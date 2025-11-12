package pe.elb.outcomememories.net.skills.blaze;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.BlazeSkillsSystem;


import java.util.function.Supplier;

public class BlazeSkillPacket {
    
    private final SkillType skillType;
    
    public enum SkillType {
        ROUNDHOUSE,
        SOL_FLAME
    }
    
    public BlazeSkillPacket(SkillType skillType) {
        this.skillType = skillType;
    }

    public static void encode(BlazeSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
    }

    public static BlazeSkillPacket decode(FriendlyByteBuf buf) {
        return new BlazeSkillPacket(buf.readEnum(SkillType.class));
    }

    public static void handle(BlazeSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case ROUNDHOUSE -> BlazeSkillsSystem.tryUseRoundhouse(player);
                case SOL_FLAME -> BlazeSkillsSystem.tryUseSolFlame(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}