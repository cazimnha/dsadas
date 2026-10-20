package com.freakcircus.netherevent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

public class NetherEventPlugin extends JavaPlugin {

    // Estado do evento
    private boolean netherOpen = false;
    private long eventEndMillis = 0L;   // quando o evento atual termina
    private boolean warned = false;     // ja avisamos que vai fechar?

    private BukkitTask tickTask;        // verifica fim do evento a cada segundo
    private BukkitTask autoTask;        // dispara eventos automaticos

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);

        // Tarefa que roda a cada segundo (20 ticks) para controlar o fim do evento
        tickTask = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);

        setupAutoEvent();
        getLogger().info("NetherEvent habilitado. Nether comeca FECHADO.");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (autoTask != null) autoTask.cancel();
    }

    // ===================== Estado =====================

    public boolean isNetherOpen() {
        return netherOpen;
    }

    public void startEvent(int minutes) {
        this.netherOpen = true;
        this.warned = false;
        this.eventEndMillis = System.currentTimeMillis() + (long) minutes * 60_000L;
        broadcast(msg("event-start"));
    }

    public void stopEvent() {
        this.netherOpen = false;
        this.eventEndMillis = 0L;
        this.warned = false;
        kickEveryoneFromNether();
        broadcast(msg("event-end"));
    }

    public long getMillisLeft() {
        if (!netherOpen) return 0L;
        return Math.max(0L, eventEndMillis - System.currentTimeMillis());
    }

    // ===================== Loop principal =====================

    private void tick() {
        if (!netherOpen) return;

        long left = getMillisLeft();
        int warnBefore = getConfig().getInt("warn-before-end-minutes", 1);

        // Aviso antes de fechar
        if (!warned && warnBefore > 0 && left <= (long) warnBefore * 60_000L) {
            warned = true;
            broadcast(msg("event-warn").replace("%minutes%", String.valueOf(warnBefore)));
        }

        // Acabou o tempo
        if (left <= 0L) {
            stopEvent();
        }
    }

    // Teleporta todo mundo que esta no Nether de volta para o spawn do Overworld
    private void kickEveryoneFromNether() {
        World overworld = getOverworld();
        if (overworld == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
                p.teleport(overworld.getSpawnLocation());
                p.sendMessage(msg("event-end"));
            }
        }
    }

    public World getOverworld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    // ===================== Evento automatico =====================

    private void setupAutoEvent() {
        if (!getConfig().getBoolean("auto-event.enabled", false)) return;

        long intervalTicks = Math.max(1L, getConfig().getLong("auto-event.interval-minutes", 120)) * 60L * 20L;
        autoTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (netherOpen) return; // ja tem evento rolando
            double chance = getConfig().getDouble("auto-event.chance", 0.5);
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                int dur = getConfig().getInt("auto-event.duration-minutes", 15);
                startEvent(dur);
            }
        }, intervalTicks, intervalTicks);
    }

    // ===================== Utilidades de mensagem =====================

    public String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + body);
    }

    public String msgNoPrefix(String key) {
        String body = getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', body);
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    // ===================== Comando /nether =====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("nether")) return false;

        if (!sender.hasPermission("netherevent.admin")) {
            sender.sendMessage(ChatColor.RED + "Voce nao tem permissao para isso.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Use: /nether start [minutos] | /nether stop | /nether status");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                int minutes = getConfig().getInt("default-duration-minutes", 15);
                if (args.length >= 2) {
                    try {
                        minutes = Integer.parseInt(args[1]);
                        if (minutes <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Numero de minutos invalido.");
                        return true;
                    }
                }
                startEvent(minutes);
                sender.sendMessage(ChatColor.GREEN + "Nether aberto por " + minutes + " minuto(s).");
            }
            case "stop" -> {
                if (!netherOpen) {
                    sender.sendMessage(ChatColor.YELLOW + "O Nether ja esta fechado.");
                    return true;
                }
                stopEvent();
                sender.sendMessage(ChatColor.GREEN + "Nether fechado.");
            }
            case "status" -> {
                if (!netherOpen) {
                    sender.sendMessage(ChatColor.YELLOW + "O Nether esta FECHADO.");
                } else {
                    long secs = getMillisLeft() / 1000L;
                    sender.sendMessage(ChatColor.GREEN + "O Nether esta ABERTO. Fecha em "
                            + (secs / 60) + "m" + (secs % 60) + "s.");
                }
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Use: /nether start [minutos] | /nether stop | /nether status");
        }
        return true;
    }
}
