package com.mongenscave.mcchatgame.update;

import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
public class UpdateChecker implements Listener {
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long NOTIFICATION_DELAY_TICKS = 50L;
    private static final McChatGame plugin = McChatGame.getInstance();

    @Getter private final String currentVersion;
    private final int resourceId;
    private final HttpClient httpClient;

    private final AtomicReference<String> latestVersion = new AtomicReference<>();
    private final AtomicBoolean isUpToDate = new AtomicBoolean(true);

    public UpdateChecker(int resourceId) {
        this.currentVersion = plugin.getDescription().getVersion();
        this.resourceId = resourceId;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startUpdateChecker();
    }

    @EventHandler
    public void onPlayerJoin(final @NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (isUpToDate.get() || latestVersion.get() == null) return;
        if (!player.hasPermission("mctreasure.update")) return;

        plugin.getScheduler().runTaskLaterAsynchronously(() -> player.sendMessage(MessageKeys.UPDATE_NOTIFY.getMessage()
                .replace("{your}", currentVersion)
                .replace("{latest}", latestVersion.get())), NOTIFICATION_DELAY_TICKS);
    }

    @NotNull
    private CompletableFuture<Boolean> checkForUpdates() {
        return fetchLatestVersion()
                .thenApply(version -> {
                    latestVersion.set(version);
                    boolean upToDate = version == null || isCurrentVersionLatest(version);
                    isUpToDate.set(upToDate);
                    return !upToDate;
                });
    }

    @NotNull
    private CompletableFuture<String> fetchLatestVersion() {
        var uri = URI.create("https://api.polymart.org/v1/getResourceInfoSimple/?resource_id=" + resourceId + "&key=version");

        var request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .exceptionally(throwable -> {
                    LoggerUtils.error("Failed to fetch latest version", throwable);
                    return null;
                });
    }

    private boolean isCurrentVersionLatest(@Nullable String latest) {
        return latest == null || compareVersions(latest, currentVersion) <= 0;
    }

    private int compareVersions(String version1, String version2) {
        if (version1 == null || version2 == null) return 0;

        try {
            var v1Parts = parseVersion(version1);
            var v2Parts = parseVersion(version2);

            return v1Parts[0] != v2Parts[0] ? Integer.compare(v1Parts[0], v2Parts[0]) :
                    v1Parts[1] != v2Parts[1] ? Integer.compare(v1Parts[1], v2Parts[1]) :
                            Integer.compare(v1Parts[2], v2Parts[2]);
        } catch (Exception exception) {
            LoggerUtils.warn("Failed to compare versions: {} vs {}", version1, version2, exception);
            return 0;
        }
    }

    @NotNull
    @Contract("_ -> new")
    private int[] parseVersion(@NotNull String version) {
        var parts = version.split("\\.", 3);
        return new int[]{
                parts.length > 0 ? Integer.parseInt(parts[0]) : 0,
                parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
                parts.length > 2 ? Integer.parseInt(parts[2]) : 0
        };
    }

    private void startUpdateChecker() {
        long intervalTicks = CHECK_INTERVAL.toSeconds() * 20L;

        plugin.getScheduler().runTaskTimerAsynchronously(() -> {
            checkForUpdates().thenAccept(updateAvailable -> {
                if (updateAvailable) {
                    plugin.getScheduler().runTaskLaterAsynchronously(() -> Bukkit.getConsoleSender().sendMessage(MessageKeys.UPDATE_NOTIFY.getMessage()
                            .replace("{your}", currentVersion)
                            .replace("{latest}", latestVersion.get())), NOTIFICATION_DELAY_TICKS);
                }
            }).exceptionally(throwable -> {
                LoggerUtils.warn("Failed to check for updates", throwable);
                return null;
            });
        }, 0L, intervalTicks);
    }

    public void shutdown() {
        httpClient.close();
    }
}
