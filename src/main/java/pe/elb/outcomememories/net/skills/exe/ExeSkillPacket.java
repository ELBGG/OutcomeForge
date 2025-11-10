package pe.elb.outcomememories.net.skills.exe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.X2011SkillsSystem;

import java.util.function.Supplier;

public class ExeSkillPacket {
    
    private final SkillType skillType;
    private final int additionalData;
    
    public enum SkillType {
        CHARGE,
        INVISIBILITY_TOGGLE,
        INVISIBILITY_TELEPORT,
        GODS_TRICKERY
    }
    
    public ExeSkillPacket(SkillType skillType) {
        this(skillType, -1);
    }
    
    public ExeSkillPacket(SkillType skillType, int additionalData) {
        this.skillType = skillType;
        this.additionalData = additionalData;
    }

    public static void encode(ExeSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
        buf.writeInt(msg.additionalData);
    }

    public static ExeSkillPacket decode(FriendlyByteBuf buf) {
        SkillType skillType = buf.readEnum(SkillType.class);
        int additionalData = buf.readInt();
        return new ExeSkillPacket(skillType, additionalData);
    }

    public static void handle(ExeSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case CHARGE -> X2011SkillsSystem.tryUseCharge(player);
                
                case INVISIBILITY_TOGGLE -> X2011SkillsSystem.tryToggleInvisibility(player);
                
                case INVISIBILITY_TELEPORT -> {
                    if (msg.additionalData != -1) {
                        X2011SkillsSystem.tryTeleportToSurvivor(player, msg.additionalData);
                    }
                }
                
                case GODS_TRICKERY -> X2011SkillsSystem.tryUseGodsTrickery(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}