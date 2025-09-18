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

    @Inject(method = "onDeath", at = @At("HEAD"))
    public void onDeathHead(DamageSource cause, CallbackInfo ci) {
        if ((Object) this instanceof IPlayerResizeable resizeable) {
            // Pre-emptively set a safe pose
            EntitySize dyingSize = resizeable.getSize(Pose.DYING);
            if (dyingSize == null || dyingSize.width <= 0.0F || dyingSize.height <= 0.0F
                || Float.isNaN(dyingSize.width) || Float.isNaN(dyingSize.height)) {
                resizeable.setPose(Pose.STANDING);
            } else {
                resizeable.setPose(Pose.DYING);
            }
            resizeable.recalculateSize();
        }
    }
    // Fix illegal stance height on death by attacking head not tail

    @Inject(method = "recalculateSize", at = @At("TAIL"))
    private void sanityCheck(CallbackInfo ci) {
        if ((Object) this instanceof IPlayerResizeable resizeable) {
            EntitySize size = resizeable.getSize(resizeable.getPose());
            if (size == null || size.width <= 0.0F || size.height <= 0.0F) {
                resizeable.setPose(Pose.STANDING);
                resizeable.recalculateSize();
                System.err.println("[AquaAcrobatics] Illegal stance auto-corrected to STANDING.");
            }
        }
    }



    // Helper method (add somewhere in your codebase)
    private float getLegalStanceForPose(Pose pose) {
        // Map pose to stance value; replace with your actual logic
        switch (pose) {
            case DYING: return 0.5F; // Or whatever is legal for DYING
            case STANDING: return 1.8F;
            case CROUCHING: return 1.5F;
            default: return 1.8F;
        }
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
