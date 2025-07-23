package com.mongenscave.mcchatgame.models.impl;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.mongenscave.mcchatgame.McChatGame;
import com.mongenscave.mcchatgame.identifiers.GameState;
import com.mongenscave.mcchatgame.identifiers.keys.ConfigKeys;
import com.mongenscave.mcchatgame.identifiers.keys.MessageKeys;
import com.mongenscave.mcchatgame.models.GameHandler;
import com.mongenscave.mcchatgame.processor.AutoGameProcessor;
import com.mongenscave.mcchatgame.processor.MessageProcessor;
import com.mongenscave.mcchatgame.services.MainThreadExecutorService;
import com.mongenscave.mcchatgame.utils.GameUtils;
import com.mongenscave.mcchatgame.utils.PlayerUtils;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.coma112.easiermessages.EasierMessages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public class GameCrafting extends GameHandler implements Listener {
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final AtomicBoolean winnerDetermined = new AtomicBoolean(false);
    private MyScheduledTask timeoutTask;
    private String targetItem;
    private List<String> requiredItems;
    private long startTime;
    private final Set<UUID> participatingPlayers = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<UUID, Inventory> playerInventories = new ConcurrentHashMap<>();

    @Override
    public void start() {
        if (state == GameState.ACTIVE) return;

        Section craftsSection = ConfigKeys.CRAFTING_CRAFTS.getSection();
        if (craftsSection == null || craftsSection.getRoutesAsStrings(false).isEmpty()) return;

        ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> crafts = new ConcurrentHashMap<>();

        for (String key : craftsSection.getRoutesAsStrings(false)) {
            Section craftSection = craftsSection.getSection(key);

            if (craftSection != null) {
                ConcurrentHashMap<String, Object> craftData = new ConcurrentHashMap<>();
                craftData.put("items-to-place", craftSection.getStringList("items-to-place"));
                crafts.put(key, craftData);
            }
        }

        GameUtils.playSoundToEveryone(ConfigKeys.SOUND_START_ENABLED, ConfigKeys.SOUND_START_SOUND);

        List<String> craftKeys = new ArrayList<>(crafts.keySet());
        this.targetItem = craftKeys.get(random.nextInt(craftKeys.size()));

        ConcurrentHashMap<String, Object> craftData = crafts.get(targetItem);
        @SuppressWarnings("unchecked") List<String> itemsToPlace = (List<String>) craftData.get("items-to-place");

        this.requiredItems = new ArrayList<>(itemsToPlace);
        this.gameData = targetItem;
        this.startTime = System.currentTimeMillis();
        this.winnerDetermined.set(false);
        this.setAsActive();

        Bukkit.getPluginManager().registerEvents(this, McChatGame.getInstance());

        announceCrafting();
        scheduleTimeout();
    }

    @Override
    public void stop() {
        if (timeoutTask != null) timeoutTask.cancel();

        for (UUID playerId : participatingPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) player.closeInventory();
        }

        HandlerList.unregisterAll(this);

        cleanup();

        AutoGameProcessor gameProcessor = McChatGame.getInstance().getGameProcessor();
        gameProcessor.start();
    }

    @Override
    public void handleAnswer(@NotNull Player player, @NotNull String answer) {}

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    protected void cleanup() {
        winnerDetermined.set(false);
        super.cleanup();
    }

    public void openCraftingMenu(@NotNull Player player) {
        if (state != GameState.ACTIVE) return;

        participatingPlayers.add(player.getUniqueId());

        Inventory craftingInv = Bukkit.createInventory(null, InventoryType.WORKBENCH, MessageProcessor.process(ConfigKeys.CRAFTING_TITLE.getString().replace("{item}", targetItem)));

        List<String> shuffledItems = new ArrayList<>(requiredItems);
        Collections.shuffle(shuffledItems);

        for (int i = 0; i < Math.min(shuffledItems.size(), 9); i++) {
            Material material = Material.valueOf(shuffledItems.get(i));
            ItemStack item = new ItemStack(material, 1);
            craftingInv.setItem(i + 1, item);
        }

        playerInventories.put(player.getUniqueId(), craftingInv);
        player.openInventory(craftingInv);
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (state != GameState.ACTIVE) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!participatingPlayers.contains(player.getUniqueId())) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null || !playerInventories.containsValue(clickedInv)) return;

        if (event.getSlot() == 0) {
            ItemStack resultItem = event.getCurrentItem();
            if (resultItem != null && resultItem.getType() == Material.valueOf(targetItem)) {
                event.setCancelled(true);
                if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) handleWin(player);
                return;
            } else {
                event.setCancelled(true);
                return;
            }
        }

        McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && !winnerDetermined.get()) updateCraftingResult(clickedInv);
        }, 1L);
    }

    @EventHandler
    public void onInventoryClose(final @NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!participatingPlayers.contains(player.getUniqueId())) return;

        participatingPlayers.remove(player.getUniqueId());
        playerInventories.remove(player.getUniqueId());
    }

    private void handleWin(@NotNull Player player) {
        long endTime = System.currentTimeMillis();
        double timeTaken = (endTime - startTime) / 1000.0;
        String formattedTime = String.format("%.2f", timeTaken);

        if (timeoutTask != null) timeoutTask.cancel();

        McChatGame.getInstance().getDatabase().incrementWin(player)
                .thenCompose(v -> McChatGame.getInstance().getDatabase().setTime(player, timeTaken))
                .thenAcceptAsync(v -> {
                    GameUtils.rewardPlayer(player);
                    GameUtils.broadcast(MessageKeys.CRAFTING_WIN.getMessage()
                            .replace("{time}", formattedTime)
                            .replace("{player}", player.getName()));

                    handlePlayerWin(player);
                    cleanup();
                }, MainThreadExecutorService.getInstance().getMainThreadExecutor());

        PlayerUtils.sendToast(player, ConfigKeys.TOAST_MESSAGE, ConfigKeys.TOAST_MATERIAL, ConfigKeys.TOAST_ENABLED);
        GameUtils.playSoundToWinner(player, ConfigKeys.SOUND_WIN_ENABLED, ConfigKeys.SOUND_WIN_SOUND);
    }

    private void scheduleTimeout() {
        timeoutTask = McChatGame.getInstance().getScheduler().runTaskLater(() -> {
            if (state == GameState.ACTIVE && winnerDetermined.compareAndSet(false, true)) {
                GameUtils.broadcast(MessageKeys.CRAFTING_NO_WIN.getMessage());
                handleGameTimeout();
                cleanup();
            }
        }, ConfigKeys.CRAFTING_TIME.getInt() * 20L);
    }

    private void updateCraftingResult(@NotNull Inventory inventory) {
        if (state != GameState.ACTIVE || winnerDetermined.get()) return;

        ItemStack[] matrix = new ItemStack[9];

        for (int i = 1; i <= 9; i++) {
            matrix[i - 1] = inventory.getItem(i);
        }

        for (Recipe recipe : Bukkit.getRecipesFor(new ItemStack(Material.valueOf(targetItem)))) {
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                if (matchesShapedRecipe(matrix, shapedRecipe)) {
                    inventory.setItem(0, new ItemStack(Material.valueOf(targetItem), 1));
                    return;
                }
            } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                if (matchesShapelessRecipe(matrix, shapelessRecipe)) {
                    inventory.setItem(0, new ItemStack(Material.valueOf(targetItem), 1));
                    return;
                }
            }
        }

        inventory.setItem(0, null);
    }

    private boolean matchesShapedRecipe(ItemStack[] matrix, @NotNull ShapedRecipe recipe) {
        String[] shape = recipe.getShape();
        Map<Character, ItemStack> ingredients = recipe.getIngredientMap();

        for (int offsetRow = 0; offsetRow <= 3 - shape.length; offsetRow++) {
            for (int offsetCol = 0; offsetCol <= 3 - shape[0].length(); offsetCol++) {
                if (matchesAtPosition(matrix, shape, ingredients, offsetRow, offsetCol)) return true;
            }
        }

        return false;
    }

    private boolean matchesAtPosition(ItemStack[] matrix, @NotNull String[] shape, Map<Character, ItemStack> ingredients, int offsetRow, int offsetCol) {
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                char c = shape[row].charAt(col);
                int matrixIndex = (row + offsetRow) * 3 + (col + offsetCol);

                ItemStack required = ingredients.get(c);
                ItemStack actual = matrix[matrixIndex];

                if (c == ' ') if (actual != null && actual.getType() != Material.AIR) return false;
                else if (!itemsMatch(actual, required)) return false;
            }
        }

        for (int i = 0; i < 9; i++) {
            int row = i / 3;
            int col = i % 3;

            boolean isInShape = (row >= offsetRow && row < offsetRow + shape.length && col >= offsetCol && col < offsetCol + shape[0].length());

            if (!isInShape) {
                ItemStack item = matrix[i];
                if (item != null && item.getType() != Material.AIR) return false;
            }
        }

        return true;
    }

    private boolean matchesShapelessRecipe(@NotNull ItemStack[] matrix, @NotNull org.bukkit.inventory.ShapelessRecipe recipe) {
        List<ItemStack> required = new ArrayList<>(recipe.getIngredientList());
        List<ItemStack> provided = new ArrayList<>();

        for (ItemStack item : matrix) {
            if (item != null && item.getType() != Material.AIR) provided.add(item.clone());
        }

        if (required.size() != provided.size()) return false;

        for (ItemStack req : required) {
            boolean found = false;

            for (int i = 0; i < provided.size(); i++) {
                if (itemsMatch(provided.get(i), req)) {
                    provided.remove(i);
                    found = true;
                    break;
                }
            }

            if (!found) return false;
        }

        return provided.isEmpty();
    }

    private boolean itemsMatch(@Nullable ItemStack actual, @Nullable ItemStack required) {
        if (actual == null && required == null) return true;
        if (actual == null || required == null) return false;

        return actual.getType() == required.getType();
    }

    private void announceCrafting() {
        for (String lines : MessageKeys.CRAFTING.getMessages()) {
            Component component = EasierMessages.translateMessage(lines.replace("{item}", targetItem))
                    .build();

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(component);
            }
        }
    }
}