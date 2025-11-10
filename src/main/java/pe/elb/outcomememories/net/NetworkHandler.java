package pe.elb.outcomememories.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pe.elb.outcomememories.client.cache.ClientPlayerData;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.packets.*;
import pe.elb.outcomememories.net.skills.amy.*;
import pe.elb.outcomememories.net.skills.cream.*;
import pe.elb.outcomememories.net.skills.sonic.*;
import pe.elb.outcomememories.net.skills.tails.*;
import pe.elb.outcomememories.net.skills.eggman.*;
import pe.elb.outcomememories.net.skills.knuckles.*;
import pe.elb.outcomememories.net.skills.exe.*;


import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static pe.elb.outcomememories.Outcomememories.LOGGER;

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
                AmySkillPacket.class,
                AmySkillPacket::encode,
                AmySkillPacket::decode,
                AmySkillPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                AmySyncPacket.class,
                AmySyncPacket::encode,
                AmySyncPacket::decode,
                AmySyncPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                CreamSkillPacket.class,
                CreamSkillPacket::encode,
                CreamSkillPacket::decode,
                CreamSkillPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                EggmanSkillPacket.class,
                EggmanSkillPacket::encode,
                EggmanSkillPacket::decode,
                EggmanSkillPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                ExeSkillPacket.class,
                ExeSkillPacket::encode,
                ExeSkillPacket::decode,
                ExeSkillPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                ExeSyncPacket.class,
                ExeSyncPacket::encode,
                ExeSyncPacket::decode,
                ExeSyncPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                KnucklesSkillPacket.class,
                KnucklesSkillPacket::encode,
                KnucklesSkillPacket::decode,
                KnucklesSkillPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                SonicSkillPacket.class,
                SonicSkillPacket::encode,
                SonicSkillPacket::decode,
                SonicSkillPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                SonicSyncPacket.class,
                SonicSyncPacket::encode,
                SonicSyncPacket::decode,
                SonicSyncPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                TailsSkillPacket.class,
                TailsSkillPacket::encode,
                TailsSkillPacket::decode,
                TailsSkillPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                TailsSyncPacket.class,
                TailsSyncPacket::encode,
                TailsSyncPacket::decode,
                TailsSyncPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                CooldownSyncPacket.class,
                CooldownSyncPacket::encode,
                CooldownSyncPacket::decode,
                CooldownSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT) // Servidor -> Cliente
        );

        CHANNEL.registerMessage(
                nextId(),
                LMSMusicPacket.class,
                LMSMusicPacket::encode,
                LMSMusicPacket::decode,
                LMSMusicPacket::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                LMSLyricsPacket.class,
                LMSLyricsPacket::encode,
                LMSLyricsPacket::decode,
                LMSLyricsPacket::handle
        );

        CHANNEL.messageBuilder(LMSBeatZoomPacket.class, nextId())
                .encoder(LMSBeatZoomPacket::encode)
                .decoder(LMSBeatZoomPacket::decode)
                .consumerMainThread(LMSBeatZoomPacket::handle)
                .add();


    }

    private static int nextId() {
        return packetId++;
    }

    public static void sendRoleUpdate(ServerPlayer target, PlayerTypeOM type, boolean disableQ, boolean disableE, boolean disableF5, int cameraMode) {
        LOGGER.info("[NetworkHandler] Enviando RoleUpdate para {} (tipo: {}) a TODOS",
                target.getGameProfile().getName(), type);

        CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new RoleUpdatePacket(target.getUUID(), type, disableQ, disableE, disableF5, cameraMode)
        );
    }

    public static class RoleUpdatePacket {
        private final UUID playerUUID; // ✅ NUEVO: UUID del jugador
        private final PlayerTypeOM type;
        private final boolean disableQ;
        private final boolean disableE;
        private final boolean disableF5;
        private final int cameraMode;
        private final boolean isExecutionerMode;

        public RoleUpdatePacket(UUID playerUUID, PlayerTypeOM type, boolean disableQ, boolean disableE, boolean disableF5, int cameraMode) {
            this(playerUUID, type, disableQ, disableE, disableF5, cameraMode, false);
        }

        public RoleUpdatePacket(UUID playerUUID, PlayerTypeOM type, boolean disableQ, boolean disableE, boolean disableF5, int cameraMode, boolean isExecutionerMode) {
            this.playerUUID = playerUUID;
            this.type = type;
            this.disableQ = disableQ;
            this.disableE = disableE;
            this.disableF5 = disableF5;
            this.cameraMode = cameraMode;
            this.isExecutionerMode = isExecutionerMode;
        }

        public static void encode(RoleUpdatePacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.playerUUID); // ✅ NUEVO
            buf.writeEnum(msg.type);
            buf.writeBoolean(msg.disableQ);
            buf.writeBoolean(msg.disableE);
            buf.writeBoolean(msg.disableF5);
            buf.writeInt(msg.cameraMode);
            buf.writeBoolean(msg.isExecutionerMode);
        }

        public static RoleUpdatePacket decode(FriendlyByteBuf buf) {
            UUID playerUUID = buf.readUUID(); // ✅ NUEVO
            PlayerTypeOM type = buf.readEnum(PlayerTypeOM.class);
            boolean disableQ = buf.readBoolean();
            boolean disableE = buf.readBoolean();
            boolean disableF5 = buf.readBoolean();
            int cameraMode = buf.readInt();
            boolean isExecutionerMode = buf.readBoolean();
            return new RoleUpdatePacket(playerUUID, type, disableQ, disableE, disableF5, cameraMode, isExecutionerMode);
        }

        public static void handle(RoleUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (FMLEnvironment.dist.isClient()) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null) return;

                    LOGGER.info("[RoleUpdatePacket] Recibido - UUID: {}, Type: {}", msg.playerUUID, msg.type);

                    // ✅ Buscar el jugador en el mundo
                    Player targetPlayer = mc.level.getPlayerByUUID(msg.playerUUID);

                    if (targetPlayer != null) {
                        LOGGER.info("[RoleUpdatePacket] Jugador encontrado: {}, actualizando PlayerRegistry",
                                targetPlayer.getGameProfile().getName());

                        PlayerRegistry.setPlayerType(targetPlayer, msg.type);

                        if (targetPlayer == mc.player) {
                            LOGGER.info("[RoleUpdatePacket] Es el jugador local, aplicando configuraciones");

                            ClientPlayerData.setType(msg.type);


                            // Ajustar cámara
                            mc.options.setCameraType(msg.cameraMode == 1
                                    ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                                    : net.minecraft.client.CameraType.FIRST_PERSON);

                            // Modo Executioner
                            if (msg.isExecutionerMode) {
                                ClientPlayerData.enableExecutionerMode(mc.player);
                            } else {
                                ClientPlayerData.disableExecutionerMode();
                            }

                            // Mensaje de confirmación
                            mc.player.sendSystemMessage(Component.literal(
                                    "§a✓ Rol sincronizado: §b" + msg.type.name()));
                        } else {
                            LOGGER.info("[RoleUpdatePacket] Jugador {} tiene tipo {} (otro jugador)",
                                    targetPlayer.getGameProfile().getName(), msg.type);
                        }
                    } else {
                        LOGGER.warn("[RoleUpdatePacket] Jugador con UUID {} no encontrado en el cliente (aún)",
                                msg.playerUUID);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}