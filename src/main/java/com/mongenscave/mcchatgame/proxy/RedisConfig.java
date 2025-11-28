package com.mongenscave.mcchatgame.proxy;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import lombok.Getter;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

public class RedisConfig {
    @Getter private JedisPool jedisPool;
    private final McChatGame plugin;

    public RedisConfig(McChatGame plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            String host = plugin.getConfiguration().getString("redis.host", "localhost");
            int port = plugin.getConfiguration().getInt("redis.port", 6379);
            String password = plugin.getConfiguration().getString("redis.password", "");
            int database = plugin.getConfiguration().getInt("redis.database", 0);
            int timeout = plugin.getConfiguration().getInt("redis.timeout", 2000);
            boolean ssl = plugin.getConfiguration().getBoolean("redis.ssl", false);

            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(plugin.getConfiguration().getInt("redis.pool.max-total", 8));
            poolConfig.setMaxIdle(plugin.getConfiguration().getInt("redis.pool.max-idle", 8));
            poolConfig.setMinIdle(plugin.getConfiguration().getInt("redis.pool.min-idle", 0));
            poolConfig.setMaxWait(Duration.ofMillis(plugin.getConfiguration().getLong("redis.pool.max-wait", 2000)));
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            if (password != null && !password.trim().isEmpty()) jedisPool = new JedisPool(poolConfig, host, port, timeout, password, database, ssl);
            else jedisPool = new JedisPool(poolConfig, host, port, timeout, null, database, ssl);

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            LoggerUtils.info("Successfully connected to Redis at {}:{}", host, port);
            return true;
        } catch (Exception exception) {
            LoggerUtils.error("Failed to connect to Redis: " + exception.getMessage());
            return false;
        }
    }

    public void disconnect() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            LoggerUtils.info("Redis connection closed");
        }
    }

    public boolean isConnected() {
        if (jedisPool == null || jedisPool.isClosed()) return false;

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.isConnected() && "PONG".equals(jedis.ping());
        } catch (Exception exception) {
            return false;
        }
    }
}