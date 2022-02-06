package de.btegermany.terraplusminus.gen;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

public abstract class NMSInjectorAbstract {

    public void startupTasks() {}


    /**
     * @param world
     * @return whether or not the injection was a success
     */
    public abstract boolean attemptInject(World world);


    /**
     * Force an NMS physics update at the location.
     */
    public void updatePhysics(World world, org.bukkit.block.Block block) {
        throw new UnsupportedOperationException("Tried to update physics without implementing.");
    };

    public int getMinY() {
        return 0;
    }

    public int getMaxY() {
        return 2032;
    }

    public void debugTest(Player p) {}
}