package com.bolat.casinomod.network;

import com.bolat.casinomod.menu.CasinoMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "I picked item number <index> from the picker". */
public class PickPacket {
    private final int index;

    public PickPacket(int index) {
        this.index = index;
    }

    public static void encode(PickPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.index);
    }

    public static PickPacket decode(FriendlyByteBuf buf) {
        return new PickPacket(buf.readVarInt());
    }

    public static void handle(PickPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null && player.containerMenu instanceof CasinoMenu menu) {
                menu.pick(msg.index);
            }
        });
        c.setPacketHandled(true);
    }
}
