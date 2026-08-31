package com.pisomarket.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// The one place this mod's chat styling lives, implementing the colour
// system in CLAUDE.md's "Chat formatting" section.
//
// The rule that makes it feel deliberate: MONEY IS ALWAYS YELLOW-BOLD and
// NAMES ARE ALWAYS AQUA. A player scanning a wall of chat picks out amounts
// instantly without reading.
//
// There is no font size in Minecraft chat — the only levers are the 16
// named ChatFormatting colours plus bold/italic/underline/strikethrough.
// Anything that reads as "bigger" has to come from colour and weight.
public final class PisoText {
	private PisoText() {
	}

	// Every mod message carries this, so it is instantly separable from
	// vanilla output.
	public static MutableComponent prefix() {
		return Component.literal("[Piso] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}

	public static MutableComponent success(final String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.GREEN));
	}

	public static MutableComponent failure(final String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.RED));
	}

	public static MutableComponent body(final String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.WHITE));
	}

	public static MutableComponent warning(final String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
	}

	// Footnotes, usage tips, "updates every ..." — deliberately quiet so
	// they never compete with the line above them.
	public static MutableComponent hint(final String text) {
		return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
	}

	// Currency amount. Never used for anything that is not money.
	public static MutableComponent money(final long amount) {
		return Component.literal(String.valueOf(amount)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
	}

	// Player names, item names, claim ids.
	public static MutableComponent name(final String text) {
		return Component.literal(text).withStyle(ChatFormatting.AQUA);
	}

	public static MutableComponent plain(final String text) {
		return Component.literal(text).withStyle(ChatFormatting.WHITE);
	}
}
