package com.mongenscave.mcchatgame.utils;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.commands.CommandChat;
import com.mongenscave.mcchatgame.handler.CommandExceptionHandler;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import lombok.experimental.UtilityClass;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.orphan.Orphans;

@UtilityClass
public class RegisterUtils {
    public void registerCommands() {
        var lamp = BukkitLamp.builder(McChatGame.getInstance())
                .exceptionHandler(new CommandExceptionHandler())
                .build();

        lamp.register(Orphans.path(ConfigKeys.ALIASES.getList().toArray(String[]::new)).handler(new CommandChat()));
    }
}
