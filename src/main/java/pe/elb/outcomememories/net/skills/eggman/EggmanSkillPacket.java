package pe.elb.outcomememories.net.skills.eggman;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.EggmanSkillsSystem;

import java.util.function.Supplier;

public class EggmanSkillPacket {
    
    private final SkillType skillType;
    
    public enum SkillType {
        SHIELD,
        BOOST,
        DOUBLE_JUMP
    }
    
    public EggmanSkillPacket(SkillType skillType) {
        this.skillType = skillType;
    }

    public static void encode(EggmanSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
    }

    public static EggmanSkillPacket decode(FriendlyByteBuf buf) {
        return new EggmanSkillPacket(buf.readEnum(SkillType.class));
    }

    public static void handle(EggmanSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case SHIELD -> EggmanSkillsSystem.tryUseShield(player);
                case BOOST -> EggmanSkillsSystem.tryUseBoost(player);
                case DOUBLE_JUMP -> EggmanSkillsSystem.tryUseDoubleJump(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}