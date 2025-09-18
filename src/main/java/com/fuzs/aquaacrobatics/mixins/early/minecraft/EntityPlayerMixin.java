package com.fuzs.aquaacrobatics.mixins.early.minecraft;

import static com.fuzs.aquaacrobatics.config.ConfigHandler.MiscellaneousConfig.CrawlingId;
import static com.fuzs.aquaacrobatics.config.ConfigHandler.MiscellaneousConfig.poseId;

import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidBlock;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.integration.IntegrationManager;
import com.fuzs.aquaacrobatics.integration.efr.EFRIntegration;
import com.fuzs.aquaacrobatics.integration.morph.MorphIntegration;
import com.fuzs.aquaacrobatics.util.math.MathHelperNew;
import com.google.common.collect.ImmutableMap;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SuppressWarnings({ "unused", "ConstantConditions" })
@Mixin(EntityPlayer.class)
public abstract class EntityPlayerMixin extends EntityLivingBase implements IPlayerResizeable {

    private static final EntitySize STANDING_SIZE = EntitySize.flexible(0.6F, 1.8F);
    private static final Map<Pose, EntitySize> SIZE_BY_POSE = ImmutableMap.<Pose, EntitySize>builder()
        .put(Pose.STANDING, STANDING_SIZE)
        .put(Pose.SLEEPING, EntitySize.fixed(0.2F, 0.2F))
        .put(Pose.FALL_FLYING, EntitySize.flexible(0.6F, 0.6F))
        .put(Pose.SWIMMING, EntitySize.flexible(0.6F, 0.6F))
        .put(Pose.SPIN_ATTACK, EntitySize.flexible(0.6F, 0.6F))
        .put(Pose.CROUCHING, EntitySize.flexible(0.6F, 1.5F))
        .put(Pose.DYING, EntitySize.fixed(0.2F, 0.2F))
        .build();

    @Shadow
    public PlayerCapabilities capabilities;
    @Shadow
    public float prevCameraYaw;
    @Shadow
    public float cameraYaw;
    @Shadow(remap = false)
    @Final // don't accidentally write to this
    public float eyeHeight;

    protected boolean eyesInWater;
    protected boolean eyesInWaterPlayer;
    private EntitySize size;
    // Forge adds an eyeHeight field, we need a different name
    private float playerEyeHeight;
    private float previousEyeHeight;
    private float swimAnimation;
    private float lastSwimAnimation;
    private float timeUnderwater;

    private boolean inBubbleColumn;

    public EntityPlayerMixin(World worldIn) {

        super(worldIn);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(CallbackInfo callbackInfo) {
        this.size = EntitySize.flexible(0.6F, 1.8F);
        this.playerEyeHeight = this.getEyeHeight(Pose.STANDING, this.size);
        this.getDataWatcher()
            .addObject(poseId, Pose.STANDING.ordinal());
        if (ConfigHandler.MovementConfig.enableToggleCrawling) {
            this.getDataWatcher()
                .addObject(CrawlingId, 0);
        }
    }

    @Override
    public void func_145781_i(@Nonnull int key) {
        if (key == poseId && this.worldObj.isRemote && !this.isRiding()) {
            this.recalculateEyeHeight();
            this.recalculateSize();

        }

        super.func_145781_i(key);
    }

    @Override
    public void onEntityUpdate() {

        super.onEntityUpdate();
        if (this.isInWater()) {
            int i = EFRIntegration.isSpectator(this.getPlayer()) ? 10 : 1;
            this.timeUnderwater = MathHelper.clamp_float(this.timeUnderwater + i, 0, 600);
        } else if (this.timeUnderwater > 0) {
            this.timeUnderwater = MathHelper.clamp_float(this.timeUnderwater - 10, 0, 600);
        }

        // updateAquatics
        this.updateEyesInWater();
        this.updateSwimming();

    }

    // based on 1.16
    @Override
    public float getWaterVision() {
        if (!this.isInWater()) {
            return 0.0f;
        } else {
            float f = 600.0f;
            float f1 = 100.0f;
            if (this.timeUnderwater >= 600.0f) {
                return 1.0f;
            } else {
                float f2 = MathHelper.clamp_float(this.timeUnderwater / 100.0f, 0.0f, 1.0f);
                float f3 = this.timeUnderwater < 100.0f ? 0.0f
                    : MathHelper.clamp_float(((float) this.timeUnderwater - 100.0f) / 500.0f, 0.0f, 1.0f);
                return f2 * 0.6f + f3 * 0.39999998f;
            }
        }
    }

    @Override
    public boolean canForceCrawling() {
        return ConfigHandler.MovementConfig.enableToggleCrawling && !this.isRiding()
            && !this.capabilities.isFlying
            && !this.isOnLadder();
    }

    @Override
    public boolean isForcingCrawling() {
        return this.canForceCrawling() && this.getDataWatcher()
            .getWatchableObjectInt(CrawlingId) == 1;
    }

    @Override
    public void setForcingCrawling(boolean flag) {
        if (!this.canForceCrawling()) return;
        this.getDataWatcher()
            .updateObject(CrawlingId, flag ? 1 : 0);
    }

    @Override
    public boolean canSwim() {

        return this.eyesInWater && this.isInWater();
    }

    @Override
    public void updateSwimming() {

        if (this.capabilities.isFlying) {

            this.setSwimming(false);
        } else if (this.isSwimming()) {

            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isRiding());
        } else {
            this.setSwimming(this.isSprinting() && this.canSwim() && !this.isRiding());
        }
    }

