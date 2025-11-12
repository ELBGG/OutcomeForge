package pe.elb.outcomememories.client.overlays.guis;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import pe.elb.outcomememories.client.cache.PlayerSkillsClientCache;
import pe.elb.outcomememories.game.PlayerTypeOM;

import static pe.elb.outcomememories.game.game.PlayerRegistry.getPlayerType;

public class PlayerMeterHud {

    private static final ResourceLocation METER_BAR_BG = new ResourceLocation("outcomememories", "textures/gui/healbar1.png");
    private static final ResourceLocation METER_BAR_FG = new ResourceLocation("outcomememories", "textures/gui/healbar2.png");

    private static final int HUD_X = 20;
    private static final int HUD_Y_OFFSET = -85;
    private static final int HUD_WIDTH = 259;
    private static final int HUD_HEIGHT = 30;

    public static void render(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        PlayerTypeOM playerType = getPlayerType(player.getUUID());
        if (playerType == null) return;

        float currentValue = 0;
        float maxValue = 0;
        String label = "";
        int baseColor = 0x00FFFF;

        switch (playerType) {
            case SONIC:
                currentValue = PlayerSkillsClientCache.getDodgeHP(player.getUUID());
                maxValue = 50.0F;
                label = "DODGE METER";
                baseColor = 0x0080FF;
                break;

            case BLAZE:
                currentValue = getSolMeter(player.getUUID());
                maxValue = 100.0F;
                label = "SOL METER";
                baseColor = 0xFF8000;
                break;

            default:
                return;
        }

        if (currentValue <= 0 && maxValue <= 0) {
            return;
        }

        float percent = currentValue / maxValue;

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = HUD_X;
        int y = screenHeight + HUD_Y_OFFSET;
        int width = HUD_WIDTH;
        int height = HUD_HEIGHT;

        int meterColor = getMeterColor(percent, baseColor);

        float alpha = 1.0f;
        if (percent < 0.2f) {
            long time = System.currentTimeMillis();
            alpha = 0.5f + 0.5f * (float)Math.abs(Math.sin(time / 200.0));
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderTexture(0, METER_BAR_FG);
        RenderSystem.setShaderColor(
                ((meterColor >> 16) & 0xFF) / 255f,
                ((meterColor >> 8) & 0xFF) / 255f,
                (meterColor & 0xFF) / 255f,
                alpha
        );

        int meterWidth = (int)(width * percent);
        gui.blit(METER_BAR_FG, x, y, 0, 0, meterWidth, height, width, height);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        RenderSystem.setShaderTexture(0, METER_BAR_BG);
        gui.blit(METER_BAR_BG, x, y, 0, 0, width, height, width, height);

        if (percent >= 0.95f) {
            long time = System.currentTimeMillis();
            float glowAlpha = 0.3f + 0.2f * (float)Math.sin(time / 300.0);

            RenderSystem.setShaderColor(1f, 1f, 1f, glowAlpha);
            gui.blit(METER_BAR_FG, x, y, 0, 0, width, height, width, height);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        RenderSystem.disableBlend();

        String valueText = String.format("%.0f", currentValue);
        int fontScale = 1;

        int textX = x + width / 2 - mc.font.width(valueText) * fontScale / 2;
        int textY = y + height / 2 - 4;

        gui.pose().pushPose();
        gui.pose().scale(fontScale, fontScale, 1.0f);

        int textColor = percent < 0.2f ? 0xFF0000 : baseColor;

        gui.drawString(mc.font, valueText, textX / fontScale, textY / fontScale, textColor, true);
        gui.pose().popPose();

        int labelX = x + 5;
        int labelY = y - 10;

        gui.drawString(mc.font, label, labelX, labelY, baseColor, true);
    }

    private static int getMeterColor(float percent, int baseColor) {
        if (percent <= 0.3f) {
            float t = percent / 0.3f;
            int r = 0xFF;
            int g = (int)(0x00 + ((baseColor >> 8) & 0xFF) * t);
            int b = 0x00;
            return (r << 16) | (g << 8) | b;
        } else if (percent <= 0.7f) {
            float t = (percent - 0.3f) / 0.4f;
            int targetR = (baseColor >> 16) & 0xFF;
            int targetG = (baseColor >> 8) & 0xFF;
            int targetB = baseColor & 0xFF;

            int r = (int)(0xFF * (1.0f - t) + targetR * t);
            int g = (int)(0xFF * (1.0f - t) + targetG * t);
            int b = (int)(0x00 * (1.0f - t) + targetB * t);
            return (r << 16) | (g << 8) | b;
        } else {
            return baseColor;
        }
    }

    private static float getSolMeter(java.util.UUID playerUUID) {
        // TODO: Implementar cache de Sol Meter similar a DodgeMeterClientCache
        // Por ahora retorna 0
        return 0.0F;
    }
}