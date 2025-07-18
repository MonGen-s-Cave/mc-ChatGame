package com.mongenscave.mcchatgame.models;

import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.manager.GameManager;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GameHandler {
    @Getter protected GameState state = GameState.INACTIVE;
    protected Object gameData;

    private static GameHandler currentActiveGame = null;

    public abstract void start();
    public abstract void stop();
    public abstract void handleAnswer(@NotNull Player player, @NotNull String answer);

    protected void cleanup() {
        state = GameState.INACTIVE;
        gameData = null;

        if (currentActiveGame == this) currentActiveGame = null;

        GameManager.removeInactiveGames();
    }

    protected void setAsActive() {
        currentActiveGame = this;
        state = GameState.ACTIVE;
    }

    @Nullable
    public static GameHandler getCurrentActiveGame() {
        return currentActiveGame;
    }

    @Nullable
    public String getGameData() {
        return gameData != null ? gameData.toString() : null;
    }

    public abstract long getStartTime();
}
