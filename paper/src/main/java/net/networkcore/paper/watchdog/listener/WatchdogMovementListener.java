package net.networkcore.paper.watchdog.listener;

import net.networkcore.paper.watchdog.model.WatchdogCategory;
import net.networkcore.paper.watchdog.service.WatchdogService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

public final class WatchdogMovementListener implements Listener {

    private final WatchdogService service;

    public WatchdogMovementListener(WatchdogService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        service.grantTeleportGrace(event.getPlayer(), 1500L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        service.grantVelocityGrace(event.getPlayer(), 1200L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            return;
        }
        Player player = event.getPlayer();
        if (ignore(player) || service.hasMovementGrace(player)) {
            return;
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double vertical = to.getY() - from.getY();
        if (horizontal > 1.45) {
            service.record(player, WatchdogCategory.MOVEMENT, "horizontal-speed", 2.5, "distance=" + service.format(horizontal));
        }
        if (vertical > 1.05 && !player.isInsideVehicle()) {
            service.record(player, WatchdogCategory.MOVEMENT, "vertical-motion", 2.0, "deltaY=" + service.format(vertical));
        }
    }

    private boolean ignore(Player player) {
        GameMode gameMode = player.getGameMode();
        return gameMode == GameMode.CREATIVE
                || gameMode == GameMode.SPECTATOR
                || player.getAllowFlight()
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isGliding()
                || player.isSwimming()
                || player.isRiptiding()
                || player.isClimbing();
    }
}
