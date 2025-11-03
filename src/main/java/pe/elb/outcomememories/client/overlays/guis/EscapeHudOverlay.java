package pe.elb.outcomememories.client.overlays;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class EscapeHudOverlay implements IGuiOverlay {
    private static final ResourceLocation NORMAL = new ResourceLocation("outcomememories", "textures/gui/0.png");
    private static final ResourceLocation PRESSED = new ResourceLocation("outcomememories", "textures/gui/1.png");

    private static final int SPRITE_WIDTH = 64;
    private static final int SPRITE_HEIGHT = 20;
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 6;

    // Sistema de timing
    private static final int SWEET_SPOT_SIZE = 20; // Tamaño del sweet spot
    private static int sweetSpotPosition = 0; // Posición actual del sweet spot (0-100)
    private static int sweetSpotDirection = 1; // 1 = derecha, -1 = izquierda
    private static final int SWEET_SPOT_SPEED = 2; // Velocidad del movimiento

    private static int pressAnimationTicks = 0;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!TrapClientHandler.isPlayerTrapped()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int centerX = screenWidth / 2;
        int hotbarY = screenHeight - 22;
        int overlayY = hotbarY - 50;

        float progress = TrapClientHandler.getEscapeProgress();

        // Renderizar barra de escape
        renderEscapeBar(guiGraphics, centerX - BAR_WIDTH / 2, overlayY - 10, progress);

        // Renderizar sweet spot móvil
        renderSweetSpot(guiGraphics, centerX - BAR_WIDTH / 2, overlayY - 18);

        // Renderizar sprite de ESPACIO
        renderSpaceSprite(guiGraphics, centerX - SPRITE_WIDTH / 2, overlayY);

        // Renderizar indicador de timing
        renderTimingIndicator(guiGraphics, centerX, overlayY + 30);

        // Actualizar animación
        if (pressAnimationTicks > 0) {
            pressAnimationTicks--;
        }

        // Actualizar posición del sweet spot
        updateSweetSpot();
    }

    private void renderEscapeBar(GuiGraphics guiGraphics, int x, int y, float progress) {
        int filledWidth = (int) (BAR_WIDTH * (progress / 100.0f));

        // Borde
        guiGraphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xFF000000);

        // Fondo
        guiGraphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF442222);

        // Progreso
        if (filledWidth > 0) {
            int color = getBarColor(progress);
            guiGraphics.fill(x, y, x + filledWidth, y + BAR_HEIGHT, color);
        }

        // Texto de progreso
        String progressText = String.format("%.0f%%", progress);
        int textX = x + BAR_WIDTH / 2 - Minecraft.getInstance().font.width(progressText) / 2;
        guiGraphics.drawString(Minecraft.getInstance().font, progressText, textX, y - 10, 0xFFFFFF, true);
    }

    private void renderSweetSpot(GuiGraphics guiGraphics, int x, int y) {
        // Calcular posición del sweet spot
        int sweetSpotX = x + (int)((BAR_WIDTH - SWEET_SPOT_SIZE) * (sweetSpotPosition / 100.0f));

        // Renderizar sweet spot (zona verde brillante)
        guiGraphics.fill(sweetSpotX, y, sweetSpotX + SWEET_SPOT_SIZE, y + 3, 0xFF00FF00);
    }

    private void updateSweetSpot() {
        // Mover sweet spot de lado a lado
        sweetSpotPosition += SWEET_SPOT_SPEED * sweetSpotDirection;

        // Cambiar dirección al llegar a los bordes
        if (sweetSpotPosition >= 100) {
            sweetSpotPosition = 100;
            sweetSpotDirection = -1;
        } else if (sweetSpotPosition <= 0) {
            sweetSpotPosition = 0;
            sweetSpotDirection = 1;
        }
    }

    private void renderTimingIndicator(GuiGraphics guiGraphics, int centerX, int y) {
        String text = "¡Presiona ESPACIO cuando esté en verde!";
        int textWidth = Minecraft.getInstance().font.width(text);
        guiGraphics.drawString(Minecraft.getInstance().font, text, centerX - textWidth / 2, y, 0xFFFFFF00, true);
    }

    private int getBarColor(float progress) {
        if (progress < 25.0f) return 0xFFAA2222; // Rojo
        if (progress < 50.0f) return 0xFFAA6622; // Naranja
        if (progress < 75.0f) return 0xFFAAAA22; // Amarillo
        return 0xFF22AA22; // Verde
    }

    private void renderSpaceSprite(GuiGraphics guiGraphics, int x, int y) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();

        ResourceLocation texture = pressAnimationTicks > 0 ? PRESSED : NORMAL;
        RenderSystem.setShaderTexture(0, texture);

        guiGraphics.blit(texture, x, y, 0, 0, SPRITE_WIDTH, SPRITE_HEIGHT, SPRITE_WIDTH, SPRITE_HEIGHT);

        RenderSystem.disableBlend();
    }

    public static void onSpacePressed() {
        pressAnimationTicks = 8;
    }

    /**
     * Verifica si el jugador presionó en el momento perfecto
     */
    public static boolean isPerfectTiming() {
        float currentProgress = TrapClientHandler.getEscapeProgress();
        int barPosition = (int)(currentProgress); // Posición actual en la barra (0-100)

        // Verificar si está dentro del sweet spot
        return Math.abs(barPosition - sweetSpotPosition) <= (SWEET_SPOT_SIZE / 2);
    }
}