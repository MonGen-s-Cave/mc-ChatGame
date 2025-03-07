package hu.fyremc.fyrechatgame.processor;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.identifiers.GameTypes;
import hu.fyremc.fyrechatgame.identifiers.keys.ConfigKeys;
import hu.fyremc.fyrechatgame.manager.GameManager;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AutoGameProcessor {
    private final List<GameTypes> GAME_POOL = Arrays.asList(
            GameTypes.MATH,
            GameTypes.RANDOM_CHARACTERS,
            GameTypes.WHO_AM_I,
            GameTypes.WORD_STOP,
            GameTypes.WORD_GUESSER,
            GameTypes.REVERSE,
            GameTypes.FILL_OUT
    );

    private MyScheduledTask task;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public void start() {
        stopExistingTask();
        scheduleNewTask();
    }

    public void stop() {
        stopExistingTask();
    }

    private void stopExistingTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void scheduleNewTask() {
        long intervalTicks = ConfigKeys.TIME_BETWEEN_GAMES.getInt() * 20L;
        task = FyreChatGame.getInstance().getScheduler().runTaskTimer(
                this::tryStartGame,
                intervalTicks,
                intervalTicks
        );
    }

    private void tryStartGame() {
        GameManager.removeInactiveGames();

        if (GameManager.getActiveGameCount() == 0) startRandomGame();
    }

    private void startRandomGame() {
        GameTypes randomType = GAME_POOL.get(random.nextInt(GAME_POOL.size()));
        GameManager.startGame(randomType);
    }
}