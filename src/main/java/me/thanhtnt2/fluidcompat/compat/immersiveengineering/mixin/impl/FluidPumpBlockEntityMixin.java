package me.thanhtnt2.fluidcompat.compat.immersiveengineering.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.immersiveengineering.IePumpBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The Immersive Engineering Fluid Pump hooks (see {@code IePumpBridge} for what each one is for). They apply or fail as
 * one: {@code PumpContract} checks every call they redirect, and an IE update that changes any of them switches all
 * of them off. The class is {@code @Pseudo} and the target is named, so Fluid Compat loads without IE.
 *
 * <p>Remapping: IE's own methods and the Forge methods ({@code canConvertToSource}, {@code FluidTank.fill}) keep their
 * names, so those {@code @At}s say {@code remap = false}. {@code FluidState.getType} and
 * {@code Level.setBlockAndUpdate} are vanilla methods with SRG names in a real game: an {@code @At} inherits the remap
 * flag of its injector, so each of those says {@code remap = true} itself and gets its refmap entry. Both are declared
 * in the class named as the owner.
 */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.blocks.metal.FluidPumpBlockEntity")
public abstract class FluidPumpBlockEntityMixin {

    private static final String CAN_CONVERT =
            "Lnet/minecraft/world/level/material/FluidState;canConvertToSource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z";
    private static final String DRAIN_BLOCK =
            "Lblusunrize/immersiveengineering/common/util/Utils;drainFluidBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/minecraftforge/fluids/FluidStack;";
    private static final String RELATED_FLUID =
            "Lblusunrize/immersiveengineering/common/util/Utils;getRelatedFluid(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)"
                    + "Lnet/minecraft/world/level/material/Fluid;";
    private static final String TANK_FILL =
            "Lnet/minecraftforge/fluids/capability/templates/FluidTank;fill(Lnet/minecraftforge/fluids/FluidStack;"
                    + "Lnet/minecraftforge/fluids/capability/IFluidHandler$FluidAction;)I";

    @Shadow
    private Fluid searchFluid;

    // How many mB the world gave less than the SIMULATE step promised; read by the tank fill right after the drain
    @Unique
    private int[] fluidcompat$shortfall = new int[1];

    // ---- the infinite rule and the area search's "can this fluid be pumped away" filter

    @Redirect(method = "checkAreaTick()V", at = @At(value = "INVOKE", target = CAN_CONVERT, ordinal = 0, remap = false),
            remap = false, require = 0)
    private boolean fluidcompat$searchFilterStart(FluidState state, Level level, BlockPos pos) {
        return IePumpBridge.canConvertToSource(state, level, pos);
    }

    @Redirect(method = "checkAreaTick()V", at = @At(value = "INVOKE", target = CAN_CONVERT, ordinal = 1, remap = false),
            remap = false, require = 0)
    private boolean fluidcompat$searchFilterNeighbour(FluidState state, Level level, BlockPos pos) {
        return IePumpBridge.canConvertToSource(state, level, pos);
    }

    @Redirect(method = "gatherInfiniteFluidFromWorld(Lnet/minecraft/core/Direction;)V",
            at = @At(value = "INVOKE", target = CAN_CONVERT, ordinal = 0, remap = false), remap = false, require = 0)
    private boolean fluidcompat$infiniteRule(FluidState state, Level level, BlockPos pos) {
        return IePumpBridge.canConvertToSource(state, level, pos);
    }

    // ---- thin layers: the search compares fluids with ==, so both sides use the source variant

    @Redirect(method = "checkAreaTick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;getType()Lnet/minecraft/world/level/material/Fluid;",
                    ordinal = 0, remap = true),
            remap = false, require = 0)
    private Fluid fluidcompat$searchStartFluid(FluidState state) {
        return IePumpBridge.searchFluid(state.getType());
    }

    @Redirect(method = "checkAreaTick()V", at = @At(value = "INVOKE", target = RELATED_FLUID, ordinal = 0, remap = false),
            remap = false, require = 0)
    private Fluid fluidcompat$searchNeighbourFluid(Level level, BlockPos pos) {
        return IePumpBridge.searchFluid(level.getFluidState(pos).getType());
    }

    // ---- the drain of one block: the search (SIMULATE) and the pump cycle (SIMULATE, then EXECUTE)

    @Redirect(method = "checkAreaTick()V", at = @At(value = "INVOKE", target = DRAIN_BLOCK, ordinal = 0, remap = false),
            remap = false, require = 0)
    private FluidStack fluidcompat$searchDrain(Level level, BlockPos pos, IFluidHandler.FluidAction action) {
        return IePumpBridge.drainBlock((BlockEntity) (Object) this, level, pos, action, fluidcompat$shortfall);
    }

    @Redirect(method = "tickServer()V", at = @At(value = "INVOKE", target = DRAIN_BLOCK, ordinal = 0, remap = false),
            remap = false, require = 0)
    private FluidStack fluidcompat$simulateDrain(Level level, BlockPos pos, IFluidHandler.FluidAction action) {
        return IePumpBridge.drainBlock((BlockEntity) (Object) this, level, pos, action, fluidcompat$shortfall);
    }

    @Redirect(method = "tickServer()V", at = @At(value = "INVOKE", target = DRAIN_BLOCK, ordinal = 1, remap = false),
            remap = false, require = 0)
    private FluidStack fluidcompat$executeDrain(Level level, BlockPos pos, IFluidHandler.FluidAction action) {
        fluidcompat$shortfall[0] = 0;
        return IePumpBridge.drainBlock((BlockEntity) (Object) this, level, pos, action, fluidcompat$shortfall);
    }

    // ---- what the tank receives, and the cobblestone

    @Redirect(method = "tickServer()V", at = @At(value = "INVOKE", target = TANK_FILL, ordinal = 1, remap = false),
            remap = false, require = 0)
    private int fluidcompat$fillWhatWasDrained(FluidTank tank, FluidStack promised, IFluidHandler.FluidAction action) {
        int shortfall = fluidcompat$shortfall[0];
        fluidcompat$shortfall[0] = 0;
        FluidStack stack = IePumpBridge.stackToFill(promised, shortfall);
        return stack.isEmpty() ? 0 : tank.fill(stack, action);
    }

    @Redirect(method = "tickServer()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
                    ordinal = 0, remap = true),
            remap = false, require = 0)
    private boolean fluidcompat$cobblestone(Level level, BlockPos pos, BlockState cobblestone) {
        return IePumpBridge.placeCobble(level, pos, cobblestone, searchFluid);
    }
}
