package de.btegermany.terraplusminus.data;

import de.btegermany.terraplusminus.geo.GeographicProjection;
import de.btegermany.terraplusminus.geo.ModifiedAirocean;
import de.btegermany.terraplusminus.geo.ScaleProjection;
import net.buildtheearth.terraminusminus.dataset.IScalarDataset;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.GeneratorDatasets;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.projection.transform.ScaleProjectionTransform;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;

import java.util.concurrent.CompletableFuture;

import static net.buildtheearth.terraminusminus.util.MathUtils.clamp;


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

    public CompletableFuture<double[]> getTreeCover(int baseX, int baseZ) throws OutOfProjectionBoundsException {
        GeneratorDatasets datasets = new GeneratorDatasets(bteGeneratorSettings);
        CompletableFuture<double[]> treeCoverF = null;
        try {
            Bounds2d chunkBounds = Bounds2d.of(baseX, baseX + 16, baseZ, baseZ + 16);
            CornerBoundingBox2d chunkBoundsGeo = chunkBounds.toCornerBB(datasets.projection(), false).toGeo();

            treeCoverF = datasets.<IScalarDataset>getCustom(EarthGeneratorPipelines.KEY_DATASET_TREE_COVER)
                    .getAsync(chunkBoundsGeo, 16, 16);
        }catch(Exception e){
            e.printStackTrace();
        }
        return treeCoverF;
    }

    public void bake(ChunkPos pos, CachedChunkData.Builder builder, double[] treeCover) {
        byte[] arr = new byte[16 * 16];
        if (treeCover != null) {
            for (int i = 0; i < 16 * 16; i++) {
                arr[i] = treeChance(treeCover[i]);
            }
        }
        builder.putCustom(EarthGeneratorPipelines.KEY_DATA_TREE_COVER, arr);
    }

    public static final double TREE_AREA = 2.0d * 2.0d;

    static byte treeChance(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }

        //value is in range [0-1]
        value *= (1.0 / TREE_AREA);

        //scale to byte range
        value *= 255.0d;

        //increase by 50%
        value *= 1.50d;

        return (byte) clamp(value, 0, 255);
    }

}