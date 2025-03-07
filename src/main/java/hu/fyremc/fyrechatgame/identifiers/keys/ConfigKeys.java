package hu.fyremc.fyrechatgame.identifiers.keys;

import com.artillexstudios.axapi.config.Config;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.processor.MessageProcessor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public enum ConfigKeys {
    ALIASES("aliases"),
    REWARDS("rewards"),

    MATH_TIME("math.time"),
    MATH_PROBLEMS("math.problems"),

    WHO_AM_I_TIME("who-am-i.time"),
    WHO_AM_I_WORDS("who-am-i.words"),

    WORD_STOP_TIME("word-stop.time"),
    WORD_STOP_MOBS("word-stop.mobs"),

    WORD_GUESSER_TIME("word-guesser.time"),
    WORD_GUESSER_WORDS("word-guesser.words"),

    RANDOM_CHARACTERS_TIME("random-characters.time"),
    RANDOM_CHARACTERS_LENGTH("random-characters.length");

    private final String path;
    private static final Config config = FyreChatGame.getInstance().getConfiguration();

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
