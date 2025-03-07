package hu.fyremc.fyrechatgame.handler;

import hu.fyremc.fyrechatgame.identifiers.GameState;
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
    }
}
