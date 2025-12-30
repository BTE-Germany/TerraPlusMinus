package de.btegermany.terraplusminus.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Properties {
    public static final String CHAT_PREFIX = "prefix";
    public static final String HEIGHT_DATAPACK = "height_datapack";
    private static final String OFFSET_PREFIX = "terrain_offset.";
    public static final String X_OFFSET = OFFSET_PREFIX + "x";
    public static final String Y_OFFSET = OFFSET_PREFIX + "y";
    public static final String Z_OFFSET = OFFSET_PREFIX + "z";
    public static final String MIN_LAT = "min_latitude";
    public static final String MAX_LAT = "max_latitude";
    public static final String MIN_LON = "min_longitude";
    public static final String MAX_LON = "max_longitude";
    public static final String LINKED_WORLDS_ENABLED = "linked_worlds.enabled";
    public static final String LINKED_WORLDS_METHOD = "linked_worlds.method";
    public static final String PASSTHROUGH_TPLL = "passthrough_tpll";
    public static final String ACTIONBAR_HEIGHT = "height_in_actionbar";
    public static final String SURFACE_MATERIAL = "surface_material";
    public static final String BUILDING_OUTLINES_MATERIAL = "building_outlines_material";
    public static final String ROAD_MATERIAL = "road_material";
    public static final String PATH_MATERIAL = "path_material";

    public class NonConfigurable {
        public static final String METHOD_MV = "MULTIVERSE";
        public static final String METHOD_SRV = "SERVER";
        public static final String CROSS_TELEPORTATION_CHANNEL = "terraplusminus:teleportbridge";
    }
}
