package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.gen.populate.RealWorldPopulator;
import lombok.NonNull;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.util.OrderedRegistry;
import org.bukkit.generator.BlockPopulator;

public class InitRealWorldPopulatorRegistryEvent extends InitRealEarthRegistryEvent<RealWorldPopulator> {
    protected InitRealWorldPopulatorRegistryEvent(@NonNull EarthGeneratorSettings settings, @NonNull RealWorldGenerator generator, @NonNull OrderedRegistry<RealWorldPopulator> registry) {
        super(settings, generator, registry);
    }
}
