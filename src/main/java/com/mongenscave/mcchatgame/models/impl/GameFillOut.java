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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GameFillOut extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private MyScheduledTask timeoutTask;
    private long startTime;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        List<String> words = ConfigKeys.FILL_OUT_WORDS.getList();
        if (words.isEmpty()) return;

        String originalWord = words.get(random.nextInt(words.size())).trim();
        String filled = generateFillOut(originalWord);
        this.state = GameState.ACTIVE;
        this.gameData = filled;
        this.startTime = System.currentTimeMillis();

        announceFillOut(filled);
        scheduleTimeout();
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

    private void announceFillOut(@NotNull String filled) {
        GameUtils.broadcast(MessageKeys.FILL_OUT.getMessage().replace("{word}", filled));
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE) {
                GameUtils.broadcast(MessageKeys.FILL_OUT_NO_WIN.getMessage());
                cleanup();
            }
        }, ConfigKeys.FILL_OUT_TIME.getInt() * 20L);
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

        long endTime = System.currentTimeMillis();
        double timeTaken = (endTime - startTime) / 1000.0;
        String formattedTime = String.format("%.2f", timeTaken);

        McChatGame.getInstance().getDatabase().incrementWin(player)
                .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                .thenAcceptAsync(v -> {
                    GameUtils.rewardPlayer(player);
                    GameUtils.broadcast(MessageKeys.FILL_OUT_WIN.getMessage()
                            .replace("{player}", player.getName())
                            .replace("{time}", formattedTime));
                    cleanup();
                }, MainThreadExecutorService.getInstance().getMainThreadExecutor());
    }
}
