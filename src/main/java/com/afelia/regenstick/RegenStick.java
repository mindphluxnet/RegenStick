package com.afelia.regenstick;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.attribute.Attribute;
import org.jetbrains.annotations.NotNull;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegenStick extends JavaPlugin implements Listener {
    private int cooldownSeconds;
    private List<String> stickLore = new ArrayList<>();
    private String stickName;
    private Map<Player, Long> lastUsedTimeMap;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        loadConfig();
        lastUsedTimeMap = new HashMap<>();
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        cooldownSeconds = config.getInt("cooldown", 5);
        stickName = config.getString("stick_name");
        stickLore = config.getStringList("stick_lore");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("healingwand")) {

            if(args.length == 0) return false;

            if(args[0].equals("reload")) {
                if(sender instanceof ConsoleCommandSender) {
                    loadConfig();
                    Bukkit.getConsoleSender().sendMessage("Configuration reloaded.");
                }

                return true;
            }

            if(sender instanceof ConsoleCommandSender) {
                Bukkit.getConsoleSender().sendMessage("Only players have access to this command!");
                return false;
            }

            Player player = (Player) sender;

            if(args.length == 0) {
                return false;
            }

            if(args[0].equals("get")) {
                // Check if player has permission to give regeneration sticks
                if (!player.hasPermission("healingwand.get")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }

                ItemStack stick = createRegenerationStick();
                if (!player.getInventory().contains(stick)) {
                    player.getInventory().addItem(stick);
                }

                return true;
            }

            if(args[0].equals("give")) {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /healingwand give <player>");
                    return false;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Player not found or not online.");
                    return true;
                }

                // Check if console has permission to give regeneration sticks to other players
                if (!sender.hasPermission("healingwand.give")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }

                ItemStack stick = createRegenerationStick();
                if(!target.getInventory().contains(stick)) {
                    target.getInventory().addItem(stick);
                    sender.sendMessage(ChatColor.GREEN + "You have given a healing wand to " + target.getName() + ".");
                }
                return true;
            }

            return false;

        }
        return false;
    }

    private ItemStack createRegenerationStick() {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        NamespacedKey key = new NamespacedKey(this, "healingwand");

        if (meta != null) {
            // Set display name with color codes
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', stickName));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);

            // Set lore with color codes
            List<String> lore = new ArrayList<>();
            for (String s : stickLore) {
                lore.add(ChatColor.translateAlternateColorCodes('&', s));
            }

            meta.setLore(lore);

            // Retrieve custom model data from config
            FileConfiguration config = getConfig();
            int customModelData = config.getInt("custom_model_data", 0); // Default to 0 if not specified in config
            meta.setCustomModelData(customModelData);

            stick.setItemMeta(meta);
        }
        return stick;
    }

    private boolean isHealingWand(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(this, "healingwand");
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if(meta.getPersistentDataContainer().has(key)) {
            return container.get(key, PersistentDataType.BOOLEAN);
        };
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && item.getType() == Material.STICK && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && isHealingWand(item)) {
                if (checkCooldown(player)) {
                    player.sendMessage("Wand is on cooldown!");
                    return;
                }

                double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                double currentHealth = player.getHealth();
                double healAmount = maxHealth / 4;

                // don't use charges if the target is at max health

                if(currentHealth >= maxHealth) {
                    player.sendMessage("You are already at full health!");
                    return;
                }

                double newHealth = Math.min(maxHealth, currentHealth + healAmount); // Ensure health doesn't exceed max

                player.setHealth(newHealth);

                updateLastUsedTime(player);

            }
        }
    }

    private boolean checkCooldown(Player player) {
        if (!lastUsedTimeMap.containsKey(player)) {
            return false;
        }
        long lastUsedTime = lastUsedTimeMap.get(player);
        long currentTime = System.currentTimeMillis() / 1000; // Convert to seconds
        return (currentTime - lastUsedTime) < cooldownSeconds;
    }

    private void updateLastUsedTime(Player player) {
        lastUsedTimeMap.put(player, System.currentTimeMillis() / 1000); // Convert to seconds
    }
}
