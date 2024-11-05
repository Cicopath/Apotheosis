package dev.shadowsoffire.apotheosis.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.ItemAffixes;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class LootifyCommand {

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_RARITY = RarityCommand.SUGGEST_RARITY;

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("lootify").requires(c -> c.hasPermission(2)).then(Commands.argument("rarity", ResourceLocationArgument.id()).suggests(SUGGEST_RARITY).executes(c -> {
            Player p = c.getSource().getPlayerOrException();
            LootRarity rarity = RarityRegistry.INSTANCE.getValue(ResourceLocationArgument.getId(c, "rarity"));
            ItemStack stack = p.getMainHandItem();
            AffixHelper.setAffixes(stack, ItemAffixes.EMPTY);
            LootController.createLootItem(stack, rarity, p.getRandom(), WorldTier.getTier(p), p.getLuck());
            return 0;
        })));
    }

}
