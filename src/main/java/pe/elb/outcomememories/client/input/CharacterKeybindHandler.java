package pe.elb.outcomememories.client.input;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pe.elb.outcomememories.Outcomememories;
import pe.elb.outcomememories.client.handlers.KnucklesClientHandler;
import pe.elb.outcomememories.game.PlayerTypeOM;
import pe.elb.outcomememories.game.game.PlayerRegistry;
import pe.elb.outcomememories.net.NetworkHandler;
import pe.elb.outcomememories.net.skills.amy.AmySkillPacket;
import pe.elb.outcomememories.net.skills.cream.CreamSkillPacket;
import pe.elb.outcomememories.net.skills.eggman.EggmanSkillPacket;
import pe.elb.outcomememories.net.skills.exe.ExeSkillPacket;
import pe.elb.outcomememories.net.skills.knuckles.KnucklesSkillPacket;
import pe.elb.outcomememories.net.skills.sonic.SonicSkillPacket;
import pe.elb.outcomememories.net.skills.tails.TailsSkillPacket;

@Mod.EventBusSubscriber(modid = Outcomememories.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CharacterKeybindHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        PlayerTypeOM type = PlayerRegistry.getPlayerType(mc.player);
        if (type == null) return;

        handleKeybinds(type);
    }

    private static void handleKeybinds(PlayerTypeOM type) {
        while (KeyBindings.ABILITY_PRIMARY.consumeClick()) {
            handlePrimaryAbility(type);
        }

        while (KeyBindings.ABILITY_SECONDARY.consumeClick()) {
            handleSecondaryAbility(type);
        }

        while (KeyBindings.ABILITY_SPECIAL.consumeClick()) {
            handleSpecialAbility(type);
        }
    }

    private static void handlePrimaryAbility(PlayerTypeOM type) {
        switch (type) {
            case AMY -> NetworkHandler.CHANNEL.sendToServer(new AmySkillPacket(AmySkillPacket.SkillType.HAMMER));
            case CREAM -> NetworkHandler.CHANNEL.sendToServer(new CreamSkillPacket(CreamSkillPacket.SkillType.HEAL));
            case X2011 -> NetworkHandler.CHANNEL.sendToServer(new ExeSkillPacket(ExeSkillPacket.SkillType.INVISIBILITY_TOGGLE));
            case TAILS -> NetworkHandler.CHANNEL.sendToServer(new TailsSkillPacket(TailsSkillPacket.SkillType.LASER_START));
            case EGGMAN -> NetworkHandler.CHANNEL.sendToServer(new EggmanSkillPacket(EggmanSkillPacket.SkillType.SHIELD));
            case SONIC -> NetworkHandler.CHANNEL.sendToServer(new SonicSkillPacket(SonicSkillPacket.SkillType.PEELOUT));
            case KNUCKLES -> NetworkHandler.CHANNEL.sendToServer(new KnucklesSkillPacket(KnucklesSkillPacket.SkillType.COUNTER));
        }
    }

    private static void handleSecondaryAbility(PlayerTypeOM type) {
        switch (type) {
            case AMY -> NetworkHandler.CHANNEL.sendToServer(new AmySkillPacket(AmySkillPacket.SkillType.THROW_HAMMER));
            case CREAM -> NetworkHandler.CHANNEL.sendToServer(new CreamSkillPacket(CreamSkillPacket.SkillType.DASH));
            case X2011 -> NetworkHandler.CHANNEL.sendToServer(new ExeSkillPacket(ExeSkillPacket.SkillType.CHARGE));
            case TAILS -> NetworkHandler.CHANNEL.sendToServer(new TailsSkillPacket(TailsSkillPacket.SkillType.GLIDE_START));
            case EGGMAN -> NetworkHandler.CHANNEL.sendToServer(new EggmanSkillPacket(EggmanSkillPacket.SkillType.BOOST));
            case SONIC -> NetworkHandler.CHANNEL.sendToServer(new SonicSkillPacket(SonicSkillPacket.SkillType.DROPDASH));
            case KNUCKLES -> {NetworkHandler.CHANNEL.sendToServer(new KnucklesSkillPacket(KnucklesSkillPacket.SkillType.PUNCH_START));
                KnucklesClientHandler.setChargingPunch(true);
            }
        }
    }

    private static void handleSpecialAbility(PlayerTypeOM type) {
        switch (type) {
            case X2011 -> NetworkHandler.CHANNEL.sendToServer(new ExeSkillPacket(ExeSkillPacket.SkillType.GODS_TRICKERY));
            case AMY -> NetworkHandler.CHANNEL.sendToServer(new AmySkillPacket(AmySkillPacket.SkillType.REROLL));
        }
    }
}