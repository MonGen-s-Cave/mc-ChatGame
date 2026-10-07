package com.mongenscave.mcchatgame.data;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record WeightedReward(int weight, @NotNull List<String> commands, @NotNull String displayName) {}
