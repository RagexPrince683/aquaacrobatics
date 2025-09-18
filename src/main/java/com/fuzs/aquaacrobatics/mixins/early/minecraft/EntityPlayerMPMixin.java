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

    // thread-local to prevent recursive re-entry into our validation injection
    private static final ThreadLocal<Boolean> POSE_BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);


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
            if (size == null || size.width <= 0.0F || size.height <= 0.0F
                || Float.isNaN(size.width) || Float.isNaN(size.height)) {

                POSE_BYPASS.set(Boolean.TRUE);
                try {
                    resizeable.setPose(Pose.STANDING);
                    resizeable.recalculateSize();
                } finally {
                    POSE_BYPASS.set(Boolean.FALSE);
                }

                System.err.println("[AquaAcrobatics] Illegal stance auto-corrected to STANDING.");
            }
        }
    }


    @Inject(method = "setPose", at = @At("HEAD"), cancellable = true)
    private void validatePose(Pose pose, CallbackInfo ci) {
        // If we are bypassing validation, allow original method to run
        if (POSE_BYPASS.get()) {
            return;
        }

        if ((Object) this instanceof IPlayerResizeable resizeable) {
            EntitySize size = resizeable.getSize(pose);

            // If requested pose is invalid, fallback to STANDING, but avoid recursion:
            if (size == null || size.width <= 0.0F || size.height <= 0.0F
                || Float.isNaN(size.width) || Float.isNaN(size.height)) {

                // Prevent our injection from running again while we set the safe pose
                POSE_BYPASS.set(Boolean.TRUE);
                try {
                    // call original setPose once while bypassing the injection
                    resizeable.setPose(Pose.STANDING);
                    resizeable.recalculateSize();
                } finally {
                    POSE_BYPASS.set(Boolean.FALSE);
                }

                // cancel the original setPose call (we already set a safe one)
                System.err.println("[AquaAcrobatics] Blocked illegal pose: " + pose + " → fallback to STANDING.");
                ci.cancel();
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
        if ((Object) this instanceof IPlayerResizeable resizeable) {
            Pose pose = resizeable.getPose();
            EntitySize entitySize = resizeable.getSize(pose);

            // Null-safe — avoid passing bad values to setSize
            if (entitySize != null && entitySize.width > 0.0F && entitySize.height > 0.0F
                && !Float.isNaN(entitySize.width) && !Float.isNaN(entitySize.height)) {

                this.setSize(entitySize.width, entitySize.height);
            } else {
                // keep a safe fallback — don't let bounding box be set to zero/NaN
                this.setSize(0.6F, 1.62F);
                System.err.println("[AquaAcrobatics] onServerUpdate found invalid size for pose " + pose + ", using fallback.");
            }
        }
    }

}
