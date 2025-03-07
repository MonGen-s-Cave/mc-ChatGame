package hu.fyremc.fyrechatgame.database;

import com.artillexstudios.axapi.libs.boostedyaml.boostedyaml.block.implementation.Section;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record DatabaseConfig(@NotNull String host, int port, @NotNull String database, @NotNull String username, @NotNull String password) {
    @NotNull
    @Contract("_ -> new")
    public static DatabaseConfig fromSection(@NotNull Section section) {
        return new DatabaseConfig(
                section.getString("host"),
                section.getInt("port"),
                section.getString("database"),
                section.getString("username"),
                section.getString("password")
        );
    }
}
