package de.btegermany.terraplusminus.gen;

import de.btegermany.terraplusminus.Terraplusminus;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.dimension.DimensionManager;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;

import java.lang.reflect.Field;
import java.util.OptionalLong;


public class NMSInjector {

    public void attemptInject(World world) {
        CraftWorld cw = (CraftWorld) world;
        WorldServer ws = cw.getHandle();
        DimensionManager delegate = ws.q_(); //getDimensionManager

		DimensionManager replacement = DimensionManager.a(
		(OptionalLong) queryDimensionManagerPrivateField("w",delegate),
		(boolean) queryDimensionManagerPrivateField("x",delegate),
		(boolean) queryDimensionManagerPrivateField("y",delegate),
		(boolean) queryDimensionManagerPrivateField("z",delegate),
		(boolean) queryDimensionManagerPrivateField("A",delegate),
		(double) queryDimensionManagerPrivateField("B",delegate),
		(boolean) queryDimensionManagerPrivateField("C",delegate),
		(boolean) queryDimensionManagerPrivateField("D",delegate),
		(boolean) queryDimensionManagerPrivateField("E",delegate),
		(boolean) queryDimensionManagerPrivateField("F",delegate),
		(boolean) queryDimensionManagerPrivateField("G",delegate),
				Terraplusminus.config.getInt("min-height"),//queryDimensionManagerPrivateField("H",delegate), //minY
				Terraplusminus.config.getInt("max-height"),//queryDimensionManagerPrivateField("I",delegate), //Height
				Terraplusminus.config.getInt("max-height"),//queryDimensionManagerPrivateField("J",delegate), //Logical Height
		(MinecraftKey) queryDimensionManagerPrivateField("K",delegate),
		(MinecraftKey) queryDimensionManagerPrivateField("L",delegate),
		(float) queryDimensionManagerPrivateField("M",delegate)
		);

		try {
            Terraplusminus.privateFieldHandler.injectField(
					ws,
                    net.minecraft.world.level.World.class.getDeclaredField("C"),
                    replacement);
			System.out.println("&aSuccessfully injected custom world height!");
			System.out.println("&aNew Heights (WorldServer, Bukkit World):");
			System.out.println("- minY " + ws.q_().k() + "   " + world.getMinHeight());
			System.out.println("- Height " + ws.q_().l() + "   " + world.getMaxHeight());
			System.out.println("- LogicalHeight " + ws.q_().m() + "   " + world.getLogicalHeight());

		} catch (Throwable e) {
            e.printStackTrace();
        }
    }

    	private static Object queryDimensionManagerPrivateField(String fieldName, DimensionManager delegate) {
		try {
			Field field = delegate.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(delegate);
		}
		catch(Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}