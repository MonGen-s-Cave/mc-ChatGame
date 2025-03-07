package hu.fyremc.fyrechatgame.listener;

import hu.fyremc.fyrechatgame.FyreChatGame;
import org.bukkit.event.Listener;
import hu.fyremc.fyrechatgame.manager.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("deprecation")
public class GameListener implements Listener {
    @EventHandler
    public void onChat(final @NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(FyreChatGame.getInstance(), () ->
                GameManager.handleAnswer(player, message)
        );
    }
}
