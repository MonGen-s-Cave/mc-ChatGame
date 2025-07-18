package com.mongenscave.mcchatgame.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
@SuppressWarnings("deprecation")
public class PlayerUtils {
    private static final McChatGame plugin = McChatGame.getInstance();
    private static final ConcurrentHashMap<UUID, NamespacedKey> toastAdvancements = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    public void sendToast(@NotNull Player player, @NotNull ConfigKeys title, @NotNull ConfigKeys icon, @NotNull ConfigKeys enabled) {
        if (!enabled.getBoolean()) return;

        NamespacedKey key = new NamespacedKey(plugin, "toast_" + player.getUniqueId() + "_" + System.currentTimeMillis());

        try {
            createAndRegisterAdvancement(key, title.getString(), Material.valueOf(icon.getString()));

            plugin.getScheduler().runTaskLater(() -> grantAdvancement(player, key), 1L);
            plugin.getScheduler().runTaskLater(() -> removeAdvancement(player, key), 100L);
        } catch (Exception exception) {
            LoggerUtils.error(exception.getMessage());
        }
    }

    private void createAndRegisterAdvancement(NamespacedKey key, String title, Material icon) {
        try {
            JsonObject advancement = createAdvancementJson(title, icon);
            Bukkit.getUnsafe().loadAdvancement(key, advancement.toString());
        } catch (Exception exception) {
            LoggerUtils.error(exception.getMessage());
        }
    }

    private void grantAdvancement(@NotNull Player player, NamespacedKey key) {
        try {
            Advancement advancement = Bukkit.getAdvancement(key);

            if (advancement != null) {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);

                if (!progress.isDone()) {
                    for (String criteria : progress.getRemainingCriteria()) {
                        progress.awardCriteria(criteria);
                    }
                }

                toastAdvancements.put(player.getUniqueId(), key);
            }
        } catch (Exception exception) {
            LoggerUtils.error(exception.getMessage());
        }
    }

    public void removeAdvancement(@NotNull Player player, NamespacedKey key) {
        try {
            Advancement advancement = Bukkit.getAdvancement(key);
            if (advancement != null) {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);

                for (String criteria : advancement.getCriteria()) {
                    progress.revokeCriteria(criteria);
                }
            }

            toastAdvancements.remove(player.getUniqueId());

            try {
                Bukkit.getUnsafe().removeAdvancement(key);
            } catch (Exception ignored) {}

        } catch (Exception exception) {
            LoggerUtils.error(exception.getMessage());
        }
    }

    @NotNull
    private JsonObject createAdvancementJson(String title, @NotNull Material icon) {
        String jsonString = String.format("""
                        {
                            "display": {
                                "icon": {
                                    "id": "%s"
                                },
                                "title": {
                                    "text": "%s"
                                },
                                "description": {
                                    "text": "%s"
                                },
                                "background": "minecraft:textures/gui/advancements/backgrounds/adventure.png",
                                "frame": "task",
                                "announce_to_chat": false,
                                "show_toast": true,
                                "hidden": true
                            },
                            "criteria": {
                                "trigger": {
                                    "trigger": "minecraft:impossible"
                                }
                            }
                        }""",

                icon.getKey(),
                escapeJson(title),
                escapeJson("")
        );

        return gson.fromJson(jsonString, JsonObject.class);
    }

    @NotNull
    private static String escapeJson(@NotNull String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public void cleanup() {
        for (ConcurrentHashMap.Entry<UUID, NamespacedKey> entry : toastAdvancements.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) removeAdvancement(player, entry.getValue());
        }

        toastAdvancements.clear();
    }
}
