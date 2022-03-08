package de.btegermany.terraplusminus.data;

import de.btegermany.terraplusminus.geo.GeographicProjection;
import de.btegermany.terraplusminus.geo.ModifiedAirocean;
import de.btegermany.terraplusminus.geo.ScaleProjection;
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

    private static final GeographicProjection projection = new ModifiedAirocean();
    private static final GeographicProjection uprightProj = GeographicProjection.orientProjection(projection, GeographicProjection.Orientation.upright);
    private static final ScaleProjection scaleProj = new ScaleProjection(uprightProj, 7318261.522857145, 7318261.522857145);

    /**
     * Gets the geographical location from in-game coordinates
     *
     * @param x X-Axis in-game
     * @param z Z-Axis in-game
     * @return The geographical location (Long, Lat)
     */
    public static double[] toGeo(double x, double z) {
        return scaleProj.toGeo(x, z);
    }

    /**
     * Gets in-game coordinates from geographical location
     *
     * @param lon Geographical Longitude
     * @param lat Geographic Latitude
     * @return The in-game coordinates (x, z)
     */
    public static double[] fromGeo(double lon, double lat) {
        return scaleProj.fromGeo(lon, lat);
    }


    private final EarthGeneratorSettings bteGeneratorSettings = EarthGeneratorSettings.parse(EarthGeneratorSettings.BTE_DEFAULT_SETTINGS);

    public CompletableFuture<Double> getHeight(double x, double z) {
        double[] adjustedProj = toGeo(x, z);
        double adjustedLon = adjustedProj[0];
        double adjustedLat = adjustedProj[1];
        GeneratorDatasets datasets = new GeneratorDatasets(bteGeneratorSettings);
        CompletableFuture<Double> altFuture;
        try {
            altFuture = datasets.<IScalarDataset>getCustom(EarthGeneratorPipelines.KEY_DATASET_HEIGHTS)
                    .getAsync(adjustedLon, adjustedLat)
                    .thenApply(a -> a + 1.0d);
        } catch (OutOfProjectionBoundsException e) {
            altFuture = CompletableFuture.completedFuture(0.0);
        }
        return altFuture;
    }


}