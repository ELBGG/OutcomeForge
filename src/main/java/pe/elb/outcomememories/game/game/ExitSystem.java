package pe.elb.outcomememories.game.game;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sistema de control de salidas durante LMS
 * TODO: Adaptar a tu sistema específico de puertas/salidas
 */
public class ExitSystem {
    
    private static final List<ExitDoor> exits = new ArrayList<>();
    
    public static class ExitDoor {
        public final ServerLevel level;
        public final BlockPos pos;
        public boolean isOpen;
        
        public ExitDoor(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
            this.isOpen = true;
        }
    }
    
    /**
     * Registra una salida
     */
    public static void registerExit(ServerLevel level, BlockPos pos) {
        exits.add(new ExitDoor(level, pos));
    }
    
    /**
     * Cierra todas las salidas
     */
    public static void closeAllExits() {
        for (ExitDoor exit : exits) {
            exit.isOpen = false;
            // TODO: Implementar cierre visual (puertas de hierro, barreras, etc.)
            // exit.level.setBlock(exit.pos, Blocks.BARRIER.defaultBlockState(), 3);
        }
        System.out.println("[ExitSystem] All exits closed");
    }
    
    /**
     * Abre una salida aleatoria
     */
    public static void openRandomExit() {
        if (exits.isEmpty()) return;
        
        ExitDoor randomExit = exits.get(new Random().nextInt(exits.size()));
        randomExit.isOpen = true;
        
        // TODO: Implementar apertura visual
        // randomExit.level.setBlock(randomExit.pos, Blocks.AIR.defaultBlockState(), 3);
        
        System.out.println("[ExitSystem] Random exit opened at " + randomExit.pos);
    }
    
    /**
     * Abre todas las salidas
     */
    public static void openAllExits() {
        for (ExitDoor exit : exits) {
            exit.isOpen = true;
            // TODO: Implementar apertura visual
        }
        System.out.println("[ExitSystem] All exits opened");
    }
    
    /**
     * Verifica si un jugador llegó a una salida
     */
    public static boolean isPlayerAtExit(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        
        for (ExitDoor exit : exits) {
            if (!exit.isOpen) continue;
            if (!exit.level.equals(player.level())) continue;
            
            if (exit.pos.distSqr(playerPos) <= 4.0) { // 2 bloques de radio
                return true;
            }
        }
        
        return false;
    }

    // Añadir estos métodos a ExitSystem.java

    /**
     * Obtiene el número de salidas registradas
     */
    public static int getExitCount() {
        return exits.size();
    }

    /**
     * Lista todas las salidas (para comando)
     */
    public static void listExits(CommandSourceStack source) {
        int index = 1;
        for (ExitDoor exit : exits) {
            String status = exit.isOpen ? "§aAbierta" : "§cCerrada";
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "§7%d. §f%s §7[%s§7]",
                            index,
                            exit.pos.toShortString(),
                            status
                    )),
                    false
            );
        }
    }

    /**
     * Limpia todas las salidas (para comando)
     */
    public static int clearExits() {
        int count = exits.size();
        exits.clear();
        return count;
    }
}