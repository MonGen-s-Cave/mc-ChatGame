package hu.fyremc.fyrechatgame.models;

import com.artillexstudios.axapi.scheduler.ScheduledTask;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.handler.GameHandler;
import hu.fyremc.fyrechatgame.identifiers.GameState;
import hu.fyremc.fyrechatgame.identifiers.keys.ConfigKeys;
import hu.fyremc.fyrechatgame.identifiers.keys.MessageKeys;
import hu.fyremc.fyrechatgame.processor.AutoGameProcessor;
import hu.fyremc.fyrechatgame.services.MainThreadExecutorService;
import hu.fyremc.fyrechatgame.utils.GameUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GameReverse extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private ScheduledTask timeoutTask;
    private String originalWord;
    private long startTime;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        List<String> words = ConfigKeys.REVERSE_WORDS.getList();
        if (words.isEmpty()) return;

        this.originalWord = words.get(random.nextInt(words.size())).trim();
        String reversed = new StringBuilder(originalWord).reverse().toString();
        this.state = GameState.ACTIVE;
        this.gameData = reversed;
        startTime = System.currentTimeMillis();

        announceReversed(reversed);
        scheduleTimeout();
    }

    private void announceReversed(@NotNull String reversed) {
        GameUtils.broadcast(MessageKeys.REVERSE.getMessage().replace("{word}", reversed));
    }

    private void scheduleTimeout() {
        timeoutTask = FyreChatGame.getInstance().getScheduler().runLater(() -> {
            if (state == GameState.ACTIVE) {
                GameUtils.broadcast(MessageKeys.REVERSE_NO_WIN.getMessage());
                cleanup();
            }
        }, ConfigKeys.REVERSE_TIME.getInt() * 20L);
    }

    @Override
    public void stop() {
        if (timeoutTask != null) timeoutTask.cancel();
        cleanup();

        AutoGameProcessor gameProcessor = FyreChatGame.getInstance().getGameProcessor();
        gameProcessor.start();
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {
        if (state != GameState.ACTIVE) return;

        if (answer.trim().equalsIgnoreCase(originalWord)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            FyreChatGame.getInstance().getGameService().incrementWin(player)
                    .thenCompose(v -> FyreChatGame.getInstance().getGameService().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);
                        GameUtils.broadcast(MessageKeys.REVERSE_WIN.getMessage()
                                .replace("{player}", player.getName())
                                .replace("{time}", formattedTime));
                        cleanup();
                    }, MainThreadExecutorService.getInstance().getMainThreadExecutor());
        }
    }
}
