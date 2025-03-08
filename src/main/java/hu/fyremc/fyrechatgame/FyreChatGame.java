package hu.fyremc.fyrechatgame;

import com.artillexstudios.axapi.config.Config;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import hu.fyremc.fyrechatgame.database.Database;
import hu.fyremc.fyrechatgame.database.DatabaseConfig;
import hu.fyremc.fyrechatgame.hooks.plugins.PlaceholderAPI;
import hu.fyremc.fyrechatgame.listener.GameListener;
import hu.fyremc.fyrechatgame.processor.AutoGameProcessor;
import hu.fyremc.fyrechatgame.services.GameService;
import hu.fyremc.fyrechatgame.services.MainThreadExecutorService;
import hu.fyremc.fyrechatgame.utils.LoggerUtils;
import hu.fyremc.fyrechatgame.utils.RegisterUtils;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import revxrsal.zapper.ZapperJavaPlugin;
import com.artillexstudios.axapi.libs.boostedyaml.boostedyaml.settings.dumper.DumperSettings;
import com.artillexstudios.axapi.libs.boostedyaml.boostedyaml.settings.general.GeneralSettings;
import com.artillexstudios.axapi.libs.boostedyaml.boostedyaml.settings.loader.LoaderSettings;
import com.artillexstudios.axapi.libs.boostedyaml.boostedyaml.settings.updater.UpdaterSettings;

import java.io.File;
import java.util.concurrent.Executor;

public final class FyreChatGame extends ZapperJavaPlugin {
    @Getter static FyreChatGame instance;
    @Getter TaskScheduler scheduler;
    @Getter Config language;
    @Getter Database database;
    @Getter GameService gameService;
    @Getter Executor mainThreadExecutor;
    Config config;
    AutoGameProcessor gameProcessor;

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
    }

    @Override
    public void onDisable() {
        if (database != null) {
            gameService.shutdown();
            database.close();
        }

        if (gameProcessor != null) gameProcessor.stop();
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
        try {
            LoggerUtils.info("### Connecting to database... ###");

            DatabaseConfig databaseConfig = DatabaseConfig.fromSection(getConfiguration().getSection("database"));
            database = new Database(databaseConfig);
            gameService = new GameService(database);

            LoggerUtils.info("### Database connected successfully! ###");
        } catch (Exception exception) {
            LoggerUtils.error(exception.getMessage());
        }
    }
}
