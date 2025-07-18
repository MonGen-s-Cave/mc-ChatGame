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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GameWordGuess extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
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
        this.state = GameState.ACTIVE;
        this.gameData = scrambled;
        this.startTime = System.currentTimeMillis();

        announceScrambled(scrambled);
        scheduleTimeout();
    }

    @NotNull
    private String scrambleWord(@NotNull String word) {
        List<Character> chars = Collections.synchronizedList(new ArrayList<>(word.chars()
                .mapToObj(c -> (char) c)
                .toList()));

        Collections.shuffle(chars);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private void announceScrambled(@NotNull String scrambled) {
        GameUtils.broadcast(MessageKeys.WORD_GUESSER.getMessage().replace("{word}", scrambled));
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE) {
                GameUtils.broadcast(MessageKeys.WORD_GUESSER_NO_WIN.getMessage());
                cleanup();
            }
        }, ConfigKeys.WORD_GUESSER_TIME.getInt() * 20L);
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

        if (answer.trim().equalsIgnoreCase(originalWord)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            McChatGame.getInstance().getDatabase().incrementWin(player)
                    .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);
                        GameUtils.broadcast(MessageKeys.WORD_GUESSER_WIN.getMessage()
                                .replace("{player}", player.getName())
                                .replace("{time}", formattedTime));
                        cleanup();
                    }, MainThreadExecutorService.getInstance().getMainThreadExecutor());
        }
    }
}
