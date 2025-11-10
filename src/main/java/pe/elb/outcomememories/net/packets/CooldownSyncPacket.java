package pe.elb.outcomememories.net.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.elb.outcomememories.client.handlers.CooldownManager;

import java.util.function.Supplier;

/**
 * Packet: Servidor -> Cliente para notificar el inicio de un cooldown por id.
 */
public class CooldownSyncPacket {
    private static final Logger LOGGER = LoggerFactory.getLogger("CooldownSyncPacket");

    private final String id;
    private final long durationMs;
    private final long startMs;

    public CooldownSyncPacket(String id, long durationMs, long startMs) {
        this.id = id;
        this.durationMs = durationMs;
        this.startMs = startMs;
    }

    public static void encode(CooldownSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.id);
        buf.writeLong(msg.durationMs);
        buf.writeLong(msg.startMs);
    }

    public static CooldownSyncPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(32767);
        long duration = buf.readLong();
        long start = buf.readLong();
        return new CooldownSyncPacket(id, duration, start);
    }

    public static void handle(CooldownSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Ejecutar solo en cliente
            if (FMLEnvironment.dist.isClient()) {
                // Guardar cooldown en el gestor cliente
                CooldownManager.setCooldown(msg.id, msg.durationMs, msg.startMs);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}