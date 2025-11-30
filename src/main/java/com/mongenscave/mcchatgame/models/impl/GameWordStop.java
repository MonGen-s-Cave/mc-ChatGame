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

public class GameWordStop extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final AtomicBoolean winnerDetermined = new AtomicBoolean(false);
    private MyScheduledTask timeoutTask;
    private String correctMob;
    private String displayLetter;
    private String originalMobString;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        if (isRemoteGame && remoteGameData != null && !remoteGameData.isEmpty()) {
            String[] mobData = parseMob(remoteGameData);
            if (mobData == null) {
                LoggerUtils.error("Failed to parse remote word-stop data: {}", remoteGameData);
                return;
            }
            this.displayLetter = mobData[0];
            this.correctMob = mobData[1];
            this.originalMobString = remoteGameData;
            LoggerUtils.info("Starting REMOTE word-stop - Letter: {}, Mob: {}", displayLetter, correctMob);
        } else {
            List<String> mobs = ConfigKeys.WORD_STOP_MOBS.getList();
            if (mobs.isEmpty()) return;

            this.originalMobString = mobs.get(random.nextInt(mobs.size()));
            String[] mobData = parseMob(originalMobString);
            if (mobData == null) return;

            this.displayLetter = mobData[0];
            this.correctMob = mobData[1];
        }

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        this.gameData = displayLetter;
        this.winnerDetermined.set(false);
        this.setAsActive();

        announceClue();
        scheduleTimeout();
    }

    @Override
    protected String getOriginalGameData() {
        return originalMobString;
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

        if (answer.trim().equalsIgnoreCase(correctMob)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            if (timeoutTask != null) timeoutTask.cancel();

            McChatGame.getInstance().getDatabase().incrementWin(player)
                    .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);

                        if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                            GameUtils.broadcast(MessageKeys.WORD_STOP_WIN.getMessage()
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
        correctMob = null;
        displayLetter = null;
        originalMobString = null;
        super.cleanup();
    }

    @Override
    protected GameType getGameType() {
        return GameType.WORD_STOP;
    }

    @Nullable
    private String[] parseMob(@NotNull String raw) {
        String[] parts = raw.split("=", 2);
        if (parts.length != 2) return null;
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private void announceClue() {
        GameUtils.broadcastMessages(MessageKeys.WORD_STOP, "{character}", displayLetter);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                if (McChatGame.getInstance().getProxyManager().isEnabled() &&
                        McChatGame.getInstance().getProxyManager().isMasterServer()) {
                    McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), correctMob);
                } else if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                    GameUtils.broadcast(MessageKeys.WORD_STOP_NO_WIN.getMessage().replace("{answer}", correctMob));
                }

                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.WORD_STOP_TIME.getInt() * 20L);
    }
}