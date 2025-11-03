package pe.elb.outcomememories.client.overlays;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

public class PlayerHealtBarHud {
    private static final ResourceLocation HEALBAR_BG = new ResourceLocation("outcomememories", "textures/gui/healbar1.png"); // healbar1
    private static final ResourceLocation HEALBAR_FG = new ResourceLocation("outcomememories", "textures/gui/healbar2.png"); // healbar2

    private static final int HUD_X = 20;
    private static final int HUD_Y = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 93;
    private static final int HUD_WIDTH = 259;
    private static final int HUD_HEIGHT = 73;

    private static final float PLAYER_OPACITY = 0.55f;
    private static final int PLAYER_OFFSET_X = 30;
    private static final int PLAYER_OFFSET_Y = 0;
    private static final int PLAYER_SCALE = 50;

    private static int getGradientColor(float percent) {
        if (percent <= 0.5f) {
            float t = percent / 0.5f;
            int r = 0xFF;
            int g = (int)(0x3F + (0xE5 - 0x3F) * t);
            int b = (int)(0x3F * (1.0f - t));
            return (r << 16) | (g << 8) | b;
        } else {
            float t = (percent - 0.5f) / 0.5f;
            int r = (int)(0xFF * (1.0f - t) + 0x7C * t);
            int g = (int)(0xE5 * (1.0f - t) + 0x3F * t);
            int b = (int)(0x3F * (1.0f - t) + 0xFF * t);
            return (r << 16) | (g << 8) | b;
        }
    }

    public static void render(GuiGraphics gui, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // --- NUEVO: usamos directamente la vida del jugador sin regla de tres ---
        float customHealth = player.getHealth(); // rango 0–100
        float customMaxHealth = player.getMaxHealth(); // rango 0–100
        float percent = customHealth / customMaxHealth;

        int barColor = getGradientColor(percent);

        // --- 1) Render del jugador (atrás) ---
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();

        gui.pose().pushPose();
        gui.pose().translate(0, 0, -200);
        RenderSystem.setShaderColor(1f, 1f, 1f, PLAYER_OPACITY);

        int entityX = x + PLAYER_OFFSET_X + 20;
        int entityY = y + height + PLAYER_OFFSET_Y - 20;
        int entityScale = PLAYER_SCALE + 10;

        Quaternionf rotX = new Quaternionf().rotationX((float) Math.toRadians(180));
        Quaternionf rotY = new Quaternionf().rotationY((float) Math.toRadians(180));

        InventoryScreen.renderEntityInInventory(gui, entityX, entityY, entityScale, rotX, rotY, mc.player);
        gui.pose().popPose();

        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // --- 2) Render healbar2 (medio) ---
        RenderSystem.setShaderTexture(0, HEALBAR_FG);
        RenderSystem.setShaderColor(
                ((barColor >> 16) & 0xFF) / 255f,
                ((barColor >> 8) & 0xFF) / 255f,
                (barColor & 0xFF) / 255f,
                1.0f
        );
        int healthWidth = (int)(width * percent);
        gui.blit(HEALBAR_FG, x, y, 0, 0, healthWidth, height, width, height);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // --- 3) Render healbar1 (encima) ---
        RenderSystem.setShaderTexture(0, HEALBAR_BG);
        gui.blit(HEALBAR_BG, x, y, 0, 0, width, height, width, height);

        // --- 4) Texto de salud encima de todo ---
        String healthStr = String.valueOf((int) customHealth);
        int fontScale = 2;
        int healthTextX = x + width / 2 - mc.font.width(healthStr) * fontScale / 2;
        int healthTextY = y + height / 2 - 12 * fontScale;

        gui.pose().pushPose();
        gui.pose().scale(fontScale, fontScale, 1.0f);
        gui.drawString(mc.font, healthStr, healthTextX / fontScale, healthTextY / fontScale, 0xA3FF80, true);
        gui.pose().popPose();
    }
}
