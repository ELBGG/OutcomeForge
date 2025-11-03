package pe.elb.outcomememories.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pe.elb.morphed.net.MorphPacket;
import pe.elb.outcomememories.client.ActivableKeyManager;
import pe.elb.outcomememories.client.overlays.CarryClientHandler;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.net.packets.EscapeInputPacket;
import pe.elb.outcomememories.net.packets.TrapStatePacket;
import pe.elb.outcomememories.net.packets.TrapSyncPacket;
import pe.elb.outcomememories.net.skills.amy.AttackKeyPacket;
import pe.elb.outcomememories.net.skills.amy.ThrowHammerKeyPacket;
import pe.elb.outcomememories.net.skills.cream.GlideAttemptPacket;
import pe.elb.outcomememories.net.skills.cream.HealKeyPacket;
import pe.elb.outcomememories.net.skills.cream.PacketCreamDash;
import pe.elb.outcomememories.net.skills.exe.ChargeKeyPacket;
import pe.elb.outcomememories.net.skills.exe.ExecutionerInvisibilityPacket;
import pe.elb.outcomememories.net.skills.exe.ExecutionerTrickeryPacket;
import pe.elb.outcomememories.net.skills.tails.*;

import java.util.Optional;
import java.util.function.Supplier;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("outcomememories", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /** Llamar en el evento FMLCommonSetupEvent */
    public static void register() {
        CHANNEL.registerMessage(
                nextId(),
                RoleUpdatePacket.class,
                RoleUpdatePacket::encode,
                RoleUpdatePacket::decode,
                RoleUpdatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        
        CHANNEL.registerMessage(
                nextId(),
                MorphPacket.class,
                MorphPacket::encode,
                MorphPacket::decode,
                MorphPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                AttackKeyPacket.class,
                AttackKeyPacket::encode,
                AttackKeyPacket::decode,
                AttackKeyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                ThrowHammerKeyPacket.class,
                ThrowHammerKeyPacket::encode,
                ThrowHammerKeyPacket::decode,
                ThrowHammerKeyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                GlideAttemptPacket.class,
                GlideAttemptPacket::encode,
                GlideAttemptPacket::decode,
                GlideAttemptPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                HealKeyPacket.class,
                HealKeyPacket::encode,
                HealKeyPacket::decode,
                HealKeyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                PacketCreamDash.class,
                PacketCreamDash::encode,
                PacketCreamDash::decode,
                PacketCreamDash::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                ChargeKeyPacket.class,
                ChargeKeyPacket::encode,
                ChargeKeyPacket::decode,
                ChargeKeyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                ExecutionerInvisibilityPacket.class,
                ExecutionerInvisibilityPacket::encode,
                ExecutionerInvisibilityPacket::decode,
                ExecutionerInvisibilityPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                ExecutionerTrickeryPacket.class,
                ExecutionerTrickeryPacket::encode,
                ExecutionerTrickeryPacket::decode,
                ExecutionerTrickeryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                TrapStatePacket.class,
                TrapStatePacket::encode,
                TrapStatePacket::decode,
                TrapStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                nextId(),
                EscapeInputPacket.class,
                EscapeInputPacket::encode,
                EscapeInputPacket::decode,
                EscapeInputPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                TrapStatePacket.class,
                TrapStatePacket::encode,
                TrapStatePacket::decode,
                TrapStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                TrapSyncPacket.class,
                TrapSyncPacket::encode,
                TrapSyncPacket::decode,
                TrapSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                CarryStatePacket.class,
                CarryStatePacket::encode,
                CarryStatePacket::decode,
                CarryStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                GlideAscendPacket.class,
                GlideAscendPacket::encode,
                GlideAscendPacket::decode,
                GlideAscendPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                GlideStartPacket.class,
                GlideStartPacket::encode,
                GlideStartPacket::decode,
                GlideStartPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                GlideStopPacket.class,
                GlideStopPacket::encode,
                GlideStopPacket::decode,
                GlideStopPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                LaserCancelChargePacket.class,
                LaserCancelChargePacket::encode,
                LaserCancelChargePacket::decode,
                LaserCancelChargePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                LaserStartChargePacket.class,
                LaserStartChargePacket::encode,
                LaserStartChargePacket::decode,
                LaserStartChargePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId(),
                LaserFirePacket.class,
                LaserFirePacket::encode,
                LaserFirePacket::decode,
                LaserFirePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );


    }

    private static int nextId() {
        return packetId++;
    }

    /**
     * Envía al cliente la configuración del rol (Amy u otros)
     */
    public static void sendRoleUpdate(ServerPlayer target, PlayerTypeOM type, boolean disableQ, boolean disableE, boolean disableF5, int cameraMode) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new RoleUpdatePacket(type, disableQ, disableE, disableF5, cameraMode));
    }

    public static class RoleUpdatePacket {
        private final PlayerTypeOM type;
        private final boolean disableQ;
        private final boolean disableE;
        private final boolean disableF5;
        private final int cameraMode;
        private final boolean isExecutionerMode; // ✅ nuevo campo opcional global

        public RoleUpdatePacket(PlayerTypeOM type, boolean disableQ, boolean disableE, boolean disableF5, int cameraMode) {
            this(type, disableQ, disableE, disableF5, cameraMode, false);
        }

        public RoleUpdatePacket(PlayerTypeOM type, boolean disableQ, boolean disableE, boolean disableF5, int cameraMode, boolean isExecutionerMode) {
            this.type = type;
            this.disableQ = disableQ;
            this.disableE = disableE;
            this.disableF5 = disableF5;
            this.cameraMode = cameraMode;
            this.isExecutionerMode = isExecutionerMode;
        }

        public static void encode(RoleUpdatePacket msg, FriendlyByteBuf buf) {
            buf.writeEnum(msg.type);
            buf.writeBoolean(msg.disableQ);
            buf.writeBoolean(msg.disableE);
            buf.writeBoolean(msg.disableF5);
            buf.writeInt(msg.cameraMode);
            buf.writeBoolean(msg.isExecutionerMode); // ✅ nuevo
        }

        public static RoleUpdatePacket decode(FriendlyByteBuf buf) {
            PlayerTypeOM type = buf.readEnum(PlayerTypeOM.class);
            boolean disableQ = buf.readBoolean();
            boolean disableE = buf.readBoolean();
            boolean disableF5 = buf.readBoolean();
            int cameraMode = buf.readInt();
            boolean isExecutionerMode = buf.readBoolean();
            return new RoleUpdatePacket(type, disableQ, disableE, disableF5, cameraMode, isExecutionerMode);
        }

        public static void handle(RoleUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (FMLEnvironment.dist.isClient()) {
                    var mc = Minecraft.getInstance();
                    if (mc.player == null) return;

                    // 1️⃣ Actualizar tipo del jugador local
                    pe.elb.outcomememories.client.ClientPlayerData.setType(msg.type);

                    // 2️⃣ Aplicar restricciones globales de teclas
                    ActivableKeyManager.setQEnabled(!msg.disableQ);
                    ActivableKeyManager.setEEnabled(!msg.disableE);
                    ActivableKeyManager.setF5Enabled(!msg.disableF5);

                    // 3️⃣ Ajustar cámara
                    mc.options.setCameraType(msg.cameraMode == 1
                            ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                            : net.minecraft.client.CameraType.FIRST_PERSON);

                    // 4️⃣ Modo Executioner (o cualquier otro modo global)
                    if (msg.isExecutionerMode) {
                        pe.elb.outcomememories.client.ClientPlayerData.enableExecutionerMode(mc.player);
                    } else {
                        pe.elb.outcomememories.client.ClientPlayerData.disableExecutionerMode();
                    }

                    // 5️⃣ Mensaje de confirmación
                    mc.player.sendSystemMessage(Component.literal(
                            "§7[ClientSync] Rol sincronizado: §b" + msg.type.name() +
                                    (msg.isExecutionerMode ? " §c(Executioner Mode)" : "")));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }


}
