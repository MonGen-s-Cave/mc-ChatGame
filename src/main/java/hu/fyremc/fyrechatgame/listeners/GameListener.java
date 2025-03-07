package hu.fyremc.fyrechatgame.listeners;

import hu.fyremc.fyrechatgame.FyreChatGame;
import org.bukkit.event.Listener;
import hu.fyremc.fyrechatgame.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class GameListener implements Listener {
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(FyreChatGame.getInstance(), () ->
                GameManager.handleAnswer(player, message)
        );
    }
}
