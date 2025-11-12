package pe.elb.outcomememories.client.overlays.guis;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import pe.elb.outcomememories.client.handlers.CooldownManager;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.game.skills.*;

import java.util.Locale;


public class PlayerAttacksSetHud implements IGuiOverlay {

    private static final int ICON_SIZE = 56;        // tamaño del icono (cuadrado)
    private static final int ICON_PADDING = 8;      // espacio interno alrededor del icono
    private static final int ICON_SPACING = 84;     // distancia entre centros de iconos
    private static final int BOTTOM_OFFSET = 38;    // distancia desde la parte inferior (ajustar)
    private static final int RIGHT_OFFSET = 18;     // margen derecho
    private static final int KEY_Y_OFFSET = -18;    // offset para la etiqueta de la tecla (arriba del icono)
    private static final int NAME_Y_OFFSET = 8;     // offset para el nombre (debajo del icono)

    private static final int COLOR_CYAN_TEXT = 0x00FFFF;
    private static final int COLOR_SHADOW = 0xAA000000;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // No mostrar si el HUD está oculto o hay alguna pantalla abierta
        if (mc.options.hideGui) return;
        if (mc.screen != null && !(mc.screen instanceof Screen)) return;

        // Obtener el tipo de jugador
        PlayerTypeOM type = PlayerRegistry.getPlayerType(mc.player);
        if (type == null) return;

