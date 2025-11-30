package com.mongenscave.mcchatgame.managers;

import com.google.gson.JsonObject;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.models.impl.*;
import com.mongenscave.mcchatgame.proxy.RedisConfig;
import com.mongenscave.mcchatgame.proxy.RedisPublisher;
import com.mongenscave.mcchatgame.proxy.RedisSubscriber;
import com.mongenscave.mcchatgame.utils.GameUtils;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ProxyManager {
    private final McChatGame plugin;
    @Getter private final RedisConfig redisConfig;
    @Getter private final RedisPublisher publisher;
    @Getter private final RedisSubscriber subscriber;
    @Getter private final String serverId;
    @Getter private boolean enabled;
    @Getter private boolean isMasterServer;

    public ProxyManager(@NotNull McChatGame plugin) {
        this.plugin = plugin;
        this.serverId = plugin.getConfiguration().getString("redis.server-id", plugin.getServer().getPort() + "-" + System.currentTimeMillis());
        this.redisConfig = new RedisConfig(plugin);
        this.publisher = new RedisPublisher(redisConfig, serverId);
        this.subscriber = new RedisSubscriber(redisConfig, this, serverId);
        this.enabled = false;
        this.isMasterServer = false;
    }

    public void initialize() {
        if (!plugin.getConfiguration().getBoolean("redis.enabled", false)) {
            LoggerUtils.info("Redis cross-server support is disabled");
            enabled = false;
            isMasterServer = true;
            return;
        }

        String serverRole = plugin.getConfiguration().getString("redis.server-role", "master").toLowerCase();
        isMasterServer = serverRole.equals("master");

        if (!redisConfig.connect()) {
            LoggerUtils.error("Failed to connect to Redis - cross-server features disabled");
            enabled = false;
            isMasterServer = true;
            return;
        }

        if (!redisConfig.isConnected()) {
            LoggerUtils.error("Redis connection test failed");
            enabled = false;
            isMasterServer = true;
            return;
        }

        LoggerUtils.info("Redis connection successful, starting subscriber...");

        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}

        subscriber.subscribe();
        enabled = true;

        LoggerUtils.info("Redis cross-server support enabled");
        LoggerUtils.info("Server ID: {}", serverId);
        LoggerUtils.info("Server Role: {}", isMasterServer ? "MASTER (starts games)" : "SLAVE (receives games)");
    }

    public void testConnection() {
        if (!enabled) {
            LoggerUtils.warn("Cannot test connection - Redis is not enabled");
            return;
        }

        try {
            publisher.publishGameStart(GameType.MATH, "TEST", System.currentTimeMillis());
            LoggerUtils.info("Test message published successfully");
        } catch (Exception exception) {
            LoggerUtils.error("Test message failed: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    public void shutdown() {
        if (enabled) {
            subscriber.shutdownSubscriber();
            redisConfig.disconnect();
            enabled = false;
            LoggerUtils.info("Redis cross-server support disabled");
        }
    }

    public void broadcastGameStart(@NotNull GameType gameType, @NotNull String gameData, long startTime) {
        if (!enabled) return;
        LoggerUtils.info("=== BROADCASTING GAME START ===");
        LoggerUtils.info("Type: {}", gameType);
        LoggerUtils.info("Data: {}", gameData);
        LoggerUtils.info("Time: {}", startTime);
        LoggerUtils.info("==============================");
        publisher.publishGameStart(gameType, gameData, startTime);
    }

    public void broadcastGameStop(@NotNull GameType gameType) {
        if (!enabled) return;
        publisher.publishGameStop(gameType);
    }

    public void broadcastGameTimeout(@NotNull GameType gameType, @NotNull String correctAnswer) {
        if (!enabled) return;
        publisher.publishGameTimeout(gameType, correctAnswer);
    }

    public void broadcastPlayerWin(@NotNull Player player, @NotNull GameType gameType, double timeTaken) {
        if (!enabled) return;
        publisher.publishPlayerWin(player.getName(), gameType, timeTaken);
    }

    public void handleRemoteGameStart(@NotNull GameType gameType, @NotNull String gameData, long startTime) {
        LoggerUtils.info("=== HANDLE REMOTE GAME START (ENTRY) ===");
        LoggerUtils.info("Thread: {}", Thread.currentThread().getName());
        LoggerUtils.info("Game Type: {}", gameType);
        LoggerUtils.info("Game Data: {}", gameData);
        LoggerUtils.info("Start Time: {}", startTime);
        LoggerUtils.info("Is Primary Thread: {}", plugin.getServer().isPrimaryThread());

        plugin.getScheduler().runTask(() -> {
            LoggerUtils.info("=== HANDLE REMOTE GAME START (SCHEDULER TASK) ===");
            LoggerUtils.info("Thread: {}", Thread.currentThread().getName());
            LoggerUtils.info("Is Primary Thread NOW: {}", plugin.getServer().isPrimaryThread());

            LoggerUtils.info("Received remote game start: {} with data: '{}'", gameType, gameData);

            LoggerUtils.info("Stopping all existing games...");
            GameManager.stopAllGames();

            boolean wasEnabled = this.enabled;
            LoggerUtils.info("Temporarily disabling Redis (was: {})", wasEnabled);
            this.enabled = false;

            try {
                LoggerUtils.info("Creating game handler for: {}", gameType);
                GameHandler handler = createGameHandler(gameType);

                LoggerUtils.info("Starting game as remote...");
                LoggerUtils.info("  - Remote Start Time: {}", startTime);
                LoggerUtils.info("  - Game Data: {}", gameData);

                handler.startAsRemote(startTime, gameData);

                LoggerUtils.info("Adding game to GameManager...");
                GameManager.addGame(gameType, handler);

                LoggerUtils.info("✓ Remote game started successfully!");
            } catch (Exception e) {
                LoggerUtils.error("✗ ERROR starting remote game: " + e.getMessage());
                e.printStackTrace();
            } finally {
                LoggerUtils.info("Re-enabling Redis (to: {})", wasEnabled);
                this.enabled = wasEnabled;
            }

            LoggerUtils.info("=================================================");
        });

        LoggerUtils.info("======================================");
    }

    public void handleRemoteGameStop(@NotNull GameType gameType) {
        plugin.getScheduler().runTask(() -> GameManager.stopGame(gameType));
    }

    public void handleRemoteGameTimeout(@NotNull GameType gameType, @NotNull String correctAnswer) {
        plugin.getScheduler().runTask(() -> {
            String messageKey = getTimeoutMessageKey(gameType);
            String message = MessageKeys.valueOf(messageKey).getMessage();

            // CRITICAL FIX: Handle ALL game types properly
            switch (gameType) {
                case RANGE -> {
                    // RANGE needs {answer}, {min}, {max}
                    String rangeConfig = plugin.getConfiguration().getString("range.range", "0-20");
                    String[] rangeParts = rangeConfig.split("-");
                    String min = rangeParts.length > 0 ? rangeParts[0] : "0";
                    String max = rangeParts.length > 1 ? rangeParts[1] : "20";

                    message = message.replace("{answer}", correctAnswer)
                            .replace("{min}", min)
                            .replace("{max}", max);
                }
                case MATH, WHO_AM_I, WORD_STOP, WORD_GUESSER, REVERSE, FILL_OUT, HANGMAN -> {
                    // These games only need {answer}
                    message = message.replace("{answer}", correctAnswer);
                }
                case RANDOM_CHARACTERS, CRAFTING -> {
                    // These games don't have answer in the message
                    // No placeholder replacement needed
                }
            }

            LoggerUtils.info("Broadcasting timeout message: {}", message);
            GameUtils.broadcast(message);
            LoggerUtils.info("Remote game timeout: {} (answer: {})", gameType, correctAnswer);
        });
    }

    @NotNull
    private GameHandler createGameHandler(@NotNull GameType gameType) {
        return switch (gameType) {
            case MATH -> new GameMath();
            case WHO_AM_I -> new GameWhoAmI();
            case WORD_GUESSER -> new GameWordGuess();
            case RANDOM_CHARACTERS -> new GameRandomCharacters();
            case WORD_STOP -> new GameWordStop();
            case REVERSE -> new GameReverse();
            case FILL_OUT -> new GameFillOut();
            case CRAFTING -> new GameCrafting();
            case HANGMAN -> new GameHangman();
            case RANGE -> new GameRange();
        };
    }

    public void handleRemotePlayerWin(@NotNull String playerName, @NotNull GameType gameType, double timeTaken) {
        plugin.getScheduler().runTask(() -> {
            String messageKey = getWinMessageKey(gameType);
            String formattedTime = String.format("%.2f", timeTaken);
            String message = MessageKeys.valueOf(messageKey).getMessage()
                    .replace("{player}", playerName)
                    .replace("{time}", formattedTime);

            // CRITICAL: Broadcast win message on ALL servers (both master and slave)
            GameUtils.broadcast(message);

            LoggerUtils.info("Remote player win: {} in {} ({}s)", playerName, gameType, formattedTime);
        });
    }

    public void handleRemoteBroadcast(@NotNull String messageKey, @NotNull JsonObject placeholders) {
        plugin.getScheduler().runTask(() -> {
            try {
                String message = MessageKeys.valueOf(messageKey).getMessage();

                for (String key : placeholders.keySet()) {
                    message = message.replace("{" + key + "}", placeholders.get(key).getAsString());
                }

                GameUtils.broadcast(message);
            } catch (Exception exception) {
                LoggerUtils.error("Error handling remote broadcast: " + exception.getMessage());
            }
        });
    }

    public void handleRemoteSound(@NotNull String sound) {
        plugin.getScheduler().runTask(() -> {
            try {
                Sound soundEnum = Sound.valueOf(sound);
                Bukkit.getOnlinePlayers().forEach(player ->
                        player.playSound(player.getLocation(), soundEnum, 0.5f, 1.0f)
                );
            } catch (Exception exception) {
                LoggerUtils.error("Invalid sound: " + sound);
            }
        });
    }

    @NotNull
    private String getWinMessageKey(@NotNull GameType gameType) {
        return switch (gameType) {
            case MATH -> "MATH_GAME_WIN";
            case WHO_AM_I -> "WHO_AM_I_WIN";
            case WORD_GUESSER -> "WORD_GUESSER_WIN";
            case RANDOM_CHARACTERS -> "RANDOM_CHARACTERS_WIN";
            case WORD_STOP -> "WORD_STOP_WIN";
            case REVERSE -> "REVERSE_WIN";
            case FILL_OUT -> "FILL_OUT_WIN";
            case CRAFTING -> "CRAFTING_WIN";
            case HANGMAN -> "HANGMAN_WIN";
            case RANGE -> "RANGE_WIN";
        };
    }

    @NotNull
    private String getTimeoutMessageKey(@NotNull GameType gameType) {
        return switch (gameType) {
            case MATH -> "MATH_GAME_NO_WIN";
            case WHO_AM_I -> "WHO_AM_I_NO_WIN";
            case WORD_GUESSER -> "WORD_GUESSER_NO_WIN";
            case RANDOM_CHARACTERS -> "RANDOM_CHARACTERS_NO_WIN";
            case WORD_STOP -> "WORD_STOP_NO_WIN";
            case REVERSE -> "REVERSE_NO_WIN";
            case FILL_OUT -> "FILL_OUT_NO_WIN";
            case CRAFTING -> "CRAFTING_NO_WIN";
            case HANGMAN -> "HANGMAN_NO_WIN";
            case RANGE -> "RANGE_NO_WIN";
        };
    }
}