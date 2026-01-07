package de.btegermany.terraplusminus.events;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.NonNull;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

import static net.daporkchop.lib.common.util.PorkUtil.uncheckedCast;

/**
 * Based on:
 * <a href="https://github.com/BuildTheEarth/terraplusplus/blob/master/src/main/java/net/buildtheearth/terraplusplus/event/AbstractCustomRegistrationEvent.java">AbstractCustomRegistrationEvent.java @author DaPorkchop_</>
 *
 * @author DavixDevelop
 */
public class AbstractRealCustomRegistrationEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    protected final Map<String, Object> customData = new Object2ObjectOpenHashMap<>();

    public void registerData(@NonNull String key, @NonNull Object value) {
        this.customData.put(key, value);
    }

    public void removeData(@NonNull String key) {
        this.customData.remove(key);
    }

    public <T> T getData(@NonNull String key) {
        return uncheckedCast(this.customData.get(key));
    }

    public  Map<String, Object> getCustomData() {
        return this.customData.isEmpty() ? Collections.emptyMap() : ImmutableMap.copyOf(this.customData);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    static HandlerList getHandlerList(){
        return handlers;
    }
}
