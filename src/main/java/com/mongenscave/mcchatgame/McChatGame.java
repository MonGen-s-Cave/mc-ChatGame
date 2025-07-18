package com.mongenscave.mcchatgame;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.mongenscave.mcchatgame.config.Config;
import com.mongenscave.mcchatgame.database.Database;
import com.mongenscave.mcchatgame.database.impl.H2;
import com.mongenscave.mcchatgame.database.impl.MySQL;
import com.mongenscave.mcchatgame.hooks.plugins.PlaceholderAPI;
import com.mongenscave.mcchatgame.listener.GameListener;
import com.mongenscave.mcchatgame.processor.AutoGameProcessor;
import com.mongenscave.mcchatgame.services.MainThreadExecutorService;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import com.mongenscave.mcchatgame.utils.PlayerUtils;
import com.mongenscave.mcchatgame.utils.RegisterUtils;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import revxrsal.zapper.ZapperJavaPlugin;

import java.io.File;
import java.util.concurrent.Executor;

public final class McChatGame extends ZapperJavaPlugin {
    @Getter static McChatGame instance;
    @Getter TaskScheduler scheduler;
    @Getter Config language;
    @Getter Database database;
    @Getter Executor mainThreadExecutor;
    @Getter AutoGameProcessor gameProcessor;
    Config config;

    @Override
    public void onLoad() {
        instance = this;
        scheduler = UniversalScheduler.getScheduler(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeComponents();
        initializeDatabase();
        MainThreadExecutorService.initialize();

        getServer().getPluginManager().registerEvents(new GameListener(), this);

        gameProcessor = new AutoGameProcessor();

        gameProcessor.start();
        PlaceholderAPI.registerHook();
        RegisterUtils.registerCommands();

        new Metrics(this, 26553);
        LoggerUtils.printStartup();
    }

    @Override
    public void onDisable() {
        if (database != null) database.shutdown();
        if (gameProcessor != null) gameProcessor.stop();

        PlayerUtils.cleanup();
    }

    public Config getConfiguration() {
        return config;
    }

    private void initializeComponents() {
        final GeneralSettings generalSettings = GeneralSettings.builder()
                .setUseDefaults(false)
                .build();

        final LoaderSettings loaderSettings = LoaderSettings.builder()
                .setAutoUpdate(true)
                .build();

        final UpdaterSettings updaterSettings = UpdaterSettings.builder()
                .setKeepAll(true)
                .build();

        config = loadConfig("config.yml", generalSettings, loaderSettings, updaterSettings);
        language = loadConfig("messages.yml", generalSettings, loaderSettings, updaterSettings);
    }

    @NotNull
    @Contract("_, _, _, _ -> new")
    private Config loadConfig(@NotNull String fileName, @NotNull GeneralSettings generalSettings, @NotNull LoaderSettings loaderSettings, @NotNull UpdaterSettings updaterSettings) {
        return new Config(
                new File(getDataFolder(), fileName),
                getResource(fileName),
                generalSettings,
                loaderSettings,
                DumperSettings.DEFAULT,
                updaterSettings
        );
    }

    private void initializeDatabase() {
        String databaseType = config.getString("database.type", "h2").toLowerCase();

        switch (databaseType) {
            case "mysql" -> {
                String host = config.getString("database.mysql.host");
                int port = config.getInt("database.mysql.port");
                String databaseName = config.getString("database.mysql.database");
                String username = config.getString("database.mysql.username");
                String password = config.getString("database.mysql.password");

                database = new MySQL(host, port, databaseName, username, password);
            }

            case "h2" -> database = new H2();
            default -> {
                LoggerUtils.error("Unsupported database type: " + databaseType);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        try {
            database.initialize().join();
            LoggerUtils.info("Database initialized successfully");
        } catch (Exception exception) {
            LoggerUtils.error("Failed to initialize database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}
