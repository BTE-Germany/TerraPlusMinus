package de.btegermany.terraplusminus.events;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorSettings;
import net.buildtheearth.terraminusminus.generator.GeneratorDatasets;
import org.bukkit.event.Event;

/**
 * Fired on {@link org.bukkit.plugin.PluginManager#callEvent(Event)} when a new instance of {@link GeneratorDatasets} is being constructed.
 * <p>
 * Allows custom datasets to be registered.
 * <p>
 * Based on:
 * <a href="https://github.com/BuildTheEarth/terraplusplus/blob/master/src/main/java/net/buildtheearth/terraplusplus/event/InitDatasetsEvent.java#L19">InitDatasetsEvent.java @author DaPorkchop_</a>
 * @author DavixDevelop
 */
@RequiredArgsConstructor
@Getter
public class InitRealDatasetsEvent extends AbstractRealCustomRegistrationEvent {
    @NonNull
    protected final EarthGeneratorSettings settings;
}
