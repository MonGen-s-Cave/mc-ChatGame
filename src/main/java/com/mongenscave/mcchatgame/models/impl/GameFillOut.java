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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameFillOut extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final AtomicBoolean winnerDetermined = new AtomicBoolean(false);
    private MyScheduledTask timeoutTask;
    private String correctAnswer;
    private String filledWord;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        if (isRemoteGame && remoteGameData != null && !remoteGameData.isEmpty()) {
            this.correctAnswer = remoteGameData;
            this.filledWord = generateFillOut(correctAnswer);
            LoggerUtils.info("Starting REMOTE fill-out - Original: {}, Filled: {}", correctAnswer, filledWord);
        } else {
            List<String> words = ConfigKeys.FILL_OUT_WORDS.getList();
            if (words.isEmpty()) return;

            this.correctAnswer = words.get(random.nextInt(words.size())).trim();
            this.filledWord = generateFillOut(correctAnswer);
        }

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        this.gameData = filledWord;
        this.winnerDetermined.set(false);
        this.setAsActive();

        announceFillOut();
        scheduleTimeout();
    }

    @Override
    protected String getOriginalGameData() {
        return correctAnswer;
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
                            GameUtils.broadcast(MessageKeys.FILL_OUT_WIN.getMessage()
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
        filledWord = null;
        super.cleanup();
    }

    @Override
    protected GameType getGameType() {
        return GameType.FILL_OUT;
    }

    @NotNull
    @Contract("_ -> new")
    private String generateFillOut(@NotNull String word) {
        int length = word.length();
        int replaceCount = Math.max(1, (int) Math.ceil(length / 2.0));
        List<Integer> indices = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < length; i++) indices.add(i);
        Collections.shuffle(indices);

        indices = indices.subList(0, replaceCount);
        char[] chars = word.toCharArray();

        for (int index : indices) {
            chars[index] = '_';
        }

        return new String(chars);
    }

    private void announceFillOut() {
        GameUtils.broadcastMessages(MessageKeys.FILL_OUT, "{word}", filledWord);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                if (McChatGame.getInstance().getProxyManager().isEnabled() &&
                        McChatGame.getInstance().getProxyManager().isMasterServer()) {
                    McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), correctAnswer);
                } else if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                    GameUtils.broadcast(MessageKeys.FILL_OUT_NO_WIN.getMessage().replace("{answer}", correctAnswer));
                }

                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.FILL_OUT_TIME.getInt() * 20L);
    }
}