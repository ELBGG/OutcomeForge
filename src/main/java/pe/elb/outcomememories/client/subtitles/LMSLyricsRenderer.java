package pe.elb.outcomememories.client.subtitles;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderiza subtítulos LMS en pantalla con soporte de colores y estilos
 */
@OnlyIn(Dist.CLIENT)
public class LMSLyricsRenderer {

    private static final LMSLyricsRenderer INSTANCE = new LMSLyricsRenderer();
    private static final Pattern COLOR_PATTERN = Pattern.compile("\\[([#a-zA-Z_]+)]([^\\[]+?)\\[/]");

    private LMSLyricsSystem.Subtitle currentSubtitle;

    private LMSLyricsRenderer() {}

    public static LMSLyricsRenderer getInstance() {
        return INSTANCE;
    }

    // ============ RENDERIZADO ============

    /**
     * Renderiza el subtítulo actual
     */
    public void render(GuiGraphics guiGraphics) {
        if (currentSubtitle == null || currentSubtitle.getText().isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        String text = currentSubtitle.getText();
        String position = currentSubtitle.getMetadata("position", "bottom");
        String bgColor = currentSubtitle.getMetadata("backgroundColor", "rgba(0, 0, 0, 0.7)");
        float scale = currentSubtitle.getMetadata("textScale", 1.0f);

        // Crear Component con colores aplicados
        Component styledComponent = createStyledComponent(text);

        // Calcular dimensiones
        int totalWidth = font.width(styledComponent);
        int totalHeight = font.lineHeight;
        int scaledWidth = (int) (totalWidth * scale);
        int scaledHeight = (int) (totalHeight * scale);

        // Calcular posición
        int xPos = (screenWidth - scaledWidth) / 2;
        int yPos = switch (position) {
            case "top" -> 25;
            case "mid" -> (screenHeight - scaledHeight) / 2;
            default -> screenHeight - 60 - scaledHeight;
        };

        // Padding
        int paddingX = 5;
        int paddingY = 3;

        // Parsear color de fondo
        int bgColorValue = parseBackgroundColor(bgColor);

        // Aplicar escala
        if (scale != 1.0f) {
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();

            float centerX = (float) screenWidth / 2.0f;
            float centerY = (float) yPos + (float) scaledHeight / 2.0f;

            poseStack.translate(centerX, centerY, 0);
            poseStack.scale(scale, scale, 1.0f);
            poseStack.translate(-centerX / scale, -centerY / scale, 0);

            xPos = (int) (((float) screenWidth / scale - (float) totalWidth) / 2.0f);
            yPos = (int) ((float) yPos / scale);
        }

        // Dibujar fondo
        int bgXStart = xPos - paddingX;
        int bgYStart = yPos - paddingY;
        int bgXEnd = xPos + totalWidth + paddingX;
        int bgYEnd = yPos + totalHeight + paddingY;

        guiGraphics.fill(bgXStart, bgYStart, bgXEnd, bgYEnd, bgColorValue);

        // Renderizar texto
        guiGraphics.drawString(font, styledComponent, xPos, yPos, 0xFFFFFF, false);

        if (scale != 1.0f) {
            guiGraphics.pose().popPose();
        }
    }

    /**
     * Crea un Component con estilo (solo colores)
     */
    private Component createStyledComponent(String text) {
        MutableComponent result = Component.empty();
        List<TextComponent> components = parseTextWithColors(text);

        for (TextComponent comp : components) {
            MutableComponent part = Component.literal(comp.text);
            Style style = Style.EMPTY.withColor(comp.color);
            part = part.withStyle(style);
            result.append(part);
        }

        return result;
    }

    // ============ CONTROL ============

    public void tick() {
        if (currentSubtitle != null && !currentSubtitle.tick()) {
            currentSubtitle = null;
        }
    }

    public void showSubtitle(LMSLyricsSystem.Subtitle subtitle) {
        if (subtitle != null) {
            this.currentSubtitle = subtitle;
            subtitle.reset();
        }
    }

    public void clearSubtitle() {
        this.currentSubtitle = null;
    }

    public LMSLyricsSystem.Subtitle getCurrentSubtitle() {
        return currentSubtitle;
    }

    // ============ PARSEO DE COLORES ============

    private List<TextComponent> parseTextWithColors(String text) {
        List<TextComponent> components = new ArrayList<>();
        Matcher matcher = COLOR_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String beforeText = text.substring(lastEnd, matcher.start());
                components.add(new TextComponent(beforeText, 0xFFFFFFFF));
            }

            String colorName = matcher.group(1);
            String coloredText = matcher.group(2);
            int color = parseColorName(colorName);
            components.add(new TextComponent(coloredText, color));

            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            components.add(new TextComponent(remainingText, 0xFFFFFFFF));
        }

        if (components.isEmpty()) {
            components.add(new TextComponent(text, 0xFFFFFFFF));
        }

        return components;
    }

    private int parseColorName(String colorName) {
        if (colorName == null || colorName.isEmpty()) {
            return 0xFFFFFFFF;
        }

        if (colorName.startsWith("#")) {
            try {
                String hex = colorName.substring(1);
                return 0xFF000000 | Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                return 0xFFFFFFFF;
            }
        }

        return switch (colorName.toLowerCase()) {
            case "black" -> 0xFF000000;
            case "dark_blue" -> 0xFF0000AA;
            case "dark_green" -> 0xFF00AA00;
            case "dark_aqua" -> 0xFF00AAAA;
            case "dark_red" -> 0xFFAA0000;
            case "dark_purple" -> 0xFFAA00AA;
            case "gold" -> 0xFFFFAA00;
            case "gray" -> 0xFFAAAAAA;
            case "dark_gray" -> 0xFF555555;
            case "blue" -> 0xFF5555FF;
            case "green" -> 0xFF55FF55;
            case "aqua" -> 0xFF55FFFF;
            case "red" -> 0xFFFF5555;
            case "light_purple", "purple" -> 0xFFFF55FF;
            case "yellow" -> 0xFFFFFF55;
            default -> 0xFFFFFFFF;
        };
    }

    private int parseBackgroundColor(String bgColor) {
        if (bgColor.startsWith("rgba(")) {
            try {
                String[] parts = bgColor.replace("rgba(", "").replace(")", "").split(",");
                if (parts.length == 4) {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    int a = (int) (Float.parseFloat(parts[3].trim()) * 255);
                    return (a << 24) | (r << 16) | (g << 8) | b;
                }
            } catch (Exception ignored) {}
        }
        return 0xB0000000;
    }

    // ============ CLASE INTERNA ============

    private record TextComponent(String text, int color) {
    }
}
