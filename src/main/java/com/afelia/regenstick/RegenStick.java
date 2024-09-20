package com.afelia.regenstick;

import net.shortninja.test.Vector3D;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.attribute.Attribute;
import org.bukkit.util.Vector;
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
        if (command.getName().equalsIgnoreCase("gethealingwand")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }
            Player player = (Player) sender;

            // Check if player has permission to give regeneration sticks
            if (!player.hasPermission("healingwand.give")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            ItemStack stick = createRegenerationStick();
            if(!player.getInventory().contains(stick)) {
                player.getInventory().addItem(stick);
            }

            return true;
        } else if (command.getName().equalsIgnoreCase("givehealingwand")) {
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /givehealingwand <player>");
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
            if (!sender.hasPermission("healingwand.giveplayer")) {
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
        int healAmount = 4;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && item.getType() == Material.STICK && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && isHealingWand(item)) {
                if (checkCooldown(player)) {
                    player.sendMessage("Wand is on cooldown!");
                    return;
                }

                Player targetPlayer = getTargetPlayer(player);
                if (targetPlayer == null) {
                    // self heal with no target
                    targetPlayer = player;
                }

                double maxHealth = targetPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                double currentHealth = targetPlayer.getHealth();

                // don't use charges if the target is at max health

                if(currentHealth >= maxHealth) {
                    if(targetPlayer == player) {
                        player.sendMessage("You are already at full health!");
                        return;
                    }
                    player.sendMessage("Target player is already at full health!");
                    return;
                }

                if(targetPlayer != player) {
                    drawParticles(player, targetPlayer);
                }

                double newHealth = Math.min(maxHealth, currentHealth + healAmount); // Ensure health doesn't exceed max

                targetPlayer.setHealth(newHealth); // Regenerate 2 hearts (4 health points)


                updateLastUsedTime(player);

            }
        }
    }

    private void drawParticles(Player player, Player targetPlayer) {
        // Draw heart particles between caster and target
        Vector playerLocVector = player.getLocation().toVector();
        Vector targetLocVector = targetPlayer.getLocation().toVector();
        Vector betweenPandTVector = targetLocVector.clone().subtract(playerLocVector);
        Vector directionVector = betweenPandTVector.clone().normalize();

        for(int i = 0; i < betweenPandTVector.length(); i++) {
            Vector particlePoint = playerLocVector.clone().add(directionVector.clone().multiply(i));
            player.getWorld().spawnParticle(Particle.HEART,
                    particlePoint.getX(),
                    particlePoint.getY() + 1,
                    particlePoint.getZ(),
                    0, 0.001, 1, 0, 1,
                    new Particle.DustOptions(Color.RED, 1));
        }
    }

    private Player getTargetPlayer(Player player)
    {
        Player targetPlayer = null;
        Location playerPos = player.getEyeLocation();
        Vector3D playerDir = new Vector3D(playerPos.getDirection());
        Vector3D playerStart = new Vector3D(playerPos);
        Vector3D playerEnd = playerStart.add(playerDir.multiply(100));

        for(Player p : player.getWorld().getPlayers())
        {
            Vector3D targetPos = new Vector3D(p.getLocation());
            Vector3D minimum = targetPos.add(-0.5, 0, -0.5);
            Vector3D maximum = targetPos.add(0.5, 1.67, 0.5);

            if(p != player && hasIntersection(playerStart, playerEnd, minimum, maximum))
            {
                if(targetPlayer == null || targetPlayer.getLocation().distanceSquared(playerPos) > p.getLocation().distanceSquared(playerPos))
                {
                    targetPlayer = p;
                }
            }
        }

        return targetPlayer;
    }

    private boolean hasIntersection(Vector3D p1, Vector3D p2, Vector3D min, Vector3D max)
    {
        final double epsilon = 0.0001f;
        Vector3D d = p2.subtract(p1).multiply(0.5);
        Vector3D e = max.subtract(min).multiply(0.5);
        Vector3D c = p1.add(d).subtract(min.add(max).multiply(0.5));
        Vector3D ad = d.abs();

        if(Math.abs(c.x) > e.x + ad.x) return false;
        if(Math.abs(c.y) > e.y + ad.y) return false;
        if(Math.abs(c.z) > e.z + ad.z) return false;

        if(Math.abs(d.y * c.z - d.z * c.y) > e.y * ad.z + e.z * ad.y + epsilon) return false;
        if(Math.abs(d.z * c.x - d.x * c.z) > e.z * ad.x + e.x * ad.z + epsilon) return false;
        if(Math.abs(d.x * c.y - d.y * c.x) > e.x * ad.y + e.y * ad.x + epsilon) return false;

        return true;
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
