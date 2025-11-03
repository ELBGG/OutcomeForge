package pe.elb.vitacarnis.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pe.elb.vitacarnis.entities.CrawlerTrapEntity;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class EscapeInputPacket {
    private final UUID playerId;
    private final long timestamp;

    public EscapeInputPacket(UUID playerId) {
        this.playerId = playerId;
        this.timestamp = System.currentTimeMillis();
    }

    public EscapeInputPacket(UUID playerId, long timestamp) {
        this.playerId = playerId;
        this.timestamp = timestamp;
    }

    public static void encode(EscapeInputPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId);
        buffer.writeLong(packet.timestamp);
    }

    public static EscapeInputPacket decode(FriendlyByteBuf buffer) {
        return new EscapeInputPacket(
                buffer.readUUID(),
                buffer.readLong()
        );
    }

    public static void handle(EscapeInputPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.getUUID().equals(packet.playerId)) {
                long latency = System.currentTimeMillis() - packet.timestamp;
                if (latency > 500) return;

                List<CrawlerTrapEntity> nearbyTraps = player.serverLevel().getEntitiesOfClass(
                        CrawlerTrapEntity.class, player.getBoundingBox().inflate(5.0));

                for (CrawlerTrapEntity trap : nearbyTraps) {
                    if (trap.processEscapeInput(player.getUUID())) {
                        break;
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
