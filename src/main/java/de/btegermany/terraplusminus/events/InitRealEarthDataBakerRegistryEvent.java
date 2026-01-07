package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.data.IEarthDataBaker;
import net.buildtheearth.terraminusminus.util.OrderedRegistry;

public class InitRealEarthDataBakerRegistryEvent extends InitRealEarthRegistryEvent<IEarthDataBaker<?>> {
    protected InitRealEarthDataBakerRegistryEvent(@NonNull EarthGeneratorSettings settings, @NonNull RealWorldGenerator generator, @NonNull OrderedRegistry<IEarthDataBaker<?>> registry) {
        super(settings, generator, registry);
    }
}
