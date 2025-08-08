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
        String main = "\u001B[38;2;66;245;152m";
        String reset = "\u001B[0m";
        String software = McChatGame.getInstance().getServer().getName();
        String version = McChatGame.getInstance().getServer().getVersion();

        info("");
        info("{}      ____ _   _    _  _____ ____    _    __  __ _____ {}", main, reset);
        info("{}    / ___| | | |  / \\|_   _/ ___|  / \\  |  \\/  | ____| {}", main, reset);
        info("{}   | |   | |_| | / _ \\ | || |  _  / _ \\ | |\\/| |  _|   {}", main, reset);
        info("{}   | |___|  _  |/ ___ \\| || |_| |/ ___ \\| |  | | |___  {}", main, reset);
        info("{}    \\____|_| |_/_/   \\_\\_| \\____/_/   \\_\\_|  |_|_____| {}", main, reset);
        info("");
        info("{}   The plugin successfully started.{}", main, reset);
        info("{}   mc-ChatGame {} {}{}", main, software, version, reset);
        info("{}   Discord @ dc.mongenscave.com{}", main, reset);
        info("");
    }
}