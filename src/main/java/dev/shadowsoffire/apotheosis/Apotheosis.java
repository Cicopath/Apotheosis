package dev.shadowsoffire.apotheosis;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.shadowsoffire.apotheosis.Apoth.Items;
import dev.shadowsoffire.apotheosis.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.boss.BossEvents;
import dev.shadowsoffire.apotheosis.boss.BossRegistry;
import dev.shadowsoffire.apotheosis.boss.Exclusion;
import dev.shadowsoffire.apotheosis.boss.MinibossRegistry;
import dev.shadowsoffire.apotheosis.compat.AdventureTwilightCompat;
import dev.shadowsoffire.apotheosis.compat.GatewaysCompat;
import dev.shadowsoffire.apotheosis.data.ApothLootProvider;
import dev.shadowsoffire.apotheosis.data.ApothRecipeProvider;
import dev.shadowsoffire.apotheosis.data.ApothTagsProvider;
import dev.shadowsoffire.apotheosis.loot.AffixLootRegistry;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRule;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.net.BossSpawnPayload;
import dev.shadowsoffire.apotheosis.net.RadialStateChangePayload;
import dev.shadowsoffire.apotheosis.net.RerollResultPayload;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.GemBonus;
import dev.shadowsoffire.apotheosis.spawner.RogueSpawnerRegistry;
import dev.shadowsoffire.apothic_enchanting.util.DataGenBuilder;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.tabs.TabFillingRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@Mod(Apotheosis.MODID)
public class Apotheosis {

    public static final String MODID = "apotheosis";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final boolean STAGES_LOADED = ModList.get().isLoaded("gamestages");
    static final Map<ResourceLocation, LootCategory> IMC_TYPE_OVERRIDES = new HashMap<>();

    public Apotheosis(IEventBus bus) {
        Apoth.bootstrap();
        bus.register(this);
        ObfuscationReflectionHelper.setPrivateValue(RangedAttribute.class, (RangedAttribute) Attributes.ARMOR, 200D, "minValue");
        ObfuscationReflectionHelper.setPrivateValue(RangedAttribute.class, (RangedAttribute) Attributes.ARMOR_TOUGHNESS, 100D, "maxValue");
    }

    @SubscribeEvent
    public void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            LootRule.initCodecs();
            Exclusion.initCodecs();
            GemBonus.initCodecs();

            TabFillingRegistry.register(Apoth.Tabs.ADVENTURE.getKey(), Items.COMMON_MATERIAL, Items.UNCOMMON_MATERIAL, Items.RARE_MATERIAL, Items.EPIC_MATERIAL, Items.MYTHIC_MATERIAL, Items.GEM_DUST,
                Items.GEM_FUSED_SLATE, Items.SIGIL_OF_SOCKETING, Items.SIGIL_OF_WITHDRAWAL, Items.SIGIL_OF_REBIRTH, Items.SIGIL_OF_ENHANCEMENT, Items.SIGIL_OF_UNNAMING, Items.BOSS_SUMMONER,
                Items.SALVAGING_TABLE, Items.GEM_CUTTING_TABLE, Items.SIMPLE_REFORGING_TABLE, Items.REFORGING_TABLE, Items.AUGMENTING_TABLE, Items.GEM);

            if (ModList.get().isLoaded("gateways")) GatewaysCompat.register();
            if (ModList.get().isLoaded("twilightforest")) AdventureTwilightCompat.register();
        });
        PayloadHelper.registerPayload(new BossSpawnPayload.Provider());
        PayloadHelper.registerPayload(new RerollResultPayload.Provider());
        PayloadHelper.registerPayload(new RadialStateChangePayload.Provider());
        NeoForge.EVENT_BUS.register(new AdventureEvents());
        NeoForge.EVENT_BUS.register(new BossEvents());
        RarityRegistry.INSTANCE.registerToBus();
        AffixRegistry.INSTANCE.registerToBus();
        GemRegistry.INSTANCE.registerToBus();
        AffixLootRegistry.INSTANCE.registerToBus();
        BossRegistry.INSTANCE.registerToBus();
        RogueSpawnerRegistry.INSTANCE.registerToBus();
        MinibossRegistry.INSTANCE.registerToBus();
    }

    @SubscribeEvent
    public void data(GatherDataEvent e) {
        DataGenBuilder.create(Apotheosis.MODID)
            .provider(ApothLootProvider::create)
            .provider(ApothRecipeProvider::new)
            .provider(ApothTagsProvider::new)
            .build(e);
    }

    /**
     * Constructs a resource location using the {@link Apotheosis#MODID} as the namespace.
     */
    public static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /**
     * Constructs a mutable component with a lang key of the form "type.modid.path", using {@link Apotheosis#MODID}.
     * 
     * @param type The type of language key, "misc", "info", "title", etc...
     * @param path The path of the language key.
     * @param args Translation arguments passed to the created translatable component.
     */
    public static MutableComponent lang(String type, String path, Object... args) {
        return Component.translatable(type + "." + MODID + "." + path, args);
    }

    public static MutableComponent sysMessageHeader() {
        return Component.translatable("[%s] ", Component.literal("Apoth").withStyle(ChatFormatting.GOLD));
    }

}
