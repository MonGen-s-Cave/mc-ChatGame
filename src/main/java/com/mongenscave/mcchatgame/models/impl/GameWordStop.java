package com.mongenscave.mcchatgame.models.impl;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.processor.AutoGameProcessor;
import com.mongenscave.mcchatgame.services.MainThreadExecutorService;
import com.mongenscave.mcchatgame.utils.GameUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GameWordStop extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private MyScheduledTask timeoutTask;
    private String correctMob;
    private long startTime;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        List<String> mobs = ConfigKeys.WORD_STOP_MOBS.getList();
        if (mobs.isEmpty()) return;

        String[] mobData = parseMob(mobs.get(random.nextInt(mobs.size())));
        if (mobData == null) return;

        this.correctMob = mobData[1];
        this.state = GameState.ACTIVE;
        this.gameData = mobData[0];
        this.startTime = System.currentTimeMillis();

        announceClue();
        scheduleTimeout();
    }

    @Nullable
    private String[] parseMob(@NotNull String raw) {
        String[] parts = raw.split("=", 2);
        if (parts.length != 2) return null;
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private void announceClue() {
        String letter = (String) gameData;
        GameUtils.broadcast(MessageKeys.WORD_STOP.getMessage().replace("{character}", letter));
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE) {
                GameUtils.broadcast(MessageKeys.WORD_STOP_NO_WIN.getMessage());
                cleanup();
            }
        }, ConfigKeys.WORD_STOP_TIME.getInt() * 20L);
    }

    @Override
    public void stop() {
        if (timeoutTask != null) timeoutTask.cancel();
        cleanup();

        AutoGameProcessor gameProcessor = McChatGame.getInstance().getGameProcessor();
        gameProcessor.start();
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {
        if (state != GameState.ACTIVE) return;

        if (answer.trim().equalsIgnoreCase(correctMob)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            McChatGame.getInstance().getDatabase().incrementWin(player)
                    .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);
                        GameUtils.broadcast(MessageKeys.WORD_STOP_WIN.getMessage()
                                .replace("{player}", player.getName())
                                .replace("{time}", formattedTime));
                        cleanup();
                    }, MainThreadExecutorService.getInstance().getMainThreadExecutor());
        }
    }
}
