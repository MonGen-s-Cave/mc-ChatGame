package com.mongenscave.mcchatgame.managers;

import com.google.gson.JsonObject;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
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

    public ProxyManager(@NotNull McChatGame plugin) {
        this.plugin = plugin;
        this.serverId = plugin.getConfiguration().getString("redis.server-id", plugin.getServer().getPort() + "-" + System.currentTimeMillis());
        this.redisConfig = new RedisConfig(plugin);
        this.publisher = new RedisPublisher(redisConfig, serverId);
        this.subscriber = new RedisSubscriber(redisConfig, this, serverId);
        this.enabled = false;
    }

    public void initialize() {
        if (!plugin.getConfiguration().getBoolean("redis.enabled", false)) {
            LoggerUtils.info("Redis cross-server support is disabled");
            return;
        }

        if (!redisConfig.connect()) {
            LoggerUtils.error("Failed to connect to Redis - cross-server features disabled");
            return;
        }

        subscriber.subscribe();
        enabled = true;

        LoggerUtils.info("Redis cross-server support enabled (Server ID: {})", serverId);
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

    public void broadcastPlayerAnswer(@NotNull Player player, @NotNull String answer, @NotNull GameType gameType) {
        if (!enabled) return;
        publisher.publishPlayerAnswer(player.getName(), answer, gameType);
    }

    public void broadcastPlayerWin(@NotNull Player player, @NotNull GameType gameType, double timeTaken) {
        if (!enabled) return;
        publisher.publishPlayerWin(player.getName(), gameType, timeTaken);
    }

    public void handleRemoteGameStart() {
        plugin.getScheduler().runTask(GameManager::stopAllGames);
    }

    public void handleRemoteGameStop(@NotNull GameType gameType) {
        plugin.getScheduler().runTask(() -> GameManager.stopGame(gameType));
    }

    public void handleRemoteGameTimeout(@NotNull GameType gameType, @NotNull String correctAnswer) {
        plugin.getScheduler().runTask(() -> {
            String messageKey = getTimeoutMessageKey(gameType);
            String message = MessageKeys.valueOf(messageKey).getMessage().replace("{answer}", correctAnswer);
            GameUtils.broadcast(message);

            LoggerUtils.info("Remote game timeout: {} (answer: {})", gameType, correctAnswer);
        });
    }

    public void handleRemotePlayerWin(@NotNull String playerName, @NotNull GameType gameType, double timeTaken) {
        plugin.getScheduler().runTask(() -> {
            String messageKey = getWinMessageKey(gameType);
            String formattedTime = String.format("%.2f", timeTaken);
            String message = MessageKeys.valueOf(messageKey).getMessage()
                    .replace("{player}", playerName)
                    .replace("{time}", formattedTime);

            GameUtils.broadcast(message);
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