package com.fuzs.aquaacrobatics.mixins.early.minecraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.mojang.authlib.GameProfile;

@SuppressWarnings("unused")
@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMPMixin extends EntityPlayer {

    public EntityPlayerMPMixin(World worldIn, GameProfile gameProfileIn) {

        super(worldIn, gameProfileIn);
    }

    @Inject(method = "onDeath", at = @At("TAIL"))
    public void onDeath(DamageSource cause, CallbackInfo callbackInfo) {

        // super method is never called where this is set in vanilla
        ((IPlayerResizeable) this).setPose(Pose.DYING);
    }

    @Override
    public float getDefaultEyeHeight() {
        // Handle server-side swimming eye height
        if (((IPlayerResizeable) this).getPose() == Pose.SWIMMING) {
            return 0.4F; // Fixed server-side swimming eye height
        }
        return 1.62F; // Default eye height for other poses
    }

    @Override
    public float getEyeHeight() {
        // Handle server-side swimming eye height for getEyeHeight method too
        if (((IPlayerResizeable) this).getPose() == Pose.SWIMMING) {
            return 0.4F; // Fixed server-side swimming eye height
        }
        return super.getEyeHeight();
    }

    // Inject into onUpdate to ensure size is correct
    @Inject(method = "onUpdate", at = @At("TAIL"))
    public void onServerUpdate(CallbackInfo ci) {
        Pose pose = ((IPlayerResizeable) this).getPose();
        EntitySize entitySize = ((IPlayerResizeable) this).getSize(pose);
        this.setSize(entitySize.width, entitySize.height);
    }
}
