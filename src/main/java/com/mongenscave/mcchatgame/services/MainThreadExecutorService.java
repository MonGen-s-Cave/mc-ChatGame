package com.mongenscave.mcchatgame.services;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public class MainThreadExecutorService {
    @Getter private static MainThreadExecutorService instance;
    @Getter private final Executor mainThreadExecutor;

    private MainThreadExecutorService() {
        this.mainThreadExecutor = createMainThreadExecutor();
    }

    public static void initialize() {
        instance = new MainThreadExecutorService();
    }

    @NotNull
    @Contract(pure = true)
    private Executor createMainThreadExecutor() {
        return command -> {
            if (McChatGame.getInstance().getServer().isPrimaryThread()) {
                try {
                    command.run();
                } catch (Exception exception) {
                    LoggerUtils.error(exception.getMessage());
                }
            } else {
                McChatGame.getInstance().getScheduler().runTask(() -> {
                    try {
                        command.run();
                    } catch (Exception exception) {
                        LoggerUtils.error(exception.getMessage());
                    }
                });
            }
        };
    }
}