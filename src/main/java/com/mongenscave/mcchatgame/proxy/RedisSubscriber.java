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
            try (Jedis jedis = redisConfig.getJedisPool().getResource()) {
                String[] channels = new String[RedisMessageType.values().length];

                for (int i = 0; i < RedisMessageType.values().length; i++) {
                    channels[i] = channelPrefix + ":" + RedisMessageType.values()[i].getChannel();
                }

                jedis.subscribe(this, channels);
            } catch (Exception exception) {
                LoggerUtils.error("Redis subscriber error: " + exception.getMessage());
            }
        });
    }

    public void shutdownSubscriber() {
        if (isSubscribed()) super.unsubscribe();
        if (subscriberThread != null && subscriberThread.isAlive()) subscriberThread.interrupt();

        executorService.shutdown();
        LoggerUtils.info("Redis subscriber stopped");
    }

    @Override
    public void onMessage(String channel, String message) {
        executorService.submit(() -> handleMessage(message));
    }

    private void handleMessage(@NotNull String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);

            String messageServerId = json.get("serverId").getAsString();
            if (messageServerId.equals(serverId)) return;

            String typeStr = json.get("type").getAsString();
            RedisMessageType type = RedisMessageType.valueOf(typeStr);

            switch (type) {
                case GAME_START -> handleGameStart();
                case GAME_STOP -> handleGameStop(json);
                case GAME_TIMEOUT -> handleGameTimeout(json);
                case PLAYER_WIN -> handlePlayerWin(json);
                case BROADCAST_MESSAGE -> handleBroadcastMessage(json);
                case BROADCAST_SOUND -> handleBroadcastSound(json);
            }

        } catch (Exception exception) {
            LoggerUtils.error("Error handling Redis message: " + exception.getMessage());
        }
    }

    private void handleGameStart() {
        proxyManager.handleRemoteGameStart();
    }

    private void handleGameStop(@NotNull JsonObject json) {
        GameType gameType = GameType.valueOf(json.get("gameType").getAsString());
        proxyManager.handleRemoteGameStop(gameType);
    }

    private void handleGameTimeout(@NotNull JsonObject json) {
        GameType gameType = GameType.valueOf(json.get("gameType").getAsString());
        String correctAnswer = json.get("correctAnswer").getAsString();

        proxyManager.handleRemoteGameTimeout(gameType, correctAnswer);
    }

    private void handlePlayerWin(@NotNull JsonObject json) {
        String playerName = json.get("playerName").getAsString();
        GameType gameType = GameType.valueOf(json.get("gameType").getAsString());
        double timeTaken = json.get("timeTaken").getAsDouble();

        proxyManager.handleRemotePlayerWin(playerName, gameType, timeTaken);
    }

    private void handleBroadcastMessage(@NotNull JsonObject json) {
        String messageKey = json.get("messageKey").getAsString();
        JsonObject placeholders = json.getAsJsonObject("placeholders");

        proxyManager.handleRemoteBroadcast(messageKey, placeholders);
    }

    private void handleBroadcastSound(@NotNull JsonObject json) {
        String sound = json.get("sound").getAsString();
        proxyManager.handleRemoteSound(sound);
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        LoggerUtils.info("Subscribed to channel: {} (Total: {})", channel, subscribedChannels);
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        LoggerUtils.info("Unsubscribed from channel: {} (Remaining: {})", channel, subscribedChannels);
    }
}