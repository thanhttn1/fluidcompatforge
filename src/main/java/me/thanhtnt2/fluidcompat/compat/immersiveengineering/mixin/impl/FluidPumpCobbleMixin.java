package me.thanhtnt2.fluidcompat.compat.immersiveengineering.mixin.impl;

import java.util.List;

import me.thanhtnt2.fluidcompat.compat.immersiveengineering.IePumpBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the Fluid Pump from putting cobblestone where it emptied a block of a finite fluid.
 *
 * <p>After taking a block the pump replaces it with cobblestone, and by then the block is already air, so the fluid can
 * no longer be looked up. The fluid is therefore checked just before the pump takes the block, and the result is kept
 * until the cobblestone call that follows in the same tick. The pump also places cobblestone on a block that turned
 * out to be empty already (its work list is older than the water, which Flowing Fluids keeps moving), so the fluid the
 * pump is working on decides as well.
 *
 * <p>Its own mixin and config on purpose: {@code ImmersiveEngineeringCobbleMixinPlugin} checks this part of the pump on
 * its own, so if IE changes it only the cobblestone handling switches off. The vanilla call is written with its SRG id
 * ({@code m_46597_}) and {@code remap = false}, exactly as it is in the bytecode of a real game, so nothing depends on
 * a refmap entry.
 */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.blocks.metal.FluidPumpBlockEntity", remap = false)
public abstract class FluidPumpCobbleMixin {

    private static final String DRAIN_BLOCK =
            "Lblusunrize/immersiveengineering/common/util/Utils;drainFluidBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;";
    private static final String SET_BLOCK_AND_UPDATE =
            "Lnet/minecraft/world/level/Level;m_46597_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z";

    @Shadow(remap = false)
    @Final
    private List<BlockPos> closedList;

    // The fluid the pump's area search is working on; null until a search has found one
    @Shadow(remap = false)
    private Fluid searchFluid;

    // Position of the block about to be taken, set only when its fluid is finite. Not saved, one per pump
    @Unique
    private BlockPos fluidcompat$finiteDrainTarget;

    // The second of the two drainFluidBlock calls in tickServer: the first only simulates, this one really takes the block
    @Inject(method = "tickServer()V",
            at = @At(value = "INVOKE", target = DRAIN_BLOCK, ordinal = 1, remap = false),
            remap = false, require = 0)
    private void fluidcompat$rememberFiniteDrain(CallbackInfo callback) {
        Level level = ((BlockEntity) (Object) this).getLevel();
        this.fluidcompat$finiteDrainTarget = IePumpBridge.finiteDrainTarget(level, this.closedList);
    }

    // The single setBlockAndUpdate call in tickServer is the cobblestone placement
    @Redirect(method = "tickServer()V",
            at = @At(value = "INVOKE", target = SET_BLOCK_AND_UPDATE, ordinal = 0, remap = false),
            remap = false, require = 0)
    private boolean fluidcompat$skipCobbleForFiniteFluid(Level level, BlockPos pos, BlockState cobblestone) {
        BlockPos finiteTarget = this.fluidcompat$finiteDrainTarget;
        this.fluidcompat$finiteDrainTarget = null;
        return IePumpBridge.placeCobble(level, pos, cobblestone, finiteTarget, this.searchFluid);
    }
}
