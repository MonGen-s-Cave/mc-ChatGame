package com.mongenscave.mcchatgame.identifiers.keys;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.config.Config;
import com.mongenscave.mcchatgame.processor.MessageProcessor;
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
    REVERSE("messages.reverse"),
    FILL_OUT("messages.fill-out"),
    RANDOM_CHARACTERS("messages.random-characters"),

    MATH_GAME_WIN("messages.math-game-win"),
    WHO_AM_I_WIN("messages.who-am-i-win"),
    WORD_STOP_WIN("messages.word-stop-win"),
    WORD_GUESSER_WIN("messages.word-guesser-win"),
    COPY_WIN("messages.copy-win"),
    REVERSE_WIN("messages.reverse-win"),
    FILL_OUT_WIN("messages.fill-out-win"),
    RANDOM_CHARACTERS_WIN("messages.random-characters-win"),

    MATH_GAME_NO_WIN("messages.math-game-no-win"),
    WHO_AM_I_NO_WIN("messages.who-am-i-no-win"),
    WORD_STOP_NO_WIN("messages.word-stop-no-win"),
    WORD_GUESSER_NO_WIN("messages.word-guesser-no-win"),
    COPY_NO_WIN("messages.copy-no-win"),
    REVERSE_NO_WIN("messages.reverse-no-win"),
    FILL_OUT_NO_WIN("messages.fill-out-no-win"),
    RANDOM_CHARACTERS_NO_WIN("messages.random-characters-no-win");

    private final String path;
    private static final Config config = McChatGame.getInstance().getLanguage();

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