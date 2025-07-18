package com.mongenscave.mcchatgame.utils;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class GameUtils {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public void rewardPlayer(@NotNull Player winner) {
        List<String> rewards = ConfigKeys.REWARDS.getList();
        if (rewards.isEmpty()) return;

        String command = rewards.get(random.nextInt(rewards.size())).replace("{player}", winner.getName());

        McChatGame.getInstance().getScheduler().runTask(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    public void broadcast(@NotNull String message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }

    public void playSoundToEveryone(@NotNull ConfigKeys enabled, @NotNull ConfigKeys sound) {
        if (!enabled.getBoolean()) return;

        Bukkit.getOnlinePlayers().forEach(player -> player.playSound(player.getLocation(), Sound.valueOf(sound.getString()), 0.5f, 1.0f));
    }

    public void playSoundToWinner(@NotNull Player player, @NotNull ConfigKeys enabled, @NotNull ConfigKeys sound) {
        if (!enabled.getBoolean()) return;

        player.playSound(player.getLocation(), Sound.valueOf(sound.getString()), 0.5f, 1.0f);
    }
}
