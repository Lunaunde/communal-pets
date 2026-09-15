package com.github.lunaunde.communalpets;

import com.github.lunaunde.communalpets.command.CommunalPetsCommand;
import com.github.lunaunde.communalpets.world.entity.animal.PetGlow;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommunalPets implements ModInitializer {
	public static final String MOD_ID = "communal-pets";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Loading Communal Pets");

		CommunalPetsConfig.load();
		CommunalPetsCommand.register();

		// "只有 owner 群看得见发光" 的纯服务端实现：
		// END_LEVEL_TICK 注入在 ServerLevel.tick() 的 TAIL 上（entityManagement 之后），
		// 所以每 tick 在这里补发一次发光位，就能保证它是本 tick 最后一个写这个字节的人。
		ServerTickEvents.END_LEVEL_TICK.register(PetGlow::pushGlowFlags);
	}
}
