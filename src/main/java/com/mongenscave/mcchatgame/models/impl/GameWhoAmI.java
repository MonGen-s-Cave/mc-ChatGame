package com.mongenscave.mcchatgame.models.impl;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.processor.AutoGameProcessor;
import com.mongenscave.mcchatgame.services.MainThreadExecutorService;
import com.mongenscave.mcchatgame.utils.GameUtils;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import com.mongenscave.mcchatgame.utils.PlayerUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameWhoAmI extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final AtomicBoolean winnerDetermined = new AtomicBoolean(false);
    private MyScheduledTask timeoutTask;
    private String correctAnswer;
    private String displayClue;
    private String originalWordString;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        if (isRemoteGame && remoteGameData != null && !remoteGameData.isEmpty()) {
            String[] data = parseWord(remoteGameData);
            if (data == null) {
                LoggerUtils.error("Failed to parse remote who-am-i data: {}", remoteGameData);
                return;
            }
            this.displayClue = data[0];
            this.correctAnswer = data[1];
            this.originalWordString = remoteGameData;
            LoggerUtils.info("Starting REMOTE who-am-i - Clue: {}, Answer: {}", displayClue, correctAnswer);
        } else {
            List<String> words = ConfigKeys.WHO_AM_I_WORDS.getList();
            if (words.isEmpty()) return;

            this.originalWordString = words.get(random.nextInt(words.size()));
            String[] data = parseWord(originalWordString);
            if (data == null) return;

            this.displayClue = data[0];
            this.correctAnswer = data[1];
        }

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        this.gameData = displayClue;
        this.winnerDetermined.set(false);
        this.setAsActive();

        announceClue();
        scheduleTimeout();
    }

    @Override
    protected String getOriginalGameData() {
        return originalWordString;
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
        if (state != GameState.ACTIVE) return;
        if (!winnerDetermined.compareAndSet(false, true)) return;

        if (answer.trim().equalsIgnoreCase(correctAnswer)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            if (timeoutTask != null) timeoutTask.cancel();

            McChatGame.getInstance().getDatabase().incrementWin(player)
                    .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);

                        if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                            GameUtils.broadcast(MessageKeys.WHO_AM_I_WIN.getMessage()
                                    .replace("{player}", player.getName())
                                    .replace("{time}", formattedTime));
                        }

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
        correctAnswer = null;
        displayClue = null;
        originalWordString = null;
        super.cleanup();
    }

    @Override
    protected GameType getGameType() {
        return GameType.WHO_AM_I;
    }

    @Nullable
    private String[] parseWord(@NotNull String raw) {
        String[] parts = raw.split("=", 2);
        if (parts.length != 2) return null;
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private void announceClue() {
        GameUtils.broadcastMessages(MessageKeys.WHO_AM_I, "{question}", displayClue);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                if (McChatGame.getInstance().getProxyManager().isEnabled() &&
                        McChatGame.getInstance().getProxyManager().isMasterServer()) {
                    McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), correctAnswer);
                } else if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                    GameUtils.broadcast(MessageKeys.WHO_AM_I_NO_WIN.getMessage().replace("{answer}", correctAnswer));
                }

                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.WHO_AM_I_TIME.getInt() * 20L);
    }
}