package pe.elb.outcomememories.net.skills.amy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.outcomememories.game.skills.AmySkillsSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet unificado para sincronizar el estado de Amy del servidor al cliente
 * Servidor → Cliente
 */
public class AmySyncPacket {
    
    private final UUID playerUUID;
    private final SyncType syncType;
    
    // Para TAROT_CARDS
    private final List<String> cardNames;
    
    // Para STATS
    private final float speedModifier;
    private final float damageModifier;
    private final boolean hasTheWorld;
    private final boolean hasTheSun;
    private final boolean hasTheMoon;
    
    public enum SyncType {
        TAROT_CARDS,    // Sincronizar cartas del Tarot
        STATS,          // Sincronizar modificadores de stats
        FULL            // Sincronizar todo (al login o reroll)
    }
    
    // Constructor para TAROT_CARDS
    public AmySyncPacket(UUID playerUUID, List<AmySkillsSystem.TarotCard> cards) {
        this.playerUUID = playerUUID;
        this.syncType = SyncType.TAROT_CARDS;
        
        this.cardNames = new ArrayList<>();
        for (AmySkillsSystem.TarotCard card : cards) {
            this.cardNames.add(card.name());
        }
        
        this.speedModifier = 0;
        this.damageModifier = 0;
        this.hasTheWorld = false;
        this.hasTheSun = false;
        this.hasTheMoon = false;
    }
    
    // Constructor para STATS
    public AmySyncPacket(UUID playerUUID, float speedModifier, float damageModifier,
                         boolean hasTheWorld, boolean hasTheSun, boolean hasTheMoon) {
        this.playerUUID = playerUUID;
        this.syncType = SyncType.STATS;
        
        this.cardNames = new ArrayList<>();
        
        this.speedModifier = speedModifier;
        this.damageModifier = damageModifier;
        this.hasTheWorld = hasTheWorld;
        this.hasTheSun = hasTheSun;
        this.hasTheMoon = hasTheMoon;
    }
    
    // Constructor para FULL (cartas + stats)
    public AmySyncPacket(UUID playerUUID, List<AmySkillsSystem.TarotCard> cards,
                         float speedModifier, float damageModifier,
                         boolean hasTheWorld, boolean hasTheSun, boolean hasTheMoon) {
        this.playerUUID = playerUUID;
        this.syncType = SyncType.FULL;
        
        this.cardNames = new ArrayList<>();
        for (AmySkillsSystem.TarotCard card : cards) {
            this.cardNames.add(card.name());
        }
        
        this.speedModifier = speedModifier;
        this.damageModifier = damageModifier;
        this.hasTheWorld = hasTheWorld;
        this.hasTheSun = hasTheSun;
        this.hasTheMoon = hasTheMoon;
    }
    
    // Constructor privado para decode
    private AmySyncPacket(UUID playerUUID, SyncType syncType, List<String> cardNames,
                          float speedModifier, float damageModifier,
                          boolean hasTheWorld, boolean hasTheSun, boolean hasTheMoon) {
        this.playerUUID = playerUUID;
        this.syncType = syncType;
        this.cardNames = cardNames;
        this.speedModifier = speedModifier;
        this.damageModifier = damageModifier;
        this.hasTheWorld = hasTheWorld;
        this.hasTheSun = hasTheSun;
        this.hasTheMoon = hasTheMoon;
    }

    public static void encode(AmySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeEnum(msg.syncType);
        
        // Escribir cartas si es necesario
        if (msg.syncType == SyncType.TAROT_CARDS || msg.syncType == SyncType.FULL) {
            buf.writeInt(msg.cardNames.size());
            for (String cardName : msg.cardNames) {
                buf.writeUtf(cardName);
            }
        }
        
        // Escribir stats si es necesario
        if (msg.syncType == SyncType.STATS || msg.syncType == SyncType.FULL) {
            buf.writeFloat(msg.speedModifier);
            buf.writeFloat(msg.damageModifier);
            buf.writeBoolean(msg.hasTheWorld);
            buf.writeBoolean(msg.hasTheSun);
            buf.writeBoolean(msg.hasTheMoon);
        }
    }

    public static AmySyncPacket decode(FriendlyByteBuf buf) {
        UUID playerUUID = buf.readUUID();
        SyncType syncType = buf.readEnum(SyncType.class);
        
        List<String> cardNames = new ArrayList<>();
        float speedModifier = 0;
        float damageModifier = 0;
        boolean hasTheWorld = false;
        boolean hasTheSun = false;
        boolean hasTheMoon = false;
        
        // Leer cartas si es necesario
        if (syncType == SyncType.TAROT_CARDS || syncType == SyncType.FULL) {
            int size = buf.readInt();
            for (int i = 0; i < size; i++) {
                cardNames.add(buf.readUtf());
            }
        }
        
        // Leer stats si es necesario
        if (syncType == SyncType.STATS || syncType == SyncType.FULL) {
            speedModifier = buf.readFloat();
            damageModifier = buf.readFloat();
            hasTheWorld = buf.readBoolean();
            hasTheSun = buf.readBoolean();
            hasTheMoon = buf.readBoolean();
        }
        
        return new AmySyncPacket(playerUUID, syncType, cardNames,
                speedModifier, damageModifier, hasTheWorld, hasTheSun, hasTheMoon);
    }

    public static void handle(AmySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            handleClientSide(msg);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClientSide(AmySyncPacket msg) {
        // Aquí actualizas tu HUD/UI del cliente según el syncType
        
        if (msg.syncType == SyncType.TAROT_CARDS || msg.syncType == SyncType.FULL) {
            // Actualizar cartas en el HUD
            List<AmySkillsSystem.TarotCard> cards = new ArrayList<>();
            for (String cardName : msg.cardNames) {
                try {
                    cards.add(AmySkillsSystem.TarotCard.valueOf(cardName));
                } catch (IllegalArgumentException e) {
                    // Ignorar cartas inválidas
                }
            }
            
            // ClientAmyData.setTarotCards(msg.playerUUID, cards);
        }
        
        if (msg.syncType == SyncType.STATS || msg.syncType == SyncType.FULL) {
            // Actualizar efectos visuales
            
            /*
            if (msg.hasTheWorld) {
                ClientAmyEffects.enableWallhacks(msg.playerUUID);
            }
            
            if (msg.hasTheSun) {
                ClientAmyEffects.showHealingAura(msg.playerUUID);
            }
            
            if (msg.hasTheMoon) {
                ClientAmyEffects.enableFogEffect(msg.playerUUID);
            }
            
            // Actualizar HUD con modificadores
            ClientAmyData.setSpeedModifier(msg.playerUUID, msg.speedModifier);
            ClientAmyData.setDamageModifier(msg.playerUUID, msg.damageModifier);
            */
        }
    }

    // Getters
    public UUID getPlayerUUID() { return playerUUID; }
    public SyncType getSyncType() { return syncType; }
    public List<String> getCardNames() { return cardNames; }
    public float getSpeedModifier() { return speedModifier; }
    public float getDamageModifier() { return damageModifier; }
    public boolean hasTheWorld() { return hasTheWorld; }
    public boolean hasTheSun() { return hasTheSun; }
    public boolean hasTheMoon() { return hasTheMoon; }
}