package pe.elb.outcomememories.net.skills.amy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.AmySkillsSystem;

import java.util.function.Supplier;


public class AmySkillPacket {
    
    private final SkillType skillType;
    private final int entityId; // Solo usado para PICKUP_HAMMER
    
    public enum SkillType {
        HAMMER,         // E - Usar Hammer
        THROW_HAMMER,   // Q - Lanzar martillo
        REROLL,         // X - Reroll Tarot Cards
        PICKUP_HAMMER   // Interactuar - Recoger martillo
    }
    
    public AmySkillPacket(SkillType skillType) {
        this(skillType, -1);
    }
    
    public AmySkillPacket(SkillType skillType, int entityId) {
        this.skillType = skillType;
        this.entityId = entityId;
    }

    public static void encode(AmySkillPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.skillType);
        buf.writeInt(msg.entityId);
    }

    public static AmySkillPacket decode(FriendlyByteBuf buf) {
        SkillType skillType = buf.readEnum(SkillType.class);
        int entityId = buf.readInt();
        return new AmySkillPacket(skillType, entityId);
    }

    public static void handle(AmySkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.skillType) {
                case HAMMER -> AmySkillsSystem.tryUseHammer(player);
                
                case THROW_HAMMER -> AmySkillsSystem.tryThrowHammer(player);
                
                case REROLL -> AmySkillsSystem.tryReroll(player);
                
                case PICKUP_HAMMER -> {
                    if (msg.entityId != -1) {
                        net.minecraft.world.entity.Entity entity = player.level().getEntity(msg.entityId);
                        if (entity instanceof ItemEntity itemEntity) {
                            AmySkillsSystem.attemptPickupHammer(player, itemEntity);
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}