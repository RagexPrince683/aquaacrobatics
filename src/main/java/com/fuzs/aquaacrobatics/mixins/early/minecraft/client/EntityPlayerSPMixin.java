package com.fuzs.aquaacrobatics.mixins.early.minecraft.client;

import static net.minecraft.entity.SharedMonsterAttributes.movementSpeed;

import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.fuzs.aquaacrobatics.client.entity.IPlayerSPSwimming;
import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.integration.efr.EFRIntegration;
import com.fuzs.aquaacrobatics.util.BlockPos;
import com.fuzs.aquaacrobatics.util.MovementInputStorage;
import com.fuzs.aquaacrobatics.util.math.AxisAlignedBBSpliterator;
import com.mojang.authlib.GameProfile;

@SuppressWarnings("unused")
@Mixin(EntityPlayerSP.class)
public abstract class EntityPlayerSPMixin extends AbstractClientPlayer implements IPlayerSPSwimming {

    private static final boolean BETTER_SPRINTING_LOADED =
        cpw.mods.fml.common.Loader.isModLoaded("bettersprinting");

    @Shadow
    protected Minecraft mc;
    @Shadow
    protected int sprintToggleTimer;
    @Shadow
    public MovementInput movementInput;

    private final MovementInputStorage movementStorage = new MovementInputStorage();
    private boolean isCrouching;

    public EntityPlayerSPMixin(World worldIn, GameProfile playerProfile) {

        super(worldIn, playerProfile);
    }

