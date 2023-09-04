package de.btegermany.terraplusminus.utils;

import de.btegermany.terraplusminus.Terraplusminus;

import java.util.List;

public class PluginMessageUtil {

    private static List<String> getList() {
        return (List<String>) Terraplusminus.config.getList("linked_servers.servers");
    }

    public static String getNextServerName() {
        List<String> servers = getList();
        int index = servers.indexOf("current_server");
        String servername = servers.get(index + 1);
        if (servername.equals("another_server")) {
            return null;
        } else {
            return servername;
        }
    }

    public static String getLastServerName() {
        List<String> servers = getList();
        int index = servers.indexOf("current_server");
        String servername = servers.get(index - 1);
        if (servername.equals("another_server")) {
            return null;
        } else {
            return servername;
        }
    }

}
