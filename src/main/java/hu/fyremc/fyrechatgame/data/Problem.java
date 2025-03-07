package hu.fyremc.fyrechatgame.data;

import org.jetbrains.annotations.NotNull;

public record Problem(@NotNull String question, @NotNull String answer) {
    public static Problem parseProblem(@NotNull String rawProblem) {
        String[] parts = rawProblem.split("=", 2);

        return new Problem(parts[0].trim(), parts[1].trim());
    }
}
