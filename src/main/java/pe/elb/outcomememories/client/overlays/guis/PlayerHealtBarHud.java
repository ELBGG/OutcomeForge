package pe.elb.outcomememories.client.overlays.guis;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Quaternionf;

/**
 * HUD de barra de salud personalizada con modelo del jugador
 */
public class PlayerHealtBarHud implements IGuiOverlay {

    private static final ResourceLocation HEALBAR_BG = new ResourceLocation("outcomememories", "textures/gui/healbar1.png");
    private static final ResourceLocation HEALBAR_FG = new ResourceLocation("outcomememories", "textures/gui/healbar2.png");

    private static final int HUD_WIDTH = 259;
    private static final int HUD_HEIGHT = 73;
    private static final int HUD_X = 20;

    private static final float PLAYER_OPACITY = 0.55f;
    private static final int PLAYER_OFFSET_X = 30;
    private static final int PLAYER_OFFSET_Y = 0;
    private static final int PLAYER_SCALE = 50;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        // No renderizar si no hay jugador o está en modo espectador
        if (player == null || player.isSpectator()) {
            return;
        }

        // Calcular posición Y dinámica basada en el tamaño de la pantalla
        int hudY = screenHeight - HUD_HEIGHT - 20;

        // Renderizar el HUD
        renderHealthBar(guiGraphics, mc, player, HUD_X, hudY, HUD_WIDTH, HUD_HEIGHT);
    }

    /**
     * Renderiza la barra de salud completa
     */
    private void renderHealthBar(GuiGraphics guiGraphics, Minecraft mc, LocalPlayer player,
                                 int x, int y, int width, int height) {
        // Obtener salud actual
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float healthPercent = Math.min(1.0f, currentHealth / maxHealth);

        // Calcular color del gradiente
        int barColor = getGradientColor(healthPercent);

        // 1. Renderizar modelo del jugador (fondo)
        renderPlayerModel(guiGraphics, mc, player, x, y, width, height);

        // 2. Renderizar barra de salud coloreada (medio)
        renderHealthForeground(guiGraphics, x, y, width, height, healthPercent, barColor);

        // 3. Renderizar marco/fondo de la barra (encima)
        renderHealthBackground(guiGraphics, x, y, width, height);

        // 4. Renderizar texto de salud (más encima)
        renderHealthText(guiGraphics, mc, x, y, width, height, currentHealth);
    }

    /**
     * Renderiza el modelo del jugador en el fondo
     */
    private void renderPlayerModel(GuiGraphics guiGraphics, Minecraft mc, LocalPlayer player,
                                   int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();

        guiGraphics.pose().pushPose();

        // Mover a una capa más atrás
        guiGraphics.pose().translate(0, 0, -200);

        // Aplicar opacidad
        RenderSystem.setShaderColor(1f, 1f, 1f, PLAYER_OPACITY);

        // Calcular posición del modelo
        int entityX = x + PLAYER_OFFSET_X + 20;
        int entityY = y + height + PLAYER_OFFSET_Y - 20;
        int entityScale = PLAYER_SCALE + 10;

        // Rotaciones para el modelo
        Quaternionf rotX = new Quaternionf().rotationX((float) Math.toRadians(180));
        Quaternionf rotY = new Quaternionf().rotationY((float) Math.toRadians(180));

        // Renderizar entidad
        InventoryScreen.renderEntityInInventory(
                guiGraphics,
                entityX,
                entityY,
                entityScale,
                rotX,
                rotY,
                player
        );

        guiGraphics.pose().popPose();

        // Restaurar estado de renderizado
        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Renderiza la barra de salud coloreada (foreground)
     */
    private void renderHealthForeground(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                        float healthPercent, int barColor) {
        RenderSystem.setShaderTexture(0, HEALBAR_FG);

        // Aplicar color del gradiente
        float r = ((barColor >> 16) & 0xFF) / 255f;
        float g = ((barColor >> 8) & 0xFF) / 255f;
        float b = (barColor & 0xFF) / 255f;
        RenderSystem.setShaderColor(r, g, b, 1.0f);

        // Calcular ancho de la barra según la salud
        int healthWidth = (int)(width * healthPercent);

        // Renderizar barra
        guiGraphics.blit(HEALBAR_FG, x, y, 0, 0, healthWidth, height, width, height);

        // Restaurar color
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Renderiza el marco/fondo de la barra
     */
    private void renderHealthBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        RenderSystem.setShaderTexture(0, HEALBAR_BG);
        guiGraphics.blit(HEALBAR_BG, x, y, 0, 0, width, height, width, height);
    }

    /**
     * Renderiza el texto de salud
     */
    private void renderHealthText(GuiGraphics guiGraphics, Minecraft mc, int x, int y,
                                  int width, int height, float health) {
        String healthStr = String.valueOf((int) health);
        int fontScale = 2;

        // Calcular posición centrada
        int healthTextX = x + width / 2 - mc.font.width(healthStr) * fontScale / 2;
        int healthTextY = y + height / 2 - 12 * fontScale - 5;

        // Renderizar con escala
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(fontScale, fontScale, 1.0f);
        guiGraphics.drawString(
                mc.font,
                healthStr,
                healthTextX / fontScale,
                healthTextY / fontScale,
                0xA3FF80, // Color verde claro
                true // Sombra
        );
        guiGraphics.pose().popPose();
    }

    /**
     * Calcula el color del gradiente basado en el porcentaje de salud
     * 0% = Rojo, 50% = Amarillo, 100% = Verde/Azul
     */
    private static int getGradientColor(float percent) {
        if (percent <= 0.5f) {
            // De rojo a amarillo (0% - 50%)
            float t = percent / 0.5f;
            int r = 0xFF;
            int g = (int)(0x3F + (0xE5 - 0x3F) * t);
            int b = (int)(0x3F * (1.0f - t));
            return (r << 16) | (g << 8) | b;
        } else {
            // De amarillo a verde/azul (50% - 100%)
            float t = (percent - 0.5f) / 0.5f;
            int r = (int)(0xFF * (1.0f - t) + 0x7C * t);
            int g = (int)(0xE5 * (1.0f - t) + 0x3F * t);
            int b = (int)(0x3F * (1.0f - t) + 0xFF * t);
            return (r << 16) | (g << 8) | b;
        }
    }
}