package com.afelia.regenstick;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegenStick extends JavaPlugin implements Listener {
    private int maxUses;
    private int cooldownSeconds;
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
        maxUses = config.getInt("max_uses", 10);
        cooldownSeconds = config.getInt("cooldown", 5);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("giveregenstick")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }
            Player player = (Player) sender;

            // Check if player has permission to give regeneration sticks
            if (!player.hasPermission("regenstick.give")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            int remainingUses = 0; // Initially set to zero uses
            ItemStack stick = createRegenerationStick(remainingUses);
            player.getInventory().addItem(stick);
            return true;
        } else if (command.getName().equalsIgnoreCase("giveplayerregenstick")) {
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /giveplayerregenstick <player>");
                return false;
            }

            // Check if the sender is the console
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(ChatColor.RED + "This command can only be executed from the console.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online.");
                return true;
            }

            // Check if console has permission to give regeneration sticks to other players
            if (!sender.hasPermission("regenstick.giveplayer")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            int remainingUses = 0; // Initially set to zero uses
            ItemStack stick = createRegenerationStick(remainingUses);
            target.getInventory().addItem(stick);
            sender.sendMessage(ChatColor.GREEN + "You have given a regeneration stick to " + target.getName() + ".");
            return true;
        }
        return false;
    }




    private ItemStack createRegenerationStick(int remainingUses) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        if (meta != null) {
            // Set display name with color codes
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "Regeneration Stick"));

            // Set lore with color codes
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', "Right-click to regenerate health over time!"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "Remaining Uses: " + remainingUses + "/" + maxUses));
            meta.setLore(lore);

            // Retrieve custom model data from config
            FileConfiguration config = getConfig();
            int customModelData = config.getInt("custom_model_data", 0); // Default to 0 if not specified in config
            meta.setCustomModelData(customModelData);

            stick.setItemMeta(meta);
        }
        return stick;
    }



    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && item.getType() == Material.STICK && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("Regeneration Stick")) {
                if (checkCooldown(player)) {
                    player.sendMessage("Stick is on cooldown!");
                    return;
                }
                int uses = getRemainingUses(meta);
                if (uses >= maxUses) {
                    player.sendMessage("You've used up all the charges on this stick!");
                    return;
                }

                double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                double currentHealth = player.getHealth();
                double newHealth = Math.min(maxHealth, currentHealth + 4); // Ensure health doesn't exceed max

                player.setHealth(newHealth); // Regenerate 2 hearts (4 health points)

                // Update the lore to reflect the remaining uses
                List<String> lore = meta.getLore();
                if (lore != null && !lore.isEmpty()) {
                    lore.set(1, "Remaining Uses: " + (uses + 1) + "/" + maxUses);
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                    updateLastUsedTime(player);
                }
            }
        }
    }


    private int getRemainingUses(ItemMeta meta) {
        List<String> lore = meta.getLore();
        if (lore != null && lore.size() >= 2) {
            String remainingUsesLine = lore.get(1);
            String[] parts = remainingUsesLine.split("/");
            if (parts.length == 2) {
                try {
                    return Integer.parseInt(parts[0].replaceAll("\\D+", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
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