        // Seleccionar moveset según tipo y renderizar usando adaptadores
        switch (type) {
            case CREAM -> renderMoves(mc, guiGraphics, CreamSkillsSystem.CreamMoveSet.values(), screenWidth, screenHeight);
            case AMY -> renderMoves(mc, guiGraphics, AmySkillsSystem.AmyMoveSet.values(), screenWidth, screenHeight);
            case EGGMAN -> renderMoves(mc, guiGraphics, EggmanSkillsSystem.EggmanMoveSet.values(), screenWidth, screenHeight);
            case SONIC -> renderMoves(mc, guiGraphics, SonicSkillsSystem.SonicMoveSet.values(), screenWidth, screenHeight);
            case KNUCKLES -> renderMoves(mc, guiGraphics, KnucklesSkillsSystem.KnucklesMoveSet.values(), screenWidth, screenHeight);
            case TAILS -> renderMoves(mc, guiGraphics, TailsSkillsSystem.TailsMoveSet.values(), screenWidth, screenHeight);
            case X2011 -> renderMoves(mc, guiGraphics, X2011SkillsSystem.ExeMoveSet.values(), screenWidth, screenHeight);
            case BLAZE -> renderMoves(mc , guiGraphics, BlazeSkillsSystem.BlazeMoveSet.values(), screenWidth, screenHeight);
            case METAL_SONIC -> renderMoves(mc , guiGraphics, MetalSonicSkillsSystem.MetalSonicMoveSet.values(), screenWidth, screenHeight);
            default -> {}
        }
    }

    // Generic overloads for enums that provide getId/getTexture/getKeybind/getDisplayName
    private <T> void renderMoves(Minecraft mc, GuiGraphics guiGraphics, T[] moves, int screenWidth, int screenHeight) {
        if (moves == null || moves.length == 0) return;
        int totalWidth = (moves.length - 1) * ICON_SPACING + ICON_SIZE;
        int startX = screenWidth - RIGHT_OFFSET - totalWidth;
        int y = screenHeight - BOTTOM_OFFSET - ICON_SIZE;

        for (int i = 0; i < moves.length; i++) {
            T move = moves[i];
            // Reflection-lite: call common methods via casts to known interfaces is not available,
            // so use instanceof checks for the supported move enums:
            String id = null;
            ResourceLocation tex = null;
            String key = null;
            String display = null;

            if (move instanceof CreamSkillsSystem.CreamMoveSet cms) {
                id = cms.getId();
                tex = cms.getTexture();
                key = cms.getKeybind();
                display = cms.getDisplayName();
            } else if (move instanceof AmySkillsSystem.AmyMoveSet ams) {
                id = ams.getId();
                tex = ams.getTexture();
                key = ams.getKeybind();
                display = ams.getDisplayName();
            } else if (move instanceof EggmanSkillsSystem.EggmanMoveSet ems) {
                id = ems.getId();
                tex = ems.getTexture();
                key = ems.getKeybind();
                display = ems.getDisplayName();
            } else if (move instanceof SonicSkillsSystem.SonicMoveSet sms) {
                id = sms.getId();
                tex = sms.getTexture();
                key = sms.getKeybind();
                display = sms.getDisplayName();
            } else if (move instanceof KnucklesSkillsSystem.KnucklesMoveSet kms) {
                id = kms.getId();
                tex = kms.getTexture();
                key = kms.getKeybind();
                display = kms.getDisplayName();
            } else if (move instanceof TailsSkillsSystem.TailsMoveSet tms) {
                id = tms.getId();
                tex = tms.getTexture();
                key = tms.getKeybind();
                display = tms.getDisplayName();
            } else if (move instanceof X2011SkillsSystem.ExeMoveSet xms) {
                id = xms.getId();
                tex = xms.getTexture();
                key = xms.getKeybind();
                display = xms.getDisplayName();
            } else if (move instanceof BlazeSkillsSystem.BlazeMoveSet bms) {
                id = bms.getId();
                tex = bms.getTexture();
                key = bms.getKeybind();
                display = bms.getDisplayName();
            } else if (move instanceof MetalSonicSkillsSystem.MetalSonicMoveSet mms) {
                id = mms.getId();
                tex = mms.getTexture();
                key = mms.getKeybind();
                display = mms.getDisplayName();
            } else if (move instanceof BlazeSkillsSystem.BlazeMoveSet mms) {
                id = mms.getId();
                tex = mms.getTexture();
                key = mms.getKeybind();
                display = mms.getDisplayName();
            }

            if (tex == null) continue;

            int x = startX + i * ICON_SPACING;
            renderMove(guiGraphics, mc, id, tex, key, display, i, x, y);
        }
    }

    private void renderMove(GuiGraphics guiGraphics, Minecraft mc, String id,
                            ResourceLocation tex, String keyLabel, String displayName,
                            int index, int x, int y) {
        // Fondo sombreado para legibilidad
        int bgX0 = x - 6;
        int bgY0 = y - 10;
        int bgX1 = x + ICON_SIZE + 6;
        int bgY1 = y + ICON_SIZE + 24;
        guiGraphics.fill(bgX0, bgY0, bgX1, bgY1, COLOR_SHADOW);

        // Caja del icono (borde y fondo)
        int bx0 = x;
        int by0 = y;
        int bx1 = x + ICON_SIZE;
        int by1 = y + ICON_SIZE;
        guiGraphics.fill(bx0 - 3, by0 - 3, bx1 + 3, by1 + 3, 0x8000FFFF);
        guiGraphics.fill(bx0, by0, bx1, by1, 0xFF10202A);

        // Dibujar textura del icono (dentro de la caja)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int iconInset = ICON_PADDING;
        int iconW = ICON_SIZE - iconInset * 2;
        int iconH = ICON_SIZE - iconInset * 2;
        guiGraphics.blit(tex, bx0 + iconInset, by0 + iconInset, 0, 0, iconW, iconH, iconW, iconH);
        RenderSystem.disableBlend();

        // Si hay cooldown, dibujar máscara oscura de abajo hacia arriba y texto de segundos
        if (id != null && CooldownManager.isOnCooldown(id)) {
            double fraction = CooldownManager.getFraction(id); // 1.0 == full, 0 == ready
            // coverHeight: fraction of ICON_SIZE (bottom->top)
            int coverHeight = (int) Math.round(ICON_SIZE * fraction);
            if (coverHeight > 0) {
                guiGraphics.fill(bx0, by1 - coverHeight, bx1, by1, 0xCC000000); // máscara semi-opaca
            }

            long remMs = CooldownManager.getRemainingMs(id);
            int secs = (int) Math.ceil(remMs / 1000.0);
            String secText = String.valueOf(secs);
            int secW = mc.font.width(secText);
            int secX = x + (ICON_SIZE / 2) - (secW / 2);
            int secY = y + (ICON_SIZE / 2) - 6;
            guiGraphics.drawString(mc.font, secText, secX + 1, secY + 1, 0x000000, false);
            guiGraphics.drawString(mc.font, secText, secX, secY, 0xFFFFFF, false);
        }

        // Número índice arriba del icono (1,2,...)
        String idxText = String.valueOf(index + 1);
        int idxW = mc.font.width(idxText);
        int idxX = x + (ICON_SIZE / 2) - (idxW / 2);
        int idxY = y - 18;
        guiGraphics.drawString(mc.font, idxText, idxX + 1, idxY + 1, 0x000000, false);
        guiGraphics.drawString(mc.font, idxText, idxX, idxY, COLOR_CYAN_TEXT, false);

        // Etiqueta de la tecla
        String keyLabelFinal = keyLabel != null ? keyLabel : "";
        int keyW = mc.font.width(keyLabelFinal);
        int keyX = x + (ICON_SIZE / 2) - (keyW / 2);
        int keyY = y + KEY_Y_OFFSET;
        guiGraphics.fill(keyX - 4, keyY - 3, keyX + keyW + 4, keyY + 10, 0x88000000);
        guiGraphics.drawString(mc.font, keyLabelFinal.toUpperCase(Locale.ROOT), keyX, keyY, COLOR_CYAN_TEXT, true);

        // Nombre del movimiento debajo del icono
        String display = displayName != null ? displayName : "";
        int nameW = mc.font.width(display);
        int nameX = x + (ICON_SIZE / 2) - (nameW / 2);
        int nameY = y + ICON_SIZE + NAME_Y_OFFSET;
        guiGraphics.drawString(mc.font, display, nameX + 1, nameY + 1, 0x000000, true);
        guiGraphics.drawString(mc.font, display, nameX, nameY, COLOR_CYAN_TEXT, true);
    }
}