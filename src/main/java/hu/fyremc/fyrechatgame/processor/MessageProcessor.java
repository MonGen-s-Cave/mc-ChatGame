package hu.fyremc.fyrechatgame.processor;

import lombok.experimental.UtilityClass;
import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
@SuppressWarnings("deprecation")
public class MessageProcessor {
    private static final char COLOR_CHAR = '§';
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public @NotNull String process(@Nullable String message) {
        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder builder = new StringBuilder(message.length() + 4 * 8);

        while (matcher.find()) {
            String group = matcher.group(1);

            matcher.appendReplacement(builder, COLOR_CHAR + "x"
                    + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                    + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                    + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5)
            );
        }

        String hexProcessed = matcher.appendTail(builder).toString();
        return ChatColor.translateAlternateColorCodes('&', hexProcessed);
    }
}
