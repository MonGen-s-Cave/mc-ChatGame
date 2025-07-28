package com.mongenscave.mcchatgame.commands;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import com.mongenscave.mcchatgame.managers.GameManager;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.models.impl.GameCrafting;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

public class CommandChat implements OrphanCommand {
    private static final McChatGame plugin = McChatGame.getInstance();

    @Subcommand("reload")
    @CommandPermission("chatgame.reload")
    public void reload(@NotNull CommandSender sender) {
        plugin.getConfiguration().reload();
        plugin.getLanguage().reload();
        sender.sendMessage(MessageKeys.RELOAD.getMessage());
    }

    @Subcommand("start")
    @CommandPermission("chatgame.start")
    public void start(@NotNull CommandSender sender, @NotNull GameType type) {
        GameManager.stopAllGames();
        GameManager.startGame(type);
    }

    @Subcommand("crafting")
    public void crafting(@NotNull Player player) {
        GameHandler activeGame = GameHandler.getCurrentActiveGame();

        if (!(activeGame instanceof GameCrafting craftingGame)) {
            player.sendMessage(MessageKeys.NO_CRAFTING_GAME.getMessage());
            return;
        }

        craftingGame.openCraftingMenu(player);
    }
}
