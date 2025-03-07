package hu.fyremc.fyrechatgame.processor;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import hu.fyremc.fyrechatgame.FyreChatGame;
import hu.fyremc.fyrechatgame.identifiers.GameTypes;
import hu.fyremc.fyrechatgame.manager.GameManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AutoGameProcessor {
    private final List<GameTypes> GAME_POOL = Arrays.asList(
            GameTypes.MATH,
            GameTypes.RANDOM_CHARACTERS
    );
    private MyScheduledTask task;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    public void start() {
        stopExistingTask();
        scheduleNewTask();
    }

    public void stop() {
        stopExistingTask();
    }

    private void stopExistingTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void scheduleNewTask() {
        task = FyreChatGame.getInstance().getScheduler().runTaskTimer(() -> {
            if (GameManager.getActiveGameCount() == 0) {
                startRandomGame();
            }
        }, 2400L, 2400L);
    }

    private void startRandomGame() {
        GameTypes randomType = GAME_POOL.get(random.nextInt(GAME_POOL.size()));
        GameManager.startGame(randomType);
    }
}
