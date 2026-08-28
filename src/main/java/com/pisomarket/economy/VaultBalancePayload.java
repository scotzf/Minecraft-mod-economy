package com.pisomarket.economy;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.pisomarket.PisoMarket;

// Server -> client only. The vault balance lives in PisoVault, a server-side
// SavedData — the client (where the HUD renders every frame) has no access
// to it at all, so it has to be pushed over the network and cached
// client-side. See VaultSync for where this actually gets sent.
public record VaultBalancePayload(long balance) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<VaultBalancePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PisoMarket.MOD_ID, "vault_balance"));

	public static final StreamCodec<ByteBuf, VaultBalancePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_LONG, VaultBalancePayload::balance, VaultBalancePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
