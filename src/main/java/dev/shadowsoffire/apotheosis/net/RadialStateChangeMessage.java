package dev.shadowsoffire.apotheosis.net;

import java.util.function.Supplier;

import dev.shadowsoffire.apotheosis.affix.effect.RadialAffix;
import dev.shadowsoffire.placebo.network.MessageHelper;
import dev.shadowsoffire.placebo.network.MessageProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

public class RadialStateChangeMessage {
    
    public static RadialStateChangeMessage INSTANCE = new RadialStateChangeMessage();

    public static class Provider implements MessageProvider<RadialStateChangeMessage> {

        @Override
        public Class<?> getMsgClass() {
            return RadialStateChangeMessage.class;
        }

        @Override
        public void write(RadialStateChangeMessage msg, FriendlyByteBuf buf) {

        }

        @Override
        public RadialStateChangeMessage read(FriendlyByteBuf buf) {
            return new RadialStateChangeMessage();
        }

        @Override
        public void handle(RadialStateChangeMessage msg, Supplier<Context> ctx) {
            MessageHelper.handlePacket(() -> {
                Player player = ctx.get().getSender();
                if (player == null) return;
                RadialAffix.toggleRadialState(player);
            }, ctx);
        }

    }

}