    private void updateEyesInWater() {

        this.eyesInWater = this.isInsideOfMaterial(Material.water);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected boolean updateEyesInWaterPlayer() {

        this.eyesInWaterPlayer = this.isInsideOfMaterial(Material.water);
        return this.eyesInWaterPlayer;
    }

    @Override
    public boolean getEyesInWaterPlayer() {

        return this.eyesInWaterPlayer;
    }

    @Override
    public final float getWidth() {

        return this.size.width;
    }

    @Override
    public final float getHeight() {

        return this.size.height;
    }

    @Override
    public EntitySize getSize(Pose poseIn) {
        return SIZE_BY_POSE.getOrDefault(poseIn, STANDING_SIZE);
    }

    @Override
    public void recalculateSize() {
        EntitySize oldSize = this.size;
        Pose pose = this.getPose();
        EntitySize newSize = this.getSize(pose);
        if (this.isResizingAllowed()) {

            this.recalculateSize(oldSize, newSize);
            // don't forget to update those
            this.width = newSize.width;
            this.height = newSize.height;
        }

        // update after calling #isResizingAllowed
        this.size = newSize;
    }

    protected void recalculateSize(EntitySize oldSize, EntitySize newSize) {
        if (newSize.width < oldSize.width) {

            double d0 = (double) newSize.width / 2.0;
            this.boundingBox.setBB(
                AxisAlignedBB.getBoundingBox(
                    this.posX - d0,
                    this.posY,
                    this.posZ - d0,
                    this.posX + d0,
                    this.posY + (double) newSize.height,
                    this.posZ + d0));
        } else {

            AxisAlignedBB axisalignedbb = this.boundingBox;
            this.boundingBox.setBB(
                AxisAlignedBB.getBoundingBox(
                    axisalignedbb.minX,
                    axisalignedbb.minY,
                    axisalignedbb.minZ,
                    axisalignedbb.minX + (double) newSize.width,
                    axisalignedbb.minY + (double) newSize.height,
                    axisalignedbb.minZ + (double) newSize.width));
            if (newSize.width > oldSize.width && !this.firstUpdate && !this.worldObj.isRemote) {

                float distance = oldSize.width - newSize.width;
                this.moveEntity(distance, 0.0, distance);
            }
        }
    }

    private void recalculateEyeHeight() {

        Pose pose = this.getPose();
        EntitySize entitysize = this.getSize(pose);
        this.playerEyeHeight = this.getEyeHeight(pose, entitysize);
        this.previousEyeHeight = this.eyeHeight;

    }

    @Override
    public boolean isResizingAllowed() {

        if (IntegrationManager.isMorphEnabled() && MorphIntegration.isMorphing(this.getPlayer())) {
            return false;
        }

        // is another mod interfering
        final float delta = 0.025F;
        AxisAlignedBB bb = this.boundingBox;
        // something is not right
        if (this.width < delta || this.height < delta || bb.maxX - bb.minX < delta || bb.maxY - bb.minY < delta) {

            return true;
        }

        boolean sizeIsOk = Math.abs(this.width / this.getWidth() - 1.0F) < delta
            && Math.abs(this.height / this.getHeight() - 1.0F) < delta;
        boolean boundingBoxIsOk = Math.abs((bb.maxX - bb.minX) / this.getWidth() - 1.0F) < delta
            && Math.abs((bb.maxY - bb.minY) / this.getHeight() - 1.0F) < delta;
        return sizeIsOk && boundingBoxIsOk;
    }

    protected float getEyeHeight(Pose poseIn, EntitySize sizeIn) {
        return poseIn == Pose.SLEEPING || poseIn == Pose.DYING ? 0.2F : this.getStandingEyeHeight(poseIn, sizeIn);
    }

    @Override
    public boolean isActuallySneaking() {

        return this.isSneaking();
    }

    @Override
    public float getStandingEyeHeight(Pose poseIn, EntitySize sizeIn) {
        switch (poseIn) {

            case SWIMMING:
            case FALL_FLYING:
            case SPIN_ATTACK:
                return this.eyeHeight;
            case CROUCHING:
                // far less than in vanilla 1.12, so better treat mods differently
                return (this.isResizingAllowed() ? 0.35F : 0.08F);
            default:
                return this.eyeHeight;
        }
    }

    // @Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true)
    // public final void getEyeHeight(CallbackInfoReturnable<Float> callbackInfoReturnable) {
    // // Only override if playerEyeHeight has been properly initialized
    // if (this.playerEyeHeight > 0) {
    // callbackInfoReturnable.setReturnValue(this.playerEyeHeight);
    // }
    // }
    //
    // @Inject(method = "getDefaultEyeHeight", at = @At("HEAD"), cancellable = true, remap = false)
    // public final void getDefaultEyeHeight(CallbackInfoReturnable<Float> callbackInfoReturnable) {
    // // Only override if playerEyeHeight has been properly initialized
    // if (this.playerEyeHeight > 0) {
    // callbackInfoReturnable.setReturnValue(this.playerEyeHeight);
    // }
    // }

    @Override
    public void setPose(Pose poseIn) {
        this.getDataWatcher()
            .updateObject(poseId, poseIn.ordinal());
    }

    @Override
    public Pose getPose() {
        // Add null check to prevent NPE during initialization
        try {
            return Pose.values()[this.getDataWatcher()
                .getWatchableObjectInt(poseId)];
        } catch (Exception e) {
            // Return default pose if DataWatcher isn't ready yet
            return Pose.STANDING;
        }
    }

    @Override
    public boolean isPoseClear(Pose poseIn) {
        return this.worldObj.getCollidingBoundingBoxes(this, this.getBoundingBox(poseIn))
            .isEmpty();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void preparePlayerToSpawn() {

        // need to overwrite whole method due to this being client exclusive
        this.yOffset = 1.62F;
        this.setPose(Pose.STANDING);
        super.preparePlayerToSpawn();
        this.setHealth(this.getMaxHealth());
        this.deathTime = 0;
    }

    @Inject(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "cpw/mods/fml/common/FMLCommonHandler.onPlayerPostTick(Lnet/minecraft/entity/player/EntityPlayer;)V",
            shift = At.Shift.BEFORE,
            remap = false))
    protected void onUpdate(CallbackInfo callbackInfo) {
        this.updateSwimAnimation();
        this.updateEyesInWaterPlayer();
    }

    @Inject(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "cpw/mods/fml/common/FMLCommonHandler.onPlayerPostTick(Lnet/minecraft/entity/player/EntityPlayer;)V",
            shift = At.Shift.AFTER,
            remap = false))
    protected void onUpdateSize(CallbackInfo callbackInfo) {
        // run after Forge event in case a mod still wants to do changes
        this.updatePose();
        this.updateEyeHeight();
    }

