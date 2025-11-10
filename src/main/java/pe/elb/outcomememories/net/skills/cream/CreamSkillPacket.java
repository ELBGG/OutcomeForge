package pe.elb.outcomememories.net.skills.cream;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.CreamSkillsSystem;

import java.util.function.Supplier;

public class CreamSkillPacket {
    
    private final SkillType skillType;
    
    public enum SkillType {
        DASH,
        HEAL,
        GLIDE
    }
    
    public CreamSkillPacket(SkillType skillType) {
        this.skillType = skillType;
    }

    public static void encode(CreamSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
    }

    public static CreamSkillPacket decode(FriendlyByteBuf buf) {
        return new CreamSkillPacket(buf.readEnum(SkillType.class));
    }

    public static void handle(CreamSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case DASH -> CreamSkillsSystem.tryUseDash(player);
                case HEAL -> CreamSkillsSystem.tryUseHeal(player);
                case GLIDE -> CreamSkillsSystem.startGlide(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}