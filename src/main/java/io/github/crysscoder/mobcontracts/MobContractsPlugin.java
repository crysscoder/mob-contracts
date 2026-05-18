package io.github.crysscoder.mobcontracts;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MobContractsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Contract> contracts = new HashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadContracts();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("contracts")).setExecutor(this);
        Objects.requireNonNull(getCommand("contracts")).setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveContracts();
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();

        if (killer == null) {
            return;
        }

        Contract contract = contracts.get(killer.getUniqueId());

        if (contract == null || contract.type() != event.getEntityType()) {
            return;
        }

        Contract updated = new Contract(contract.type(), contract.target(), contract.progress() + 1);

        if (updated.progress() >= updated.target()) {
            contracts.remove(killer.getUniqueId());
            reward(killer);
            saveContracts();
            send(killer, "complete");
            return;
        }

        contracts.put(killer.getUniqueId(), updated);
        saveContracts();
        send(killer, "progress", Map.of("type", updated.type().name(), "progress", String.valueOf(updated.progress()), "target", String.valueOf(updated.target())));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "only-player");
            return true;
        }

        if (!player.hasPermission("mobcontracts.use")) {
            send(player, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            status(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            send(player, "list", Map.of("targets", targets().stream().map(Enum::name).collect(Collectors.joining(", "))));
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel")) {
            if (contracts.remove(player.getUniqueId()) == null) {
                send(player, "none");
                return true;
            }
            saveContracts();
            send(player, "cancelled");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("mobcontracts.reload")) {
                send(player, "no-permission");
                return true;
            }
            reloadConfig();
            send(player, "reloaded");
            return true;
        }

        if (args[0].equalsIgnoreCase("take") && args.length > 1) {
            take(player, args[1]);
            return true;
        }

        send(player, "list", Map.of("targets", targets().stream().map(Enum::name).collect(Collectors.joining(", "))));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("list", "take", "status", "cancel", "reload").stream().filter(item -> item.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("take")) {
            return targets().stream().map(type -> type.name().toLowerCase(Locale.ROOT)).filter(item -> item.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }

        return List.of();
    }

    private void take(Player player, String value) {
        if (contracts.containsKey(player.getUniqueId())) {
            send(player, "already");
            return;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            send(player, "bad-target");
            return;
        }

        if (!targets().contains(type)) {
            send(player, "bad-target");
            return;
        }

        int target = Math.max(1, getConfig().getInt("target-count", 20));
        contracts.put(player.getUniqueId(), new Contract(type, target, 0));
        saveContracts();
        send(player, "taken", Map.of("type", type.name(), "target", String.valueOf(target)));
    }

    private void status(Player player) {
        Contract contract = contracts.get(player.getUniqueId());

        if (contract == null) {
            send(player, "none");
            return;
        }

        send(player, "progress", Map.of("type", contract.type().name(), "progress", String.valueOf(contract.progress()), "target", String.valueOf(contract.target())));
    }

    private List<EntityType> targets() {
        List<EntityType> values = getConfig().getStringList("targets").stream()
            .map(this::entityType)
            .filter(java.util.Objects::nonNull)
            .toList();

        if (values.isEmpty()) {
            return List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER);
        }

        return values;
    }

    private void reward(Player player) {
        player.giveExp(Math.max(0, getConfig().getInt("reward-exp", 150)));
        int emeralds = Math.max(0, getConfig().getInt("reward-emeralds", 4));
        if (emeralds > 0) {
            player.getInventory().addItem(new ItemStack(Material.EMERALD, emeralds));
        }
    }

    private void loadContracts() {
        contracts.clear();
        if (getConfig().getConfigurationSection("data") == null) {
            return;
        }

        for (String key : getConfig().getConfigurationSection("data").getKeys(false)) {
            String path = "data." + key;
            try {
                EntityType type = entityType(getConfig().getString(path + ".type", ""));

                if (type != null) {
                    contracts.put(UUID.fromString(key), new Contract(type, Math.max(1, getConfig().getInt(path + ".target")), Math.max(0, getConfig().getInt(path + ".progress"))));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private EntityType entityType(String value) {
        try {
            EntityType type = EntityType.valueOf(value.toUpperCase(Locale.ROOT));
            return type.isAlive() ? type : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void saveContracts() {
        getConfig().set("data", null);
        for (Map.Entry<UUID, Contract> entry : contracts.entrySet()) {
            String path = "data." + entry.getKey();
            Contract contract = entry.getValue();
            getConfig().set(path + ".type", contract.type().name());
            getConfig().set(path + ".target", contract.target());
            getConfig().set(path + ".progress", contract.progress());
        }
        saveConfig();
    }

    private void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    private void send(CommandSender sender, String key, Map<String, String> values) {
        String prefix = getConfig().getString("messages.prefix", "&7[&aContracts&7]");
        String result = getConfig().getString("messages." + key, "").replace("%prefix%", prefix);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        sender.sendMessage(legacy.deserialize(result));
    }

    private record Contract(EntityType type, int target, int progress) {
    }
}
