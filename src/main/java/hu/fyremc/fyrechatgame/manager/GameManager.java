package hu.fyremc.fyrechatgame.manager;

import hu.fyremc.fyrechatgame.handler.GameHandler;
import hu.fyremc.fyrechatgame.identifiers.GameState;
import hu.fyremc.fyrechatgame.identifiers.GameTypes;
import hu.fyremc.fyrechatgame.models.GameFillOut;
import hu.fyremc.fyrechatgame.models.GameMath;
import hu.fyremc.fyrechatgame.models.GameRandomCharacters;
import hu.fyremc.fyrechatgame.models.GameReverse;
import hu.fyremc.fyrechatgame.models.GameWhoAmI;
import hu.fyremc.fyrechatgame.models.GameWordGuess;
import hu.fyremc.fyrechatgame.models.GameWordStop;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

public class GameManager {
    private static final Map<GameTypes, GameHandler> ACTIVE_GAMES = new EnumMap<>(GameTypes.class);

    public static void startGame(@NotNull GameTypes type) {
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
        ACTIVE_GAMES.entrySet().removeIf(entry -> entry.getValue().getState() == GameState.INACTIVE);
    }
}
