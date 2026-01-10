package de.btegermany.terraplusminus.gen.populate.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import de.btegermany.terraplusminus.gen.CustomBiomeProvider;
import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import de.btegermany.terraplusminus.gen.populate.RealWorldPopulator;
import de.btegermany.terraplusminus.utils.Properties;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.data.TreeCoverBaker;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.TerraBukkit;
import net.daporkchop.lib.common.reference.ReferenceStrength;
import net.daporkchop.lib.common.reference.cache.Cached;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;


public class TreePopulator extends RealWorldPopulator {

    public static final Cached<byte[]> RNG_CACHE = Cached.threadLocal(() -> new byte[16 * 16], ReferenceStrength.SOFT);
    boolean generateTrees; // Should Trees be added to the Terrain
    String surface;

    final CustomBiomeProvider customBiomeProvider;

    // List of Possible trees by type
    HashMap<String, ArrayList<ArrayList<TreeBlock>>> trees = new HashMap<>();


    public TreePopulator(RealWorldGenerator generator) {
        super(generator);
        this.customBiomeProvider = generator.getCustomBiomeProvider();
        this.generateTrees = generator.getPlugin().getConfig().getBoolean(Properties.GENERATE_TREES);
        this.surface = generator.getPlugin().getConfig().getString(Properties.SURFACE_MATERIAL);

        // Load Trees from customTrees.json
        JsonObject treeTypes = getJSONObject();
        final TreeLoadingStatistics stats = new TreeLoadingStatistics();
        treeTypes.entrySet().forEach(treeTypeEntry -> {
            final String treeType = treeTypeEntry.getKey();
            final JsonObject treeSizeVariants = treeTypeEntry.getValue().getAsJsonObject();
            stats.familyCount++;
            generator.getPlugin().getComponentLogger().debug("Loading tree family {} with {} size variants", treeType, treeSizeVariants.size());

            this.trees.put(treeType, new ArrayList<>());

            treeSizeVariants.entrySet().forEach(variantEntry -> {
                final String sizeName = variantEntry.getKey();  // s, m, l, ...
                final JsonObject treeVariants = variantEntry.getValue().getAsJsonObject();
                generator.getPlugin().getComponentLogger().trace("Loading trees of family {} and size {} with {} variants", treeType, sizeName, treeVariants.size());

                treeVariants.entrySet().forEach(treeEntry -> {
                    final String treeName = treeEntry.getKey();
                    final JsonObject treeConfig = treeEntry.getValue().getAsJsonObject();
                    generator.getPlugin().getComponentLogger().trace("Loading tree variant {} of size {} and family {}", treeName, sizeName, treeType);

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
        generator.getPlugin().getComponentLogger().info("Loaded {} custom trees from {} families", stats.totalVariantCount, stats.familyCount);

    }

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, @NotNull int x, @NotNull int z, @NotNull int xOffset, @NotNull int zOffset, @NotNull int yOffset, @NotNull LimitedRegion limitedRegion, @NotNull CachedChunkData data, @NotNull RealWorldGenerator worldGenerator) {
        if(!generateTrees)
            return;

        final World world = Bukkit.getWorld(worldInfo.getName());
        try {

            byte[] treeCover = data.getCustom(EarthGeneratorPipelines.KEY_DATA_TREE_COVER, TreeCoverBaker.FALLBACK_TREE_DENSITY);
            byte[] rng = RNG_CACHE.get();

            for (int i = 0, dx = 0; dx < 16 >> 1; dx++) {
                for (int dz = 0; dz < 16 >> 1; dz++, i++) {
                    if ((rng[i] & 0xFF) < (treeCover[(((x * 16) & 0xF) << 4) | ((z * 16) & 0xF)] & 0xFF)) {
                        random.nextBytes(rng);

                        int valueX = random.nextInt(15) + 1; // Depending on the size of the tree this should be changed
                        int valueZ = random.nextInt(15) + 1;
                        int groundY = 0;
                        int waterY = 0;
                        BlockState state = data.surfaceBlock(0, 0);

                        try {
                            groundY = data.groundHeight(valueX, valueZ);
                            waterY = data.waterHeight(valueX, valueZ);
                            state = data.surfaceBlock(valueX, valueZ);
                        } catch (IndexOutOfBoundsException e) {
                            worldGenerator.getPlugin().getComponentLogger().warn(
                                    "Chunk boundary overflow when attempting to generate a tree at chunk {}/{}",
                                    x, z
                            );
                        }

                        if (groundY < waterY) {
                            return;
                        }

                        Biome biome = TerraBukkit.toBukkitBiome(data.biome(dx, dz));

                        Location loc = new Location(world, valueX + x * 16, groundY + 1 + yOffset, valueZ + z * 16); // is offset missing?
                        if (!(groundY < waterY) && groundY + yOffset < world.getMaxHeight() - 35 && groundY + yOffset > world.getMinHeight() && state == null) {
                            if (biome == Biome.DESERT || biome == Biome.SAVANNA || biome == Biome.SAVANNA_PLATEAU) // desert, savanna and savanna plateau
                                generateCustomTree(limitedRegion, loc, "savanna");
                            else if (biome == Biome.FLOWER_FOREST) // flower forest
                                generateCustomTree(limitedRegion, loc, "oak", "birch");
                            else if (biome == Biome.TAIGA) // taiga
                                generateCustomTree(limitedRegion, loc, "spruce");
                            else if (biome == Biome.SNOWY_SLOPES || biome == Biome.SNOWY_PLAINS || biome == Biome.ICE_SPIKES) {// snowy regions
                                // TODO: trees with snow
                            } else
                                    generateCustomTree(limitedRegion, loc, "oak", "birch");
                        }
                    }
                }
            }


        } catch (Exception e) {
            worldGenerator.getPlugin().getComponentLogger().warn(
                    "Exception when generating trees in chunk {}/{} in world {}",
                    x, z,
                    worldInfo.getName(),
                    e
            );
        }
    }

    public void generateCustomTree(LimitedRegion limitedRegion, Location loc, String... types) {

        ArrayList<ArrayList<TreeBlock>> trees = new ArrayList<>();
        for (String type : types) {
            this.trees.get(type).forEach((tree) -> {
                trees.add(tree);
            });
        }

        // Random Tree
        if (trees.size() == 0) return;

        int randTree = (new Random()).nextInt(trees.size());
        if (randTree < 0) randTree = 0;
        if (randTree > trees.size() - 1) randTree = trees.size() - 1;
        ArrayList<TreeBlock> tree = trees.get(randTree);

        int originX = loc.getBlockX();
        int originY = loc.getBlockY();
        int originZ = loc.getBlockZ();


        // Rotate Tree Randomly
        Random rand = new Random();
        int angle = rand.nextInt(4) * 90;

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