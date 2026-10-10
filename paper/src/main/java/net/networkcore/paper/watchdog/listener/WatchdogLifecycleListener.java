package net.networkcore.paper.watchdog.listener;

import net.networkcore.paper.NetworkCorePaperPlugin;
import net.networkcore.paper.watchdog.service.WatchdogService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

public final class WatchdogLifecycleListener implements Listener {

    private final NetworkCorePaperPlugin plugin;
    private final WatchdogService service;

    public WatchdogLifecycleListener(NetworkCorePaperPlugin plugin, WatchdogService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.profile(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clearTransientState(event.getPlayer());
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            service.close();
        }
    }
}
