package com.mongenscave.mcchatgame.managers;

import com.google.gson.JsonObject;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
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
        this.serverId = plugin.getConfiguration().getString("redis.server-id",
                plugin.getServer().getPort() + "-" + System.currentTimeMillis());
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

    public void shutdown() {
        if (enabled) {
            subscriber.shutdownSubscriber();
            redisConfig.disconnect();
            enabled = false;
            LoggerUtils.info("Redis cross-server support disabled");
        }
    }

    public void broadcastGameStart(@NotNull GameType gameType, @NotNull String gameData, long startTime) {
        if (!enabled || !isMasterServer) return;
        LoggerUtils.info("Broadcasting GAME_START - Type: {}, Data: {}", gameType, gameData);
        publisher.publishGameStart(gameType, gameData, startTime);
    }

    public void broadcastGameStop(@NotNull GameType gameType) {
        if (!enabled || !isMasterServer) return;
        publisher.publishGameStop(gameType);
    }

    public void broadcastGameTimeout(@NotNull GameType gameType, @NotNull String correctAnswer) {
        if (!enabled || !isMasterServer) return;
        LoggerUtils.info("Broadcasting GAME_TIMEOUT - Type: {}, Answer: {}", gameType, correctAnswer);
        publisher.publishGameTimeout(gameType, correctAnswer);
    }

    public void broadcastPlayerWin(@NotNull Player player, @NotNull GameType gameType, double timeTaken) {
        if (!enabled || !isMasterServer) return;
        LoggerUtils.info("Broadcasting PLAYER_WIN - Player: {}, Type: {}, Time: {}",
                player.getName(), gameType, timeTaken);
        publisher.publishPlayerWin(player.getName(), gameType, timeTaken);
    }

    public void handleRemoteGameStart(@NotNull GameType gameType, @NotNull String gameData, long startTime) {
        LoggerUtils.info("=== HANDLE REMOTE GAME START ===");
        LoggerUtils.info("Game Type: {}", gameType);
        LoggerUtils.info("Game Data: '{}'", gameData);
        LoggerUtils.info("Start Time: {}", startTime);

        plugin.getScheduler().runTask(() -> {
            LoggerUtils.info("Stopping all existing games...");
            GameManager.stopAllGames();

            LoggerUtils.info("Creating game handler for: {}", gameType);
            GameHandler handler = createGameHandler(gameType);

            LoggerUtils.info("Starting game as remote with data: '{}'", gameData);
            handler.startAsRemote(startTime, gameData);

            LoggerUtils.info("Adding game to GameManager...");
            GameManager.addGame(gameType, handler);

            LoggerUtils.info("✓ Remote game started successfully!");
        });
    }

    public void handleRemoteGameStop(@NotNull GameType gameType) {
        plugin.getScheduler().runTask(() -> {
            LoggerUtils.info("Remote game stop received: {}", gameType);
            GameManager.stopGame(gameType);
        });
    }

    public void handleRemoteGameTimeout(@NotNull GameType gameType, @NotNull String correctAnswer) {
        plugin.getScheduler().runTask(() -> {
            LoggerUtils.info("Remote game timeout - Type: {}, Answer: {}", gameType, correctAnswer);

            String message = getTimeoutMessage(gameType, correctAnswer);
            GameUtils.broadcast(message);

            GameManager.stopGame(gameType);
        });
    }

    public void handleRemotePlayerWin(@NotNull String playerName, @NotNull GameType gameType, double timeTaken) {
        plugin.getScheduler().runTask(() -> {
            String formattedTime = String.format("%.2f", timeTaken);
            String message = getWinMessage(gameType)
                    .replace("{player}", playerName)
                    .replace("{time}", formattedTime);

            if (gameType == GameType.RANGE) {
                message = message.replace("{number}", "?");
            }

            GameUtils.broadcast(message);
            LoggerUtils.info("Remote player win broadcast: {} in {} ({}s)", playerName, gameType, formattedTime);

            GameManager.stopGame(gameType);
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

    @NotNull
    private String getWinMessage(@NotNull GameType gameType) {
        return switch (gameType) {
            case MATH -> MessageKeys.MATH_GAME_WIN.getMessage();
            case WHO_AM_I -> MessageKeys.WHO_AM_I_WIN.getMessage();
            case WORD_GUESSER -> MessageKeys.WORD_GUESSER_WIN.getMessage();
            case RANDOM_CHARACTERS -> MessageKeys.RANDOM_CHARACTERS_WIN.getMessage();
            case WORD_STOP -> MessageKeys.WORD_STOP_WIN.getMessage();
            case REVERSE -> MessageKeys.REVERSE_WIN.getMessage();
            case FILL_OUT -> MessageKeys.FILL_OUT_WIN.getMessage();
            case CRAFTING -> MessageKeys.CRAFTING_WIN.getMessage();
            case HANGMAN -> MessageKeys.HANGMAN_WIN.getMessage();
            case RANGE -> MessageKeys.RANGE_WIN.getMessage();
        };
    }

    @NotNull
    private String getTimeoutMessage(@NotNull GameType gameType, @NotNull String correctAnswer) {
        String message = switch (gameType) {
            case MATH -> MessageKeys.MATH_GAME_NO_WIN.getMessage();
            case WHO_AM_I -> MessageKeys.WHO_AM_I_NO_WIN.getMessage();
            case WORD_GUESSER -> MessageKeys.WORD_GUESSER_NO_WIN.getMessage();
            case RANDOM_CHARACTERS -> MessageKeys.RANDOM_CHARACTERS_NO_WIN.getMessage();
            case WORD_STOP -> MessageKeys.WORD_STOP_NO_WIN.getMessage();
            case REVERSE -> MessageKeys.REVERSE_NO_WIN.getMessage();
            case FILL_OUT -> MessageKeys.FILL_OUT_NO_WIN.getMessage();
            case CRAFTING -> MessageKeys.CRAFTING_NO_WIN.getMessage();
            case HANGMAN -> MessageKeys.HANGMAN_NO_WIN.getMessage();
            case RANGE -> MessageKeys.RANGE_NO_WIN.getMessage();
        };

        message = message.replace("{answer}", correctAnswer);
        message = message.replace("{word}", correctAnswer);

        if (gameType == GameType.RANGE) {
            String rangeConfig = plugin.getConfiguration().getString("range.range", "0-20");
            String[] rangeParts = rangeConfig.split("-");
            String min = rangeParts.length > 0 ? rangeParts[0] : "0";
            String max = rangeParts.length > 1 ? rangeParts[1] : "20";
            message = message.replace("{min}", min).replace("{max}", max);
        }

        return message;
    }
}