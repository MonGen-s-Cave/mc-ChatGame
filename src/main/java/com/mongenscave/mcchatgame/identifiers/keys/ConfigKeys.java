package com.mongenscave.mcchatgame.identifiers.keys;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.config.Config;
import com.mongenscave.mcchatgame.processor.MessageProcessor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public enum ConfigKeys {
    ALIASES("aliases"),
    REWARDS("rewards"),
    TIME_BETWEEN_GAMES("time-between-games"),

    MATH_TIME("math.time"),
    MATH_PROBLEMS("math.problems"),

    WHO_AM_I_TIME("who-am-i.time"),
    WHO_AM_I_WORDS("who-am-i.words"),

    WORD_STOP_TIME("word-stop.time"),
    WORD_STOP_MOBS("word-stop.mobs"),

    WORD_GUESSER_TIME("word-guesser.time"),
    WORD_GUESSER_WORDS("word-guesser.words"),

    RANDOM_CHARACTERS_TIME("random-characters.time"),
    RANDOM_CHARACTERS_LENGTH("random-characters.length"),

    REVERSE_TIME("reverse.time"),
    REVERSE_WORDS("reverse.words"),

    FILL_OUT_TIME("fill-out.time"),
    FILL_OUT_WORDS("fill-out.words");

    private final String path;
    private static final Config config = McChatGame.getInstance().getConfiguration();

    ConfigKeys(@NotNull String path) {
        this.path = path;
    }

    public @NotNull String getString() {
        return MessageProcessor.process(config.getString(path));
    }

    public static @NotNull String getString(@NotNull String path) {
        return config.getString(path);
    }

    public boolean getBoolean() {
        return config.getBoolean(path);
    }

    public int getInt() {
        return config.getInt(path);
    }

    public List<String> getList() {
        return config.getList(path);
    }
}
