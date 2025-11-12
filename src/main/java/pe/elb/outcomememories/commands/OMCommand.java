package pe.elb.outcomememories.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.*;

import java.util.Collection;

/**
 * Comando principal de administración para Outcome Memories
 * 
 * Comandos disponibles:
 * /om start - Inicia el juego
 * /om stop - Detiene el juego
 * /om lms start - Fuerza inicio de LMS
 * /om lms exit - Fuerza fase de salida
 * /om lms stop - Detiene LMS
 * /om setrole <jugador> <rol> - Asigna un rol a un jugador
 * /om swap <jugador1> <jugador2> - Intercambia roles de dos jugadores
 * /om stats [jugador] - Muestra estadísticas
 * /om forceswap <exe> <survivor> - Fuerza un swap (para testing)
 * /om exit register - Registra posición actual como salida
 * /om exit list - Lista todas las salidas
 * /om exit clear - Limpia todas las salidas
 */
@Mod.EventBusSubscriber(modid = "outcomememories", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OMCommand {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(
            Commands.literal("om")
                .requires(source -> source.hasPermission(2)) // Nivel 2 (OP)
                
                // ========== /om start ==========
                .then(Commands.literal("start")
                    .executes(context -> {
                        boolean success = OutcomeMemoriesGameSystem.startGame();
                        
                        if (success) {
                            context.getSource().sendSuccess(
                                () -> Component.literal("§a✓ Juego iniciado correctamente"),
                                true
                            );
                            return 1;
                        } else {
                            context.getSource().sendFailure(
                                Component.literal("§c✗ No se pudo iniciar el juego (¿ya está activo o faltan jugadores?)")
                            );
                            return 0;
                        }
                    })
                )
                
                // ========== /om stop ==========
                .then(Commands.literal("stop")
                    .executes(context -> {
                        if (!OutcomeMemoriesGameSystem.isGameActive()) {
                            context.getSource().sendFailure(
                                Component.literal("§c✗ No hay ningún juego activo")
                            );
                            return 0;
                        }

                        OutcomeMemoriesGameSystem.forceEndGame();
                        context.getSource().sendSuccess(
                            () -> Component.literal("§7Juego detenido manualmente"),
                            true
                        );
                        return 1;
                    })
                )
                
                // ========== /om lms ==========
                .then(Commands.literal("lms")
                    
                    // /om lms start
                    .then(Commands.literal("start")
                        .executes(context -> {
                            if (LMSSystem.isLMSActive()) {
                                context.getSource().sendFailure(
                                    Component.literal("§c✗ LMS ya está activo")
                                );
                                return 0;
                            }
                            
                            LMSSystem.forceActivateLMS();
                            context.getSource().sendSuccess(
                                () -> Component.literal("§a✓ LMS forzado a iniciar"),
                                true
                            );
                            return 1;
                        })
                    )
                    
                    // /om lms exit
                    .then(Commands.literal("exit")
                        .executes(context -> {
                            if (!LMSSystem.isLMSActive()) {
                                context.getSource().sendFailure(
                                    Component.literal("§c✗ LMS no está activo")
                                );
                                return 0;
                            }
                            
                            if (LMSSystem.isExitPhaseActive()) {
                                context.getSource().sendFailure(
                                    Component.literal("§c✗ La fase de salida ya está activa")
                                );
                                return 0;
                            }
                            
                            LMSSystem.forceExitPhase();
                            context.getSource().sendSuccess(
                                () -> Component.literal("§a✓ Fase de salida forzada (50 segundos)"),
                                true
                            );
                            return 1;
                        })
                    )
                    
                    // /om lms stop
                    .then(Commands.literal("stop")
                        .executes(context -> {
                            if (!LMSSystem.isLMSActive()) {
                                context.getSource().sendFailure(
                                    Component.literal("§c✗ LMS no está activo")
                                );
                                return 0;
                            }
                            
                            LMSSystem.forceEndLMS();
                            context.getSource().sendSuccess(
                                () -> Component.literal("§7LMS detenido manualmente"),
                                true
                            );
                            return 1;
                        })
                    )
                    
                    // /om lms info
                    .then(Commands.literal("info")
                        .executes(context -> {
                            if (!LMSSystem.isLMSActive()) {
                                context.getSource().sendFailure(
                                    Component.literal("§c✗ LMS no está activo")
                                );
                                return 0;
                            }
                            
                            long remaining = LMSSystem.getLMSTimeRemaining();
                            boolean isExitPhase = LMSSystem.isExitPhaseActive();
                            
                            context.getSource().sendSuccess(
                                () -> Component.literal("§e=== Estado de LMS ==="),
                                false
                            );
                            context.getSource().sendSuccess(
                                () -> Component.literal("§7Activo: §aYes"),
                                false
                            );
                            context.getSource().sendSuccess(
                                () -> Component.literal("§7Fase: " + (isExitPhase ? "§eExít (50s)" : "§cTNT Tag (3min)")),
                                false
                            );
                            context.getSource().sendSuccess(
                                () -> Component.literal("§7Tiempo restante: §f" + formatTime(remaining)),
                                false
                            );
                            
                            return 1;
                        })
                    )
                )
                
                // ========== /om setrole <jugador> <rol> ==========
                .then(Commands.literal("setrole")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("role", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                builder.suggest("SONIC");
                                builder.suggest("TAILS");
                                builder.suggest("KNUCKLES");
                                builder.suggest("AMY");
                                builder.suggest("CREAM");
                                builder.suggest("EGGMAN");
                                builder.suggest("X2011");
                                return builder.buildFuture();
                            })
                            .executes(context -> executeSetRole(context))
                        )
                    )
                )
                
                // ========== /om swap <jugador1> <jugador2> ==========
                .then(Commands.literal("swap")
                    .then(Commands.argument("player1", EntityArgument.player())
                        .then(Commands.argument("player2", EntityArgument.player())
                            .executes(context -> executeSwap(context))
                        )
                    )
                )
                
                // ========== /om forceswap <exe> <survivor> ==========
                .then(Commands.literal("forceswap")
                    .then(Commands.argument("executioner", EntityArgument.player())
                        .then(Commands.argument("survivor", EntityArgument.player())
                            .executes(context -> executeForceSwap(context))
                        )
                    )
                )
                
                // ========== /om stats [jugador] ==========
                .then(Commands.literal("stats")
                    .executes(context -> executeStats(context, null))
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            return executeStats(context, target);
                        })
                    )
                )
                
                // ========== /om exit ==========
                .then(Commands.literal("exit")
                    
                    // /om exit register
                    .then(Commands.literal("register")
                        .executes(context -> {
                            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                context.getSource().sendFailure(
                                    Component.literal("§c✗ Solo jugadores pueden registrar salidas")
                                );
                                return 0;
                            }

                            OutcomeMemoriesGameSystem.registerExit(
                                (net.minecraft.server.level.ServerLevel) player.level(),
                                player.blockPosition()
                            );
                            
                            context.getSource().sendSuccess(
                                () -> Component.literal("§a✓ Salida registrada en: " + player.blockPosition().toShortString()),
                                false
                            );
                            return 1;
                        })
                    )
                    
                    // /om exit list
                    .then(Commands.literal("list")
                        .executes(context -> {
                            int count = OutcomeMemoriesGameSystem.getExitCount();
                            
                            if (count == 0) {
                                context.getSource().sendSuccess(
                                    () -> Component.literal("§7No hay salidas registradas"),
                                    false
                                );
                                return 0;
                            }
                            
                            context.getSource().sendSuccess(
                                () -> Component.literal("§e=== Salidas Registradas (" + count + ") ==="),
                                false
                            );

                            OutcomeMemoriesGameSystem.listExits(context.getSource());
                            return 1;
                        })
                    )
                    
                    // /om exit clear
                    .then(Commands.literal("clear")
                        .executes(context -> {
                            int count = OutcomeMemoriesGameSystem.clearExits();
                            
                            context.getSource().sendSuccess(
                                () -> Component.literal("§7Eliminadas " + count + " salidas"),
                                false
                            );
                            return 1;
                        })
                    )
                    
                    // /om exit open
                    .then(Commands.literal("open")
                        .executes(context -> {
                            OutcomeMemoriesGameSystem.openAllExits();
                            context.getSource().sendSuccess(
                                () -> Component.literal("§a✓ Todas las salidas abiertas"),
                                true
                            );
                            return 1;
                        })
                    )
                    
                    // /om exit close
                    .then(Commands.literal("close")
                        .executes(context -> {
                            OutcomeMemoriesGameSystem.closeAllExits();
                            context.getSource().sendSuccess(
                                () -> Component.literal("§c✓ Todas las salidas cerradas"),
                                true
                            );
                            return 1;
                        })
                    )
                )
                
                // ========== /om reload ==========
                .then(Commands.literal("reload")
                    .executes(context -> {
                        // Recargar configuraciones si existen
                        context.getSource().sendSuccess(
                            () -> Component.literal("§a✓ Configuración recargada"),
                            false
                        );
                        return 1;
                    })
                )
                
                // ========== /om debug ==========
                .then(Commands.literal("debug")
                    .executes(context -> {
                        OutcomeMemoriesGameSystem.GameState state = OutcomeMemoriesGameSystem.getCurrentState();
                        boolean lmsActive = LMSSystem.isLMSActive();
                        boolean exitPhase = LMSSystem.isExitPhaseActive();
                        
                        context.getSource().sendSuccess(
                            () -> Component.literal("§e=== Debug Info ==="),
                            false
                        );
                        context.getSource().sendSuccess(
                            () -> Component.literal("§7Game State: §f" + state),
                            false
                        );
                        context.getSource().sendSuccess(
                            () -> Component.literal("§7LMS Active: " + (lmsActive ? "§aYes" : "§cNo")),
                            false
                        );
                        context.getSource().sendSuccess(
                            () -> Component.literal("§7Exit Phase: " + (exitPhase ? "§aYes" : "§cNo")),
                            false
                        );
                        context.getSource().sendSuccess(
                            () -> Component.literal("§7Exits Registered: §f" + OutcomeMemoriesGameSystem.getExitCount()),
                            false
                        );
                        
                        return 1;
                    })
                )
        );
    }
    
    // ========== MÉTODOS DE EJECUCIÓN ==========
    
    private static int executeSetRole(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            String roleStr = StringArgumentType.getString(context, "role").toUpperCase();
            
            PlayerTypeOM role;
            try {
                role = PlayerTypeOM.valueOf(roleStr);
            } catch (IllegalArgumentException e) {
                context.getSource().sendFailure(
                    Component.literal("§c✗ Rol inválido. Roles disponibles: SONIC, TAILS, KNUCKLES, AMY, CREAM, EGGMAN, X2011")
                );
                return 0;
            }
            
            PlayerRegistry.setPlayerType(player, role);
            
            // Si se convierte en exe, actualizar en GameSystem
            if (role == PlayerTypeOM.X2011) {
                OutcomeMemoriesGameSystem.updateCurrentExecutioner(player.getUUID());
            }
            
            context.getSource().sendSuccess(
                () -> Component.literal("§a✓ " + player.getName().getString() + " ahora es §f" + role.name()),
                true
            );
            
            player.sendSystemMessage(Component.literal("§eTu rol ha sido cambiado a: §f" + role.name()));
            
            return 1;
            
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c✗ Error: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int executeSwap(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player1 = EntityArgument.getPlayer(context, "player1");
            ServerPlayer player2 = EntityArgument.getPlayer(context, "player2");
            
            PlayerTypeOM type1 = PlayerRegistry.getPlayerType(player1);
            PlayerTypeOM type2 = PlayerRegistry.getPlayerType(player2);
            
            // Intercambiar
            PlayerRegistry.setPlayerType(player1, type2);
            PlayerRegistry.setPlayerType(player2, type1);
            
            context.getSource().sendSuccess(
                () -> Component.literal("§a✓ Roles intercambiados entre " + 
                    player1.getName().getString() + " y " + player2.getName().getString()),
                true
            );
            
            player1.sendSystemMessage(Component.literal("§eTu rol se intercambió con " + player2.getName().getString()));
            player2.sendSystemMessage(Component.literal("§eTu rol se intercambió con " + player1.getName().getString()));
            
            return 1;
            
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c✗ Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeForceSwap(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer exe = EntityArgument.getPlayer(context, "executioner");
            ServerPlayer survivor = EntityArgument.getPlayer(context, "survivor");

            PlayerTypeOM exeType = PlayerRegistry.getPlayerType(exe);
            PlayerTypeOM survivorType = PlayerRegistry.getPlayerType(survivor);

            if (exeType != PlayerTypeOM.X2011) {
                context.getSource().sendFailure(
                        Component.literal("§c✗ " + exe.getName().getString() + " no es un Executioner")
                );
                return 0;
            }

            if (survivorType == PlayerTypeOM.X2011) {
                context.getSource().sendFailure(
                        Component.literal("§c✗ " + survivor.getName().getString() + " ya es un Executioner")
                );
                return 0;
            }

            // Guardar el tipo anterior del exe
            PlayerRegistry.savePreviousRole(exe.getUUID(), survivorType);

            // Intercambiar roles manualmente
            PlayerRegistry.setPlayerType(exe, survivorType);
            PlayerRegistry.setPlayerType(survivor, PlayerTypeOM.X2011);

            // Actualizar executioner actual en el GameSystem
            OutcomeMemoriesGameSystem.updateCurrentExecutioner(survivor.getUUID());

            // Restaurar salud
            exe.setHealth(exe.getMaxHealth());
            survivor.setHealth(survivor.getMaxHealth());

            // Mensajes
            exe.sendSystemMessage(Component.literal("§a§l¡Ahora eres SURVIVOR!"));
            exe.sendSystemMessage(Component.literal("§7Personaje: §f" + survivorType.name()));

            survivor.sendSystemMessage(Component.literal("§c§l¡Ahora eres EXECUTIONER!"));
            survivor.sendSystemMessage(Component.literal("§7Elimina a todos los survivors"));

            // Anuncio global
            OutcomeMemoriesGameSystem.broadcastMessage("§e⚡ " + survivor.getName().getString() + " §7es ahora el §cExecutioner§7! (Comando admin)");

            context.getSource().sendSuccess(
                    () -> Component.literal("§a✓ Swap forzado entre " +
                            exe.getName().getString() + " y " + survivor.getName().getString()),
                    true
            );

            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c✗ Error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
    
    private static int executeStats(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        try {
            if (target == null) {
                // Mostrar estadísticas globales
                context.getSource().sendSuccess(
                    () -> Component.literal("§e=== Estadísticas Globales ==="),
                    false
                );

                OutcomeMemoriesGameSystem.showAllStats(context.getSource());
                
            } else {
                // Mostrar estadísticas de un jugador específico
                OutcomeMemoriesGameSystem.PlayerStats stats = OutcomeMemoriesGameSystem.getPlayerStats(target.getUUID());
                
                if (stats == null) {
                    context.getSource().sendFailure(
                        Component.literal("§c✗ No hay estadísticas para " + target.getName().getString())
                    );
                    return 0;
                }
                
                context.getSource().sendSuccess(
                    () -> Component.literal("§e=== Stats: " + target.getName().getString() + " ==="),
                    false
                );
                context.getSource().sendSuccess(
                    () -> Component.literal("§7Kills: §c" + stats.kills),
                    false
                );
                context.getSource().sendSuccess(
                    () -> Component.literal("§7Deaths: §c" + stats.deaths),
                    false
                );
                context.getSource().sendSuccess(
                    () -> Component.literal("§7Times as Exe: §e" + stats.timesAsExe),
                    false
                );
                context.getSource().sendSuccess(
                    () -> Component.literal("§7Times as Survivor: §a" + stats.timesAsSurvivor),
                    false
                );
            }
            
            return 1;
            
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c✗ Error: " + e.getMessage()));
            return 0;
        }
    }

    private static String formatTime(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}