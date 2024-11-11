package dev.shadowsoffire.apotheosis.attachments;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Bonus loot tables can be attached to a {@link Mob} and will be rolled when the mob is killed.
 */
public record BonusLootTables(List<ResourceKey<LootTable>> tables) {

    public static final BonusLootTables EMPTY = new BonusLootTables(List.of());

    public static final Codec<BonusLootTables> CODEC = ResourceKey.codec(Registries.LOOT_TABLE).listOf().xmap(BonusLootTables::new, BonusLootTables::tables);

    /**
     * Rolls all additional loot tables contained in this attachment and spawns the items via {@link Mob#spawnatLocation}.
     */
    public void drop(Mob owner, DamageSource source, boolean hitByPlayer) {
        for (ResourceKey<LootTable> key : this.tables) {
            // This is a copy of LivingEntity#dropLootFromLootTable - we can't invoke it directly since Mob overrides it
            LootTable table = owner.level().getServer().reloadableRegistries().getLootTable(key);
            if (table == LootTable.EMPTY) {
                continue;
            }

            LootParams.Builder lootparams$builder = new LootParams.Builder((ServerLevel) owner.level())
                .withParameter(LootContextParams.THIS_ENTITY, owner)
                .withParameter(LootContextParams.ORIGIN, owner.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
            if (hitByPlayer && owner.lastHurtByPlayer != null) {
                lootparams$builder = lootparams$builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, owner.lastHurtByPlayer)
                    .withLuck(owner.lastHurtByPlayer.getLuck());
            }

            LootParams lootparams = lootparams$builder.create(LootContextParamSets.ENTITY);
            table.getRandomItems(lootparams, owner.getLootTableSeed(), owner::spawnAtLocation);
        }
    }

}
