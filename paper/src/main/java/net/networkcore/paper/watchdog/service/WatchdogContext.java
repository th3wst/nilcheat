package net.networkcore.paper.watchdog.service;

import org.bukkit.entity.Player;

public record WatchdogContext(
        String platform,
        int ping,
        String serverId
) {
    public static WatchdogContext from(Player player, WatchdogService service, String serverId) {
        return new WatchdogContext(service.platform(player), player.getPing(), serverId);
    }
}
