package com.mongenscave.mcchatgame.processor;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameTypes;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.manager.GameManager;

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
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private void scheduleNewTask() {
        stopExistingTask();

        long checkIntervalTicks = 20;

        task = McChatGame.getInstance().getScheduler().runTaskLater(this::checkAndStartGame, checkIntervalTicks);
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