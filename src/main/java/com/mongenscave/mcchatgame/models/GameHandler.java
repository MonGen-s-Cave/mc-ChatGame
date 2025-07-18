package com.mongenscave.mcchatgame.models;

import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.manager.GameManager;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public abstract class GameHandler {
    @Getter protected GameState state = GameState.INACTIVE;
    protected Object gameData;

    public abstract void start();
    public abstract void stop();
    public abstract void handleAnswer(@NotNull Player player, @NotNull String answer);

    protected void cleanup() {
        state = GameState.INACTIVE;
        gameData = null;
        GameManager.removeInactiveGames();
    }
}
