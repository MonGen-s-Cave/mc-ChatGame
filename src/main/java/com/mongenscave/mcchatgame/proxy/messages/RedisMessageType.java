package com.mongenscave.mcchatgame.proxy.messages;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
@AllArgsConstructor
public enum RedisMessageType {
    GAME_START("game:start"),
    GAME_STOP("game:stop"),
    GAME_TIMEOUT("game:timeout"),

    PLAYER_ANSWER("player:answer"),
    PLAYER_WIN("player:win"),

    BROADCAST_MESSAGE("broadcast:message"),
    BROADCAST_SOUND("broadcast:sound"),

    STREAK_UPDATE("streak:update"),
    STREAK_RESET("streak:reset");

    private final String channel;

    @NotNull
    public JsonObject createBaseMessage(@NotNull String serverId) {
        JsonObject json = new JsonObject();
        json.addProperty("type", this.name());
        json.addProperty("serverId", serverId);
        json.addProperty("timestamp", System.currentTimeMillis());
        return json;
    }
}