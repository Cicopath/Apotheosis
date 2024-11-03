package dev.shadowsoffire.apotheosis.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.AdventureConfig;
import dev.shadowsoffire.apotheosis.Apoth.Components;
import dev.shadowsoffire.apotheosis.util.LootPatternMatcher;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class AffixLootModifier extends LootModifier {

    public static final MapCodec<AffixLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, AffixLootModifier::new));

    protected AffixLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        var player = GemLootPoolEntry.findPlayer(context);
        if (player == null) return generatedLoot;

        // TODO: Move convert loot rules into this loot modifier as a codec parameter.
        for (LootPatternMatcher m : AdventureConfig.AFFIX_ITEM_LOOT_RULES) {
            if (m.matches(context.getQueriedLootTableId())) {
                if (context.getRandom().nextFloat() <= m.chance()) {
                    ItemStack affixItem = LootController.createRandomLootItem(context.getRandom(), null, player, context.getLevel());
                    if (affixItem.isEmpty()) break;
                    affixItem.set(Components.FROM_CHEST, true);
                    generatedLoot.add(affixItem);
                }
                break;
            }
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
