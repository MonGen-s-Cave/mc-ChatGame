package com.mongenscave.mcchatgame.commands;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.orphan.OrphanCommand;

public class CommandChat implements OrphanCommand {
    private static final McChatGame plugin = McChatGame.getInstance();

    @Subcommand("reload")
    @CommandPermission("fyrechatgame.reload")
    public void reload(@NotNull CommandSender sender) {
        plugin.getConfiguration().reload();
        plugin.getLanguage().reload();
        sender.sendMessage(MessageKeys.RELOAD.getMessage());
    }
}
