package hu.fyremc.fyrechatgame.handler;

import hu.fyremc.fyrechatgame.identifiers.keys.MessageKeys;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.locales.LocaleReader;

import java.util.Locale;

@SuppressWarnings("deprecation")
public class ErrorHandler implements LocaleReader {
    @Override
    public boolean containsKey(@NotNull String string) {
        return true;
    }

    @Override
    public String get(@NotNull String string) {
        String result;

        if (string.equals("no-permission")) result = MessageKeys.NO_PERMISSION.getMessage();
        else result = "";

        return result;
    }

    private final Locale LOCALE = new Locale("en", "US");

    @Override
    public Locale getLocale() {
        return LOCALE;
    }
}
