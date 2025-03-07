package hu.fyremc.fyrechatgame.models;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.handler.GameHandler;
import hu.fyremc.fyrechatgame.identifiers.GameState;
import hu.fyremc.fyrechatgame.identifiers.keys.ConfigKeys;
import hu.fyremc.fyrechatgame.identifiers.keys.MessageKeys;
import hu.fyremc.fyrechatgame.services.MainThreadExecutorService;
import hu.fyremc.fyrechatgame.utils.GameUtils;
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
    private String originalWord;
    private long startTime;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        List<String> words = ConfigKeys.FILL_OUT_WORDS.getList();
        if (words.isEmpty()) return;

        this.originalWord = words.get(random.nextInt(words.size())).trim();
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
        List<Integer> indices = new ArrayList<>();

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
        timeoutTask = FyreChatGame.getInstance().getScheduler().runTaskLater(() -> {
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
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {
        if (state != GameState.ACTIVE) return;

        long endTime = System.currentTimeMillis();
        double timeTaken = (endTime - startTime) / 1000.0;
        String formattedTime = String.format("%.2f", timeTaken);

        FyreChatGame.getInstance().getGameService().incrementWin(player)
                .thenCompose(v -> FyreChatGame.getInstance().getGameService().setTime(player, timeTaken))
                .thenAcceptAsync(v -> {
                    GameUtils.rewardPlayer(player);
                    GameUtils.broadcast(MessageKeys.FILL_OUT_WIN.getMessage()
                            .replace("{player}", player.getName())
                            .replace("{time}", formattedTime));
                    cleanup();
                }, MainThreadExecutorService.getInstance().getMainThreadExecutor());
    }
}
