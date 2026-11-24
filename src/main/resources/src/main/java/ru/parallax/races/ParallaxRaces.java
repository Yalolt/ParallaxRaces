package ru.parallax.races;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ParallaxRaces extends JavaPlugin implements Listener, TabCompleter {

    private final Random random = new Random();
    private final Set<Material> plantFoods = new HashSet<>(Arrays.asList(
            Material.APPLE, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
            Material.BREAD, Material.CARROT, Material.GOLDEN_CARROT,
            Material.POTATO, Material.BAKED_POTATO, Material.POISONOUS_POTATO,
            Material.BEETROOT, Material.BEETROOT_SOUP, Material.MELON_SLICE,
            Material.SWEET_BERRIES, Material.GLOW_BERRIES, Material.DRIED_KELP,
            Material.PUMPKIN_PIE, Material.COOKIE, Material.MUSHROOM_STEW,
            Material.SUSPICIOUS_STEW, Material.CHORUS_FRUIT
    ));

    private final Set<Material> rawMeats = new HashSet<>(Arrays.asList(
            Material.BEEF, Material.PORKCHOP, Material.CHICKEN,
            Material.MUTTON, Material.RABBIT, Material.COD,
            Material.SALMON, Material.TROPICAL_FISH, Material.SPIDER_EYE
    ));

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("race") != null) {
            getCommand("race").setExecutor(this);
            getCommand("race").setTabCompleter(this);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPlayerRace(player);
        }
        getLogger().info("ParallaxRaces 1.21.11 запущен! (made by yalolt)");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> applyPlayerRace(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> applyPlayerRace(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();

        if (type == Material.MILK_BUCKET) {
            Bukkit.getScheduler().runTaskLater(this, () -> applyEffectsOnly(player), 2L);
            return;
        }

        String raceId = getConfig().getString("players." + player.getUniqueId());
        if (raceId == null) return;

        if (getConfig().getBoolean("races." + raceId + ".carnivore", false)) {
            if (plantFoods.contains(type)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 0));
                player.sendMessage(ChatColor.DARK_GREEN + "Ваш организм не переваривает растительную пищу...");
            } else if (rawMeats.contains(type)) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    player.removePotionEffect(PotionEffectType.HUNGER);
                    player.removePotionEffect(PotionEffectType.POISON);
                    player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onHungerChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (event.getFoodLevel() < player.getFoodLevel()) {
            String raceId = getConfig().getString("players." + player.getUniqueId());
            if (raceId != null && getConfig().getBoolean("races." + raceId + ".low-hunger", false)) {
                if (random.nextBoolean()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    public void resetPlayer(Player player) {
        setAttrByKey(player, "scale", 1.0);
        setAttrByKey(player, "max_health", 20.0);
        setAttrByKey(player, "movement_speed", 0.1);
        setAttrByKey(player, "jump_strength", 0.42);
        setAttrByKey(player, "safe_fall_distance", 3.0);
        setAttrByKey(player, "attack_damage", 1.0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.isInfinite()) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    public void applyPlayerRace(Player player) {
        try {
            resetPlayer(player);
            String raceId = getConfig().getString("players." + player.getUniqueId());
            if (raceId == null || !getConfig().contains("races." + raceId)) return;

            String path = "races." + raceId;
            ConfigurationSection attrSec = getConfig().getConfigurationSection(path + ".attributes");
            if (attrSec != null) {
                if (attrSec.contains("scale")) setAttrByKey(player, "scale", attrSec.getDouble("scale"));
                if (attrSec.contains("max-health")) setAttrByKey(player, "max_health", attrSec.getDouble("max-health"));
                if (attrSec.contains("movement-speed")) setAttrByKey(player, "movement_speed", attrSec.getDouble("movement-speed"));
                if (attrSec.contains("jump-strength")) setAttrByKey(player, "jump_strength", attrSec.getDouble("jump-strength"));
                if (attrSec.contains("safe-fall-distance")) setAttrByKey(player, "safe_fall_distance", attrSec.getDouble("safe-fall-distance"));
                if (attrSec.contains("attack-damage")) setAttrByKey(player, "attack_damage", attrSec.getDouble("attack-damage"));
            }

            applyEffectsOnly(player);
        } catch (Exception e) {
            getLogger().warning("Ошибка применения расы: " + e.getMessage());
        }
    }

    private void applyEffectsOnly(Player player) {
        String raceId = getConfig().getString("players." + player.getUniqueId());
        if (raceId == null || !getConfig().contains("races." + raceId)) return;

        List<String> effects = getConfig().getStringList("races." + raceId + ".effects");
        for (String entry : effects) {
            List<String> parts = Arrays.asList(entry.split(":"));
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(parts.get(0).toLowerCase()));
            int amp = parts.size() > 1 ? Integer.parseInt(parts.get(1)) : 0;
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amp, false, false, false));
            }
        }
    }

    private void setAttrByKey(Player player, String key, double value) {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (attr != null) {
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null) {
                instance.setBaseValue(value);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String... args) {
        List<String> argList = Arrays.asList(args);
        if (argList.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "ParallaxRaces v1.0.0 " + ChatColor.GRAY + "(made by yalolt)");
            sender.sendMessage(ChatColor.RED + "Использование: /race <set|remove|list|reload>");
            return true;
        }

        switch (argList.get(0).toLowerCase()) {
            case "reload":
                reloadConfig();
                for (Player p : Bukkit.getOnlinePlayers()) applyPlayerRace(p);
                sender.sendMessage(ChatColor.GREEN + "Конфиг ParallaxRaces перезагружен!");
                return true;

            case "list":
                ConfigurationSection races = getConfig().getConfigurationSection("races");
                if (races == null) {
                    sender.sendMessage(ChatColor.YELLOW + "Расы не настроены.");
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "Доступные расы: " + ChatColor.WHITE + String.join(", ", races.getKeys(false)));
                return true;

            case "set":
                if (argList.size() < 3) {
                    sender.sendMessage(ChatColor.RED + "Использование: /race set <игрок> <раса>");
                    return true;
                }
                Player target = Bukkit.getPlayer(argList.get(1));
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Игрок не в сети.");
                    return true;
                }
                String raceId = argList.get(2).toLowerCase();
                if (!getConfig().contains("races." + raceId)) {
                    sender.sendMessage(ChatColor.RED + "Раса '" + raceId + "' не найдена в config.yml!");
                    return true;
                }
                getConfig().set("players." + target.getUniqueId(), raceId);
                saveConfig();
                applyPlayerRace(target);
                String name = getConfig().getString("races." + raceId + ".display-name", raceId);
                sender.sendMessage(ChatColor.GREEN + "Игроку " + target.getName() + " выдана особенность: " + name);
return true;
case "remove":
if (argList.size() < 2) {
sender.sendMessage(ChatColor.RED + "Использование: /race remove <игрок>");
return true;
}
Player remTarget = Bukkit.getPlayer(argList.get(1));
if (remTarget == null) {
sender.sendMessage(ChatColor.RED + "Игрок не в сети.");
return true;
}
getConfig().set("players." + remTarget.getUniqueId(), null);
saveConfig();
resetPlayer(remTarget);
sender.sendMessage(ChatColor.YELLOW + "Особенности игрока " + remTarget.getName() + " сброшены.");
return true;
}
return true;
}
@Override
public List onTabComplete(CommandSender sender, Command command, String alias, String... args) {
List argList = Arrays.asList(args);
if (argList.size() == 1) return Arrays.asList("set", "remove", "list", "reload");
if (argList.size() == 2 && (argList.get(0).equalsIgnoreCase("set") || argList.get(0).equalsIgnoreCase("remove"))) {
List names = new ArrayList<>();
for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
return names;
}
if (argList.size() == 3 && argList.get(0).equalsIgnoreCase("set")) {
ConfigurationSection races = getConfig().getConfigurationSection("races");
if (races != null) return new ArrayList<>(races.getKeys(false));
}
return new ArrayList<>();
}
}
