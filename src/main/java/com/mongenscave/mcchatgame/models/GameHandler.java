package com.mongenscave.mcchatgame.models;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.managers.GameManager;
import com.mongenscave.mcchatgame.managers.StreakManager;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GameHandler {
    @Getter protected GameState state = GameState.INACTIVE;
    protected Object gameData;
    protected boolean gameStarted = false;
    @Getter protected long startTime;

    private static GameHandler currentActiveGame = null;

    public abstract void start();
    public abstract void stop();
    public abstract void handleAnswer(@NotNull Player player, @NotNull String answer);
    protected abstract GameType getGameType();

    protected void cleanup() {
        state = GameState.INACTIVE;
        gameData = null;
        gameStarted = false;
        startTime = 0;

        if (currentActiveGame == this) currentActiveGame = null;

        GameManager.removeInactiveGames();

        if (McChatGame.getInstance().getProxyManager().isEnabled()) McChatGame.getInstance().getProxyManager().broadcastGameStop(getGameType());
    }

    protected void setAsActive() {
        currentActiveGame = this;
        state = GameState.ACTIVE;
        startTime = System.currentTimeMillis();

        if (!gameStarted) {
            StreakManager.getInstance().onGameStart();
            gameStarted = true;

            if (McChatGame.getInstance().getProxyManager().isEnabled()) {
                McChatGame.getInstance().getProxyManager().broadcastGameStart(
                        getGameType(),
                        getGameData() != null ? getGameData() : "",
                        startTime
                );
            }
        }
    }

    protected void handlePlayerWin(@NotNull Player winner) {
        StreakManager.getInstance().onPlayerWin(winner);

        if (McChatGame.getInstance().getProxyManager().isEnabled()) {
            long endTime = System.currentTimeMillis();
            double timeTaken = (endTime - startTime) / 1000.0;
            McChatGame.getInstance().getProxyManager().broadcastPlayerWin(winner, getGameType(), timeTaken);
        }
    }

    protected void handleGameTimeout() {
        StreakManager.getInstance().onGameTimeout();

        if (McChatGame.getInstance().getProxyManager().isEnabled()) McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), "N/A");
    }

    @Nullable
    public static GameHandler getCurrentActiveGame() {
        return currentActiveGame;
    }

    @Nullable
    public String getGameData() {
        return gameData != null ? gameData.toString() : null;
    }
}