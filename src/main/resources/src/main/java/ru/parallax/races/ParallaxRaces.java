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
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParallaxRaces extends JavaPlugin implements Listener, TabCompleter {

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
        getLogger().info("ParallaxRaces 1.21.11 успешно запущен! (made by yalolt)");
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
    public void onMilkDrink(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            Bukkit.getScheduler().runTaskLater(this, () -> applyEffectsOnly(event.getPlayer()), 2L);
        }
    }

    public void resetPlayer(Player player) {
        setAttr(player, Attribute.SCALE, 1.0);
        setAttr(player, Attribute.MAX_HEALTH, 20.0);
        setAttr(player, Attribute.MOVEMENT_SPEED, 0.1);
        setAttr(player, Attribute.JUMP_STRENGTH, 0.42);
        setAttr(player, Attribute.SAFE_FALL_DISTANCE, 3.0);
        setAttr(player, Attribute.ATTACK_DAMAGE, 1.0);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.isInfinite()) {
                player.removePotionEffect(effect.getType());
            }
        }
    }

    public void applyPlayerRace(Player player) {
        resetPlayer(player);
        String raceId = getConfig().getString("players." + player.getUniqueId());
        if (raceId == null || !getConfig().contains("races." + raceId)) return;

        String path = "races." + raceId;
        ConfigurationSection attrSec = getConfig().getConfigurationSection(path + ".attributes");
        if (attrSec != null) {
            if (attrSec.contains("scale")) setAttr(player, Attribute.SCALE, attrSec.getDouble("scale"));
            if (attrSec.contains("max-health")) setAttr(player, Attribute.MAX_HEALTH, attrSec.getDouble("max-health"));
            if (attrSec.contains("movement-speed")) setAttr(player, Attribute.MOVEMENT_SPEED, attrSec.getDouble("movement-speed"));
            if (attrSec.contains("jump-strength")) setAttr(player, Attribute.JUMP_STRENGTH, attrSec.getDouble("jump-strength"));
            if (attrSec.contains("safe-fall-distance")) setAttr(player, Attribute.SAFE_FALL_DISTANCE, attrSec.getDouble("safe-fall-distance"));
            if (attrSec.contains("attack-damage")) setAttr(player, Attribute.ATTACK_DAMAGE, attrSec.getDouble("attack-damage"));
        }

        applyEffectsOnly(player);
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

    private void setAttr(Player player, Attribute attr, double value) {
        AttributeInstance instance = player.getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(value);
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
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String... args) {
        List<String> argList = Arrays.asList(args);
        if (argList.size() == 1) return Arrays.asList("set", "remove", "list", "reload");
        if (argList.size() == 2 && (argList.get(0).equalsIgnoreCase("set") || argList.get(0).equalsIgnoreCase("remove"))) {
            List<String> names = new ArrayList<>();
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
