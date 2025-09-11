package com.mongenscave.mcchatgame.identifiers.keys;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.config.Config;
import com.mongenscave.mcchatgame.processor.MessageProcessor;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

@Getter
public enum ConfigKeys {
    ALIASES("aliases"),
    REWARDS("rewards"),
    TIME_BETWEEN_GAMES("time-between-games"),
    MIN_PLAYERS("min-players"),

    MATH_TIME("math.time"),
    MATH_PROBLEMS("math.problems"),
    MATH_ENABLED("math.enabled"),

    WHO_AM_I_TIME("who-am-i.time"),
    WHO_AM_I_WORDS("who-am-i.words"),
    WHO_AM_I_ENABLED("who-am-i.enabled"),

    WORD_STOP_TIME("word-stop.time"),
    WORD_STOP_MOBS("word-stop.mobs"),
    WORD_STOP_ENABLED("word-stop.enabled"),

    WORD_GUESSER_TIME("word-guesser.time"),
    WORD_GUESSER_WORDS("word-guesser.words"),
    WORD_GUESSER_ENABLED("word-guesser.enabled"),

    RANDOM_CHARACTERS_TIME("random-characters.time"),
    RANDOM_CHARACTERS_LENGTH("random-characters.length"),
    RANDOM_CHARACTERS_ENABLED("random-characters.enabled"),

    HANGMAN_TIME("hangman.time"),
    HANGMAN_WORDS("hangman.words"),
    HANGMAN_ENABLED("hangman.enabled"),
    HANGMAN_ONLY_ONCE("hangman.only-once"),
    HANGMAN_PLACEHOLDERS("hangman.placeholders"),
    HANGMAN_FOUND("hangman.found"),
    HANGMAN_STAGE_IN("hangman.stage-in"),
    HANGMAN_STAGE_OUT("hangman.stage-out"),
    HANGMAN_STAGES_ENABLED("hangman.stages-enabled"),

    REVERSE_TIME("reverse.time"),
    REVERSE_WORDS("reverse.words"),
    REVERSE_ENABLED("reverse.enabled"),

    FILL_OUT_TIME("fill-out.time"),
    FILL_OUT_WORDS("fill-out.words"),
    FILL_OUT_ENABLED("fill-out.enabled"),

    CRAFTING_TIME("crafting.time"),
    CRAFTING_CRAFTS("crafting.crafts"),
    CRAFTING_ENABLED("crafting.enabled"),
    CRAFTING_TITLE("crafting.title"),

    RANGE_TIME("range.time"),
    RANGE_RANGE("range.range"),
    RANGE_ENABLED("range.enabled"),

    TOAST_ENABLED("toast.enabled"),
    TOAST_MESSAGE("toast.message"),
    TOAST_MATERIAL("toast.material"),

    SOUND_START_ENABLED("sounds.start.enabled"),
    SOUND_START_SOUND("sounds.start.sound"),

    SOUND_WIN_ENABLED("sounds.win.enabled"),
    SOUND_WIN_SOUND("sounds.win.sound"),

    PLACEHOLDER_FILL_OUT("placeholders.fill-out"),
    PLACEHOLDER_MATH("placeholders.math"),
    PLACEHOLDER_RANDOM("placeholders.random"),
    PLACEHOLDER_REVERSE("placeholders.reverse"),
    PLACEHOLDER_WHO_AM_I("placeholders.who-am-i"),
    PLACEHOLDER_WORD_GUESS("placeholders.word-guess"),
    PLACEHOLDER_WORD_STOP("placeholders.word-stop"),
    PLACEHOLDER_CRAFTING("placeholders.crafting"),
    PLACEHOLDER_HANGMAN("placeholders.hangman"),
    PLACEHOLDER_RANGE("placeholders.range"),
    PLACEHOLDER_NO_GAMES("placeholders.no-game"),

    STREAKS_ENABLED("streaks.enabled"),
    STREAKS_MILESTONES("streaks");

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

    public Section getSection() {
        return config.getSection(path);
    }

    @NotNull
    public Set<String> getKeys() {
        Section section = config.getSection(path);
        return section != null ? section.getRoutesAsStrings(false) : Set.of();
    }
}
