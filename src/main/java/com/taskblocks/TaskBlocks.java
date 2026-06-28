package com.taskblocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class TaskBlocks implements ModInitializer {
    public static final String MOD_ID = "taskblocks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[TaskBlocks] Mod initialized.");
    }
}