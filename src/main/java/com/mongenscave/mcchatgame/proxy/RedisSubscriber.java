package com.mongenscave.mcchatgame.proxy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.managers.ProxyManager;
import com.mongenscave.mcchatgame.proxy.messages.RedisMessageType;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisSubscriber extends JedisPubSub {
    private final RedisConfig redisConfig;
    private final ProxyManager proxyManager;
    private final Gson gson;
    private final String serverId;
    private final String channelPrefix;
    private final ExecutorService executorService;
    private Thread subscriberThread;

    public RedisSubscriber(@NotNull RedisConfig redisConfig, @NotNull ProxyManager proxyManager, @NotNull String serverId) {
        this.redisConfig = redisConfig;
        this.proxyManager = proxyManager;
        this.serverId = serverId;
        this.gson = new Gson();
        this.channelPrefix = McChatGame.getInstance().getConfiguration()
                .getString("redis.channel-prefix", "mcchatgame");
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void subscribe() {
        if (subscriberThread != null && subscriberThread.isAlive()) {
            LoggerUtils.warn("Subscriber already running");
            return;
        }

        subscriberThread = Thread.ofVirtual().start(() -> {
            Jedis jedis = null;
            try {
                jedis = redisConfig.getJedisPool().getResource();

                String[] channels = new String[RedisMessageType.values().length];
                for (int i = 0; i < RedisMessageType.values().length; i++) {
                    channels[i] = channelPrefix + ":" + RedisMessageType.values()[i].getChannel();
                }

                LoggerUtils.info("=== REDIS SUBSCRIBER STARTING ===");
                LoggerUtils.info("Server ID: {}", serverId);
                LoggerUtils.info("Channel Prefix: {}", channelPrefix);
                LoggerUtils.info("Subscribing to {} Redis channels:", channels.length);
                for (String channel : channels) {
                    LoggerUtils.info("  - {}", channel);
                }
                LoggerUtils.info("=================================");

                jedis.subscribe(this, channels);
            } catch (Exception exception) {
                LoggerUtils.error("Redis subscriber error: " + exception.getMessage());
                exception.printStackTrace();
            } finally {
                if (jedis != null) {
                    try {
                        jedis.close();
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    public void shutdownSubscriber() {
        try {
            if (isSubscribed()) {
                LoggerUtils.info("Unsubscribing from Redis channels...");
                super.unsubscribe();
            }

            if (subscriberThread != null && subscriberThread.isAlive()) {
                subscriberThread.interrupt();
                subscriberThread.join(5000);
            }

            executorService.shutdown();
            LoggerUtils.info("Redis subscriber stopped");
        } catch (Exception exception) {
            LoggerUtils.error("Error shutting down subscriber: " + exception.getMessage());
        }
    }

    @Override
    public void onMessage(String channel, String message) {
        LoggerUtils.info("=== REDIS MESSAGE RECEIVED ===");
        LoggerUtils.info("Channel: {}", channel);
        LoggerUtils.info("Raw Message: {}", message);
        LoggerUtils.info("=============================");

        executorService.submit(() -> handleMessage(message));
    }

    private void handleMessage(@NotNull String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);

            String messageServerId = json.get("serverId").getAsString();

            LoggerUtils.info("=== PROCESSING REDIS MESSAGE ===");
            LoggerUtils.info("Message Server ID: {}", messageServerId);
            LoggerUtils.info("Our Server ID: {}", serverId);

            if (messageServerId.equals(serverId)) {
                LoggerUtils.info("IGNORING: This is our own message");
                LoggerUtils.info("===============================");
                return;
            }

            String typeStr = json.get("type").getAsString();
            RedisMessageType type = RedisMessageType.valueOf(typeStr);

            LoggerUtils.info("Message Type: {}", type);
            LoggerUtils.info("Message Content: {}", json);

            switch (type) {
                case GAME_START -> {
                    LoggerUtils.info(">>> HANDLING GAME_START <<<");
                    handleGameStart(json);
                }
                case GAME_STOP -> {
                    LoggerUtils.info(">>> HANDLING GAME_STOP <<<");
                    handleGameStop(json);
                }
                case GAME_TIMEOUT -> {
                    LoggerUtils.info(">>> HANDLING GAME_TIMEOUT <<<");
                    handleGameTimeout(json);
                }
                case PLAYER_WIN -> {
                    LoggerUtils.info(">>> HANDLING PLAYER_WIN <<<");
                    handlePlayerWin(json);
                }
                case BROADCAST_MESSAGE -> {
                    LoggerUtils.info(">>> HANDLING BROADCAST_MESSAGE <<<");
                    handleBroadcastMessage(json);
                }
                case BROADCAST_SOUND -> {
                    LoggerUtils.info(">>> HANDLING BROADCAST_SOUND <<<");
                    handleBroadcastSound(json);
                }
            }

            LoggerUtils.info("===============================");

        } catch (Exception exception) {
            LoggerUtils.error("=== ERROR HANDLING REDIS MESSAGE ===");
            LoggerUtils.error("Error: " + exception.getMessage());
            exception.printStackTrace();
            LoggerUtils.error("===================================");
        }
    }

    private void handleGameStart(@NotNull JsonObject json) {
        GameType gameType = GameType.valueOf(json.get("gameType").getAsString());
        String gameData = json.get("gameData").getAsString();
        long startTime = json.get("startTime").getAsLong();

        LoggerUtils.info("Game Type: {}", gameType);
        LoggerUtils.info("Game Data: {}", gameData);
        LoggerUtils.info("Start Time: {}", startTime);
        LoggerUtils.info("Calling proxyManager.handleRemoteGameStart()...");

        proxyManager.handleRemoteGameStart(gameType, gameData, startTime);
    }

    private void handleGameStop(@NotNull JsonObject json) {
        GameType gameType = GameType.valueOf(json.get("gameType").getAsString());
        LoggerUtils.info("Stopping game: {}", gameType);
        proxyManager.handleRemoteGameStop(gameType);
    }

    private void handleGameTimeout(@NotNull JsonObject json) {
        GameType gameType = GameType.valueOf(json.get("gameType").getAsString());
        String correctAnswer = json.get("correctAnswer").getAsString();

        LoggerUtils.info("Game timeout - Type: {}, Answer: {}", gameType, correctAnswer);
        proxyManager.handleRemoteGameTimeout(gameType, correctAnswer);
    }

    private void handlePlayerWin(@NotNull JsonObject json) {
        String playerName = json.get("playerName").getAsString();
        GameType gameType = GameType.valueOf(json.get("gameType").getAsString());
        double timeTaken = json.get("timeTaken").getAsDouble();

        LoggerUtils.info("Player win - Player: {}, Type: {}, Time: {}", playerName, gameType, timeTaken);
        proxyManager.handleRemotePlayerWin(playerName, gameType, timeTaken);
    }

    private void handleBroadcastMessage(@NotNull JsonObject json) {
        String messageKey = json.get("messageKey").getAsString();
        JsonObject placeholders = json.getAsJsonObject("placeholders");

        LoggerUtils.info("Broadcasting message: {}", messageKey);
        proxyManager.handleRemoteBroadcast(messageKey, placeholders);
    }

    private void handleBroadcastSound(@NotNull JsonObject json) {
        String sound = json.get("sound").getAsString();
        LoggerUtils.info("Playing sound: {}", sound);
        proxyManager.handleRemoteSound(sound);
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        LoggerUtils.info("✓ Subscribed to channel: {} (Total: {})", channel, subscribedChannels);
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        LoggerUtils.info("✗ Unsubscribed from channel: {} (Remaining: {})", channel, subscribedChannels);
    }
}