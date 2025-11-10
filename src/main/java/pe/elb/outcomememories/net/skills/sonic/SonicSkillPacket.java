package pe.elb.outcomememories.net.skills.sonic;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.SonicSkillsSystem;

import java.util.function.Supplier;

public class SonicSkillPacket {
    
    private final SkillType skillType;
    
    public enum SkillType {
        DROPDASH,
        PEELOUT,
        PEELOUT_JUMP_OUT
    }
    
    public SonicSkillPacket(SkillType skillType) {
        this.skillType = skillType;
    }

    public static void encode(SonicSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
    }

    public static SonicSkillPacket decode(FriendlyByteBuf buf) {
        return new SonicSkillPacket(buf.readEnum(SkillType.class));
    }

    public static void handle(SonicSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case DROPDASH -> SonicSkillsSystem.tryUseDropdash(player);
                case PEELOUT -> SonicSkillsSystem.tryUsePeelout(player);
                case PEELOUT_JUMP_OUT -> SonicSkillsSystem.tryJumpOutFromCarry(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}