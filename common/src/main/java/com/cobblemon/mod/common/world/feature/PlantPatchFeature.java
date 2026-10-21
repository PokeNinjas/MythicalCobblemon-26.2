package com.cobblemon.mod.common.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Keeps existing plant patch attempts and triangular spread after vanilla removed random_patch. */
public final class PlantPatchFeature extends Feature<PlantPatchFeature.Configuration> {
    public PlantPatchFeature() {
        super(Configuration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Configuration> context) {
        var config = context.config();
        var random = context.random();
        var position = context.origin().mutable();
        int placed = 0;
        for (int attempt = 0; attempt < config.tries(); attempt++) {
            position.setWithOffset(context.origin(),
                random.nextInt(config.xzSpread() + 1) - random.nextInt(config.xzSpread() + 1),
                random.nextInt(config.ySpread() + 1) - random.nextInt(config.ySpread() + 1),
                random.nextInt(config.xzSpread() + 1) - random.nextInt(config.xzSpread() + 1));
            if (config.feature().value().place(context.level(), context.chunkGenerator(), random, position)) placed++;
        }
        return placed > 0;
    }

    public record Configuration(int tries, int xzSpread, int ySpread, Holder<PlacedFeature> feature) implements FeatureConfiguration {
        public static final Codec<Configuration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("tries", 128).forGetter(Configuration::tries),
            Codec.intRange(0, Integer.MAX_VALUE - 1).optionalFieldOf("xz_spread", 7).forGetter(Configuration::xzSpread),
            Codec.intRange(0, Integer.MAX_VALUE - 1).optionalFieldOf("y_spread", 3).forGetter(Configuration::ySpread),
            PlacedFeature.CODEC.fieldOf("feature").forGetter(Configuration::feature)
        ).apply(instance, Configuration::new));

        @Override
        public Stream<Holder<ConfiguredFeature<?, ?>>> getSubFeatures() {
            return feature.value().getFeatures();
        }
    }
}
