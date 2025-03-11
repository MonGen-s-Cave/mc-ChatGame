package hu.fyremc.fyrechatgame.utils;

import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.commands.CommandChat;
import hu.fyremc.fyrechatgame.handler.ErrorHandler;
import hu.fyremc.fyrechatgame.identifiers.keys.ConfigKeys;
import lombok.experimental.UtilityClass;
import revxrsal.commands.bukkit.BukkitCommandHandler;
import revxrsal.commands.orphan.Orphans;

import java.util.Locale;

@SuppressWarnings("deprecation")
@UtilityClass
public class RegisterUtils {
    public void registerCommands() {
        LoggerUtils.info("### Registering commands... ###");

        BukkitCommandHandler handler = BukkitCommandHandler.create(FyreChatGame.getInstance());

        handler.getTranslator().add(new ErrorHandler());
        handler.setLocale(new Locale("en", "US"));
        handler.register(Orphans.path(ConfigKeys.ALIASES.getList().toArray(String[]::new)).handler(new CommandChat()));
        handler.registerBrigadier();

        LoggerUtils.info("### Successfully registered exception handlers... ###");
    }
}
