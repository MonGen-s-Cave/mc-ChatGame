package hu.fyremc.fyrechatgame.processor;

import com.artillexstudios.axapi.scheduler.ScheduledTask;
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

    private ScheduledTask task;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public void start() {
        stopExistingTask();
        scheduleNewTask();
    }

    public void stop() {
        stopExistingTask();
    }

    private void stopExistingTask() {
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private void scheduleNewTask() {
        stopExistingTask();

        long checkIntervalTicks = 20;

        task = FyreChatGame.getInstance().getScheduler().runLater(this::checkAndStartGame, checkIntervalTicks);
    }

    private void checkAndStartGame() {
        int activeGames = GameManager.getActiveGameCount();
        long currentTime = System.currentTimeMillis();
        long lastGameEnd = GameManager.getLastGameEndTime();
        long cooldownPeriod = ConfigKeys.TIME_BETWEEN_GAMES.getInt() * 1000L;

        GameManager.removeInactiveGames();

        if (activeGames == 0 && (lastGameEnd == 0 || currentTime - lastGameEnd >= cooldownPeriod)) startRandomGame();

        scheduleNewTask();
    }

    private void startRandomGame() {
        GameTypes randomType = GAME_POOL.get(random.nextInt(GAME_POOL.size()));
        GameManager.startGame(randomType);
    }
}