    protected void updatePose() {

        if (this.getShouldBeDead()) {

            // this is completely ignored in vanilla
            this.setPose(Pose.DYING);
        } else if (this.isPlayerSleeping()) {

            // handle this before swimming pose clear check
            this.setPose(Pose.SLEEPING);
        } else if (this.isPoseClear(Pose.SWIMMING)) {
            Pose pose;
            if (EFRIntegration.isElytraFlying(this.getPlayer())) {

                pose = Pose.FALL_FLYING;
            } else if (this.isForcingCrawling() || this.isSwimming()) {

                pose = Pose.SWIMMING;
                // Adjust yOffset for swimming on client side
                if (this.worldObj.isRemote) {
                    // with an eye height of 0.12F this adds up to 0.4F
                    this.yOffset = 0.28F;
                }
                // otherwise unable to sneak on client when there is not enough space for the pose, but actual player
                // size is smaller
            } else if (this.isActuallySneaking() && !this.capabilities.isFlying
                && (this.onGround || !this.isInWater())
                && !this.isOnLadder()) {

                    pose = Pose.CROUCHING;
                    // Reset yOffset for non-swimming poses on client
                    if (this.worldObj.isRemote) {
                        this.yOffset = 1.62F;
                    }
                } else {

                    pose = Pose.STANDING;
                    // Reset yOffset for non-swimming poses on client
                    if (this.worldObj.isRemote) {
                        this.yOffset = 1.62F;
                    }
                }

            Pose pose1;
            if (!this.noClip && !this.isRiding() && this.isResizingAllowed() && !this.isPoseClear(pose)) {

                if (this.isPoseClear(Pose.CROUCHING)) {

                    pose1 = Pose.CROUCHING;
                } else {
                    if (ConfigHandler.MovementConfig.enableCrawling) {
                        pose1 = Pose.SWIMMING;
                    } else {
                        pose1 = Pose.STANDING;
                    }
                }
            } else {

                pose1 = pose;
            }
            this.setPose(pose1);
        }
    }

