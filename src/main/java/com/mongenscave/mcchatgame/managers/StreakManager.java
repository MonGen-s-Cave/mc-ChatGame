package com.mongenscave.mcchatgame.managers;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.services.MainThreadExecutorService;
import com.mongenscave.mcchatgame.utils.GameUtils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class StreakManager {
    private static StreakManager instance;
    private static final McChatGame plugin = McChatGame.getInstance();
    private final Set<String> playersToResetStreak = new HashSet<>();
    @Getter private String lastWinner = null;

    public static StreakManager getInstance() {
        if (instance == null) instance = new StreakManager();
        return instance;
    }

    public void onGameStart() {
        if (!ConfigKeys.STREAKS_ENABLED.getBoolean()) return;

        playersToResetStreak.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            playersToResetStreak.add(player.getName());
        }
    }

    public void onPlayerWin(@NotNull Player winner) {
        if (!ConfigKeys.STREAKS_ENABLED.getBoolean()) return;

        playersToResetStreak.remove(winner.getName());
        CompletableFuture<Void> resetTask = resetStreaksForPlayers(playersToResetStreak);

        plugin.getDatabase().getCurrentStreak(winner)
                .thenCompose(currentStreak -> plugin.getDatabase().incrementStreak(winner)
                        .thenApply(v -> currentStreak + 1))
                .thenAcceptAsync(newStreak -> {
                    checkStreakMilestone(winner, newStreak);
                    lastWinner = winner.getName();
                }, MainThreadExecutorService.getInstance().getMainThreadExecutor())
                .thenCompose(v -> resetTask);
    }

    public void onGameTimeout() {
        if (!ConfigKeys.STREAKS_ENABLED.getBoolean()) return;
        resetStreaksForPlayers(playersToResetStreak);
    }

    private CompletableFuture<Void> resetStreaksForPlayers(@NotNull Set<String> playerNames) {
        if (playerNames.isEmpty()) return CompletableFuture.completedFuture(null);

        CompletableFuture<?>[] futures = playerNames.stream()
                .map(playerName -> {
                    Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null) {
                        return plugin.getDatabase().getCurrentStreak(player)
                                .thenCompose(currentStreak -> {
                                    if (currentStreak > 0) {
                                        return plugin.getDatabase().resetStreak(player)
                                                .thenAcceptAsync(v -> checkStreakLoss(player, currentStreak),
                                                        MainThreadExecutorService.getInstance().getMainThreadExecutor());
                                    }
                                    return CompletableFuture.completedFuture(null);
                                });
                    }
                    return CompletableFuture.<Void>completedFuture(null);
                })
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    private void checkStreakMilestone(@NotNull Player player, int newStreak) {
        for (String streakKey : ConfigKeys.STREAKS_MILESTONES.getKeys()) {
            try {
                int streakValue = Integer.parseInt(streakKey);
                if (newStreak == streakValue) {
                    handleStreakReached(player, streakValue);
                    break;
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private void checkStreakLoss(@NotNull Player player, int lostStreak) {
        for (String streakKey : ConfigKeys.STREAKS_MILESTONES.getKeys()) {
            try {
                int streakValue = Integer.parseInt(streakKey);
                if (lostStreak >= streakValue) {
                    handleStreakLost(player, streakValue);
                    break;
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    private void handleStreakReached(@NotNull Player player, int streakValue) {
        String basePath = "streaks." + streakValue + ".";

        boolean reachEnabled = plugin.getConfig().getBoolean(basePath + "reach-message.enabled", false);
        if (reachEnabled) {
            List<String> messages = plugin.getConfig().getStringList(basePath + "reach-message.message");
            for (String message : messages) {
                GameUtils.broadcast(message.replace("{player}", player.getName())
                        .replace("{streak}", String.valueOf(streakValue)));
            }
        }

        List<String> commands = plugin.getConfig().getStringList(basePath + "commands");
        for (String command : commands) {
            String processedCommand = command.replace("{player}", player.getName())
                    .replace("{streak}", String.valueOf(streakValue));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
        }
    }

    private void handleStreakLost(@NotNull Player player, int streakValue) {
        String basePath = "streaks." + streakValue + ".";

        boolean lostEnabled = plugin.getConfig().getBoolean(basePath + "lost-message.enabled", false);
        if (lostEnabled) {
            List<String> messages = plugin.getConfig().getStringList(basePath + "lost-message.message");
            for (String message : messages) {
                GameUtils.broadcast(message.replace("{player}", player.getName())
                        .replace("{streak}", String.valueOf(streakValue)));
            }
        }
    }

    public CompletableFuture<Integer> getPlayerStreak(@NotNull Player player) {
        if (!ConfigKeys.STREAKS_ENABLED.getBoolean()) return CompletableFuture.completedFuture(0);
        return plugin.getDatabase().getCurrentStreak(player);
    }

    public CompletableFuture<Integer> getPlayerBestStreak(@NotNull Player player) {
        if (!ConfigKeys.STREAKS_ENABLED.getBoolean()) return CompletableFuture.completedFuture(0);
        return plugin.getDatabase().getBestStreak(player);
    }
}