package com.example;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class HardcoreReset extends JavaPlugin implements Listener {

    private static final int RESET_DELAY_TICKS = 20 * 3;
    private static final int COUNTDOWN_SECONDS = 60;
    private static final Set<Integer> BROADCAST_CHECKPOINTS = Set.of(60, 50, 40, 30, 20, 10, 5, 4, 3, 2, 1);
    private static final String RESET_FLAG_NAME = "reset.flag";

    private boolean resetScheduled;
    private Location deathLocation;
    private BukkitTask countdownTask;
    private final Set<UUID> resetCasualties = new HashSet<>();
    private final Map<UUID, String> pendingDeathMessages = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Hardcore reset plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        // Cancel all respawn tasks
        for (BukkitTask task : respawnTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        resetScheduled = false;
        countdownTask = null;
        deathLocation = null;
        resetCasualties.clear();
        pendingDeathMessages.clear();
        respawnTasks.clear();
        getLogger().info("Hardcore reset plugin disabled.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // Cancel any existing respawn task for this player
        cancelRespawnTask(playerId);

        if (!resetScheduled) {
            resetScheduled = true;
            deathLocation = cloneOrFallback(player.getLocation());
            resetCasualties.clear();
            pendingDeathMessages.clear();
            resetCasualties.add(playerId);

            String triggerMessage = ChatColor.RED + player.getName() + ChatColor.GRAY
                    + " died and triggered the hardcore reset!";
            event.setDeathMessage(triggerMessage);

            Bukkit.broadcastMessage(ChatColor.RED + player.getName() + ChatColor.GRAY
                    + " died! Server reset begins in 3 seconds.");

            Bukkit.getScheduler().runTaskLater(this, this::initiateResetSequence, RESET_DELAY_TICKS);
            scheduleAutoRespawn(player);
            return;
        }

        resetCasualties.add(playerId);
        if (pendingDeathMessages.containsKey(playerId)) {
            event.setDeathMessage(pendingDeathMessages.remove(playerId));
        } else {
            event.setDeathMessage(ChatColor.RED + player.getName() + ChatColor.GRAY
                    + " died during the hardcore reset.");
        }

        scheduleAutoRespawn(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!resetScheduled || deathLocation == null) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Cancel the auto-respawn task since player manually respawned
        cancelRespawnTask(playerId);

        event.setRespawnLocation(deathLocation);
        Bukkit.getScheduler().runTask(this, () -> prepareSpectator(player));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!resetScheduled || deathLocation == null) {
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            Player player = event.getPlayer();
            prepareSpectator(player);
            player.teleport(deathLocation);
            player.sendMessage(ChatColor.RED + "Server reset in progress. Please wait.");
        });
    }

    private void initiateResetSequence() {
        Location target = deathLocation != null ? deathLocation.clone() : fallbackLocation();

        for (Player player : Bukkit.getOnlinePlayers()) {
            killQuietly(player);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                prepareSpectator(player);
                player.teleport(target);
            }
            startCountdown();
        }, 20L);
    }

    private void startCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
        }

        countdownTask = new BukkitRunnable() {
            private int seconds = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (seconds <= 0) {
                    cancel();
                    countdownTask = null;
                    signalReset();
                    return;
                }

                if (BROADCAST_CHECKPOINTS.contains(seconds)) {
                    Bukkit.broadcastMessage(ChatColor.GOLD + "Server restarting in "
                            + ChatColor.RED + seconds + ChatColor.GOLD + " seconds.");
                }

                seconds--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void signalReset() {
        writeResetFlag();
        Bukkit.broadcastMessage(ChatColor.RED + "Restarting now...");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
    }

    private void writeResetFlag() {
        File flag = new File(getServer().getWorldContainer(), RESET_FLAG_NAME);
        if (flag.exists()) {
            return;
        }

        try {
            if (flag.createNewFile()) {
                getLogger().info("Reset flag created.");
            }
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Unable to create reset flag", ex);
        }
    }

    private void prepareSpectator(Player player) {
        if (!player.isOnline()) {
            return;
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        restoreHealth(player);
    }

    private void killQuietly(Player player) {
        if (!player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (resetCasualties.contains(playerId)) {
            return;
        }

        resetCasualties.add(playerId);
        pendingDeathMessages.put(playerId, ChatColor.RED + player.getName() + ChatColor.GRAY
                + " died during the hardcore reset.");

        if (player.getHealth() > 0.0D) {
            player.setHealth(0.0D);
        }
    }

    private void cancelRespawnTask(UUID playerId) {
        BukkitTask task = respawnTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void scheduleAutoRespawn(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Cancel any existing respawn task for this player
        cancelRespawnTask(playerId);
        
        BukkitTask task = new BukkitRunnable() {
            private int attempts;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    respawnTasks.remove(playerId);
                    cancel();
                    return;
                }

                if (!player.isDead()) {
                    if (deathLocation != null) {
                        player.teleport(deathLocation);
                    }
                    prepareSpectator(player);
                    respawnTasks.remove(playerId);
                    cancel();
                    return;
                }

                attempts++;
                try {
                    player.spigot().respawn();
                } catch (UnsupportedOperationException ignored) {
                    respawnTasks.remove(playerId);
                    cancel();
                    return;
                }

                if (attempts >= 10) {
                    player.sendMessage(ChatColor.RED + "Click Respawn to spectate the reset.");
                    respawnTasks.remove(playerId);
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 4L);
        
        respawnTasks.put(playerId, task);
    }

    private void restoreHealth(Player player) {
        if (!player.isOnline()) {
            return;
        }

        double maxHealth = 20.0D;
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
        }
        player.setHealth(Math.min(maxHealth, player.getMaxHealth()));
        player.setFoodLevel(20);
    }

    private Location cloneOrFallback(Location location) {
        if (location == null || location.getWorld() == null) {
            return fallbackLocation();
        }
        return location.clone();
    }

    private Location fallbackLocation() {
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return new Location(null, 0, 64, 0);
    }
}
