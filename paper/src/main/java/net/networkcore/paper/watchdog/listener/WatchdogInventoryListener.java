package net.networkcore.paper.watchdog.listener;

import net.networkcore.paper.watchdog.model.WatchdogCategory;
import net.networkcore.paper.watchdog.service.WatchdogService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WatchdogInventoryListener implements Listener {

    private final WatchdogService service;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> rapidStreak = new ConcurrentHashMap<>();

    public WatchdogInventoryListener(WatchdogService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.nanoTime();
        Long previous = lastClick.put(playerId, now);
        if (previous == null) {
            rapidStreak.put(playerId, 0);
            return;
        }
        double milliseconds = (now - previous) / 1_000_000.0;
        if (milliseconds < 30.0) {
            int streak = rapidStreak.merge(playerId, 1, Integer::sum);
            if (streak >= 5) {
                service.record(player, WatchdogCategory.INVENTORY, "rapid-gui-input", 1.5, "intervalMs=" + service.format(milliseconds) + ",streak=" + streak);
                rapidStreak.put(playerId, 0);
            }
            return;
        }
        rapidStreak.put(playerId, 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastClick.remove(playerId);
        rapidStreak.remove(playerId);
    }
}
