package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.gen.RealWorldGenerator;
import lombok.*;
import lombok.experimental.Accessors;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.util.OrderedRegistry;
import net.daporkchop.lib.common.util.GenericMatcher;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;


/**
 * Fired when af {@link OrderedRegistry} is being initialized
 * <p>
 * This event is fired on {@link org.bukkit.plugin.PluginManager#callEvent(Event)}
 * <p>
 * Based on:
 * <a href="https://github.com/BuildTheEarth/terraplusplus/blob/master/src/main/java/net/buildtheearth/terraplusplus/event/InitEarthRegistryEvent.java">InitEarthRegistryEvent.java @author DaPorkchop_</a>
 * @author DavixDevelop
 */
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
public class InitRealEarthRegistryEvent<T> extends Event  {
    private static final HandlerList handlers = new HandlerList();

    @NonNull
    protected final EarthGeneratorSettings settings;

    @NonNull
    protected final RealWorldGenerator generator;

    @NonNull
    protected OrderedRegistry<T> registry;

    @Accessors(fluent = false)
    protected final Class<T> genericType = GenericMatcher.uncheckedFind(this.getClass(), InitRealEarthRegistryEvent.class, "T");

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    static HandlerList getHandlerList(){
        return handlers;
    }
}
