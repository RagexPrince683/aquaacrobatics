package com.fuzs.aquaacrobatics.proxy;

import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraftforge.common.MinecraftForge;

import com.fuzs.aquaacrobatics.AquaAcrobatics;
import com.fuzs.aquaacrobatics.biome.BiomeWaterFogColors;
import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.handler.CommonHandler;
import com.fuzs.aquaacrobatics.integration.IntegrationManager;
import com.fuzs.aquaacrobatics.integration.hats.HatsIntegration;
import com.fuzs.aquaacrobatics.network.NetworkHandler;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

@EventBusSubscriber
public class CommonProxy {

    //hopefully prevent crawl jumping (I don't think most people can do that to begin with)
    //@SubscribeEvent
    //public void onPlayerJump(LivingEvent.LivingJumpEvent event) {
    //    if (event.entityLiving instanceof EntityPlayer) {
    //        EntityPlayer player = (EntityPlayer) event.entityLiving;
//
    //        if (player instanceof IPlayerResizeable) {
    //            IPlayerResizeable resizeable = (IPlayerResizeable) player;
    //            if (resizeable.isForcingCrawling()) {
    //                // cancel crawl if they jump
    //                resizeable.setForcingCrawling(false);
//
    //                // remove debuffs too
    //                player.removePotionEffect(Potion.moveSlowdown.id);
    //                player.removePotionEffect(Potion.digSlowdown.id);
    //            }
    //        }
    //    }
    //}
    //ok this is annoying

    private boolean needNetworking() {
        return ConfigHandler.MovementConfig.enableToggleCrawling;
    }

    public void onPreInit(FMLPreInitializationEvent event) {
        IntegrationManager.loadCompat();
        if (needNetworking()) NetworkHandler.registerMessages(AquaAcrobatics.MODID);
        MinecraftForge.EVENT_BUS.register(new CommonHandler());
        MinecraftForge.EVENT_BUS.register(new CrawlHandler());

    }

    public void onInit(FMLInitializationEvent event) {

    }

    public void onMappings() {

    }

    public void onPostInit(FMLPostInitializationEvent event) {

        if (IntegrationManager.isHatsEnabled()) {

            HatsIntegration.register();
        }

        BiomeWaterFogColors.recomputeColors();
        // This code will print a warning if we don't have a color mapping for the biome
        /*
         * for(BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) {
         * biome.getWaterColorMultiplier();
         * }
         */
    }

    public class CrawlHandler {
        @SubscribeEvent
        public void onPlayerJump(LivingEvent.LivingJumpEvent event) {
            if (!(event.entityLiving instanceof EntityPlayer)) return;
            EntityPlayer player = (EntityPlayer) event.entityLiving;
            if (player instanceof IPlayerResizeable) {
                IPlayerResizeable r = (IPlayerResizeable) player;
                if (r.isForcingCrawling()) {
                    r.setForcingCrawling(false);
                    if (!player.worldObj.isRemote) {
                        player.removePotionEffect(Potion.moveSlowdown.id);
                        player.removePotionEffect(Potion.digSlowdown.id);
                    }
                }
            }
        }
    }

}
