package de.btegermany.terraplusminus.data;

import net.buildtheearth.terraminusminus.dataset.IScalarDataset;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.GeneratorDatasets;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;

import java.util.concurrent.CompletableFuture;


/**
 * @author Noah Husby
 */

public class TerraConnector {

    private static final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    /**
     * Gets the geographical location from in-game coordinates
     *
     * @param x X-Axis in-game
     * @param z Z-Axis in-game
     * @return The geographical location (Long, Lat)
     */
    public static double[] toGeo(double x, double z) {
        try {
            return bteGeneratorSettings.projection().toGeo(x, z);
        } catch (OutOfProjectionBoundsException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets in-game coordinates from geographical location
     *
     * @param lon Geographical Longitude
     * @param lat Geographic Latitude
     * @return The in-game coordinates (x, z)
     */
    public static double[] fromGeo(double lon, double lat) {
        try {
            return bteGeneratorSettings.projection().fromGeo(lon, lat);
        } catch (OutOfProjectionBoundsException e) {
            throw new RuntimeException(e);
        }
    }


    public CompletableFuture<Double> getHeight(double x, double z) {
        CompletableFuture<Double> altFuture;
        try {
            double[] adjustedProj = bteGeneratorSettings.projection().toGeo(x, z);

            double adjustedLon = adjustedProj[0];
            double adjustedLat = adjustedProj[1];
            GeneratorDatasets datasets = new GeneratorDatasets(bteGeneratorSettings);


            altFuture = datasets.<IScalarDataset>getCustom(EarthGeneratorPipelines.KEY_DATASET_HEIGHTS)
                    .getAsync(adjustedLon, adjustedLat)
                    .thenApply(a -> a + 1.0d);
        } catch (OutOfProjectionBoundsException e) {
            altFuture = CompletableFuture.completedFuture(0.0);
        }
        return altFuture;
    }


}