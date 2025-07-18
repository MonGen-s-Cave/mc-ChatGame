package com.mongenscave.mcchatgame.hooks.plugins;

import com.mongenscave.mcchatgame.McChatGame;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
                case "fastest_time" -> {
                    return String.valueOf(McChatGame.getInstance().getDatabase().getTime(player));
                }

                case "wins" -> {
                    return String.valueOf(McChatGame.getInstance().getDatabase().getWins(player));
                }
            }

            return "---";
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
