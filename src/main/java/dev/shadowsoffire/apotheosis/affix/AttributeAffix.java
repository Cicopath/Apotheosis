package dev.shadowsoffire.apotheosis.affix;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.AdventureModule;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.attributeslib.AttributesLib;
import dev.shadowsoffire.attributeslib.api.IFormattableAttribute;
import dev.shadowsoffire.placebo.codec.PlaceboCodecs;
import dev.shadowsoffire.placebo.util.StepFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;

/**
 * Helper class for affixes that modify attributes, as the apply method is the same for most of those.
 */
public class AttributeAffix extends Affix {

    public static final Codec<AttributeAffix> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(a -> a.attribute),
            PlaceboCodecs.enumCodec(Operation.class).fieldOf("operation").forGetter(a -> a.operation),
            LootRarity.mapCodec(StepFunction.CODEC).fieldOf("values").forGetter(a -> a.values),
            LootCategory.SET_CODEC.fieldOf("types").forGetter(a -> a.types))
        .apply(inst, AttributeAffix::new));

    protected final Holder<Attribute> attribute;
    protected final Operation operation;
    protected final Map<LootRarity, StepFunction> values;
    protected final Set<LootCategory> types;

    protected transient final Map<LootRarity, ModifierInst> modifiers;

    public AttributeAffix(Attribute attr, Operation op, Map<LootRarity, StepFunction> values, Set<LootCategory> types) {
        super(AffixType.STAT);
        this.attribute = attr;
        this.operation = op;
        this.values = values;
        this.types = types;
        this.modifiers = values.entrySet().stream().map(entry -> Pair.of(entry.getKey(), new ModifierInst(attr, op, entry.getValue()))).collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    @Override
    public MutableComponent getDescription(AffixInstance inst) {
        return Component.empty();
    }

    @Override
    public Component getAugmentingText(AffixInstance inst) {
        ModifierInst modif = this.modifiers.get(rarity);
        double value = modif.valueFactory.get(level);

        MutableComponent comp;
        MutableComponent valueComp = IFormattableAttribute.toValueComponent(this.attribute, this.operation, value < 0 ? -value : value, AttributesLib.getTooltipFlag());

        if (value > 0.0D) {
            comp = Component.translatable("attributeslib.modifier.plus", valueComp, Component.translatable(this.attribute.getDescriptionId())).withStyle(ChatFormatting.BLUE);
        }
        else {
            comp = Component.translatable("attributeslib.modifier.take", valueComp, Component.translatable(this.attribute.getDescriptionId())).withStyle(ChatFormatting.RED);
        }

        if (modif.valueFactory.get(0) != modif.valueFactory.get(1)) {
            Component minComp = IFormattableAttribute.toValueComponent(this.attribute, this.operation, modif.valueFactory.get(0), AttributesLib.getTooltipFlag());
            Component maxComp = IFormattableAttribute.toValueComponent(this.attribute, this.operation, modif.valueFactory.get(1), AttributesLib.getTooltipFlag());
            comp.append(valueBounds(minComp, maxComp));
        }

        return comp;
    }

    @Override
    public void addModifiers(AffixInstance inst, EquipmentSlot type, BiConsumer<Attribute, AttributeModifier> map) {
        LootCategory cat = LootCategory.forItem(stack);
        if (cat.isNone()) {
            AdventureModule.LOGGER.debug("Attempted to apply the attributes of affix {} on item {}, but it is not an affix-compatible item!", this.getId(), stack.getHoverName().getString());
            return;
        }
        ModifierInst modif = this.modifiers.get(rarity);
        if (modif.attr == null) {
            AdventureModule.LOGGER.debug("The affix {} has attempted to apply a null attribute modifier to {}!", this.getId(), stack.getHoverName().getString());
            return;
        }
        for (EquipmentSlot slot : cat.getSlots()) {
            if (slot == type) {
                map.accept(modif.attr, modif.build(stack, this.getId(), level));
            }
        }
    }

    @Override
    public boolean canApplyTo(ItemStack stack, LootCategory cat, LootRarity rarity) {
        if (cat.isNone()) return false;
        return (this.types.isEmpty() || this.types.contains(cat)) && this.modifiers.containsKey(rarity);
    }

    @Override
    public Codec<? extends Affix> getCodec() {
        return CODEC;
    }

    public record ModifierInst(Attribute attr, Operation op, StepFunction valueFactory) {

        private static UUID getOrCreateUUID(ItemStack stack, ResourceLocation id) {
            CompoundTag tag = stack.getTagElement(AffixHelper.AFFIX_DATA);
            return GemItem.getOrCreateUUIDs(tag, 1).get(0);
        }

        public AttributeModifier build(ItemStack stack, ResourceLocation id, float level) {
            return new AttributeModifier(getOrCreateUUID(stack, id), "affix:" + id, this.valueFactory.get(level), this.op);
        }
    }

}
