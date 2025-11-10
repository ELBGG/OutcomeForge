package pe.elb.outcomememories.net.skills.knuckles;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.KnucklesSkillsSystem;

import java.util.function.Supplier;

public class KnucklesSkillPacket {
    
    private final SkillType skillType;
    private final boolean booleanData;
    
    public enum SkillType {
        PUNCH_START,
        PUNCH_EXECUTE,
        COUNTER,
        WALL_CLING,
        GLIDE,
        GLIDE_FROM_WALL
    }
    
    public KnucklesSkillPacket(SkillType skillType) {
        this(skillType, false);
    }
    
    public KnucklesSkillPacket(SkillType skillType, boolean booleanData) {
        this.skillType = skillType;
        this.booleanData = booleanData;
    }

    public static void encode(KnucklesSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
        buf.writeBoolean(msg.booleanData);
    }

    public static KnucklesSkillPacket decode(FriendlyByteBuf buf) {
        SkillType skillType = buf.readEnum(SkillType.class);
        boolean booleanData = buf.readBoolean();
        return new KnucklesSkillPacket(skillType, booleanData);
    }

    public static void handle(KnucklesSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case PUNCH_START -> KnucklesSkillsSystem.tryUsePunch(player);
                
                case PUNCH_EXECUTE -> KnucklesSkillsSystem.executePunch(player, msg.booleanData);
                
                case COUNTER -> KnucklesSkillsSystem.tryUseCounter(player);
                
                case WALL_CLING -> KnucklesSkillsSystem.tryWallCling(player);
                
                case GLIDE -> KnucklesSkillsSystem.startGlide(player);
                
                case GLIDE_FROM_WALL -> KnucklesSkillsSystem.startGlideFromWall(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}