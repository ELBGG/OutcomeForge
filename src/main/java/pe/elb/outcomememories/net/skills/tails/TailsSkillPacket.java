package pe.elb.outcomememories.net.skills.tails;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.TailsSkillsSystem;

import java.util.function.Supplier;

public class TailsSkillPacket {
    
    private final SkillType skillType;
    private final boolean booleanData;
    
    public enum SkillType {
        GLIDE_START,
        GLIDE_ASCEND,
        LASER_START,
        LASER_EXECUTE
    }
    
    public TailsSkillPacket(SkillType skillType) {
        this(skillType, false);
    }
    
    public TailsSkillPacket(SkillType skillType, boolean booleanData) {
        this.skillType = skillType;
        this.booleanData = booleanData;
    }

    public static void encode(TailsSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
        buf.writeBoolean(msg.booleanData);
    }

    public static TailsSkillPacket decode(FriendlyByteBuf buf) {
        SkillType skillType = buf.readEnum(SkillType.class);
        boolean booleanData = buf.readBoolean();
        return new TailsSkillPacket(skillType, booleanData);
    }

    public static void handle(TailsSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case GLIDE_START -> TailsSkillsSystem.tryUseGlide(player);
                
                case GLIDE_ASCEND -> TailsSkillsSystem.setGlideAscending(player, msg.booleanData);
                
                case LASER_START -> TailsSkillsSystem.tryUseLaser(player);
                
                case LASER_EXECUTE -> TailsSkillsSystem.handleLaserAction(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}