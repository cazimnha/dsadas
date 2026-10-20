package com.freakcircus.netherevent;

import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Bloqueia a viagem para o Nether quando nao ha evento ativo.
 * Portais para o Nether so funcionam enquanto o evento estiver aberto.
 */
public class PortalListener implements Listener {

    private final NetherEventPlugin plugin;

    public PortalListener(NetherEventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerPortalEvent.TeleportCause.NETHER_PORTAL) return;
        if (plugin.isNetherOpen()) return;

        // Destino seria o Nether? Cancela e avisa.
        if (isGoingToNether(event)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.msg("blocked"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (plugin.isNetherOpen()) return;
        if (event.getTo() != null && event.getTo().getWorld() != null
                && event.getTo().getWorld().getEnvironment() == World.Environment.NETHER) {
            event.setCancelled(true);
        }
    }

    private boolean isGoingToNether(PlayerPortalEvent event) {
        // Se ja temos destino, checa pelo ambiente do mundo destino.
        if (event.getTo() != null && event.getTo().getWorld() != null) {
            return event.getTo().getWorld().getEnvironment() == World.Environment.NETHER;
        }
        // Sem destino resolvido: se o jogador esta no Overworld usando portal do Nether,
        // o destino seria o Nether.
        return event.getPlayer().getWorld().getEnvironment() == World.Environment.NORMAL;
    }
}
