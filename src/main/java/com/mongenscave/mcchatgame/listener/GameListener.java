package com.mongenscave.mcchatgame.listener;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.database.Database;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.managers.GameManager;
import com.mongenscave.mcchatgame.managers.ProxyManager;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.models.impl.GameHangman;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class GameListener implements Listener {
    @EventHandler
    public void onChat(final @NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        Database database = McChatGame.getInstance().getDatabase();

        database.exists(player).thenAccept(exists -> {
            if (!exists) database.createPlayer(player);
        });

        if (message.startsWith("!")) message = message.substring(1);

        GameHandler currentGame = GameHandler.getCurrentActiveGame();

        if (currentGame instanceof GameHangman) {
            if (message.length() != 1 || !Character.isLetter(message.charAt(0))) return;
        }

        // CRITICAL FIX: Only broadcast player answers from MASTER server
        ProxyManager proxyManager = McChatGame.getInstance().getProxyManager();
        if (proxyManager.isEnabled() && proxyManager.isMasterServer() && currentGame != null) {
            GameType gameType = getGameType(currentGame);
            if (gameType != null) {
                proxyManager.broadcastPlayerAnswer(player, message, gameType);
            }
        }

        // IMPORTANT: Always handle answer locally (both master and slave)
        String finalMessage = message;
        McChatGame.getInstance().getScheduler().runTask(() ->
                GameManager.handleAnswer(player, finalMessage));
    }

    @Nullable
    private GameType getGameType(@NotNull GameHandler handler) {
        String className = handler.getClass().getSimpleName();
        return switch (className) {
            case "GameMath" -> GameType.MATH;
            case "GameWhoAmI" -> GameType.WHO_AM_I;
            case "GameWordGuess" -> GameType.WORD_GUESSER;
            case "GameRandomCharacters" -> GameType.RANDOM_CHARACTERS;
            case "GameWordStop" -> GameType.WORD_STOP;
            case "GameReverse" -> GameType.REVERSE;
            case "GameFillOut" -> GameType.FILL_OUT;
            case "GameCrafting" -> GameType.CRAFTING;
            case "GameHangman" -> GameType.HANGMAN;
            case "GameRange" -> GameType.RANGE;
            default -> null;
        };
    }
}