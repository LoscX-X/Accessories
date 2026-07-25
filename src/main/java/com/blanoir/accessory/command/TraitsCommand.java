package com.blanoir.accessory.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public final class TraitsCommand implements BasicCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private BiConsumer<Player, Double> addByValue;
    private BiConsumer<Player, Double> addByPercent;
    private final String permissionNode;
    private final String commandName;

    public TraitsCommand(String permissionNode, String commandName) {
        this.permissionNode = permissionNode;
        this.commandName = commandName;
    }

    public void configure(BiConsumer<Player, Double> addByValue,
                          BiConsumer<Player, Double> addByPercent) {
        this.addByValue = addByValue;
        this.addByPercent = addByPercent;
    }

    @Override
    public void execute(@NotNull CommandSourceStack commandSource, @NotNull String[] args) {
        CommandSender sender = commandSource.getSender();

        if (!sender.hasPermission(permissionNode)) {
            sender.sendMessage(MM.deserialize("<red>权限不足: " + permissionNode + "</red>"));
            return;
        }

        if (addByValue == null || addByPercent == null) {
            sender.sendMessage(MM.deserialize("<red>该护盾功能当前不可用（需要 AuraSkills）。</red>"));
            return;
        }

        if (args.length < 3) {
            usage(sender);
            return;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        String targetArg = args[1];

        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(MM.deserialize("<red>参数错误: 数值格式无效 -> " + args[2] + "</red>"));
            return;
        }

        List<Player> targets = resolveTargets(sender, targetArg);
        if (targets.isEmpty()) {
            sender.sendMessage(MM.deserialize("<red>目标不存在或不在线: " + targetArg + "</red>"));
            return;
        }

        int affected = 0;

        switch (mode) {
            case "give" -> {
                for (Player target : targets) {
                    addByValue.accept(target, value);
                    affected++;
                }
            }
            case "givep" -> {
                double ratio = value / 100.0;
                for (Player target : targets) {
                    addByPercent.accept(target, ratio);
                    affected++;
                }
            }
            default -> {
                sender.sendMessage(MM.deserialize("<red>参数错误: 未知模式 " + mode + " (可用: give, givep)</red>"));
                usage(sender);
                return;
            }
        }

        sender.sendMessage(MM.deserialize(
                "<green>执行成功: 已对 " + affected
                        + " 名玩家应用操作。模式=" + mode
                        + ", 目标=" + targetArg
                        + ", 数值=" + value
                        + "</green>"
        ));

    }

    private void usage(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<yellow>命令格式:</yellow>"));
        sender.sendMessage(MM.deserialize("<gray>/" + commandName + " give &lt;me|玩家|all&gt; &lt;数值&gt;</gray>"));
        sender.sendMessage(MM.deserialize("<gray>/" + commandName + " givep &lt;me|玩家|all&gt; &lt;百分比&gt;</gray>"));
    }

    private List<Player> resolveTargets(CommandSender sender, String targetArg) {
        String normalized = targetArg.toLowerCase(Locale.ROOT);

        if ("all".equals(normalized)) {
            return new ArrayList<>(Bukkit.getOnlinePlayers());
        }

        if ("me".equals(normalized)) {
            return sender instanceof Player player ? List.of(player) : List.of();
        }

        // 注意：这里不能用 lower-case 后的 normalized。
        // 玩家名大小写可能影响 getPlayerExact。
        Player player = Bukkit.getPlayerExact(targetArg);
        return player != null ? List.of(player) : List.of();
    }

    @Override
    public List<String> suggest(@NotNull CommandSourceStack commandSource, @NotNull String[] args) {
        CommandSender sender = commandSource.getSender();
        if (!sender.hasPermission(permissionNode)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(args[0], List.of("give", "givep"));
        }

        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            options.add("me");
            options.add("all");

            for (Player player : Bukkit.getOnlinePlayers()) {
                options.add(player.getName());
            }

            return filter(args[1], options);
        }

        if (args.length == 3) {
            return filter(args[2], List.of("10", "-10", "40", "-40"));
        }

        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }

        return out;
    }
}
