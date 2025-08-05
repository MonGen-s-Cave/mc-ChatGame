package com.mongenscave.mcchatgame.listener;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.database.Database;
import com.mongenscave.mcchatgame.managers.GameManager;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.models.impl.GameHangman;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public class GameListener implements Listener {
    @EventHandler
    public void onChat(final @NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim(); // Trim whitespace
        Database database = McChatGame.getInstance().getDatabase();

        // Create player in database if they don't exist
        database.exists(player).thenAccept(exists -> {
            if (!exists) database.createPlayer(player);
        });

        // Remove command prefix if present
        if (message.startsWith("!")) {
            message = message.substring(1);
        }

        GameHandler currentGame = GameHandler.getCurrentActiveGame();

        // For hangman, only process single character inputs
        if (currentGame instanceof GameHangman) {
            if (message.length() != 1 || !Character.isLetter(message.charAt(0))) {
                return; // Invalid input for hangman
            }
        }

        String finalMessage = message;
        McChatGame.getInstance().getScheduler().runTask(() ->
                GameManager.handleAnswer(player, finalMessage));
    }
}