    @Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true)
    public void isSneaking(CallbackInfoReturnable<Boolean> callbackInfo) {

        // don't check this directly every time to prevent crash with random things mod caused by loop
        callbackInfo.setReturnValue(this.isCrouching);
    }

    @Override
    public boolean isActuallySneaking() {

        // switched with #isSneaking
        return this.movementInput != null && this.movementInput.sneak;
    }

    @Override
    public boolean isForcedDown() {

        return ((IPlayerResizeable) this).isResizingAllowed() && !this.capabilities.isFlying
            ? this.isSneaking() || ((IPlayerResizeable) this).isVisuallySwimming()
            : this.isActuallySneaking();
    }

    @Override
    public boolean isUsingSwimmingAnimation() {

        return this.isUsingSwimmingAnimation(this.movementInput.moveForward, this.movementInput.moveStrafe);
    }

    @Override
    public boolean isUsingSwimmingAnimation(float moveForward, float moveStrafe) {

        if (this.canSwim()) {

            return this.isMovingForward(moveForward, moveStrafe);
        }

        if (ConfigHandler.MovementConfig.sidewaysSprinting) {

            return moveForward >= 0.8F || Math.abs(moveStrafe) > 0.8F;
        }

        return moveForward >= 0.8F;
    }

    @Override
    public boolean canSwim() {

        return ((IPlayerResizeable) this).getEyesInWaterPlayer();
    }

    @Override
    public boolean isMovingForward(float moveForward, float moveStrafe) {

        if (moveForward > 1.0E-5F) {

            return true;
        } else if (ConfigHandler.MovementConfig.sidewaysSwimming) {

            return Math.abs(moveStrafe) > 1.0E-5F;
        }

        return false;
    }

    @Inject(method = "func_145771_j", at = @At("HEAD"), cancellable = true)
    protected void pushOutOfBlocks(double x, double y, double z, CallbackInfoReturnable<Boolean> callbackInfo) {

        if (ConfigHandler.playerBlockCollisions != ConfigHandler.PlayerBlockCollisions.EXACT) {

            return;
        }

        if (!this.noClip) {

            this.setPlayerOffsetMotion(x, z);
        }

        // return value is never used
        callbackInfo.setReturnValue(false);
    }

    private void setPlayerOffsetMotion(double x, double z) {

        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

        if (this.shouldBlockPushPlayer(blockX, blockZ)) {

            double d0 = x - blockX;
            double d1 = z - blockZ;
            ForgeDirection direction = null;
            double closest = Double.MAX_VALUE;

            ForgeDirection[] xzPlane = new ForgeDirection[] { ForgeDirection.WEST, ForgeDirection.EAST,
                ForgeDirection.NORTH, ForgeDirection.SOUTH };

            for (ForgeDirection dir : xzPlane) {
                boolean isX = dir.offsetX != 0;
                double d3 = isX ? d0 : d1;
                double d4 = (dir.offsetX + dir.offsetZ > 0) ? (1.0 - d3) : d3;

                int offsetX = blockX + dir.offsetX;
                int offsetZ = blockZ + dir.offsetZ;

                if (d4 < closest && !this.shouldBlockPushPlayer(offsetX, offsetZ)) {
                    closest = d4;
                    direction = dir;
                }
            }

            if (direction != null) {
                if (direction.offsetX != 0) {
                    this.motionX = 0.1 * direction.offsetX;
                } else {
                    this.motionZ = 0.1 * direction.offsetZ;
                }
            }
        }
    }

    private boolean shouldBlockPushPlayer(int x, int z) {

        double minY = this.boundingBox.minY;
        double maxY = this.boundingBox.maxY;
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(x, minY, z, x + 1.0, maxY, z + 1.0);

        // don't use IBlockState#causesSuffocation as it works differently in newer versions
        return !isAxisAlignedBBNotClear(this.worldObj, this, aabb.expand(-1.0E-7, -1.0E-7, -1.0E-7));
    }

    private boolean shouldBlockPushPlayer(BlockPos pos) {

        double minY = this.boundingBox.minY;
        double maxY = this.boundingBox.maxY;
        AxisAlignedBB aabb = AxisAlignedBB
            .getBoundingBox(pos.getX(), minY, pos.getZ(), pos.getX() + 1.0, maxY, pos.getZ() + 1.0);

        // don't use IBlockState#causesSuffocation as it works differently in newer versions
        return !isAxisAlignedBBNotClear(this.worldObj, this, aabb.expand(-1.0E-7, -1.0E-7, -1.0E-7));
    }

    private static boolean isAxisAlignedBBNotClear(World world, @Nullable Entity entity, AxisAlignedBB aabb) {
        return createAxisAlignedBBStream(world, entity, aabb).allMatch(Objects::isNull);
    }

    private static Stream<AxisAlignedBB> createAxisAlignedBBStream(World world, @Nullable Entity entity,
        AxisAlignedBB aabb) {

        return StreamSupport.stream(new AxisAlignedBBSpliterator(world, entity, aabb), false);
    }

    @Redirect(method = "func_145771_j", at = @At(value = "INVOKE", target = "java/lang/Math.round(F)I"))
    private int round(float a) {

        if (ConfigHandler.playerBlockCollisions == ConfigHandler.PlayerBlockCollisions.APPROXIMATE) {

            a -= 0.65;
        }

        // make the player be able to sneak under full cubes with their new height of 1.5 blocks
        return Math.round(a);
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    public void onLivingUpdatePre(CallbackInfo callbackInfo) {

        this.updateSprintToggleTimer();
        this.movementStorage.copyFrom(this.movementInput);
        this.movementStorage.isSprinting = this.isSprinting();
        this.movementStorage.isFlying = this.capabilities.isFlying;
        this.movementStorage.isStartingToFly = this.isStartingToFly();
    }

    private void updateSprintToggleTimer() {

        // added in 1.13+, so do this for the actual field
        if (this.movementInput.sneak) {

            this.sprintToggleTimer = 0;
        }

        this.movementStorage.sprintToggleTimer = this.sprintToggleTimer;
        if (this.movementStorage.sprintToggleTimer > 0) {

            --this.movementStorage.sprintToggleTimer;
        }

        if (this.isUsingItem() && !this.isRiding()) {

            this.movementStorage.sprintToggleTimer = 0;
        }
    }

    private boolean isStartingToFly() {

        if (this.capabilities.allowFlying) {

            if (EFRIntegration.isSpectator(this)) {

                return !this.capabilities.isFlying;
            } else if (!this.movementInput.jump && this.mc.gameSettings.keyBindJump.getIsKeyPressed()) {

                return this.flyToggleTimer != 0 && !((IPlayerResizeable) this).isSwimming();
            }
        }

        return false;
    }

    @Inject(method = "onLivingUpdate", at = @At(value = "TAIL"))
    public void onLivingUpdate(CallbackInfo callbackInfo) {

        this.updatePlayerMoveState();
        this.isCrouching = this.isCrouching(!((IPlayerResizeable) this).isPoseClear(Pose.STANDING));
        // handle sprinting behaviour DEFENSIVELY!!!
        if (!BETTER_SPRINTING_LOADED ) { //&& !inWater
            if (this.isSprinting() != this.movementStorage.isSprinting) {
                this.setSprinting(this.movementStorage.isSprinting);
            }
        } else {
            if (inWater) {
                this.setSprinting(this.movementStorage.isSprinting);
            }
        }
        if (!BETTER_SPRINTING_LOADED ) {
            boolean isSaturated = (float) this.getFoodStats()
                .getFoodLevel() > 6.0F || this.capabilities.allowFlying;

            this.startSprinting(isSaturated);
            this.stopSprinting(isSaturated);
        } else {
            if (inWater) {
                boolean isSaturated = (float) this.getFoodStats()
                    .getFoodLevel() > 6.0F || this.capabilities.allowFlying;

                this.startSprinting(isSaturated);
                this.stopSprinting(isSaturated);
            }
        }
        // handle misc movement
        // this.handleElytraTakeoff();
        this.handleWaterSneaking();
        this.slowDownSneakFlying();
    }

    private void updatePlayerMoveState() {

        if (!this.movementInput.sneak && this.isForcedDown()) {

            this.movementInput.moveStrafe = (float) ((double) this.movementInput.moveStrafe * 0.3);
            this.movementInput.moveForward = (float) ((double) this.movementInput.moveForward * 0.3);
        }

        if (this.movementInput.sneak && !this.isForcedDown()) {

            this.movementInput.moveStrafe = (float) ((double) this.movementInput.moveStrafe / 0.3);
            this.movementInput.moveForward = (float) ((double) this.movementInput.moveForward / 0.3);
        }
    }

    private boolean isCrouching(boolean cantStand) {
        if ((!this.movementStorage.isFlying || !cantStand) && EFRIntegration.getTicksElytraFlying(this) <= 4) {
            if (!((IPlayerResizeable) this).isSwimming() && (this.onGround || !this.isInWater())) {

                if (!this.isOnLadder() && (((IPlayerResizeable) this).isPoseClear(Pose.CROUCHING) || this.noClip)) {

                    return this.movementInput.sneak
                        || ((IPlayerResizeable) this).isResizingAllowed() && !this.isPlayerSleeping() && cantStand;
                }
            }
        }

        return false;
    }

    private void startSprinting(boolean isSaturated) {

        boolean wasSneaking = this.movementStorage.sneak;
        boolean wasSwimming = this
            .isUsingSwimmingAnimation(this.movementStorage.moveForward, this.movementStorage.moveStrafe);
        boolean isSprintingEnvironment = this.onGround || this.canSwim() || this.movementStorage.isFlying;
        if (isSprintingEnvironment && !wasSneaking
            && !wasSwimming
            && this.isUsingSwimmingAnimation()
            && !this.isSprinting()
            && isSaturated
            && !this.isPotionActive(Potion.blindness)) {

            if (this.movementStorage.sprintToggleTimer <= 0 && !this.mc.gameSettings.keyBindSprint.getIsKeyPressed()) {

                this.sprintToggleTimer = ConfigHandler.MovementConfig.noDoubleTapSprinting ? 0 : 7;
            } else {

                this.setSprinting(true);
            }
        }

        if (!this.isSprinting() && (!this.isInWater() || this.canSwim())
            && this.isUsingSwimmingAnimation()
            && isSaturated
            && !this.isPotionActive(Potion.blindness)
            && this.mc.gameSettings.keyBindSprint.getIsKeyPressed()) {

            this.setSprinting(true);
        }
    }

    private void stopSprinting(boolean isSaturated) {

        if (this.isSprinting()) {

            boolean isNotMoving = !this.isMovingForward(this.movementInput.moveForward, this.movementInput.moveStrafe)
                || !isSaturated;
            // don't stop sprint flying when breaching water surface
            boolean hasCollided = isNotMoving || this.isInWater() && !this.canSwim() && !this.movementStorage.isFlying;
            if (((IPlayerResizeable) this).isSwimming()) {

                if (!this.movementInput.sneak && isNotMoving || !this.isInWater()) {

                    this.setSprinting(false);
                }
            } else if (hasCollided) {

                this.setSprinting(false);
            }
        }
    }

    @Override
    public boolean canPerformElytraTakeoff() {
        return (ConfigHandler.MovementConfig.easyElytraTakeoff && this.movementInput.jump
            && !this.movementStorage.isStartingToFly
            && !this.movementStorage.jump
            && this.motionY >= 0.0
            && !this.capabilities.isFlying
            && !this.isRiding()
            && !this.isOnLadder());
    }

    // private void handleElytraTakeoff() {
    // // 1.15 change for easier elytra takeoff
    // if (canPerformElytraTakeoff() && IntegrationManager.isEFREnabled()) {
    //
    // ItemStack itemstack = this.getEquipmentInSlot(3);
    // if (itemstack != null && itemstack.getItem() instanceof ItemArmorElytra) {
    // this.connection.sendPacket(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.START_FALL_FLYING));
    // } else {
    // IntegrationManager.elytraOpenHooks.forEach(hook -> hook.openElytra((EntityPlayerSP) (Object) this));
    // }
    // }
    // }

    private void handleWaterSneaking() {

        // needs to be handled on the client since the server doesn't receive actual sneak state while in water
        if (this.isInWater() && this.movementInput.sneak && !this.capabilities.isFlying) {

            this.handleSneakWater();
        }
    }

    private void slowDownSneakFlying() {

        if (this.capabilities.isFlying) {

            if (this.movementInput.sneak) {

                // normally used to counter sneaking slowdown when flying, but sneaking is no longer activated while
                // flying now
                this.movementInput.moveStrafe = (float) ((double) this.movementInput.moveStrafe * 0.3);
                this.movementInput.moveForward = (float) ((double) this.movementInput.moveForward * 0.3);
            }
        }
    }

    protected void handleSneakWater() {

        this.motionY -= 0.03999999910593033 * this.getEntityAttribute(movementSpeed)
            .getAttributeValue();
    }
}
