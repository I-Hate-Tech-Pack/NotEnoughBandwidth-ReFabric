package cn.ussshenzhou.notenoughbandwidth.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.test.TestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces the gametest server's flat world with default (noise) overworld
 * so chunk compression benchmarks use realistic terrain.
 * Only active when -Dneb.benchmark.noise=true is set.
 */
@Mixin(TestServer.class)
public class TestServerMixin {

    @Redirect(
            method = "method_40377",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/registry/RegistryWrapper$Impl;getOrThrow(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/registry/entry/RegistryEntry$Reference;")
    )
    private static RegistryEntry.Reference<WorldPreset> useNoiseOverworld(
            RegistryWrapper.Impl<WorldPreset> registry, RegistryKey<WorldPreset> key) {
        if ("true".equals(System.getProperty("neb.benchmark.noise"))) {
            return registry.getOrThrow(WorldPresets.DEFAULT);
        }
        return registry.getOrThrow(key);
    }
}
