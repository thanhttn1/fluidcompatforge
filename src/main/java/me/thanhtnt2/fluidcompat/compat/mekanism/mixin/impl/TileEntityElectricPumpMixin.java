package me.thanhtnt2.fluidcompat.compat.mekanism.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.mekanism.MekanismPumpBridge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The only place Fluid Compat touches the Mekanism Electric Pump. The target is referenced by name, so Fluid Compat
 * loads without Mekanism and no Mekanism type appears in this class; {@code MekanismMixinPlugin} decides whether it
 * applies. {@code @Pseudo} tells the annotation processor the target may be missing at compile time.
 *
 * <p>Remapping: the pump's own method is never obfuscated ({@code remap = false} on the redirect), but
 * {@code Fluids.WATER} is a vanilla field with an SRG name in a real game. The annotation processor lets an
 * {@code @At} inherit the remap flag of its injector, so the {@code @At} states {@code remap = true} itself; only then
 * is its entry written to the refmap.
 */
@Pseudo
@Mixin(targets = "mekanism.common.tile.machine.TileEntityElectricPump")
public class TileEntityElectricPumpMixin {

    // The pump compares the fluid with Fluids.WATER to decide "skip the pickup for water". Answering with another
    // fluid makes water take the branch Mekanism already uses for lava, so its own code removes the source block
    @Redirect(
            method = "suck(Lnet/minecraft/core/BlockPos;ZZ)Z",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/material/Fluids;WATER:Lnet/minecraft/world/level/material/FlowingFluid;",
                    opcode = Opcodes.GETSTATIC,
                    ordinal = 0,
                    // Explicit on purpose: an @At inherits the remap flag of its injector, which is false below
                    remap = true),
            remap = false,
            require = 0)
    private FlowingFluid fluidcompat$fluidToCompareWithWater() {
        Level level = ((BlockEntity) (Object) this).getLevel();
        return MekanismPumpBridge.shouldRemoveWaterSource(level) ? Fluids.FLOWING_WATER : Fluids.WATER;
    }
}
