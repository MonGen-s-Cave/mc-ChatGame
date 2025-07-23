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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameWordStop extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final AtomicBoolean winnerDetermined = new AtomicBoolean(false);
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
        this.gameData = mobData[0];
        this.startTime = System.currentTimeMillis();
        this.winnerDetermined.set(false);
        this.setAsActive();

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        announceClue();
        scheduleTimeout();
    }

    @Override
    public void stop() {
        if (timeoutTask != null) timeoutTask.cancel();
        cleanup();

        AutoGameProcessor gameProcessor = McChatGame.getInstance().getGameProcessor();
        gameProcessor.start();
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {
        if (state != GameState.ACTIVE || !winnerDetermined.compareAndSet(false, true)) return;

        if (answer.trim().equalsIgnoreCase(correctMob)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            if (timeoutTask != null) timeoutTask.cancel();

            McChatGame.getInstance().getDatabase().incrementWin(player)
                    .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);
                        GameUtils.broadcast(MessageKeys.WORD_STOP_WIN.getMessage()
                                .replace("{player}", player.getName())
                                .replace("{time}", formattedTime));

                        handlePlayerWin(player);
                        cleanup();
                    }, MainThreadExecutorService.getInstance().getMainThreadExecutor());

            PlayerUtils.sendToast(player, ConfigKeys.TOAST_MESSAGE, ConfigKeys.TOAST_MATERIAL, ConfigKeys.TOAST_ENABLED);
            GameUtils.playSoundToWinner(player, ConfigKeys.SOUND_WIN_ENABLED, ConfigKeys.SOUND_WIN_SOUND);
        } else {
            winnerDetermined.set(false);
        }
    }

    @Override
    protected void cleanup() {
        winnerDetermined.set(false);
        super.cleanup();
    }

    @Nullable
    private String[] parseMob(@NotNull String raw) {
        String[] parts = raw.split("=", 2);
        if (parts.length != 2) return null;
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private void announceClue() {
        String letter = (String) gameData;
        GameUtils.broadcastMessages(MessageKeys.WORD_STOP, "{character}", letter);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                GameUtils.broadcast(MessageKeys.WORD_STOP_NO_WIN.getMessage());
                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.WORD_STOP_TIME.getInt() * 20L);
    }
}