    private void updateEyeHeight() {

        if (this.eyeHeight != this.previousEyeHeight) {

            this.recalculateEyeHeight();
        }
    }

    protected AxisAlignedBB getBoundingBox(Pose pose) {
        EntitySize entitysize = this.getSize(pose);
        float f = entitysize.width / 2.0F;
        return AxisAlignedBB.getBoundingBox(
            this.posX - (double) f,
            this.posY - (double) this.yOffset + (double) this.ySize,
            this.posZ - (double) f,
            this.posX + (double) f,
            this.posY - (double) this.yOffset + (double) this.ySize + (double) entitysize.height,
            this.posZ + (double) f);
    }

    @Override
    public boolean getShouldBeDead() {

        return this.getHealth() <= 0.0F;
    }

    @Override
    public boolean isSwimming() {
        return !this.capabilities.isFlying && this.getFlag(6) && !EFRIntegration.isSpectator(this.getPlayer());
    }

    @Override
    public boolean isActuallySwimming() {

        boolean isFallFlying = this.getPose() == Pose.FALL_FLYING;
        return this.getPose() == Pose.SWIMMING || isFallFlying;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean isVisuallySwimming() {

        return this.isActuallySwimming() && !this.isInWater();
    }

    @Override
    public void setSwimming(boolean flag) {

        this.setFlag(6, flag);
    }

    @Override
    public float getSwimAnimation(float partialTicks) {

        return MathHelperNew.lerp(partialTicks, this.lastSwimAnimation, this.swimAnimation);
    }

    private void updateSwimAnimation() {

        this.lastSwimAnimation = this.swimAnimation;
        if (this.isActuallySwimming()) {

            this.swimAnimation = Math.min(1.0F, this.swimAnimation + 0.09F);
        } else {

            this.swimAnimation = Math.max(0.0F, this.swimAnimation - 0.09F);
        }
    }

    @Inject(method = "moveEntityWithHeading", at = @At("HEAD"), cancellable = true)
    public void travel(float strafe, float forward, CallbackInfo callbackInfo) {

        double d0 = this.posX;
        double d1 = this.posY;
        double d2 = this.posZ;

        if (this.isSwimming() && !this.isRiding()) {
            double d3 = this.getLookVec().yCoord;
            double d4 = d3 < -0.2 ? 0.085 : 0.06;
            Block fluidState = this.worldObj.getBlock((int) this.posX, (int) (this.posY + 1.0 - 0.1), (int) this.posZ);
            if (d3 <= 0.0 || this.isJumping || fluidState instanceof BlockLiquid || fluidState instanceof IFluidBlock) {

                double d5 = this.motionY;
                this.motionY += (d3 - d5) * d4;
            }
        }

        double d3 = this.motionY;
        float f = this.jumpMovementFactor;
        if (this.capabilities.isFlying && !this.isRiding()) {

            this.jumpMovementFactor = this.capabilities.getFlySpeed() * (float) (this.isSprinting() ? 2 : 1);
        }

        // replaces a section in super method, therefore super is called otherwise
        if (!this.capabilities.isFlying && this.isInWater()) {

            // if (this.isServerWorld() || this.canPassengerSteer()) {
            if (this.isClientWorld()) {

                double d8 = this.posY;
                float f5 = this.isSprinting() ? 0.9F : 0.8F;
                float f6 = 0.02F;
                float f7 = 1; // (float) EnchantmentHelper.getDepthStriderModifier(this);
                if (f7 > 3.0F) {

                    f7 = 3.0F;
                }

                if (!this.onGround) {

                    f7 *= 0.5F;
                }

                if (f7 > 0.0F) {

                    f5 += (0.54600006F - f5) * f7 / 3.0F;
                    f6 += (this.getAIMoveSpeed() - f6) * f7 / 3.0F;
                }

                this.moveFlying(strafe, forward, f6);
                this.moveEntity(this.motionX, this.motionY, this.motionZ);
                if (this.isCollidedHorizontally && this.isOnLadder()) {

                    this.motionY = 0.2;
                }

                this.motionX *= f5;
                this.motionY *= 0.8;
                this.motionZ *= f5;
                this.applyGravity();
                if (this.isCollidedHorizontally && this.isOffsetPositionInLiquid(
                    this.motionX,
                    this.motionY + 0.6000000238418579D - this.posY + d8,
                    this.motionZ)) {

                    this.motionY = 0.3;
                }
                this.updateLimbSwing();
            }
        } else {

            super.moveEntityWithHeading(strafe, forward);
        }

        if (this.capabilities.isFlying && !this.isRiding()) {

            this.motionY = d3 * 0.6D;
            this.jumpMovementFactor = f;
            this.fallDistance = 0.0F;
            if (IntegrationManager.isEFREnabled()) {
                this.setFlag(EFRIntegration.elytraDataWatcherFlag(), false);
            }
        }
        this.addMovementStat(this.posX - d0, this.posY - d1, this.posZ - d2);
        callbackInfo.cancel();
    }

    public void applyGravity() {

        // if (!this.hasNoGravity() && !this.isSprinting()) {
        if (!this.isSprinting()) {

            if (this.motionY <= 0.0 && Math.abs(this.motionY - 0.005) >= 0.003
                && Math.abs(this.motionY - 0.08 / 16.0) < 0.003) {

                this.motionY = -0.003;
            } else {

                this.motionY -= 0.08 / 16.0;
            }
        }
    }

    private void updateLimbSwing() {

        this.prevLimbSwingAmount = this.limbSwingAmount;
        double d5 = this.posX - this.prevPosX;
        double d7 = this.posZ - this.prevPosZ;
        // double d9 = this instanceof EntityFlying ? this.posY - this.prevPosY : 0.0;
        double d9 = 0.0;
        float f10 = MathHelper.sqrt_double(d5 * d5 + d9 * d9 + d7 * d7) * 4.0F;

        if (f10 > 1.0F) {

            f10 = 1.0F;
        }

        this.limbSwingAmount += (f10 - this.limbSwingAmount) * 0.4F;
        this.limbSwing += this.limbSwingAmount;
    }

    @Shadow
    public abstract void addMovementStat(double p_71000_1_, double p_71000_3_, double p_71000_5_);

    @Inject(method = "onLivingUpdate", at = @At(value = "TAIL"))
    public void onLivingUpdate(CallbackInfo callbackInfo) {

        // disable bobbing view when swimming
        float f = 0.0F;
        if (this.onGround && !this.getShouldBeDead() && !this.isSwimming()) {

            f = Math.min(0.1F, MathHelper.sqrt_double(this.motionX * this.motionX + this.motionZ * this.motionZ));
        }

        this.cameraYaw = this.prevCameraYaw + (f - this.prevCameraYaw) * 0.4F;
        // no longer exists in 1.13+
        this.cameraPitch = 0.0F;

        // if (!ConfigHandler.MiscellaneousConfig.sneakingForParrots) {
        //
        // return;
        // }

        // if (!this.worldObj.isRemote && (this.isSneaking() || this.isInWater())) {
        //
        // this.spawnShoulderEntities();
        // }

    }

    @Redirect(
        method = "sleepInBedAt",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setSize(FF)V"))
    public void setSizeTrySleep(EntityPlayer player, float width, float height) {

        this.setPose(Pose.SLEEPING);
    }

    // removed wakeUpPlayer hook as it's not important and is conflicting with sponge forge (they're using overwrite for
    // that method)

    private EntityPlayer getPlayer() {

        return (EntityPlayer) (Object) this;
    }
}
