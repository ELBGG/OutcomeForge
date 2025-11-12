package pe.elb.outcomememories.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.game.skills.AmySkillsSystem;
import pe.elb.outcomememories.game.skills.BlazeSkillsSystem;
import pe.elb.outcomememories.game.skills.SonicSkillsSystem;
import pe.elb.outcomememories.net.NetworkHandler;

@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DefineCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger("DefineCommands");

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("[DefineCommands] Registrando comando /define...");
        event.getDispatcher().register(
                Commands.literal("define")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("blaze")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineBlaze(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("amy")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineAmy(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("cream")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineCream(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("exe")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineExe(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("tails")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineTails(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("eggman")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineEggman(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("sonic")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineSonic(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("knuckles")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineKnuckles(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("metal_sonic")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeDefineMetalSonic(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
        );
    }

    private static int executeDefineBlaze(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define blaze para {}", target.getGameProfile().getName());
        try {
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.BLAZE);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);

            BlazeSkillsSystem.initializeSolMeter(target.getUUID());

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.BLAZE, true, true, true, 1);

            source.sendSuccess(() -> Component.literal("§6Jugador " + target.getGameProfile().getName() + " definido como BLAZE."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como BLAZE", e);
            source.sendFailure(Component.literal("Error al definir jugador como BLAZE: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDefineAmy(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define amy para {}", target.getGameProfile().getName());
        try {
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.AMY);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);

            //AmySkillsSystem.rollTarotCards(target.getUUID());

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.AMY, true, true, true, 1);
            source.sendSuccess(() -> Component.literal("§dJugador " + target.getGameProfile().getName() + " definido como AMY."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como AMY", e);
            source.sendFailure(Component.literal("Error al definir jugador como AMY: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDefineCream(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define cream para {}", target.getGameProfile().getName());
        try {
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.CREAM);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.CREAM, true, true, true, 1);
            source.sendSuccess(() -> Component.literal("§fJugador " + target.getGameProfile().getName() + " definido como CREAM."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como CREAM", e);
            source.sendFailure(Component.literal("Error al definir jugador como CREAM: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDefineExe(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define exe para {}", target.getGameProfile().getName());
        try {
            // ✅ Asignar rol previo aleatorio ANTES de convertir a exe
            PlayerTypeOM[] survivorTypes = {
                    PlayerTypeOM.SONIC, PlayerTypeOM.TAILS, PlayerTypeOM.KNUCKLES,
                    PlayerTypeOM.AMY, PlayerTypeOM.CREAM, PlayerTypeOM.EGGMAN,
                    PlayerTypeOM.BLAZE, PlayerTypeOM.METAL_SONIC
            };
            PlayerTypeOM randomPrevious = survivorTypes[new java.util.Random().nextInt(survivorTypes.length)];

            PlayerRegistry.savePreviousRole(target.getUUID(), randomPrevious);
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.X2011);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.X2011, true, true, true, 1);

            source.sendSuccess(() -> Component.literal("§cJugador " + target.getGameProfile().getName() + " definido como EXECUTIONER (rol previo: " + randomPrevious.name() + ")"), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como EXECUTIONER", e);
            source.sendFailure(Component.literal("Error al definir jugador como EXECUTIONER: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDefineTails(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define tails para {}", target.getGameProfile().getName());
        try {
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.TAILS);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.TAILS, true, true, true, 1);
            source.sendSuccess(() -> Component.literal("§eJugador " + target.getGameProfile().getName() + " definido como TAILS."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como TAILS", e);
            source.sendFailure(Component.literal("Error al definir jugador como TAILS: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDefineEggman(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define eggman para {}", target.getGameProfile().getName());
        try {
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.EGGMAN);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.EGGMAN, true, true, true, 1);

            source.sendSuccess(() -> Component.literal("§7Jugador " + target.getGameProfile().getName() + " definido como EGGMAN."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como EGGMAN", e);
            source.sendFailure(Component.literal("Error al definir jugador como EGGMAN: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDefineSonic(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define sonic para {}", target.getGameProfile().getName());
        try {
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.SONIC);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);

            SonicSkillsSystem.initializeDodgeMeter(target.getUUID());

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.SONIC, true, true, true, 1);

            source.sendSuccess(() -> Component.literal("§9Jugador " + target.getGameProfile().getName() + " definido como SONIC."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como SONIC", e);
            source.sendFailure(Component.literal("Error al definir jugador como SONIC: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDefineKnuckles(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define knuckles para {}", target.getGameProfile().getName());
        try {
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.KNUCKLES);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            target.setHealth(100.0F);

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.KNUCKLES, true, true, true, 1);

            source.sendSuccess(() -> Component.literal("§cJugador " + target.getGameProfile().getName() + " definido como KNUCKLES."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como KNUCKLES", e);
            source.sendFailure(Component.literal("Error al definir jugador como KNUCKLES: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDefineMetalSonic(CommandSourceStack source, ServerPlayer target) {
        LOGGER.info("[DefineCommands] Ejecutando /define metal_sonic para {}", target.getGameProfile().getName());
        try {
            PlayerRegistry.setPlayerType(target, PlayerTypeOM.METAL_SONIC);

            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(150.0D);
            target.setHealth(175.0F);

            NetworkHandler.sendRoleUpdate(target, PlayerTypeOM.METAL_SONIC, true, true, true, 1);

            source.sendSuccess(() -> Component.literal("§8Jugador " + target.getGameProfile().getName() + " definido como METAL SONIC."), true);
            return 1;
        } catch (Exception e) {
            LOGGER.error("[DefineCommands] Error al definir jugador como METAL SONIC", e);
            source.sendFailure(Component.literal("Error al definir jugador como METAL SONIC: " + e.getMessage()));
            return 0;
        }
    }
}