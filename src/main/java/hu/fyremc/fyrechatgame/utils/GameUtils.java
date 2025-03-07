package hu.fyremc.fyrechatgame.utils;

import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.identifiers.keys.ConfigKeys;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
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

        Bukkit.getScheduler().runTask(FyreChatGame.getInstance(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    public void broadcast(@NotNull String message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }
}
