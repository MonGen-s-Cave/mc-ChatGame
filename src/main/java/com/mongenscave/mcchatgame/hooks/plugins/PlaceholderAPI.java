package com.mongenscave.mcchatgame.hooks.plugins;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.manager.GameManager;
import com.mongenscave.mcchatgame.models.GameHandler;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Optional;

@SuppressWarnings("deprecation")
public class PlaceholderAPI {
    public static boolean isRegistered = false;

    public static void registerHook() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderIntegration().register();
            isRegistered = true;
        }
    }

    private static class PlaceholderIntegration extends PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "mcchatgame";
        }

        @Override
        public @NotNull String getAuthor() {
            return "coma112";
        }

        @Override
        public @NotNull String getVersion() {
            return McChatGame.getInstance().getDescription().getVersion();
        }

        @Override
        public boolean canRegister() {
            return true;
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(@NotNull Player player, @NotNull String params) {
            if (params.startsWith("fastest_time_player_")) return getFastestTimePlayer(params);
            if (params.startsWith("fastest_time_")) return getFastestTime(params);
            if (params.startsWith("most_win_player_")) return getMostWinPlayer(params);
            if (params.startsWith("most_win_")) return getMostWin(params);

            switch (params) {
                case "current_game" -> {
                    return getCurrentGamePlaceholder();
                }

                case "fastest_time" -> {
                    return String.valueOf(McChatGame.getInstance().getDatabase().getTime(player));
                }

                case "wins" -> {
                    return String.valueOf(McChatGame.getInstance().getDatabase().getWins(player));
                }
            }

            return "---";
        }

        private String getCurrentGamePlaceholder() {
            GameHandler activeGame = GameHandler.getCurrentActiveGame();

            if (activeGame == null || activeGame.getState() != GameState.ACTIVE) return ConfigKeys.PLACEHOLDER_NO_GAMES.getString();

            String gameType = activeGame.getClass().getSimpleName();
            String placeholder = getPlaceholderForGameType(gameType);

            if (placeholder == null) return ConfigKeys.PLACEHOLDER_NO_GAMES.getString();

            String gameWord = activeGame.getGameData();
            String timeRemaining = getTimeRemaining(activeGame);

            if (gameWord == null) return ConfigKeys.PLACEHOLDER_NO_GAMES.getString();

            return placeholder
                    .replace("{word}", gameWord)
                    .replace("{time}", timeRemaining);
        }

        @Nullable
        private String getPlaceholderForGameType(@NotNull String gameType) {
            return switch (gameType) {
                case "GameFillOut" -> ConfigKeys.PLACEHOLDER_FILL_OUT.getString();
                case "GameMath" -> ConfigKeys.PLACEHOLDER_MATH.getString();
                case "GameRandomCharacters" -> ConfigKeys.PLACEHOLDER_RANDOM.getString();
                case "GameReverse" -> ConfigKeys.PLACEHOLDER_REVERSE.getString();
                case "GameWhoAmI" -> ConfigKeys.PLACEHOLDER_WHO_AM_I.getString();
                case "GameWordGuess" -> ConfigKeys.PLACEHOLDER_WORD_GUESS.getString();
                case "GameWordStop" -> ConfigKeys.PLACEHOLDER_WORD_STOP.getString();
                default -> null;
            };
        }

        @NotNull
        private String getTimeRemaining(@NotNull GameHandler game) {
            long startTime = game.getStartTime();

            if (startTime == 0) return "---";

            int gameTimeout = getGameTimeout(game.getClass().getSimpleName());

            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            long remainingTime = (gameTimeout * 1000L) - elapsedTime;

            if (remainingTime <= 0) return "0";

            return String.format("%d", remainingTime / 1000);
        }

        private int getGameTimeout(@NotNull String gameType) {
            return switch (gameType) {
                case "GameFillOut" -> ConfigKeys.FILL_OUT_TIME.getInt();
                case "GameMath" -> ConfigKeys.MATH_TIME.getInt();
                case "GameRandomCharacters" -> ConfigKeys.RANDOM_CHARACTERS_TIME.getInt();
                case "GameReverse" -> ConfigKeys.REVERSE_TIME.getInt();
                case "GameWhoAmI" -> ConfigKeys.WHO_AM_I_TIME.getInt();
                case "GameWordGuess" -> ConfigKeys.WORD_GUESSER_TIME.getInt();
                case "GameWordStop" -> ConfigKeys.WORD_STOP_TIME.getInt();
                default -> 60;
            };
        }

        private String getFastestTimePlayer(@NotNull String params) {
            return parsePosition(params, "fastest_time_player_")
                    .map(position -> Optional.ofNullable(McChatGame.getInstance().getDatabase().getFastestTimePlayer(position).join())
                            .orElse("---"))
                    .orElse("");
        }

        private String getFastestTime(@NotNull String params) {
            return parsePosition(params, "fastest_time_")
                    .map(position -> {
                        double time = McChatGame.getInstance().getDatabase().getFastestTime(position).join();
                        return time != 0 ? String.valueOf(time) : "---";
                    })
                    .orElse("");
        }

        private String getMostWinPlayer(@NotNull String params) {
            return parsePosition(params, "most_win_player_")
                    .map(position -> Optional.ofNullable(McChatGame.getInstance().getDatabase().getMostWinsPlayer(position).join())
                            .orElse("---"))
                    .orElse("");
        }

        private String getMostWin(@NotNull String params) {
            return parsePosition(params, "most_win_")
                    .map(position -> {
                        int time = McChatGame.getInstance().getDatabase().getMostWins(position).join();
                        return time != 0 ? String.valueOf(time) : "---";
                    })
                    .orElse("");
        }

        private Optional<Integer> parsePosition(@NotNull String params, @NotNull String prefix) {
            try {
                return Optional.of(Integer.parseInt(params.substring(prefix.length())));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
    }
}