package pe.elb.outcomememories.client.overlays;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

public class DodgeMeterHud {

    private static final ResourceLocation DODGE_BAR_BG = new ResourceLocation("outcomememories", "textures/gui/healbar1.png");
    private static final ResourceLocation DODGE_BAR_FG = new ResourceLocation("outcomememories", "textures/gui/healbar2.png");

    private static final int HUD_X = 20;
    private static final int HUD_Y_OFFSET = -85;
    private static final int HUD_WIDTH = 259;
    private static final int HUD_HEIGHT = 30;

    private static int getDodgeColor(float percent) {
        if (percent <= 0.3f) {
            float t = percent / 0.3f;
            int r = 0xFF;
            int g = (int)(0x00 + (0xFF - 0x00) * t);
            int b = 0x00;
            return (r << 16) | (g << 8) | b;
        } else if (percent <= 0.7f) {
            float t = (percent - 0.3f) / 0.4f;
            int r = (int)(0xFF * (1.0f - t) + 0x00 * t);
            int g = (int)(0xFF * (1.0f - t) + 0xFF * t);
            int b = (int)(0x00 * (1.0f - t) + 0xFF * t);
            return (r << 16) | (g << 8) | b;
        } else {
            float t = (percent - 0.7f) / 0.3f;
            int r = 0x00;
            int g = 0xFF;
            int b = 0xFF;
            int brightness = (int)(50 * t);
            r = Math.min(255, r + brightness);
            g = Math.min(255, g + brightness);
            b = Math.min(255, b + brightness);
            return (r << 16) | (g << 8) | b;
        }
    }

    public static void render(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // === ARREGLADO: Usar cache del cliente ===
        float dodgeHP = DodgeMeterClientCache.getDodgeHP(player.getUUID());
        float maxDodgeHP = 50.0F;
        float percent = dodgeHP / maxDodgeHP;

        // Solo mostrar si tiene dodge HP o es Sonic (simplificado)
        // Si quieres verificar tipo, necesitarás otro packet de sincronización
        if (dodgeHP <= 0 && percent <= 0) {
            return; // No mostrar si está en 0
        }

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = HUD_X;
        int y = screenHeight + HUD_Y_OFFSET;
        int width = HUD_WIDTH;
        int height = HUD_HEIGHT;

        int dodgeColor = getDodgeColor(percent);

        float alpha = 1.0f;
        if (percent < 0.2f) {
            long time = System.currentTimeMillis();
            alpha = 0.5f + 0.5f * (float)Math.abs(Math.sin(time / 200.0));
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderTexture(0, DODGE_BAR_FG);
        RenderSystem.setShaderColor(
                ((dodgeColor >> 16) & 0xFF) / 255f,
                ((dodgeColor >> 8) & 0xFF) / 255f,
                (dodgeColor & 0xFF) / 255f,
                alpha
        );

        int dodgeWidth = (int)(width * percent);
        gui.blit(DODGE_BAR_FG, x, y, 0, 0, dodgeWidth, height, width, height);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        RenderSystem.setShaderTexture(0, DODGE_BAR_BG);
        gui.blit(DODGE_BAR_BG, x, y, 0, 0, width, height, width, height);

        if (percent >= 0.95f) {
            long time = System.currentTimeMillis();
            float glowAlpha = 0.3f + 0.2f * (float)Math.sin(time / 300.0);

            RenderSystem.setShaderColor(1f, 1f, 1f, glowAlpha);
            gui.blit(DODGE_BAR_FG, x, y, 0, 0, width, height, width, height);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        RenderSystem.disableBlend();

        String dodgeText = String.format("%.0f", dodgeHP);
        int fontScale = 1;

        int textX = x + width / 2 - mc.font.width(dodgeText) * fontScale / 2;
        int textY = y + height / 2 - 4;

        gui.pose().pushPose();
        gui.pose().scale(fontScale, fontScale, 1.0f);

        int textColor = percent < 0.2f ? 0xFF0000 : 0x00FFFF;

        gui.drawString(mc.font, dodgeText, textX / fontScale, textY / fontScale, textColor, true);
        gui.pose().popPose();

        String label = "DODGE METER";
        int labelX = x + 5;
        int labelY = y - 10;

        gui.drawString(mc.font, label, labelX, labelY, 0x00FFFF, true);
    }
}