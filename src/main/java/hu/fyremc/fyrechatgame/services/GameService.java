package hu.fyremc.fyrechatgame.services;

import hu.fyremc.fyrechatgame.database.Database;
import hu.fyremc.fyrechatgame.utils.LoggerUtils;
import org.bson.Document;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameService {
    private final Database database;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public GameService(@NotNull Database database) {
        this.database = database;
    }

    public void createPlayer(@NotNull Player player) {
        CompletableFuture.runAsync(() -> {
            Document playerDocument = new Document()
                    .append("name", player.getName())
                    .append("wins", 0)
                    .append("fastest_time", Double.MAX_VALUE);
            database.insertDocument("players", playerDocument);
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return null;
        });
    }

    public CompletableFuture<Boolean> exists(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> database.documentExists("players", "name", player.getName()), virtualThreadExecutor);
    }

    public CompletableFuture<Void> incrementWin(@NotNull Player player) {
        return CompletableFuture.runAsync(() -> {
            Document filter = new Document("name", player.getName());
            Document update = new Document("$inc", new Document("wins", 1));
            database.getDatabase().getCollection("players").updateOne(filter, update);
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> setTime(@NotNull Player player, double newTime) {
        return getTime(player).thenAccept(currentTime -> {
            if (newTime < currentTime) {
                CompletableFuture.runAsync(() -> {
                    Document filter = new Document("name", player.getName());
                    Document update = new Document("$set", new Document("fastest_time", newTime));
                    database.getDatabase().getCollection("players").updateOne(filter, update);
                }, virtualThreadExecutor);
            }
        });
    }

    public CompletableFuture<Double> getTime(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> {
            Document query = new Document("name", player.getName());
            Document result = database.getDatabase().getCollection("players")
                    .find(query)
                    .projection(new Document("fastest_time", 1))
                    .first();
            return result != null ? result.getDouble("fastest_time") : Double.MAX_VALUE;
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return Double.MAX_VALUE;
        });
    }

    public CompletableFuture<Integer> getWins(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> {
            Document query = new Document("name", player.getName());
            Document result = database.getDatabase().getCollection("players")
                    .find(query)
                    .projection(new Document("wins", 1))
                    .first();
            return result != null ? result.getInteger("wins") : 0;
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return 0;
        });
    }

    public CompletableFuture<String> getFastestTimePlayer(int position) {
        return CompletableFuture.supplyAsync(() -> {
            Document result = database.getDatabase().getCollection("players")
                    .find()
                    .sort(new Document("fastest_time", 1))
                    .skip(position - 1)
                    .limit(1)
                    .first();
            return (result != null && result.getDouble("fastest_time") != Double.MAX_VALUE) ? result.getString("name") : "---";
        }, virtualThreadExecutor);
    }

    public CompletableFuture<Double> getFastestTime(int position) {
        return CompletableFuture.supplyAsync(() -> {
            Document result = database.getDatabase().getCollection("players")
                    .find()
                    .sort(new Document("fastest_time", 1))
                    .skip(position - 1)
                    .limit(1)
                    .first();
            return (result != null && result.getDouble("fastest_time") != Double.MAX_VALUE) ? result.getDouble("fastest_time") : 0.00;
        }, virtualThreadExecutor);
    }

    public CompletableFuture<String> getMostWinsPlayer(int position) {
        return CompletableFuture.supplyAsync(() -> {
            Document result = database.getDatabase().getCollection("players")
                    .find()
                    .sort(new Document("wins", -1))
                    .skip(position - 1)
                    .limit(1)
                    .first();
            return result != null ? result.getString("name") : "---";
        }, virtualThreadExecutor);
    }

    public CompletableFuture<Integer> getMostWins(int position) {
        return CompletableFuture.supplyAsync(() -> {
            Document result = database.getDatabase().getCollection("players")
                    .find()
                    .sort(new Document("wins", -1))
                    .skip(position - 1)
                    .limit(1)
                    .first();
            return result != null ? result.getInteger("wins") : 0;
        }, virtualThreadExecutor);
    }

    public void shutdown() {
        virtualThreadExecutor.shutdown();
    }
}