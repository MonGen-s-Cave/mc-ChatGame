package hu.fyremc.fyrechatgame.models;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.data.Problem;
import hu.fyremc.fyrechatgame.handler.GameHandler;
import hu.fyremc.fyrechatgame.identifiers.GameState;
import hu.fyremc.fyrechatgame.identifiers.keys.ConfigKeys;
import hu.fyremc.fyrechatgame.identifiers.keys.MessageKeys;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class GameMath extends GameHandler {
    private MyScheduledTask timeoutTask;
    private String correctAnswer;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        List<String> problems = ConfigKeys.MATH_PROBLEMS.getList();
        if (problems.isEmpty()) return;

        String[] problemData = parseProblem(problems.get(random.nextInt(problems.size())));
        if (problemData == null) return;

        this.correctAnswer = problemData[1];
        this.state = GameState.ACTIVE;
        this.gameData = problemData[0];

        announceProblem();
        scheduleTimeout();
    }

    @Nullable
    private String[] parseProblem(@NotNull String raw) {
        String[] parts = raw.split("=", 2);
        if (parts.length != 2) return null;
        return new String[]{parts[0].trim(), parts[1].trim()};
    }

    private void announceProblem() {
        String question = (String) gameData;
        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendMessage("§e§lMATH CHALLENGE §7» §f" + question + " §eMennyi?")
        );
    }

    private void scheduleTimeout() {
        timeoutTask = FyreChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE) {
                Bukkit.broadcastMessage("§cIdő lejárt! Helyes válasz: §e" + correctAnswer);
                cleanup();
            }
        }, ConfigKeys.MATH_TIME.getInt() * 20L);
    }

    @Override
    public void stop() {
        if (timeoutTask != null) timeoutTask.cancel();
        cleanup();
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {
        if (state != GameState.ACTIVE) return;

        if (answer.trim().equalsIgnoreCase(correctAnswer)) {
            rewardPlayer(player);
            Bukkit.broadcastMessage("§a" + player.getName() + " §7megoldotta a feladatot!");
            cleanup();
        }
    }

    private void rewardPlayer(@NotNull Player winner) {
        List<String> rewards = ConfigKeys.REWARDS.getList();
        if (rewards.isEmpty()) return;

        String command = rewards.get(random.nextInt(rewards.size()))
                .replace("{player}", winner.getName());

        Bukkit.getScheduler().runTask(FyreChatGame.getInstance(), () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        );
    }
}