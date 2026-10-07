package com.mongenscave.mcchatgame.utils;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.data.WeightedReward;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import com.mongenscave.mcchatgame.processor.MessageProcessor;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class GameUtils {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final Pattern CHANCE_PATTERN = Pattern.compile("\\[(\\d+)]\\s*(.+)");

    @NotNull
    public String rewardPlayer(@NotNull Player winner) {
        List<Object> rewardConfigs = McChatGame.getInstance().getConfiguration().getList(ConfigKeys.REWARDS.getPath());
        if (rewardConfigs == null || rewardConfigs.isEmpty()) return "";

        List<WeightedReward> weightedRewards = parseRewards(rewardConfigs);
        if (weightedRewards.isEmpty()) return "";

        WeightedReward selectedReward = selectWeightedReward(weightedRewards);
        if (selectedReward == null) return "";

        List<String> commands = selectedReward.commands().stream()
                .map(command -> command.replace("{player}", winner.getName()))
                .toList();

        McChatGame.getInstance().getScheduler().runTask(() ->
                commands.forEach(command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)));

        return MessageProcessor.process(selectedReward.displayName().replace("{player}", winner.getName()));
    }

    public void broadcast(@NotNull String message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }

    public void playSoundToEveryone(@NotNull ConfigKeys enabled, @NotNull ConfigKeys sound) {
        if (!enabled.getBoolean()) return;

        Bukkit.getOnlinePlayers().forEach(player -> McChatGame.getInstance().getScheduler().runTask(player, () ->
                player.playSound(player.getLocation(), sound.getString(), 0.5f, 1.0f)));
    }

    public void playSoundToWinner(@NotNull Player player, @NotNull ConfigKeys enabled, @NotNull ConfigKeys sound) {
        if (!enabled.getBoolean()) return;

        McChatGame.getInstance().getScheduler().runTask(player, () ->
                player.playSound(player.getLocation(), sound.getString(), 0.5f, 1.0f));
    }

    public void broadcastMessages(@NotNull MessageKeys messages, @NotNull String... placeholders) {
        for (String message : messages.getMessages()) {
            broadcast(MessageProcessor.process(applyPlaceholders(message, placeholders)));
        }
    }

    private String applyPlaceholders(@NotNull String message, @NotNull String... placeholders) {
        for (int i = 0; i < placeholders.length; i+= 2) {
            message = message.replace(placeholders[i], placeholders[i + 1]);
        }

        return message;
    }

    @NotNull
    private List<WeightedReward> parseRewards(@NotNull List<Object> rewardConfigs) {
        List<WeightedReward> rewards = Collections.synchronizedList(new ArrayList<>());

        for (Object entry : rewardConfigs) {
            if (entry instanceof Map<?, ?> map) {
                WeightedReward reward = parseRewardMap(map);
                if (reward != null) rewards.add(reward);
                continue;
            }

            if (entry == null) continue;

            String config = entry.toString().trim();
            if (config.isEmpty()) continue;

            Matcher matcher = CHANCE_PATTERN.matcher(config);

            if (matcher.matches()) {
                try {
                    int weight = Integer.parseInt(matcher.group(1));
                    String command = matcher.group(2).trim();

                    if (weight > 0 && !command.isEmpty()) rewards.add(new WeightedReward(weight, List.of(command), ""));
                } catch (NumberFormatException exception) {
                    LoggerUtils.error("Invalid reward config: " + config);
                }
            } else rewards.add(new WeightedReward(1, List.of(config), ""));
        }

        return rewards;
    }

    @Nullable
    private WeightedReward parseRewardMap(@NotNull Map<?, ?> map) {
        int weight;

        try {
            Object chance = map.get("chance");
            weight = chance == null ? 1 : Integer.parseInt(chance.toString().trim());
        } catch (NumberFormatException exception) {
            LoggerUtils.error("Invalid reward chance: " + map);
            return null;
        }

        List<String> commands = new ArrayList<>();
        Object command = map.get("command");
        if (command != null && !command.toString().isBlank()) commands.add(command.toString().trim());

        if (map.get("commands") instanceof List<?> list) {
            list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .filter(line -> !line.isBlank())
                    .forEach(line -> commands.add(line.trim()));
        }

        if (weight <= 0 || commands.isEmpty()) {
            LoggerUtils.error("Invalid reward config: " + map);
            return null;
        }

        Object displayName = map.get("display-name");
        return new WeightedReward(weight, commands, displayName != null ? displayName.toString() : "");
    }

    @Nullable
    private WeightedReward selectWeightedReward(@NotNull List<WeightedReward> rewards) {
        if (rewards.isEmpty()) return null;

        int totalWeight = rewards.stream().mapToInt(WeightedReward::weight).sum();
        if (totalWeight <= 0) return null;

        int randomValue = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (WeightedReward reward : rewards) {
            currentWeight += reward.weight();
            if (randomValue < currentWeight) return reward;
        }

        return rewards.getLast();
    }
}