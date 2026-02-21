package com.fuzs.aquaacrobatics.network.message;

import com.fuzs.aquaacrobatics.entity.Pose;
import net.minecraft.entity.player.EntityPlayerMP;

import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import static com.fuzs.aquaacrobatics.config.ConfigHandler.MovementConfig.effectsWhileCrawling;

public class PacketSendKey implements IMessage {

    public enum KeybindPacket {
        UNKNOWN,
        TOGGLE_CRAWLING
    }

    private KeybindPacket keybind = KeybindPacket.UNKNOWN;

    @Override
    public void fromBytes(ByteBuf buf) {
        int idx = buf.readInt();
        if (idx >= KeybindPacket.values().length) keybind = KeybindPacket.UNKNOWN;
        else keybind = KeybindPacket.values()[idx];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(keybind.ordinal());
    }

    public PacketSendKey() {

    }

    public PacketSendKey(KeybindPacket keybind) {
        this.keybind = keybind;
    }

    public static class Handler implements IMessageHandler<PacketSendKey, IMessage> {

        @Override
        public IMessage onMessage(PacketSendKey message, MessageContext ctx) {
            // Always use a construct like this to actually handle your message. This ensures that
            // your 'handle' code is run on the main Minecraft thread. 'onMessage' itself
            // is called on the networking thread so it is not safe to do a lot of things
            // here.
            return handle(message, ctx);
        }

        private IMessage handle(PacketSendKey message, MessageContext ctx) {
            EntityPlayerMP playerEntity = ctx.getServerHandler().playerEntity;

            if (message.keybind == KeybindPacket.TOGGLE_CRAWLING) {
                IPlayerResizeable resizeable = (IPlayerResizeable) playerEntity;

                // flip crawl state
                boolean newState = !resizeable.isForcingCrawling();
                resizeable.setForcingCrawling(newState);

                if (effectsWhileCrawling) {

                    if (newState ) { //ENSURE WE ARE ACTUALLY CRAWLING, NOT JUST FORCING IT?
                        //newState is the keybind. If it's true, then we are forcing crawl pose, which means we should apply debuffs.
                        //ensure we are on server
                        if (!playerEntity.worldObj.isRemote) {
                            // Apply debuffs while crawling
                            playerEntity.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, Integer.MAX_VALUE, 1, false)); // Slowness II
                            playerEntity.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, Integer.MAX_VALUE, 0, false)); // Mining Fatigue I
                        }
                    } else {
                        //if (resizeable.getPose() == Pose.STANDING) { //NEVER FIRES
                        if (!playerEntity.worldObj.isRemote) { //ENSURE ITS ON SERVER
                        if (resizeable.isPoseClear(Pose.STANDING)) {

                            //IF THE POSE IS STANDING, THEN REMOVE DEBUFFS. THIS PREVENTS DEBUFFS FROM STICKING AROUND WHEN USING THE KEYBIND TO EXIT CRAWL POSE


                                // Remove debuffs when not crawling
                                playerEntity.removePotionEffect(Potion.moveSlowdown.id);
                                playerEntity.removePotionEffect(Potion.digSlowdown.id);
                            }
                            //}
                        }
                    }
                }
            }

            return null;
        }
    }
}
