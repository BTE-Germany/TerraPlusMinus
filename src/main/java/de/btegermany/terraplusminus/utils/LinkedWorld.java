package de.btegermany.terraplusminus.utils;

public class LinkedWorld {

    private String worldName;
    private int offset;

    public LinkedWorld(String worldName, int offset) {
        this.worldName = worldName;
        this.offset = offset;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
