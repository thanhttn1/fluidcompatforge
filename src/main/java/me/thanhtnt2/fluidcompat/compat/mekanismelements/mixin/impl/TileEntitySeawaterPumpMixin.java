package me.thanhtnt2.fluidcompat.compat.mekanismelements.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.mekanismelements.SeawaterPumpBridge;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The only place Fluid Compat touches the Seawater Pump. The target is named, not linked, so Fluid Compat loads
 * without Mekanism Elements; {@code MekanismElementsMixinPlugin} decides whether this applies. Everything touched is
 * Mekanism's own, which is never obfuscated, so no remapping is involved.
 */
@Pseudo
@Mixin(targets = "fixdol.mekanismelements.common.tile.machine.TileEntitySeawaterPump", remap = false)
public class TileEntitySeawaterPumpMixin {

    // Lives and dies with the pump, so there is no map of pumps anywhere
    @Unique
    private long fluidcompat$lastIntakeTick = -1L;

    // Hooking the stack handed to the tank means the world pays for exactly what the pump really makes
    @ModifyArg(
            method = "onUpdateServer()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/capabilities/fluid/BasicFluidTank;insert(Lnet/minecraftforge/fluids/FluidStack;Lmekanism/api/Action;Lmekanism/api/AutomationType;)Lnet/minecraftforge/fluids/FluidStack;",
                    remap = false),
            index = 0,
            remap = false,
            require = 0)
    private FluidStack fluidcompat$chargeWorldForSeawater(FluidStack seawater) {
        SeawaterPumpBridge.Charge charge =
                SeawaterPumpBridge.chargeWorld((BlockEntity) (Object) this, seawater, fluidcompat$lastIntakeTick);
        if (charge.paidAtTick() >= 0L) {
            fluidcompat$lastIntakeTick = charge.paidAtTick();
        }
        return charge.stack();
    }
}
