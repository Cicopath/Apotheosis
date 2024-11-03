package dev.shadowsoffire.apotheosis.loot;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.mutable.MutableInt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.AdventureModule;
import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixType;
import dev.shadowsoffire.apotheosis.loot.LootRarity.LootRule;
import dev.shadowsoffire.apotheosis.tiers.TieredWeights;
import dev.shadowsoffire.apotheosis.tiers.TieredWeights.Weighted;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.placebo.codec.CodecProvider;
import dev.shadowsoffire.placebo.codec.PlaceboCodecs;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class LootRarity implements CodecProvider<LootRarity>, Weighted {

    public static final Codec<LootRarity> LOAD_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        TextColor.CODEC.fieldOf("color").forGetter(LootRarity::getColor),
        ItemStack.ITEM_NON_AIR_CODEC.fieldOf("material").forGetter(r -> r.material),
        TieredWeights.CODEC.fieldOf("weights").forGetter(Weighted::weights),
        LootRule.CODEC.listOf().fieldOf("rules").forGetter(LootRarity::getRules))
        .apply(inst, LootRarity::new));

    /**
     * Direct resolution codec. Only for use in other datapack objects which load after the {@link RarityRegistry}.
     */
    public static final Codec<LootRarity> CODEC = Codec.lazyInitialized(() -> RarityRegistry.INSTANCE.holderCodec().xmap(DynamicHolder::get, RarityRegistry.INSTANCE::holder));

    private final Holder<Item> material;
    private final TextColor color;
    private final TieredWeights weights;
    private final List<LootRule> rules;

    private LootRarity(TextColor color, Holder<Item> material, TieredWeights weights, List<LootRule> rules) {
        this.color = color;
        this.material = material;
        this.weights = weights;
        this.rules = rules;
    }

    public Item getMaterial() {
        return this.material.value();
    }

    public TextColor getColor() {
        return this.color;
    }

    @Override
    public TieredWeights weights() {
        return this.weights;
    }

    public List<LootRule> getRules() {
        return this.rules;
    }

    public Component toComponent() {
        return Component.translatable("rarity." + RarityRegistry.INSTANCE.getKey(this)).withStyle(Style.EMPTY.withColor(this.color));
    }

    @Override
    public String toString() {
        return "LootRarity{" + RarityRegistry.INSTANCE.getKey(this) + "}";
    }

    @Override
    public Codec<? extends LootRarity> getCodec() {
        return LOAD_CODEC;
    }

    public static LootRarity random(RandomSource rand, WorldTier tier, float luck) {
        return RarityRegistry.INSTANCE.getRandomItem(rand, tier, luck);
    }

    public static LootRarity random(RandomSource rand, WorldTier tier, float luck, Set<LootRarity> pool) {
        return RarityRegistry.INSTANCE.getRandomItem(rand, tier, luck, pool::contains);
    }

    public static <T> Codec<Map<LootRarity, T>> mapCodec(Codec<T> codec) {
        return Codec.unboundedMap(LootRarity.CODEC, codec);
    }

    // TODO: Convert this to a subtyped system so that durability and socket info can be disjoint
    // Such a system would also permit adding loot rules thta apply specific affixes, or a pool of affixes.
    @Deprecated
    public static record LootRule(AffixType type, float chance, @Nullable LootRule backup) {

        public static final Codec<LootRule> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            PlaceboCodecs.enumCodec(AffixType.class).fieldOf("type").forGetter(LootRule::type),
            Codec.FLOAT.fieldOf("chance").forGetter(LootRule::chance),
            ExtraCodecs.lazyInitializedCodec(() -> LootRule.CODEC).optionalFieldOf("backup").forGetter(rule -> Optional.ofNullable(rule.backup())))
            .apply(inst, LootRule::new));

        private static Random jRand = new Random();

        public LootRule(AffixType type, float chance) {
            this(type, chance, Optional.empty());
        }

        public LootRule(AffixType type, float chance, Optional<LootRule> backup) {
            this(type, chance, backup.orElse(null));
        }

        public void execute(ItemStack stack, LootRarity rarity, Set<DynamicHolder<? extends Affix>> currentAffixes, MutableInt sockets, RandomSource rand) {
            if (this.type == AffixType.DURABILITY) return;
            if (rand.nextFloat() <= this.chance) {
                if (this.type == AffixType.SOCKET) {
                    sockets.add(1);
                    return;
                }
                List<DynamicHolder<? extends Affix>> available = LootController.getAvailableAffixes(stack, rarity, currentAffixes, this.type);
                if (available.size() == 0) {
                    if (this.backup != null) this.backup.execute(stack, rarity, currentAffixes, sockets, rand);
                    else AdventureModule.LOGGER.error("Failed to execute LootRule {}/{}/{}/{}!", ForgeRegistries.ITEMS.getKey(stack.getItem()), RarityRegistry.INSTANCE.getKey(rarity), this.type, this.chance);
                    return;
                }
                jRand.setSeed(rand.nextLong());
                Collections.shuffle(available, jRand);
                currentAffixes.add(available.get(0));
            }
        }
    }
}
