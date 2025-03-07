package hu.fyremc.fyrechatgame.models;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.handler.GameHandler;
import hu.fyremc.fyrechatgame.identifiers.GameState;
import hu.fyremc.fyrechatgame.identifiers.keys.ConfigKeys;
import hu.fyremc.fyrechatgame.identifiers.keys.MessageKeys;
import hu.fyremc.fyrechatgame.utils.GameUtils;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public class GameRandomCharacters extends GameHandler {
    private MyScheduledTask timeoutTask;
    private String targetSequence;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        this.targetSequence = generateSequence();
        this.state = GameState.ACTIVE;
        this.gameData = targetSequence;

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
        timeoutTask = FyreChatGame.getInstance().getScheduler().runTaskLater(() -> {
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
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {
        if (state != GameState.ACTIVE) return;

        if (answer.equals(targetSequence)) {
            GameUtils.rewardPlayer(player);
            GameUtils.broadcast(MessageKeys.RANDOM_CHARACTERS_WIN.getMessage().replace("{player}", player.getName()));
            cleanup();
        }
    }
}
