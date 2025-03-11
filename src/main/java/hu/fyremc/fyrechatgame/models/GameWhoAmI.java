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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GameWhoAmI extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private ScheduledTask timeoutTask;
    private String correctAnswer;
    private long startTime;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        List<String> words = ConfigKeys.WHO_AM_I_WORDS.getList();
        if (words.isEmpty()) return;

        String[] data = parseWord(words.get(random.nextInt(words.size())));
        if (data == null) return;

        this.correctAnswer = data[1];
        this.state = GameState.ACTIVE;
        this.gameData = data[0];
        startTime = System.currentTimeMillis();

        announceClue();
        scheduleTimeout();
    }

    @Nullable
    private String[] parseWord(@NotNull String raw) {
        String[] parts = raw.split("=", 2);
        if (parts.length != 2) return null;
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private void announceClue() {
        String clue = (String) gameData;
        GameUtils.broadcast(MessageKeys.WHO_AM_I.getMessage().replace("{question}", clue));
    }

    private void scheduleTimeout() {
        timeoutTask = FyreChatGame.getInstance().getScheduler().runLater(() -> {
            if (state == GameState.ACTIVE) {
                GameUtils.broadcast(MessageKeys.WHO_AM_I_NO_WIN.getMessage().replace("{answer}", correctAnswer));
                cleanup();
            }
        }, ConfigKeys.WHO_AM_I_TIME.getInt() * 20L);
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

        if (answer.trim().equalsIgnoreCase(correctAnswer)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            FyreChatGame.getInstance().getGameService().incrementWin(player)
                    .thenCompose(v -> FyreChatGame.getInstance().getGameService().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);
                        GameUtils.broadcast(MessageKeys.WHO_AM_I_WIN.getMessage()
                                .replace("{player}", player.getName())
                                .replace("{time}", formattedTime));
                        cleanup();
                    }, MainThreadExecutorService.getInstance().getMainThreadExecutor());
        }
    }
}
