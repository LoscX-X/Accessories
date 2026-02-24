package com.blanoir.accessory.utils;

import com.blanoir.accessory.traits.Absorb;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.BiConsumer;

public final class ShieldCurCommand implements CommandExecutor, TabCompleter {

    private final BiConsumer<Player, Double> addByValue;
    private final BiConsumer<Player, Double> addByPercent;
    private final String permissionNode;

    public ShieldCurCommand(Absorb absorb) {
        this(absorb::addShield, absorb::addShieldPercent, "accessory.shield");
    }

    public ShieldCurCommand(BiConsumer<Player, Double> addByValue,
                            BiConsumer<Player, Double> addByPercent,
                            String permissionNode) {
        this.addByValue = addByValue;
        this.addByPercent = addByPercent;
        this.permissionNode = permissionNode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission(permissionNode)) {
            sender.sendMessage(ChatColor.RED + "权限不足: " + permissionNode);
            return true;
        }

        if (args.length < 3) {
            usage(sender, label);
            return true;
        }

        String mode = args[0].toLowerCase();   // give / givep
        String target = args[1];               // me / player / all

        double val;
        try {
            val = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "参数错误: 数值格式无效 -> " + args[2]);
            return true;
        }

        List<Player> targets = resolveTargets(sender, target);
        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "目标不存在或不在线: " + target);
            return true;
        }

        int affected = 0;
        for (Player p : targets) {
            switch (mode) {
                case "give" -> {          // 按数量恢复/扣除
                    addByValue.accept(p, val);
                    affected++;
                }
                case "givep" -> {         // 按最大护盾比例恢复/扣除
                    double ratio = val / 100.0; // 40 -> 0.40
                    addByPercent.accept(p, ratio);
                    affected++;
                }
                default -> {
                    sender.sendMessage(ChatColor.RED + "参数错误: 未知模式 " + mode + " (可用: give, givep)");
                    usage(sender, label);
                    return true;
                }
            }
        }

        sender.sendMessage(ChatColor.GREEN + "执行成功: 已对 " + affected + " 名玩家应用操作。模式=" + mode + ", 目标=" + target + ", 数值=" + val);
        return true;
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "命令格式:");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " give  <me|玩家|all> <数值>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " givep <me|玩家|all> <百分比>");
    }

    private List<Player> resolveTargets(CommandSender sender, String target) {
        target = target.toLowerCase(Locale.ROOT);

        if (target.equals("all")) return new ArrayList<>(Bukkit.getOnlinePlayers());

        if (target.equals("me")) {
            if (sender instanceof Player p) return List.of(p);
            return List.of();
        }

        Player p = Bukkit.getPlayerExact(target);
        return p != null ? List.of(p) : List.of();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(permissionNode)) return List.of();

        if (args.length == 1) return filter(args[0], List.of("give", "givep"));
        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            list.add("me");
            list.add("all");
            for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
            return filter(args[1], list);
        }
        if (args.length == 3) return filter(args[2], List.of("10", "-10", "40", "-40"));
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        String in = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : options) if (s.toLowerCase(Locale.ROOT).startsWith(in)) out.add(s);
        return out;
    }
}
