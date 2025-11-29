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
    private long startTime;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        List<String> words = ConfigKeys.WORD_GUESSER_WORDS.getList();
        if (words.isEmpty()) return;

        this.originalWord = words.get(random.nextInt(words.size())).trim();
        String scrambled = scrambleWord(originalWord);
        this.gameData = scrambled;
        this.startTime = System.currentTimeMillis();
        this.winnerDetermined.set(false);
        this.setAsActive();

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        announceScrambled(scrambled);
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

        if (answer.trim().equalsIgnoreCase(originalWord)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            if (timeoutTask != null) timeoutTask.cancel();

            McChatGame.getInstance().getDatabase().incrementWin(player)
                    .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);
                        GameUtils.broadcast(MessageKeys.WORD_GUESSER_WIN.getMessage()
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

    private void announceScrambled(@NotNull String scrambled) {
        GameUtils.broadcastMessages(MessageKeys.WORD_GUESSER, "{word}", scrambled);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                if (McChatGame.getInstance().getProxyManager().isEnabled()) McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), originalWord);
                else GameUtils.broadcast(MessageKeys.WORD_GUESSER_NO_WIN.getMessage().replace("{answer}", originalWord));

                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.WORD_GUESSER_TIME.getInt() * 20L);
    }
}