package me.thanhtnt2.fluidcompat.compat.alexscaves.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.alexscaves.AlexsCavesDrainBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The only place Fluid Compat touches the Alex's Caves Drain. The target is named, not linked, so Fluid Compat loads
 * without Alex's Caves; {@code AlexsCavesMixinPlugin} decides whether this applies. {@code drainLogic} is Alex's own
 * method (never obfuscated), so no remapping is involved, and its descriptor only names vanilla classes, whose names
 * are the same in a real game and in the IDE.
 */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.block.DrainBlock", remap = false)
public class DrainBlockMixin {

    // Cancelling at HEAD skips the original body only when the bridge really did the work; otherwise it runs unchanged
    @Inject(
            method = "drainLogic(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void fluidcompat$replaceDrainLogic(BlockState state, Level level, BlockPos pos, CallbackInfo callback) {
        if (AlexsCavesDrainBridge.handle(state, level, pos)) {
            callback.cancel();
        }
    }
}
