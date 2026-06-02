package de.btegermany.terraplusminus.gen.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import de.btegermany.terraplusminus.Terraplusminus;
import de.btegermany.terraplusminus.gen.CustomBiomeProvider;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.data.TreeCoverBaker;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.daporkchop.lib.common.reference.ReferenceStrength;
import net.daporkchop.lib.common.reference.cache.Cached;
import org.bukkit.Material;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;


public class TreePopulator extends BlockPopulator {

    public static final Cached<byte[]> RNG_CACHE = Cached.threadLocal(() -> new byte[16 * 16], ReferenceStrength.SOFT);
    public final Function<ChunkPos, CompletableFuture<CachedChunkData>> chunkDataProvider;
    int yOffset;
    boolean generateTrees; // Should Trees be added to the Terrain
    String surface;
    CustomBiomeProvider customBiomeProvider;

    // List of Possible trees by type
    HashMap<String, ArrayList<ArrayList<TreeBlock>>> trees = new HashMap<>();


    public TreePopulator(
            CustomBiomeProvider customBiomeProvider,
            int yOffset,
            Function<ChunkPos, CompletableFuture<CachedChunkData>> chunkDataProvider
    ) {
        this.customBiomeProvider = customBiomeProvider;
        this.yOffset = yOffset;
        this.generateTrees = Terraplusminus.config.getBoolean("generate_trees");
        this.surface = Terraplusminus.config.getString("surface_material");
        this.chunkDataProvider = chunkDataProvider;


        // Load Trees from customTrees.json
        JsonObject treeTypes = getJSONObject();
        final TreeLoadingStatistics stats = new TreeLoadingStatistics();
        treeTypes.entrySet().forEach(treeTypeEntry -> {
            final String treeType = treeTypeEntry.getKey();
            final JsonObject treeSizeVariants = treeTypeEntry.getValue().getAsJsonObject();
            stats.familyCount++;
            Terraplusminus.instance.getComponentLogger().debug("Loading tree family {} with {} size variants", treeType, treeSizeVariants.size());

            this.trees.put(treeType, new ArrayList<>());

            treeSizeVariants.entrySet().forEach(variantEntry -> {
                final String sizeName = variantEntry.getKey();  // s, m, l, ...
                final JsonObject treeVariants = variantEntry.getValue().getAsJsonObject();
                Terraplusminus.instance.getComponentLogger().trace("Loading trees of family {} and size {} with {} variants", treeType, sizeName, treeVariants.size());

                treeVariants.entrySet().forEach(treeEntry -> {
                    final String treeName = treeEntry.getKey();
                    final JsonObject treeConfig = treeEntry.getValue().getAsJsonObject();
                    Terraplusminus.instance.getComponentLogger().trace("Loading tree variant {} of size {} and family {}", treeName, sizeName, treeType);

                    stats.totalVariantCount++;
                    ArrayList<TreeBlock> treeBlocks = new ArrayList<>();

                    treeConfig.get("blocks").getAsJsonArray().forEach(treeBlockElement -> {

                        JsonObject treeBlock = treeBlockElement.getAsJsonObject();
                        treeBlocks.add(new TreeBlock(treeBlock.get("x").getAsInt(), treeBlock.get("y").getAsInt(), treeBlock.get("z").getAsInt(), Material.getMaterial(treeBlock.get("material").getAsString())));

                    });

                    trees.get(treeTypeEntry.getKey()).add(treeBlocks);

                });

            });

        });
        Terraplusminus.instance.getComponentLogger().info("Loaded {} custom trees from {} families", stats.totalVariantCount, stats.familyCount);

    }

    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull LimitedRegion limitedRegion) {
        if (generateTrees) {
            try {
                CachedChunkData data = this.chunkDataProvider.apply(new ChunkPos(x, z)).get();

                byte[] treeCover = data.getCustom(EarthGeneratorPipelines.KEY_DATA_TREE_COVER, TreeCoverBaker.FALLBACK_TREE_DENSITY);
                byte[] rng = RNG_CACHE.get();
                random.nextBytes(rng);

                for (int i = 0, dx = 0; dx < 16 >> 1; dx++) {
                    for (int dz = 0; dz < 16 >> 1; dz++, i++) {
                        int valueX = random.nextInt(16); // Depending on the size of the tree this should be changed
                        int valueZ = random.nextInt(16);
                        if ((rng[i] & 0xFF) < (treeCover[(valueX << 4) | valueZ] & 0xFF)) {
                            int groundY = 0;
                            int waterY = 0;
                            BlockState state = data.surfaceBlock(0, 0);

                            try {
                                groundY = data.groundHeight(valueX, valueZ);
                                waterY = data.waterHeight(valueX, valueZ);
                                state = data.surfaceBlock(valueX, valueZ);
                            } catch (IndexOutOfBoundsException e) {
                                Terraplusminus.instance.getComponentLogger().warn(
                                        "Chunk boundary overflow when attempting to generate a tree at chunk {}/{}",
                                        x, z
                                );
                            }

                            if (groundY < waterY) {
                                return;
                            }

                            int originX = valueX + x * 16;
                            int originY = groundY + 1 + yOffset;
                            int originZ = valueZ + z * 16;
                            if (groundY + yOffset < worldInfo.getMaxHeight() - 35 && groundY + yOffset > worldInfo.getMinHeight() && state == null) {
                                double biomeData = customBiomeProvider.getBiomeData(worldInfo, originX, groundY + yOffset, originZ);
                                switch ((int) biomeData) {
                                    case 4, 6, 17: // desert and savanna
                                        generateCustomTree(limitedRegion, random, originX, originY, originZ, "savanna");
                                        break;
                                    case 14, 15: // flower forest
                                        generateCustomTree(limitedRegion, random, originX, originY, originZ, "oak", "birch");
                                        break;
                                    case 27: // taiga
                                        generateCustomTree(limitedRegion, random, originX, originY, originZ, "spruce");
                                        break;
                                    case 28, 29, 30: // snowy regions
                                        // TODO: trees with snow
                                        break;
                                    default:
                                        generateCustomTree(limitedRegion, random, originX, originY, originZ, "oak", "birch");
                                        break;
                                }
                            }
                        }
                    }
                }


            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Terraplusminus.instance.getComponentLogger().warn(
                        "Interrupted when generating trees in chunk {}/{} in world {}",
                        x, z,
                        worldInfo.getName(),
                        e
                );
            } catch (ExecutionException e) {
                Terraplusminus.instance.getComponentLogger().warn(
                        "Exception when generating trees in chunk {}/{} in world {}",
                        x, z,
                        worldInfo.getName(),
                        e
                );
            }
        }
    }

    public void generateCustomTree(LimitedRegion limitedRegion, Random random, int originX, int originY, int originZ, String... types) {
        List<TreeBlock> tree = selectTree(random, types);
        if (tree.isEmpty()) return;

        // Rotate Tree Randomly
        int angle = random.nextInt(4) * 90;

        // Place Tree
        for (TreeBlock block : tree) {
            int x = block.getX();
            int z = block.getZ();
            if (angle == 90) {
                int temp = x;
                x = -z;
                z = temp;
            } else if (angle == 180) {
                x = -x;
                z = -z;
            } else if (angle == 270) {
                int temp = x;
                x = z;
                z = -temp;
            }
            limitedRegion.setType(originX + x, originY + block.getY(), originZ + z, block.getMaterial());
        }
    }

    private List<TreeBlock> selectTree(Random random, String... types) {
        int totalTrees = 0;
        for (String type : types) {
            ArrayList<ArrayList<TreeBlock>> variants = this.trees.get(type);
            if (variants != null) {
                totalTrees += variants.size();
            }
        }
        if (totalTrees == 0) {
            return Collections.emptyList();
        }

        int selectedTree = random.nextInt(totalTrees);
        for (String type : types) {
            ArrayList<ArrayList<TreeBlock>> variants = this.trees.get(type);
            if (variants == null) {
                continue;
            }
            if (selectedTree < variants.size()) {
                return variants.get(selectedTree);
            }
            selectedTree -= variants.size();
        }
        return Collections.emptyList();
    }

    public JsonObject getJSONObject() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("assets/terraplusminus/data/customTrees.json");

        JsonReader reader;
        reader = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(reader);
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        return jsonObject.get("trees").getAsJsonObject();
    }

    private static class TreeLoadingStatistics {
        int familyCount = 0;
        int totalVariantCount = 0;
    }

}
