package com.mongenscave.mcchatgame.data;

import org.jetbrains.annotations.NotNull;

public record WeightedReward(int weight, @NotNull String command) {}
