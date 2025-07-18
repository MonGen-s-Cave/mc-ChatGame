package com.mongenscave.mcchatgame.manager;

import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.models.impl.GameFillOut;
import com.mongenscave.mcchatgame.models.impl.GameMath;
import com.mongenscave.mcchatgame.models.impl.GameRandomCharacters;
import com.mongenscave.mcchatgame.models.impl.GameReverse;
import com.mongenscave.mcchatgame.models.impl.GameWhoAmI;
import com.mongenscave.mcchatgame.models.impl.GameWordGuess;
import com.mongenscave.mcchatgame.models.impl.GameWordStop;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class GameManager {
    private static final Map<GameType, GameHandler> ACTIVE_GAMES = new EnumMap<>(GameType.class);
    @Getter private static long lastGameEndTime = 0;

    public static void startGame(@NotNull GameType type) {
        if (ACTIVE_GAMES.containsKey(type)) return;

        GameHandler handler = switch (type) {
            case MATH -> new GameMath();
            case WHO_AM_I -> new GameWhoAmI();
            case WORD_GUESSER -> new GameWordGuess();
            case RANDOM_CHARACTERS -> new GameRandomCharacters();
            case WORD_STOP -> new GameWordStop();
            case REVERSE -> new GameReverse();
            case FILL_OUT -> new GameFillOut();
        };

        handler.start();
        ACTIVE_GAMES.put(type, handler);
    }

    public static void handleAnswer(@NotNull Player player, @NotNull String answer) {
        ACTIVE_GAMES.values().forEach(handler -> {
            if (handler.getState() == GameState.ACTIVE) handler.handleAnswer(player, answer);
        });
    }

    public static int getActiveGameCount() {
        return (int) ACTIVE_GAMES.values().stream()
                .filter(handler -> handler.getState() == GameState.ACTIVE)
                .count();
    }

    public static void removeInactiveGames() {
        for (Map.Entry<GameType, GameHandler> entry : ACTIVE_GAMES.entrySet()) {
            if (entry.getValue().getState() == GameState.INACTIVE) {
                lastGameEndTime = System.currentTimeMillis();
                break;
            }
        }

        ACTIVE_GAMES.entrySet().removeIf(entry -> entry.getValue().getState() == GameState.INACTIVE);
    }
}