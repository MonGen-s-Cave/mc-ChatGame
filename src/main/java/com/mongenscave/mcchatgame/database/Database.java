package com.mongenscave.mcchatgame.database;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface Database {
    CompletableFuture<Void> initialize();

    void createPlayer(@NotNull Player player);

    CompletableFuture<Boolean> exists(@NotNull Player player);

    CompletableFuture<Void> incrementWin(@NotNull Player player);

    CompletableFuture<Void> setTime(@NotNull Player player, double newTime);

    CompletableFuture<Double> getTime(@NotNull Player player);

    CompletableFuture<Integer> getWins(@NotNull Player player);

    CompletableFuture<String> getFastestTimePlayer(int position);

    CompletableFuture<Double> getFastestTime(int position);

    CompletableFuture<String> getMostWinsPlayer(int position);

    CompletableFuture<Integer> getMostWins(int position);

    CompletableFuture<Integer> getCurrentStreak(@NotNull Player player);

    CompletableFuture<Integer> getBestStreak(@NotNull Player player);

    CompletableFuture<Void> incrementStreak(@NotNull Player player);

    CompletableFuture<Void> resetStreak(@NotNull Player player);

    CompletableFuture<Void> resetAllStreaks();

    void shutdown();
}
