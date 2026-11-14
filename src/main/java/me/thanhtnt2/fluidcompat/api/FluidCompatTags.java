package me.thanhtnt2.fluidcompat.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

/** Data-driven extension points: addons and modpacks add entries through plain JSON tags. */
public final class FluidCompatTags {

    /** Extra fluids (beyond water and lava) that are drained from the world instead of generated. */
    public static final TagKey<Fluid> MANAGED_FLUIDS = fluidTag("managed_fluids");

    /** Pump items that get the "finite fluid" explanation page in JEI. */
    public static final TagKey<Item> FINITE_PUMP_ITEMS = itemTag("finite_pumps");
    /** Pipe-like blocks that stay submerged in Flowing Fluids water; datapacks can add blocks to it. */
    public static final TagKey<Block> FLUID_PASSABLE_BLOCKS = blockTag("fluid_passable_blocks");

    private FluidCompatTags() {
    }

    private static TagKey<Fluid> fluidTag(String path) {
        return TagKey.create(Registries.FLUID, id(path));
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, id(path));
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FluidCompatApi.MOD_ID, path);
    }
}
