package hu.fyremc.fyrechatgame.identifiers.keys;

import com.artillexstudios.axapi.config.Config;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.processor.MessageProcessor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public enum MessageKeys {
    RELOAD("messages.reload"),
    NO_PERMISSION("messages.no-permission"),

    MATH_GAME("messages.math-game"),
    WHO_AM_I("messages.who-am-i"),
    WORD_STOP("messages.word-stop"),
    WORD_GUESSER("messages.word-guesser"),
    COPY("messages.copy"),

    MATH_GAME_WIN("messages.math-game-win"),
    WHO_AM_I_WIN("messages.who-am-i-win"),
    WORD_STOP_WIN("messages.word-stop-win"),
    WORD_GUESSER_WIN("messages.word-guesser-win"),
    COPY_WIN("messages.copy-win");

    private final String path;
    private static final Config config = FyreChatGame.getInstance().getLanguage();

    MessageKeys(@NotNull String path) {
        this.path = path;
    }

    public @NotNull String getMessage() {
        return MessageProcessor.process(config.getString(path))
                .replace("%prefix%", MessageProcessor.process(config.getString("prefix")));
    }

    public List<String> getMessages() {
        return config.getStringList(path)
                .stream()
                .toList();

    }
}
