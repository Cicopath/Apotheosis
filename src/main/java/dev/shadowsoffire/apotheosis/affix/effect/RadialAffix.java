package dev.shadowsoffire.apotheosis.affix.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.spongepowered.include.com.google.common.base.Preconditions;

import com.google.common.base.Predicate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.Apoth.Components;
import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixBuilder;
import dev.shadowsoffire.apotheosis.affix.AffixDefinition;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.placebo.util.PlaceboUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.event.level.BlockEvent;

public class RadialAffix extends Affix {

    public static final Codec<RadialAffix> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            affixDef(),
            LootRarity.mapCodec(Codec.list(RadialData.CODEC)).fieldOf("values").forGetter(a -> a.values))
        .apply(inst, RadialAffix::new));

    private static Set<UUID> breakers = new HashSet<>();

    protected final Map<LootRarity, List<RadialData>> values;

    public RadialAffix(AffixDefinition def, Map<LootRarity, List<RadialData>> values) {
        super(def);
        this.values = values;
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory cat, LootRarity rarity) {
        return cat.isBreaker() && this.values.containsKey(rarity);
    }

    @Override
    public MutableComponent getDescription(AffixInstance inst, AttributeTooltipContext ctx) {
        RadialData data = this.getTrueLevel(inst);
        return Component.translatable("affix." + this.id() + ".desc", data.x, data.y);
    }

    @Override
    public Component getAugmentingText(AffixInstance inst, AttributeTooltipContext ctx) {
        MutableComponent comp = this.getDescription(inst, ctx);
        RadialData min = this.getTrueLevel(inst.getRarity(), 0);
        RadialData max = this.getTrueLevel(inst.getRarity(), 1);

        if (min != max) {
            Component minComp = Component.translatable("%sx%s", min.x, min.y);
            Component maxComp = Component.translatable("%sx%s", max.x, max.y);
            comp.append(valueBounds(minComp, maxComp));
        }

        return comp;
    }

    // EventPriority.LOW
    public static void onBreak(BlockEvent.BreakEvent e) {
        Player player = e.getPlayer();
        ItemStack tool = player.getMainHandItem();
        Level world = player.level();
        if (!world.isClientSide && tool.has(Components.AFFIXES)) {
            AffixInstance inst = AffixHelper.streamAffixes(tool).filter(i -> i.getAffix() instanceof RadialAffix).findFirst().orElse(null);
            if (inst != null && inst.isValid() && RadialState.getState(player).isRadialMiningEnabled(player)) {
                float hardness = e.getState().getDestroySpeed(e.getLevel(), e.getPos());
                breakExtraBlocks((ServerPlayer) player, e.getPos(), tool, ((RadialAffix) inst.getAffix()).getTrueLevel(inst.rarity().get(), inst.level()), hardness);
            }
        }
    }

    private RadialData getTrueLevel(AffixInstance inst) {
        return this.getTrueLevel(inst.getRarity(), inst.level());
    }

    private RadialData getTrueLevel(LootRarity rarity, float level) {
        var list = this.values.get(rarity);
        return list.get(Math.min(list.size() - 1, (int) Mth.lerp(level, 0, list.size())));
    }

    @Override
    public Codec<? extends Affix> getCodec() {
        return CODEC;
    }

    /**
     * Updates the players radial state to the next state, and notifies them of the change.
     */
    public static void toggleRadialState(Player player) {
        RadialState state = RadialState.getState(player);
        RadialState next = state.next();
        RadialState.setState(player, next);
        player.sendSystemMessage(Apotheosis.sysMessageHeader().append(Component.translatable("misc.apotheosis.radial_state_updated", next.toComponent(), state.toComponent()).withStyle(ChatFormatting.YELLOW)));
    }

    /**
     * Performs the actual extra breaking of blocks
     *
     * @param player The player breaking the block
     * @param pos    The position of the originally broken block
     * @param tool   The tool being used (which has this affix on it)
     * @param level  The level of this affix, in this case, the mode of operation.
     */
    public static void breakExtraBlocks(ServerPlayer player, BlockPos pos, ItemStack tool, RadialData level, float hardness) {
        if (!breakers.add(player.getUUID())) return; // Prevent multiple break operations from cascading, and don't execute when sneaking.
        try {
            breakBlockRadius(player, pos, level.x, level.y, level.xOff, level.yOff, hardness);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        breakers.remove(player.getUUID());
    }

    public static void breakBlockRadius(ServerPlayer player, BlockPos pos, int x, int y, int xOff, int yOff, float hardness) {
        Level world = player.level();
        if (x < 2 && y < 2) return;
        int lowerY = (int) Math.ceil(-y / 2D), upperY = (int) Math.round(y / 2D);
        int lowerX = (int) Math.ceil(-x / 2D), upperX = (int) Math.round(x / 2D);

        Vec3 base = player.getEyePosition(0);
        Vec3 look = player.getLookAngle();
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 target = base.add(look.x * reach, look.y * reach, look.z * reach);
        HitResult trace = world.clip(new ClipContext(base, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (trace == null || trace.getType() != Type.BLOCK) return;
        BlockHitResult res = (BlockHitResult) trace;

        Direction face = res.getDirection(); // Face of the block currently being looked at by the player.

        for (int iy = lowerY; iy < upperY; iy++) {
            for (int ix = lowerX; ix < upperX; ix++) {
                BlockPos genPos = new BlockPos(pos.getX() + ix + xOff, pos.getY() + iy + yOff, pos.getZ());

                if (player.getDirection().getAxis() == Axis.X) {
                    genPos = new BlockPos(genPos.getX() - (ix + xOff), genPos.getY(), genPos.getZ() + ix + xOff);
                }

                if (face.getAxis().isVertical()) {
                    genPos = rotateDown(genPos, iy + yOff, player.getDirection());
                }

                if (genPos.equals(pos)) continue;
                BlockState state = world.getBlockState(genPos);
                float stateHardness = state.getDestroySpeed(world, genPos);
                if (!state.isAir() && stateHardness != -1 && stateHardness <= hardness * 3F && isEffective(state, player, genPos)) PlaceboUtil.tryHarvestBlock(player, genPos);
            }
        }

    }

    static BlockPos rotateDown(BlockPos pos, int y, Direction horizontal) {
        Vec3i vec = horizontal.getNormal();
        return new BlockPos(pos.getX() + vec.getX() * y, pos.getY() - y, pos.getZ() + vec.getZ() * y);
    }

    static boolean isEffective(BlockState state, Player player, BlockPos pos) {
        return player.hasCorrectToolForDrops(state, player.level(), pos);
    }

    static record RadialData(int x, int y, int xOff, int yOff) {

        public static Codec<RadialData> CODEC = RecordCodecBuilder.create(inst -> inst
            .group(
                Codec.INT.fieldOf("x").forGetter(RadialData::x),
                Codec.INT.fieldOf("y").forGetter(RadialData::y),
                Codec.INT.fieldOf("xOff").forGetter(RadialData::xOff),
                Codec.INT.fieldOf("yOff").forGetter(RadialData::yOff))
            .apply(inst, RadialData::new));

    }

    static enum RadialState {
        REQUIRE_NOT_SNEAKING(p -> !p.isShiftKeyDown()),
        REQUIRE_SNEAKING(Player::isShiftKeyDown),
        ENABLED(p -> true),
        DISABLED(p -> false);

        private Predicate<Player> condition;

        RadialState(Predicate<Player> condition) {
            this.condition = condition;
        }

        /**
         * @return If the radial breaking feature is enabled while the player is in the current state
         */
        public boolean isRadialMiningEnabled(Player input) {
            return this.condition.apply(input);
        }

        public RadialState next() {
            return switch (this) {
                case REQUIRE_NOT_SNEAKING -> REQUIRE_SNEAKING;
                case REQUIRE_SNEAKING -> ENABLED;
                case ENABLED -> DISABLED;
                case DISABLED -> REQUIRE_NOT_SNEAKING;
            };
        }

        public Component toComponent() {
            return Component.translatable("misc.apotheosis.radial_state." + this.name().toLowerCase(Locale.ROOT));
        }

        /**
         * Returns the current radial break state for the given player.
         * <p>
         * The state defaults to {@link #REQUIRE_NOT_SNEAKING} if no state is set.
         *
         * @param player The player
         * @return The current radial state, or {@link #REQUIRE_NOT_SNEAKING} if a parse error occurred.
         */
        public static RadialState getState(Player player) {
            String str = player.getPersistentData().getString("apoth.radial_state");
            try {
                return RadialState.valueOf(str);
            }
            catch (Exception ex) {
                setState(player, RadialState.REQUIRE_NOT_SNEAKING);
                return RadialState.REQUIRE_NOT_SNEAKING;
            }
        }

        public static void setState(Player player, RadialState state) {
            player.getPersistentData().putString("apoth.radial_state", state.name());
        }
    }

    public static class Builder extends AffixBuilder<Builder> {

        protected final Map<LootRarity, List<RadialData>> values = new HashMap<>();

        public Builder value(LootRarity rarity, UnaryOperator<DataListBuilder> config) {
            List<RadialData> list = new ArrayList<>();
            config.apply(new DataListBuilder(){

                @Override
                public DataListBuilder radii(int x, int y, int xOffset, int yOffset) {
                    list.add(new RadialData(x, y, xOffset, yOffset));
                    return this;
                }

            });

            this.values.put(rarity, list);
            return this;
        }

        public RadialAffix build() {
            Preconditions.checkNotNull(this.definition);
            Preconditions.checkArgument(this.values.size() > 0);
            return new RadialAffix(this.definition, this.values);
        }

        public static interface DataListBuilder {

            DataListBuilder radii(int x, int y, int xOffset, int yOffset);

            default DataListBuilder radii(int x, int y) {
                return this.radii(x, y, 0, 0);
            }
        }
    }

}
