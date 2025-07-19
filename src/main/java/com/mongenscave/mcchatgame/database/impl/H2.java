package com.mongenscave.mcchatgame.database.impl;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.database.Database;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class H2 implements Database {
    private HikariDataSource dataSource;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public H2() {
        setupDataSource();
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:h2:file:" + McChatGame.getInstance().getDataFolder().getAbsolutePath() + "/chatgame;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                createTables();
            } catch (SQLException exception) {
                LoggerUtils.error(exception.getMessage());
            }
        });
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String gamePlayersTable = """
                    CREATE TABLE IF NOT EXISTS game_players (
                        name VARCHAR(16) PRIMARY KEY,
                        wins INTEGER NOT NULL DEFAULT 0,
                        fastest_time DOUBLE NOT NULL DEFAULT 999999.99,
                        current_streak INTEGER NOT NULL DEFAULT 0,
                        best_streak INTEGER NOT NULL DEFAULT 0
                    )
                    """;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(gamePlayersTable);

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_wins ON game_players(wins)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_fastest_time ON game_players(fastest_time)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_current_streak ON game_players(current_streak)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_best_streak ON game_players(best_streak)");

                // Add streak columns if they don't exist (for existing databases)
                try {
                    stmt.execute("ALTER TABLE game_players ADD COLUMN IF NOT EXISTS current_streak INTEGER NOT NULL DEFAULT 0");
                    stmt.execute("ALTER TABLE game_players ADD COLUMN IF NOT EXISTS best_streak INTEGER NOT NULL DEFAULT 0");
                } catch (SQLException e) {
                    // Columns might already exist, ignore
                }
            }
        }
    }

    @Override
    public void createPlayer(@NotNull Player player) {
        CompletableFuture.runAsync(() -> {
            String sql = "MERGE INTO game_players (name, wins, fastest_time, current_streak, best_streak) VALUES (?, 0, 999999.99, 0, 0)";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                stmt.executeUpdate();
            } catch (SQLException exception) {
                LoggerUtils.error("Error creating player: " + exception.getMessage());
            }
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM game_players WHERE name = ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error checking player existence: " + exception.getMessage());
                return false;
            }
        }, virtualThreadExecutor);
    }

    @Override
    public CompletableFuture<Void> incrementWin(@NotNull Player player) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE game_players SET wins = wins + 1 WHERE name = ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                stmt.executeUpdate();
            } catch (SQLException exception) {
                LoggerUtils.error("Error incrementing wins: " + exception.getMessage());
            }
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> setTime(@NotNull Player player, double newTime) {
        return getTime(player).thenAccept(currentTime -> {
            if (newTime < currentTime) {
                CompletableFuture.runAsync(() -> {
                    String sql = "UPDATE game_players SET fastest_time = ? WHERE name = ?";

                    try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setDouble(1, newTime);
                        stmt.setString(2, player.getName());
                        stmt.executeUpdate();
                    } catch (SQLException exception) {
                        LoggerUtils.error("Error setting time: " + exception.getMessage());
                    }
                }, virtualThreadExecutor);
            }
        });
    }

    @Override
    public CompletableFuture<Double> getTime(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT fastest_time FROM game_players WHERE name = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getDouble("fastest_time");
                    return 999999.99;
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error getting time: " + exception.getMessage());
                return 999999.99;
            }
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return 999999.99;
        });
    }

    @Override
    public CompletableFuture<Integer> getWins(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT wins FROM game_players WHERE name = ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("wins");
                    return 0;
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error getting wins: " + exception.getMessage());
                return 0;
            }
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return 0;
        });
    }

    @Override
    public CompletableFuture<String> getFastestTimePlayer(int position) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT name FROM game_players WHERE fastest_time < 999999.99 ORDER BY fastest_time ASC LIMIT 1 OFFSET ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, position - 1);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getString("name");
                    return "---";
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error getting fastest time player: " + exception.getMessage());
                return "---";
            }
        }, virtualThreadExecutor);
    }

    @Override
    public CompletableFuture<Double> getFastestTime(int position) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT fastest_time FROM game_players WHERE fastest_time < 999999.99 ORDER BY fastest_time ASC LIMIT 1 OFFSET ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, position - 1);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getDouble("fastest_time");
                    return 0.00;
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error getting fastest time: " + exception.getMessage());
                return 0.00;
            }
        }, virtualThreadExecutor);
    }

    @Override
    public CompletableFuture<String> getMostWinsPlayer(int position) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT name FROM game_players ORDER BY wins DESC LIMIT 1 OFFSET ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, position - 1);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getString("name");
                    return "---";
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error getting most wins player: " + exception.getMessage());
                return "---";
            }
        }, virtualThreadExecutor);
    }

    @Override
    public CompletableFuture<Integer> getMostWins(int position) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT wins FROM game_players ORDER BY wins DESC LIMIT 1 OFFSET ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, position - 1);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("wins");
                    return 0;
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error getting most wins: " + exception.getMessage());
                return 0;
            }
        }, virtualThreadExecutor);
    }

    @Override
    public CompletableFuture<Integer> getCurrentStreak(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT current_streak FROM game_players WHERE name = ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("current_streak");
                    return 0;
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error getting current streak: " + exception.getMessage());
                return 0;
            }
        }, virtualThreadExecutor);
    }

    @Override
    public CompletableFuture<Integer> getBestStreak(@NotNull Player player) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT best_streak FROM game_players WHERE name = ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt("best_streak");
                    return 0;
                }
            } catch (SQLException exception) {
                LoggerUtils.error("Error getting best streak: " + exception.getMessage());
                return 0;
            }
        }, virtualThreadExecutor);
    }

    @Override
    public CompletableFuture<Void> incrementStreak(@NotNull Player player) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE game_players SET current_streak = current_streak + 1, best_streak = GREATEST(best_streak, current_streak + 1) WHERE name = ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                stmt.executeUpdate();
            } catch (SQLException exception) {
                LoggerUtils.error("Error incrementing streak: " + exception.getMessage());
            }
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> resetStreak(@NotNull Player player) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE game_players SET current_streak = 0 WHERE name = ?";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getName());
                stmt.executeUpdate();
            } catch (SQLException exception) {
                LoggerUtils.error("Error resetting streak: " + exception.getMessage());
            }
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> resetAllStreaks() {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE game_players SET current_streak = 0";

            try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            } catch (SQLException exception) {
                LoggerUtils.error("Error resetting all streaks: " + exception.getMessage());
            }
        }, virtualThreadExecutor).exceptionally(exception -> {
            LoggerUtils.error(exception.getMessage());
            return null;
        });
    }

    @Override
    public void shutdown() {
        CompletableFuture.runAsync(() -> {
            virtualThreadExecutor.shutdown();
            if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        });
    }
}