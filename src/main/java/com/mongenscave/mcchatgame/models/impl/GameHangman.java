package com.mongenscave.mcchatgame.models.impl;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.identifiers.GameType;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.processor.AutoGameProcessor;
import com.mongenscave.mcchatgame.processor.MessageProcessor;
import com.mongenscave.mcchatgame.services.MainThreadExecutorService;
import com.mongenscave.mcchatgame.utils.GameUtils;
import com.mongenscave.mcchatgame.utils.LoggerUtils;
import com.mongenscave.mcchatgame.utils.PlayerUtils;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class GameHangman extends GameHandler {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private MyScheduledTask timeoutTask;
    private String correctWord;
    private Set<Character> guessedLetters;
    private Set<Player> playersWhoGuessed;
    private int wrongGuesses;
    private boolean gameWon = false;

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        if (isRemoteGame && remoteGameData != null && !remoteGameData.isEmpty()) {
            this.correctWord = remoteGameData.toUpperCase();
            LoggerUtils.info("Starting REMOTE hangman game with word: {}", correctWord);
        } else {
            List<String> words = ConfigKeys.HANGMAN_WORDS.getList();
            if (words.isEmpty()) return;
            this.correctWord = words.get(random.nextInt(words.size())).toUpperCase();
        }

        this.guessedLetters = Collections.synchronizedSet(new HashSet<>());
        this.playersWhoGuessed = Collections.synchronizedSet(new HashSet<>());
        this.wrongGuesses = 0;
        this.gameWon = false;
        this.gameData = buildDisplayWord();
        this.setAsActive();

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        announceGame();
        scheduleTimeout();
    }

    @Override
    protected String getOriginalGameData() {
        return correctWord;
    }

    @Override
    public void stop() {
        if (timeoutTask != null) timeoutTask.cancel();
        cleanup();

        AutoGameProcessor gameProcessor = McChatGame.getInstance().getGameProcessor();
        gameProcessor.start();
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {
        if (state != GameState.ACTIVE || gameWon) return;
        if (answer.length() != 1) return;

        char letter = answer.toUpperCase().charAt(0);

        if (!Character.isLetter(letter)) return;
        if (ConfigKeys.HANGMAN_ONLY_ONCE.getBoolean() && playersWhoGuessed.contains(player)) return;
        if (guessedLetters.contains(letter)) return;

        guessedLetters.add(letter);
        playersWhoGuessed.add(player);

        if (correctWord.contains(String.valueOf(letter))) {
            this.gameData = buildDisplayWord();
            announceGame();

            if (isWordComplete()) {
                gameWon = true;
                long endTime = System.currentTimeMillis();
                double timeTaken = (endTime - startTime) / 1000.0;
                String formattedTime = String.format("%.2f", timeTaken);

                if (timeoutTask != null) timeoutTask.cancel();

                McChatGame.getInstance().getDatabase().incrementWin(player)
                        .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                        .thenAcceptAsync(v -> {
                            GameUtils.rewardPlayer(player);

                            if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                                GameUtils.broadcast(MessageProcessor.process(MessageKeys.HANGMAN_WIN.getMessage()
                                        .replace("{player}", player.getName())
                                        .replace("{time}", formattedTime)));
                            }

                            handlePlayerWin(player);
                            cleanup();
                        }, MainThreadExecutorService.getInstance().getMainThreadExecutor());

                PlayerUtils.sendToast(player, ConfigKeys.TOAST_MESSAGE, ConfigKeys.TOAST_MATERIAL, ConfigKeys.TOAST_ENABLED);
                GameUtils.playSoundToWinner(player, ConfigKeys.SOUND_WIN_ENABLED, ConfigKeys.SOUND_WIN_SOUND);
            }
        } else {
            wrongGuesses++;

            if (isGameLost()) {
                gameWon = true;
                if (timeoutTask != null) timeoutTask.cancel();

                if (McChatGame.getInstance().getProxyManager().isEnabled() &&
                        McChatGame.getInstance().getProxyManager().isMasterServer()) {
                    McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), correctWord);
                } else if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                    GameUtils.broadcast(MessageProcessor.process(MessageKeys.HANGMAN_NO_WIN.getMessage().replace("{word}", correctWord)));
                }

                handleGameTimeout();
                cleanup();
            } else {
                announceGame();
            }
        }
    }

    @Override
    protected void cleanup() {
        super.cleanup();
        guessedLetters = null;
        playersWhoGuessed = null;
        correctWord = null;
        gameWon = false;
        wrongGuesses = 0;
    }

    @Override
    protected GameType getGameType() {
        return GameType.HANGMAN;
    }

    private void announceGame() {
        String word = buildDisplayWord();
        String stages = buildStages();

        List<String> messages = MessageKeys.HANGMAN.getMessages();
        for (String message : messages) {
            String processedMessage = MessageProcessor.process(message
                    .replace("{word}", word)
                    .replace("{stage}", stages));
            GameUtils.broadcast(processedMessage);
        }
    }

    @NotNull
    private String buildDisplayWord() {
        StringBuilder displayWord = new StringBuilder();
        String placeholders = MessageProcessor.process(ConfigKeys.HANGMAN_PLACEHOLDERS.getString());
        String foundFormat = MessageProcessor.process(ConfigKeys.HANGMAN_FOUND.getString());

        for (char letter : correctWord.toCharArray()) {
            if (guessedLetters.contains(letter)) {
                displayWord.append(foundFormat.replace("{letter}", String.valueOf(letter)));
            } else {
                displayWord.append(placeholders);
            }
        }

        return displayWord.toString();
    }

    @NotNull
    private String buildStages() {
        if (!ConfigKeys.HANGMAN_STAGES_ENABLED.getBoolean()) return "";

        Section stagesSection = McChatGame.getInstance().getConfiguration().getSection("hangman.stages");
        if (stagesSection == null) return "";

        StringBuilder stages = new StringBuilder();
        String stageInColor = MessageProcessor.process(ConfigKeys.HANGMAN_STAGE_IN.getString());
        String stageOutColor = MessageProcessor.process(ConfigKeys.HANGMAN_STAGE_OUT.getString());

        Set<String> stageKeys = stagesSection.getRoutesAsStrings(false);
        List<Integer> sortedStageNumbers = Collections.synchronizedList(new ArrayList<>());

        for (String key : stageKeys) {
            try {
                sortedStageNumbers.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {}
        }

        sortedStageNumbers.sort(Integer::compareTo);

        for (int i = 0; i < sortedStageNumbers.size(); i++) {
            int stageNumber = sortedStageNumbers.get(i);
            String stageLine = stagesSection.getString(String.valueOf(stageNumber));

            if (stageLine == null) continue;

            if (stageNumber <= wrongGuesses) {
                stages.append(stageInColor).append(stageLine);
            } else {
                stages.append(stageOutColor).append(stageLine);
            }

            if (i < sortedStageNumbers.size() - 1) stages.append("\n");
        }

        return stages.toString();
    }

    private boolean isWordComplete() {
        for (char letter : correctWord.toCharArray()) {
            if (!guessedLetters.contains(letter)) return false;
        }
        return true;
    }

    private boolean isGameLost() {
        Section stagesSection = McChatGame.getInstance().getConfiguration().getSection("hangman.stages");
        if (stagesSection == null) return false;

        int maxStages = stagesSection.getRoutesAsStrings(false).size();
        return wrongGuesses >= maxStages;
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && !gameWon) {
                if (McChatGame.getInstance().getProxyManager().isEnabled() &&
                        McChatGame.getInstance().getProxyManager().isMasterServer()) {
                    McChatGame.getInstance().getProxyManager().broadcastGameTimeout(getGameType(), correctWord);
                } else if (!McChatGame.getInstance().getProxyManager().isEnabled()) {
                    GameUtils.broadcast(MessageProcessor.process(MessageKeys.HANGMAN_NO_WIN.getMessage().replace("{answer}", correctWord)));
                }

                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.HANGMAN_TIME.getInt() * 20L);
    }
}