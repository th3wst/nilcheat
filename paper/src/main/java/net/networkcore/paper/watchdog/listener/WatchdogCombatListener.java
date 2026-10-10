package net.networkcore.paper.watchdog.listener;

import net.networkcore.paper.watchdog.model.WatchdogCategory;
import net.networkcore.paper.watchdog.service.WatchdogService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WatchdogCombatListener implements Listener {

    private final WatchdogService service;
    private final Map<UUID, Long> lastAttack = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastTarget = new ConcurrentHashMap<>();

    public WatchdogCombatListener(WatchdogService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Entity target = event.getEntity();
        long now = System.nanoTime();
        UUID attackerId = attacker.getUniqueId();
        Long previousAttack = lastAttack.put(attackerId, now);
        if (previousAttack != null) {
            double milliseconds = (now - previousAttack) / 1_000_000.0;
            if (milliseconds < 35.0) {
                service.record(attacker, WatchdogCategory.COMBAT, "attack-rate", 0.75, "intervalMs=" + service.format(milliseconds));
            }
        }
        UUID previousTarget = lastTarget.put(attackerId, target.getUniqueId());
        if (previousTarget != null
                && !previousTarget.equals(target.getUniqueId())
                && previousAttack != null
                && (now - previousAttack) / 1_000_000.0 < 100.0) {
            service.record(attacker, WatchdogCategory.COMBAT, "target-switch", 1.0, "target=" + target.getUniqueId());
        }
        double reach = attacker.getEyeLocation().distance(target.getLocation());
        double threshold = service.isBedrock(attacker) ? 4.60 : 4.25;
        if (reach > threshold) {
            service.record(attacker, WatchdogCategory.COMBAT, "reach", 4.0, "distance=" + service.format(reach));
        }
    }
}
