package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.biome.IEarthBiomeFilter;
import net.buildtheearth.terraminusminus.util.OrderedRegistry;

public class InitRealBiomeFilterRegistryEvent extends InitRealEarthRegistryEvent<IEarthBiomeFilter<?>> {
    protected InitRealBiomeFilterRegistryEvent(@NonNull EarthGeneratorSettings settings, @NonNull RealWorldGenerator generator, @NonNull OrderedRegistry<IEarthBiomeFilter<?>> registry) {
        super(settings, generator, registry);
    }
}
