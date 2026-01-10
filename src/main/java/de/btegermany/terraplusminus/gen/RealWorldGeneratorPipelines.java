package de.btegermany.terraplusminus.gen;

import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.data.KoppenClimateData;
import de.btegermany.terraplusminus.events.InitRealDatasetsEvent;
import de.btegermany.terraplusminus.events.InitRealBiomeFilterRegistryEvent;
import de.btegermany.terraplusminus.events.InitRealEarthDataBakerRegistryEvent;
import de.btegermany.terraplusminus.events.InitRealWorldPopulatorRegistryEvent;
import de.btegermany.terraplusminus.events.InitRealEarthRegistryEvent;
import de.btegermany.terraplusminus.gen.biome.KoppenBiomeFilter;
import de.btegermany.terraplusminus.gen.populate.RealWorldPopulator;
import de.btegermany.terraplusminus.gen.populate.tree.TreePopulator;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.buildtheearth.terraminusminus.generator.EarthBiomeProvider;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.biome.IEarthBiomeFilter;
import net.buildtheearth.terraminusminus.generator.biome.Terra121BiomeFilter;
import net.buildtheearth.terraminusminus.generator.biome.UserOverrideBiomeFilter;
import net.buildtheearth.terraminusminus.generator.data.*;
import net.buildtheearth.terraminusminus.util.OrderedRegistry;
import org.bukkit.Bukkit;
import org.bukkit.generator.BlockPopulator;

import java.lang.reflect.Array;
import java.util.Map;

import static net.daporkchop.lib.common.util.PorkUtil.uncheckedCast;

/**
 *  Default processing pipelines for various earth generator processing steps.
 *  <p>
 *  Based on
 *  <a href="https://github.com/BuildTheEarth/terraplusplus/blob/master/src/main/java/net/buildtheearth/terraplusplus/generator/EarthGeneratorPipelines.java">EarthGeneratorPipelines.java @author DaPorkchop_</a>
 *
 * @author DavixDevelop
 */
@UtilityClass
public class RealWorldGeneratorPipelines {
    public final String KEY_DATASET_KOPPEN_CLIMATE = "koppen_climate";

    private <T> T[] fire(@NonNull InitRealEarthRegistryEvent<T> event) {
        Bukkit.getPluginManager().callEvent(event);
        return event.getRegistry().entryStream().map(Map.Entry::getValue).toArray(x -> uncheckedCast(Array.newInstance(event.getGenericType(), x)));
    }

    public Map<String, Object> datasets(@NonNull EarthGeneratorSettings settings) {
        InitRealDatasetsEvent event = new InitRealDatasetsEvent(settings);

        Map<String, Object> defaultDataset = EarthGeneratorPipelines.datasets(settings);
        defaultDataset.put(KEY_DATASET_KOPPEN_CLIMATE, new KoppenClimateData());

        for(String key : defaultDataset.keySet()){
            event.registerData(key, defaultDataset.get(key));
        }

        Bukkit.getPluginManager().callEvent(event);
        return event.getCustomData();
    }

    public IEarthBiomeFilter<?>[] biomeFilters(@NonNull EarthGeneratorSettings settings, @NonNull RealWorldGenerator generator, @NonNull boolean useLegacyDataset){
        return fire(new InitRealBiomeFilterRegistryEvent(settings, generator,
                uncheckedCast(new OrderedRegistry<IEarthBiomeFilter<?>>()
                        .addLast("biome_filter", useLegacyDataset ? new Terra121BiomeFilter() : new KoppenBiomeFilter())
                        .addLast("biome_overrides", new UserOverrideBiomeFilter(settings.projection()))
                )) {});
    }

    public IEarthDataBaker<?>[] dataBakers(@NonNull EarthGeneratorSettings settings, @NonNull RealWorldGenerator generator, EarthBiomeProvider biomeProvider){
        return fire(new InitRealEarthDataBakerRegistryEvent(settings, generator,
                uncheckedCast(new OrderedRegistry<IEarthDataBaker<?>>()
                        .addLast("initial_biomes", new InitialBiomesBaker(biomeProvider))
                        .addLast("tree_cover", new TreeCoverBaker())
                        .addLast("heights", new HeightsBaker())
                        .addLast("osm", new OSMBaker())
                        .addLast("null_inland", new NullIslandBaker()))) {});
    }


    public RealWorldPopulator[] populators(@NonNull EarthGeneratorSettings settings, @NonNull RealWorldGenerator generator){
        return fire(new InitRealWorldPopulatorRegistryEvent(settings, generator,
                uncheckedCast(new OrderedRegistry<RealWorldPopulator>()
                        .addLast("trees", new TreePopulator(generator)))) {});
    }
}
