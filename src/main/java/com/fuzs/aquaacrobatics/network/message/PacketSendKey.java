package com.fuzs.aquaacrobatics.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

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
                //todo slowness and mining fatigue to prevent abuse
                //todo also if player jumps disable crawling

                IPlayerResizeable resizeable = (IPlayerResizeable) playerEntity;
                resizeable.setForcingCrawling(!resizeable.isForcingCrawling());
            }
            return null;
        }
    }
}
