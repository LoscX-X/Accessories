package com.blanoir.accessory.command;

import com.blanoir.accessory.traits.Absorb;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import static com.blanoir.accessory.inventory.InvReload.getStrings;

public final class ShieldCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final BiConsumer<Player, Double> addByValue;
    private final BiConsumer<Player, Double> addByPercent;
    private final String permissionNode;

    public ShieldCommand(Absorb absorb) {
        this(absorb::addShield, absorb::addShieldPercent, "accessory.shield");
    }

    public ShieldCommand(BiConsumer<Player, Double> addByValue,
                         BiConsumer<Player, Double> addByPercent,
                         String permissionNode) {
        this.addByValue = addByValue;
        this.addByPercent = addByPercent;
        this.permissionNode = permissionNode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission(permissionNode)) {
            sender.sendMessage(MM.deserialize("<red>权限不足: " + permissionNode + "</red>"));
            return true;
        }

        if (args.length < 3) {
            usage(sender, label);
            return true;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        String target = args[1];

        double val;
        try {
            val = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MM.deserialize("<red>参数错误: 数值格式无效 -> " + args[2] + "</red>"));
            return true;
        }

        List<Player> targets = resolveTargets(sender, target);
        if (targets.isEmpty()) {
            sender.sendMessage(MM.deserialize("<red>目标不存在或不在线: " + target + "</red>"));
            return true;
        }

        int affected = 0;
        for (Player p : targets) {
            switch (mode) {
                case "give" -> {
                    addByValue.accept(p, val);
                    affected++;
                }
                case "givep" -> {
                    double ratio = val / 100.0;
                    addByPercent.accept(p, ratio);
                    affected++;
                }
                default -> {
                    sender.sendMessage(MM.deserialize("<red>参数错误: 未知模式 " + mode + " (可用: give, givep)</red>"));
                    usage(sender, label);
                    return true;
                }
            }
        }

        sender.sendMessage(MM.deserialize(
                "<green>执行成功: 已对 " + affected + " 名玩家应用操作。模式=" + mode + ", 目标=" + target + ", 数值=" + val + "</green>"
        ));
        return true;
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(MM.deserialize("<yellow>命令格式:</yellow>"));
        sender.sendMessage(MM.deserialize("<gray>/" + label + " give  &lt;me|玩家|all&gt; &lt;数值&gt;</gray>"));
        sender.sendMessage(MM.deserialize("<gray>/" + label + " givep &lt;me|玩家|all&gt; &lt;百分比&gt;</gray>"));
    }

    private List<Player> resolveTargets(CommandSender sender, String target) {
        target = target.toLowerCase(Locale.ROOT);

        if (target.equals("all")) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }

        if (target.equals("me")) {
            if (sender instanceof Player p) {
                return List.of(p);
            }
            return List.of();
        }

        Player p = Bukkit.getPlayerExact(target);
        return p != null ? List.of(p) : List.of();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        if (!sender.hasPermission(permissionNode)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(args[0], List.of("give", "givep"));
        }

        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            list.add("me");
            list.add("all");
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            return filter(args[1], list);
        }

        if (args.length == 3) {
            return filter(args[2], List.of("10", "-10", "40", "-40"));
        }

        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        return getStrings(input, options);
    }
}