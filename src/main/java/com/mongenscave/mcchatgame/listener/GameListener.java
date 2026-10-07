package com.mongenscave.mcchatgame.listener;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.database.Database;
import com.mongenscave.mcchatgame.managers.GameManager;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.models.impl.GameHangman;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class GameListener implements Listener {
    private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)[§&](#[0-9a-f]{6}|x([§&][0-9a-f]){6}|[0-9a-fk-or])");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(final @NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = COLOR_PATTERN.matcher(event.getMessage()).replaceAll("").trim();
        Database database = McChatGame.getInstance().getDatabase();

        database.exists(player).thenAccept(exists -> {
            if (!exists) database.createPlayer(player);
        });

        if (message.startsWith("!")) message = message.substring(1);

        GameHandler currentGame = GameHandler.getCurrentActiveGame();

        if (currentGame instanceof GameHangman) {
            if (message.length() != 1 || !Character.isLetter(message.charAt(0))) return;
        }
        
        String finalMessage = message;
        McChatGame.getInstance().getScheduler().runTask(() ->
                GameManager.handleAnswer(player, finalMessage));
    }
}