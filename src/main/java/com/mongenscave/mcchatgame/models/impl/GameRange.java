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
import com.mongenscave.mcchatgame.utils.PlayerUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameRange extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final AtomicBoolean winnerDetermined = new AtomicBoolean(false);
    private MyScheduledTask timeoutTask;
    private int targetNumber;
    private int minRange;
    private int maxRange;
    private long startTime;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        String rangeConfig = ConfigKeys.RANGE_RANGE.getString();
        if (rangeConfig.trim().isEmpty()) return;

        String[] rangeParts = rangeConfig.split("-");
        if (rangeParts.length != 2) return;

        try {
            this.minRange = Integer.parseInt(rangeParts[0].trim());
            this.maxRange = Integer.parseInt(rangeParts[1].trim());
        } catch (NumberFormatException exception) {
            return;
        }

        if (minRange >= maxRange) return;

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        this.targetNumber = random.nextInt(minRange, maxRange + 1);
        this.gameData = String.valueOf(targetNumber);
        this.startTime = System.currentTimeMillis();
        this.winnerDetermined.set(false);
        this.setAsActive();

        announceRange();
        scheduleTimeout();
    }

    @Override
    public long getStartTime() {
        return startTime;
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
        if (state != GameState.ACTIVE || !winnerDetermined.compareAndSet(false, true)) return;

        try {
            int guessedNumber = Integer.parseInt(answer.trim());

            if (guessedNumber == targetNumber) {
                long endTime = System.currentTimeMillis();
                double timeTaken = (endTime - startTime) / 1000.0;
                String formattedTime = String.format("%.2f", timeTaken);

                if (timeoutTask != null) timeoutTask.cancel();

                McChatGame.getInstance().getDatabase().incrementWin(player)
                        .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                        .thenAcceptAsync(v -> {
                            GameUtils.rewardPlayer(player);
                            GameUtils.broadcast(MessageKeys.RANGE_WIN.getMessage()
                                    .replace("{player}", player.getName())
                                    .replace("{number}", String.valueOf(targetNumber))
                                    .replace("{time}", formattedTime));

                            handlePlayerWin(player);
                            cleanup();
                        }, MainThreadExecutorService.getInstance().getMainThreadExecutor());

                PlayerUtils.sendToast(player, ConfigKeys.TOAST_MESSAGE, ConfigKeys.TOAST_MATERIAL, ConfigKeys.TOAST_ENABLED);
                GameUtils.playSoundToWinner(player, ConfigKeys.SOUND_WIN_ENABLED, ConfigKeys.SOUND_WIN_SOUND);
            } else winnerDetermined.set(false);
        } catch (NumberFormatException exception) {
            winnerDetermined.set(false);
        }
    }

    @Override
    protected void cleanup() {
        winnerDetermined.set(false);
        super.cleanup();
    }

    private void announceRange() {
        GameUtils.broadcastMessages(MessageKeys.RANGE);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                GameUtils.broadcast(MessageKeys.RANGE_NO_WIN.getMessage()
                        .replace("{answer}", String.valueOf(targetNumber))
                        .replace("{min}", String.valueOf(minRange))
                        .replace("{max}", String.valueOf(maxRange)));
                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.RANGE_TIME.getInt() * 20L);
    }
}