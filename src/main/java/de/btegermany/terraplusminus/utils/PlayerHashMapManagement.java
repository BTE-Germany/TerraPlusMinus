package de.btegermany.terraplusminus.utils;

import org.bukkit.entity.Player;

import java.util.HashMap;

public class PlayerHashMapManagement {

    HashMap<Player, String> players;

    public PlayerHashMapManagement() {
        players = new HashMap<>();
    }

    public void addPlayer(Player player, String coordinates) {
        players.put(player, coordinates);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public boolean containsPlayer(Player player) {
        return players.containsKey(player);
    }

    public String getCoordinates(Player player) {
        return players.get(player);
    }

}
