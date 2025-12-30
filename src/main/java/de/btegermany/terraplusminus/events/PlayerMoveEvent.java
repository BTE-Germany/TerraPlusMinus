package de.btegermany.terraplusminus.events;

import de.btegermany.terraplusminus.utils.ConfigurationHelper;
import de.btegermany.terraplusminus.utils.LinkedWorld;
import de.btegermany.terraplusminus.utils.Properties;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class PlayerMoveEvent implements Listener {
    final int yOffsetConfigEntry;

    private final int xOffset;
    private final int zOffset;
    private final boolean linkedWorldsEnabled;

    private final String linkedWorldsMethod;
    private final Plugin plugin;
    private final HashMap<String, Integer> worldHashMap;
    private static final long TELEPORT_COOLDOWN_MS = 5000; // 5 seconds
    private final ConcurrentHashMap<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();

    public PlayerMoveEvent(@org.jspecify.annotations.NonNull Plugin plugin) {
        this.plugin = plugin;
        xOffset = plugin.getConfig().getInt(Properties.X_OFFSET, 0);
        yOffsetConfigEntry = plugin.getConfig().getInt(Properties.Y_OFFSET, 0);
        zOffset = plugin.getConfig().getInt(Properties.Z_OFFSET, 0);
        linkedWorldsEnabled = plugin.getConfig().getBoolean(Properties.LINKED_WORLDS_ENABLED);
        linkedWorldsMethod = plugin.getConfig().getString(Properties.LINKED_WORLDS_METHOD);
        worldHashMap = new HashMap<>();
        if (linkedWorldsEnabled && linkedWorldsMethod != null
                && linkedWorldsMethod.equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV)) {
            List<LinkedWorld> worlds = ConfigurationHelper.getWorlds();
            for (LinkedWorld world : worlds) {
                this.worldHashMap.put(world.getWorldName(), world.getOffset());
            }
            plugin.getComponentLogger().info("Linked worlds enabled, using Multiverse method.");
        }
        if (plugin.getConfig().getBoolean(Properties.ACTIONBAR_HEIGHT)) startKeepActionBarAlive();
    }

    @EventHandler
    void onPlayerMove(org.bukkit.event.player.@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean(Properties.ACTIONBAR_HEIGHT)) setHeightInActionBar(player);
    }

    private void startKeepActionBarAlive() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                setHeightInActionBar(p);
            }
        }, 0, 20);
    }

    private void setHeightInActionBar(@NotNull Player p) {
        worldHashMap.putIfAbsent(p.getWorld().getName(), yOffsetConfigEntry);
        if (p.getInventory().getItemInMainHand().getType() != Material.DEBUG_STICK) {
            int height = p.getLocation().getBlockY() - worldHashMap.get(p.getWorld().getName());
            p.sendActionBar(Component.text(height + "m").decorate(TextDecoration.BOLD));
        }
    }

    @EventHandler
    void onPlayerFall(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!this.linkedWorldsEnabled && !this.linkedWorldsMethod.equalsIgnoreCase(Properties.NonConfigurable.METHOD_MV)) {
            return;
        }

        Player p = event.getPlayer();

        // Prevent repeated scheduling while on cooldown
        if (isOnTeleportCooldown(p)) {
            return;
        }

        World world = p.getWorld();
        Location location = p.getLocation();

        // Verzögerte Teleportation
        new BukkitRunnable() {
            @Override
            public void run() {
                // Prevent repeated scheduling while on cooldown
                if (isOnTeleportCooldown(p)) {
                    return;
                }
                // Teleport player from world to world
                if (p.getLocation().getY() < world.getMinHeight()) {
                    LinkedWorld previousServer = ConfigurationHelper.getPreviousServerName(world.getName());
                    if (previousServer != null) {
                        teleportPlayer(previousServer, location, p, true);
                    }
                } else if (p.getLocation().getY() > world.getMaxHeight()) {
                    LinkedWorld nextServer = ConfigurationHelper.getNextServerName(world.getName());
                    if (nextServer != null) {
                        teleportPlayer(nextServer, location, p, false);
                    }
                }
            }
        }.runTaskLater(plugin, 60L);
    }

    private void teleportPlayer(@NotNull LinkedWorld linkedWorld, @NotNull Location location, Player p, boolean maxY) {
        setTeleportCooldown(p);

        World tpWorld = Bukkit.getWorld(linkedWorld.getWorldName());
        int height = maxY ? Objects.requireNonNull(tpWorld).getMaxHeight() : Objects.requireNonNull(tpWorld).getMinHeight();
        Location newLocation = new Location(tpWorld, location.getX() + xOffset, height, location.getZ() + zOffset, location.getYaw(), location.getPitch());
        p.teleportAsync(newLocation);
        if (p.getAllowFlight()) p.setFlying(true);
        p.sendMessage(plugin.getConfig().getString(Properties.CHAT_PREFIX) + "§7You have been teleported to another world.");
    }

    private boolean isOnTeleportCooldown(@NonNull Player player) {
        long now = System.currentTimeMillis();
        Long lastTeleport = teleportCooldowns.get(player.getUniqueId());

        if (lastTeleport == null) {
            return false;
        }

        return (now - lastTeleport) < TELEPORT_COOLDOWN_MS;
    }

    private void setTeleportCooldown(@NonNull Player player) {
        teleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

}
