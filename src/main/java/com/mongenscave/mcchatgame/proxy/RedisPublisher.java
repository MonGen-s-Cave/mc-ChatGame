package com.mongenscave.mcchatgame.proxy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.proxy.messages.RedisMessageType;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;

public class RedisPublisher {
    private final RedisConfig redisConfig;
    private final Gson gson;
    @Getter private final String serverId;
    private final String channelPrefix;

    public RedisPublisher(@NotNull RedisConfig redisConfig, @NotNull String serverId) {
        this.redisConfig = redisConfig;
        this.serverId = serverId;
        this.gson = new Gson();
        this.channelPrefix = McChatGame.getInstance().getConfiguration()
                .getString("redis.channel-prefix", "mcchatgame");
    }

    public void publishGameStart(@NotNull GameType gameType, @NotNull String gameData, long startTime) {
        JsonObject message = RedisMessageType.GAME_START.createBaseMessage(serverId);
        message.addProperty("gameType", gameType.name());
        message.addProperty("gameData", gameData);
        message.addProperty("startTime", startTime);

        publish(RedisMessageType.GAME_START, message);
    }

    public void publishGameStop(@NotNull GameType gameType) {
        JsonObject message = RedisMessageType.GAME_STOP.createBaseMessage(serverId);
        message.addProperty("gameType", gameType.name());

        publish(RedisMessageType.GAME_STOP, message);
    }

    public void publishGameTimeout(@NotNull GameType gameType, @NotNull String correctAnswer) {
        JsonObject message = RedisMessageType.GAME_TIMEOUT.createBaseMessage(serverId);
        message.addProperty("gameType", gameType.name());
        message.addProperty("correctAnswer", correctAnswer);

        publish(RedisMessageType.GAME_TIMEOUT, message);
    }

    public void publishPlayerAnswer(@NotNull String playerName, @NotNull String answer, @NotNull GameType gameType) {
        JsonObject message = RedisMessageType.PLAYER_ANSWER.createBaseMessage(serverId);
        message.addProperty("playerName", playerName);
        message.addProperty("answer", answer);
        message.addProperty("gameType", gameType.name());

        publish(RedisMessageType.PLAYER_ANSWER, message);
    }

    public void publishPlayerWin(@NotNull String playerName, @NotNull GameType gameType, double timeTaken) {
        JsonObject message = RedisMessageType.PLAYER_WIN.createBaseMessage(serverId);
        message.addProperty("playerName", playerName);
        message.addProperty("gameType", gameType.name());
        message.addProperty("timeTaken", timeTaken);

        publish(RedisMessageType.PLAYER_WIN, message);
    }

    private void publish(@NotNull RedisMessageType type, @NotNull JsonObject message) {
        if (!redisConfig.isConnected()) {
            LoggerUtils.warn("Cannot publish message - Redis not connected");
            return;
        }

        try (Jedis jedis = redisConfig.getJedisPool().getResource()) {
            String channel = channelPrefix + ":" + type.getChannel();
            String json = gson.toJson(message);

            jedis.publish(channel, json);

            LoggerUtils.info("Published {} message to channel {}", type.name(), channel);
        } catch (Exception exception) {
            LoggerUtils.error("Failed to publish message: " + exception.getMessage());
        }
    }
}