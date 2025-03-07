package hu.fyremc.fyrechatgame.models;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.handler.GameHandler;
import hu.fyremc.fyrechatgame.identifiers.GameState;
import hu.fyremc.fyrechatgame.identifiers.keys.ConfigKeys;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GameRandomCharacters extends GameHandler {
    private MyScheduledTask timeoutTask;
    private String targetSequence;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        this.targetSequence = generateSequence();
        this.state = GameState.ACTIVE;
        this.gameData = targetSequence;

        announceGame();
        scheduleTimeout();
    }

    private String generateSequence() {
        int length = ConfigKeys.RANDOM_CHARACTERS_LENGTH.getInt();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    private void announceGame() {
        Bukkit.getOnlinePlayers().forEach(p ->
                p.sendMessage("§6§lRANDOM CODE §7» §eMásold le ezt: §b" + targetSequence)
        );
    }

    private void scheduleTimeout() {
        timeoutTask = FyreChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE) {
                Bukkit.broadcastMessage("§cIdő lejárt! Senki nem másolta le: §e" + targetSequence);
                cleanup();
            }
        }, ConfigKeys.RANDOM_CHARACTERS_TIME.getInt() * 20L);
    }

    @Override
    public void stop() {
        if (timeoutTask != null) timeoutTask.cancel();
        cleanup();
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {
        if (state != GameState.ACTIVE) return;

        if (answer.equals(targetSequence)) {
            rewardPlayer(player);
            Bukkit.broadcastMessage("§a" + player.getName() + " §7pontosan másolta a kódot!");
            cleanup();
        }
    }

    private void rewardPlayer(@NotNull Player winner) {
        List<String> rewards = ConfigKeys.REWARDS.getList();
        if (rewards.isEmpty()) return;

        String command = rewards.get(random.nextInt(rewards.size())).replace("{player}", winner.getName());

        Bukkit.getScheduler().runTask(FyreChatGame.getInstance(), () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }
}
