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

public final class GameMath extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final AtomicBoolean winnerDetermined = new AtomicBoolean(false);
    private MyScheduledTask timeoutTask;
    private String correctAnswer;
    private String displayEquation;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        if (isRemoteGame && remoteGameData != null && !remoteGameData.isEmpty()) {
            String[] problemData = parseProblem(remoteGameData);
            if (problemData == null) {
                LoggerUtils.error("Failed to parse remote math problem: {}", remoteGameData);
                return;
            }
            this.displayEquation = problemData[0];
            this.correctAnswer = problemData[1];
            LoggerUtils.info("Starting REMOTE math game - Equation: {}, Answer: {}", displayEquation, correctAnswer);
        } else {
            List<String> problems = ConfigKeys.MATH_PROBLEMS.getList();
            if (problems.isEmpty()) return;

            String problemString = problems.get(random.nextInt(problems.size()));
            String[] problemData = parseProblem(problemString);
            if (problemData == null) return;

            this.displayEquation = problemData[0];
            this.correctAnswer = problemData[1];

            this.gameData = problemString;
        }

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        this.winnerDetermined.set(false);
        this.setAsActive();

        announceProblem();
        scheduleTimeout();
    }

    @Override
    protected String getOriginalGameData() {
        return (String) gameData;
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
                            GameUtils.broadcast(MessageKeys.MATH_GAME_WIN.getMessage()
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
    public long getStartTime() {
        return startTime;
    }

    @Override
    protected void cleanup() {
        winnerDetermined.set(false);
        correctAnswer = null;
        displayEquation = null;
        super.cleanup();
    }

    @Override
    protected GameType getGameType() {
        return GameType.MATH;
    }

    @Nullable
    private String[] parseProblem(@NotNull String raw) {
        String[] parts = raw.split("=", 2);
        if (parts.length != 2) return null;
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private void announceProblem() {
        GameUtils.broadcastMessages(MessageKeys.MATH_GAME, "{equation}", displayEquation);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                if (McChatGame.getInstance().getProxyManager().isEnabled() &&
                        McChatGame.getInstance().getProxyManager().isMasterServer()) {
                    McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), correctAnswer);
                } else if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                    GameUtils.broadcast(MessageKeys.MATH_GAME_NO_WIN.getMessage()
                            .replace("{answer}", correctAnswer));
                }

                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.MATH_TIME.getInt() * 20L);
    }
}