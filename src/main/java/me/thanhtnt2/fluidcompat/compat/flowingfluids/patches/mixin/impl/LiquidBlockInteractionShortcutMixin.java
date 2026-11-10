package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.PatchHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Performance patch (Flowing Fluids issues 153 and 192): skips Forge's fluid interaction check when a liquid block
 * only changed its level.
 *
 * <p>In both methods the vanilla/Forge body is "if no interaction happened, schedule a fluid tick". The tick is
 * what keeps fluid flowing, so it must never be skipped. For a pure level change this handler therefore schedules
 * the tick itself, exactly as the original does, and only leaves out the interaction check in front of it. Everything
 * else runs the original method. {@code FlowingFluidsPatchesMixinPlugin} lets this mixin in only when the two methods
 * still have that exact shape.
 *
 * <p>1.20.1 notes. This is the one patch whose target is a vanilla class, and Forge 1.20.1 runs vanilla with SRG
 * names in a real game. The two {@code method} strings below are therefore written with Mojang names and remapped
 * through the refmap that the Mixin annotation processor writes at build time ({@code fluidcompat.refmap.json}); that
 * is why this class keeps the default {@code remap = true}, unlike the patches that target Flowing Fluids classes.
 * Forge also made the field {@code LiquidBlock.fluid} private and null for liquids registered through a supplier, so
 * the fluid is read through Forge's public {@code getFluid()}, which is safe for both kinds of liquid block.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockInteractionShortcutMixin {

    // Added by Forge, not part of vanilla: its name is never obfuscated, so it must not be remapped
    @Shadow(remap = false)
    public abstract FlowingFluid getFluid();

    @Inject(
            method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void fluidcompat$skipInteractionCheckOnLevelChange(
            BlockState state, Level level, BlockPos pos, Block neighbourBefore, BlockPos neighbourPos, boolean movedByPiston, CallbackInfo callback) {
        if (PatchHooks.canSkipInteractionCheckForNeighbour(state, level, neighbourBefore, neighbourPos)) {
            scheduleFluidTick(state, level, pos);
            callback.cancel();
        }
    }

    @Inject(
            method = "onPlace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V",
            at = @At("HEAD"),
            cancellable = true)
    private void fluidcompat$skipInteractionCheckOnLevelPlacement(
            BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo callback) {
        if (PatchHooks.canSkipInteractionCheckForPlacement(state, oldState)) {
            scheduleFluidTick(state, level, pos);
            callback.cancel();
        }
    }

    // The same call the original makes when no interaction took place
    private void scheduleFluidTick(BlockState state, Level level, BlockPos pos) {
        level.scheduleTick(pos, state.getFluidState().getType(), this.getFluid().getTickDelay(level));
    }
}
