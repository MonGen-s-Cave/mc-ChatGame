package com.mongenscave.mcchatgame.utils;

import com.mongenscave.mcchatgame.McChatGame;
import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class LoggerUtils {
    private final Logger logger = LogManager.getLogger("McChatGame");

    public void info(@NotNull String msg, @NotNull Object... objs) {
        logger.info(msg, objs);
    }

    public void warn(@NotNull String msg, @NotNull Object... objs) {
        logger.warn(msg, objs);
    }

    public void error(@NotNull String msg, @NotNull Object... objs) {
        logger.error(msg, objs);
    }

    public void printStartup() {
        String color = "\u001B[32m";
        String reset = "\u001B[0m";
        String software = McChatGame.getInstance().getServer().getName();
        String version = McChatGame.getInstance().getServer().getVersion();

        String asciiArt = color + "   _____ _           _    _____                      \n" + reset +
                color + "  / ____| |         | |  / ____|                     \n" + reset +
                color + " | |    | |__   __ _| |_| |  __  __ _ _ __ ___   ___ \n" + reset +
                color + " | |    | '_ \\ / _` | __| | |_ |/ _` | '_ ` _ \\ / _ \\\n" + reset +
                color + " | |____| | | | (_| | |_| |__| | (_| | | | | | |  __/\\\n" + reset +
                color + " \\_____|_| |_|\\__,_|\\__|\\_____|\\__,_|_| |_| |_|\\___|" + reset;

        info("");
        String[] lines = asciiArt.split("\n");

        for (String line : lines) {
            info(line);
        }

        info("");
        info("{}   The plugin successfully started.{}", color, reset);
        info("{}   mc-Treasure {} {}{}", color, software, version, reset);
        info("{}   Discord @ dc.mongenscave.com{}", color, reset);
        info("");
    }
}
