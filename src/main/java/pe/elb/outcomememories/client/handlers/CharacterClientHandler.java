package pe.elb.outcomememories.client.handlers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.client.cache.PlayerSkillsClientCache;
import pe.elb.outcomememories.client.renderer.EnergyShieldRenderer;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.skills.cream.CreamSkillPacket;
import pe.elb.outcomememories.net.skills.eggman.EggmanSkillPacket;
import pe.elb.outcomememories.net.skills.knuckles.KnucklesSkillPacket;
import pe.elb.outcomememories.net.skills.tails.TailsSkillPacket;

@Mod.EventBusSubscriber(modid = Outcomememories.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CharacterClientHandler {

    private static boolean isChargingPunch = false;
    private static boolean wasLeftClickPressed = false;
    private static int clickCooldown = 0;
    private static boolean wasCollidingHorizontally = false;
    private static int ticksSinceLastWallCling = 0;
    private static final int WALL_CLING_COOLDOWN_TICKS = 10;
    private static boolean wasTailsLeftClickPressed = false;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(mc.player);
        if (type == null) return;

        if (event.getKey() == GLFW.GLFW_KEY_SPACE) {
            switch (type) {
                case CREAM -> {
                    if (event.getAction() == GLFW.GLFW_PRESS) {
                        NetworkHandler.CHANNEL.sendToServer(new CreamSkillPacket(CreamSkillPacket.SkillType.GLIDE));
                    }
                }
                case EGGMAN -> {
                    if (event.getAction() == GLFW.GLFW_PRESS) {
                        NetworkHandler.CHANNEL.sendToServer(new EggmanSkillPacket(EggmanSkillPacket.SkillType.DOUBLE_JUMP));
                    }
                }
                case TAILS -> {
                    if (event.getAction() == GLFW.GLFW_PRESS) {
                        NetworkHandler.CHANNEL.sendToServer(new TailsSkillPacket(TailsSkillPacket.SkillType.GLIDE_ASCEND, true));
                    } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                        NetworkHandler.CHANNEL.sendToServer(new TailsSkillPacket(TailsSkillPacket.SkillType.GLIDE_ASCEND, false));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(player);
        if (type == null) {
            resetAllStates();
            return;
        }

        switch (type) {
            case KNUCKLES -> {
                handleKnucklesPunch(mc, player);
                handleKnucklesWallDetection(player);
            }
            case TAILS -> handleTailsLaser(mc);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        
        if (!PlayerSkillsClientCache.hasShieldActive(player.getUUID())) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() / 2.0, 0.0);

        float warningPhase = PlayerSkillsClientCache.getWarningPhase(player.getUUID());

        EnergyShieldRenderer.renderEnergyShield(
            poseStack, event.getMultiBufferSource(), event.getPackedLight(),
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, warningPhase
        );

        if (warningPhase < 0.5F || (System.currentTimeMillis() % 400) < 200) {
            EnergyShieldRenderer.renderElectricArcs(
                poseStack, event.getMultiBufferSource(), event.getPackedLight(),
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
            );
        }

        poseStack.popPose();
    }

    private static void handleKnucklesPunch(Minecraft mc, LocalPlayer player) {
        if (clickCooldown > 0) clickCooldown--;

        if (!isChargingPunch) {
            wasLeftClickPressed = false;
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean isPressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isPressed && !wasLeftClickPressed && clickCooldown == 0) {
            boolean isGroundSlam = !player.onGround();
            
            Outcomememories.LOGGER.info("Knuckles Punch ejecutado - Ground Slam: {}", isGroundSlam);
            
            NetworkHandler.CHANNEL.sendToServer(new KnucklesSkillPacket(KnucklesSkillPacket.SkillType.PUNCH_EXECUTE));
            
            isChargingPunch = false;
            
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(isGroundSlam ? "§4💥 GROUND SLAM!" : "§c👊 PUNCH!"),
                true
            );
            
            clickCooldown = 5;
        }

        wasLeftClickPressed = isPressed;
    }

    private static void handleKnucklesWallDetection(LocalPlayer player) {
        ticksSinceLastWallCling++;

        boolean isCollidingNow = player.horizontalCollision;
        boolean justHitWall = isCollidingNow && !wasCollidingHorizontally;

        if (justHitWall && !player.onGround() && ticksSinceLastWallCling >= WALL_CLING_COOLDOWN_TICKS) {
            NetworkHandler.CHANNEL.sendToServer(new KnucklesSkillPacket(KnucklesSkillPacket.SkillType.WALL_CLING));
            ticksSinceLastWallCling = 0;
            Outcomememories.LOGGER.debug("Knuckles Wall Cling detectado");
        }

        wasCollidingHorizontally = isCollidingNow;
    }

    private static void handleTailsLaser(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        boolean isPressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isPressed && !wasTailsLeftClickPressed) {
            NetworkHandler.CHANNEL.sendToServer(new TailsSkillPacket(TailsSkillPacket.SkillType.LASER_EXECUTE));
        }

        wasTailsLeftClickPressed = isPressed;
    }

    public static void setChargingPunch(boolean charging) {
        isChargingPunch = charging;
    }

    public static boolean isChargingPunch() {
        return isChargingPunch;
    }

    private static void resetAllStates() {
        wasLeftClickPressed = false;
        wasCollidingHorizontally = false;
        wasTailsLeftClickPressed = false;
    }

    public static void resetAll() {
        isChargingPunch = false;
        wasLeftClickPressed = false;
        clickCooldown = 0;
        wasCollidingHorizontally = false;
        ticksSinceLastWallCling = 0;
        wasTailsLeftClickPressed = false;
    }
}