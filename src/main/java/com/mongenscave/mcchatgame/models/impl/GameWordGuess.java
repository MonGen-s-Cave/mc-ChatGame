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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameWordGuess extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final AtomicBoolean winnerDetermined = new AtomicBoolean(false);
    private MyScheduledTask timeoutTask;
    private String originalWord;
    private String scrambledWord;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        if (isRemoteGame && remoteGameData != null && !remoteGameData.isEmpty()) {
            this.originalWord = remoteGameData;
            this.scrambledWord = scrambleWord(originalWord);
            LoggerUtils.info("Starting REMOTE word-guess - Original: {}, Scrambled: {}", originalWord, scrambledWord);
        } else {
            List<String> words = ConfigKeys.WORD_GUESSER_WORDS.getList();
            if (words.isEmpty()) return;

            this.originalWord = words.get(random.nextInt(words.size())).trim();
            this.scrambledWord = scrambleWord(originalWord);
        }

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        this.gameData = scrambledWord;
        this.winnerDetermined.set(false);
        this.setAsActive();

        announceScrambled();
        scheduleTimeout();
    }

    @Override
    protected String getOriginalGameData() {
        return originalWord;
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

        if (answer.trim().equalsIgnoreCase(originalWord)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            if (timeoutTask != null) timeoutTask.cancel();

            McChatGame.getInstance().getDatabase().incrementWin(player)
                    .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);

                        if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                            GameUtils.broadcast(MessageKeys.WORD_GUESSER_WIN.getMessage()
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
        originalWord = null;
        scrambledWord = null;
        super.cleanup();
    }

    @Override
    protected GameType getGameType() {
        return GameType.WORD_GUESSER;
    }

    @NotNull
    private String scrambleWord(@NotNull String word) {
        String[] words = word.split(" ");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            result.append(scrambleSingleWord(words[i]));
        }

        return result.toString();
    }

    @NotNull
    private String scrambleSingleWord(@NotNull String word) {
        if (word.length() <= 1) return word;

        List<Character> chars = new ArrayList<>(word.chars()
                .mapToObj(c -> (char) c)
                .toList());

        Collections.shuffle(chars);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private void announceScrambled() {
        GameUtils.broadcastMessages(MessageKeys.WORD_GUESSER, "{word}", scrambledWord);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                if (McChatGame.getInstance().getProxyManager().isEnabled() &&
                        McChatGame.getInstance().getProxyManager().isMasterServer()) {
                    McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), originalWord);
                } else if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                    GameUtils.broadcast(MessageKeys.WORD_GUESSER_NO_WIN.getMessage().replace("{answer}", originalWord));
                }

                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.WORD_GUESSER_TIME.getInt() * 20L);
    }
}