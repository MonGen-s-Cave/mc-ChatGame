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

import java.util.concurrent.ThreadLocalRandom;

public class GameRandomCharacters extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
    private ScheduledTask timeoutTask;
    private String targetSequence;
    private long startTime;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        this.targetSequence = generateSequence();
        this.state = GameState.ACTIVE;
        this.gameData = targetSequence;
        this.startTime = System.currentTimeMillis();

        announceGame();
        scheduleTimeout();
    }

    @NotNull
    private String generateSequence() {
        int length = ConfigKeys.RANDOM_CHARACTERS_LENGTH.getInt();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }

        return sb.toString();
    }

    private void announceGame() {
        GameUtils.broadcast(MessageKeys.RANDOM_CHARACTERS.getMessage().replace("{word}", targetSequence));
    }

    private void scheduleTimeout() {
        timeoutTask = FyreChatGame.getInstance().getScheduler().runLater(() -> {
            if (state == GameState.ACTIVE) {
                GameUtils.broadcast(MessageKeys.RANDOM_CHARACTERS_NO_WIN.getMessage());
                cleanup();
            }
        }, ConfigKeys.RANDOM_CHARACTERS_TIME.getInt() * 20L);
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

        if (answer.equals(targetSequence)) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            String formattedTime = String.format("%.2f", timeTaken);

            FyreChatGame.getInstance().getGameService().incrementWin(player)
                    .thenCompose(v -> FyreChatGame.getInstance().getGameService().setTime(player, timeTaken))
                    .thenAcceptAsync(v -> {
                        GameUtils.rewardPlayer(player);
                        GameUtils.broadcast(MessageKeys.RANDOM_CHARACTERS_WIN.getMessage()
                                .replace("{player}", player.getName())
                                .replace("{time}", formattedTime));
                        cleanup();
                    }, MainThreadExecutorService.getInstance().getMainThreadExecutor());
        }
    }
}
