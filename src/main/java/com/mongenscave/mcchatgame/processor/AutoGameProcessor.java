package com.mongenscave.mcchatgame.processor;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.manager.GameManager;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AutoGameProcessor {
    private final List<GameType> GAME_POOL = Collections.synchronizedList(new ArrayList<>(Arrays.asList(GameType.values())));

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

        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int minPlayers = ConfigKeys.MIN_PLAYERS.getInt();

        if (onlinePlayers < minPlayers) {
            scheduleNewTask();
            return;
        }

        if (activeGames == 0 && (lastGameEnd == 0 || currentTime - lastGameEnd >= cooldownPeriod)) startRandomGame();

        scheduleNewTask();
    }

    private void startRandomGame() {
        List<GameType> enabledGames = getEnabledGames();

        if (enabledGames.isEmpty()) return;

        GameType randomType = enabledGames.get(random.nextInt(enabledGames.size()));
        GameManager.startGame(randomType);
    }

    @NotNull
    private List<GameType> getEnabledGames() {
        List<GameType> enabledGames = Collections.synchronizedList(new ArrayList<>());

        for (GameType gameType : GAME_POOL) {
            boolean isEnabled = false;

            switch (gameType) {
                case MATH -> isEnabled = ConfigKeys.MATH_ENABLED.getBoolean();
                case RANDOM_CHARACTERS -> isEnabled = ConfigKeys.RANDOM_CHARACTERS_ENABLED.getBoolean();
                case WHO_AM_I -> isEnabled = ConfigKeys.WHO_AM_I_ENABLED.getBoolean();
                case WORD_STOP -> isEnabled = ConfigKeys.WORD_STOP_ENABLED.getBoolean();
                case WORD_GUESSER -> isEnabled = ConfigKeys.WORD_GUESSER_ENABLED.getBoolean();
                case REVERSE -> isEnabled = ConfigKeys.REVERSE_ENABLED.getBoolean();
                case FILL_OUT -> isEnabled = ConfigKeys.FILL_OUT_ENABLED.getBoolean();
                case CRAFTING -> isEnabled = ConfigKeys.CRAFTING_ENABLED.getBoolean();
            }

            if (isEnabled) enabledGames.add(gameType);
        }

        return enabledGames;
    }
}