package dev.shadowsoffire.apotheosis.compat;

import dev.shadowsoffire.apothic_attributes.ApothicAttributes;
import dev.shadowsoffire.apothic_enchanting.ApothicEnchanting;
import dev.shadowsoffire.apothic_spawners.ApothicSpawners;
import net.neoforged.fml.ModList;
import vazkii.patchouli.api.PatchouliAPI;
import vazkii.patchouli.api.PatchouliAPI.IPatchouliAPI;

public class PatchouliCompat {

    public static void register() {
        IPatchouliAPI api = PatchouliAPI.get();
        if (!api.isStub()) {
            api.setConfigFlag("apotheosis:enchanting", ModList.get().isLoaded(ApothicEnchanting.MODID));
            api.setConfigFlag("apotheosis:adventure", true);
            api.setConfigFlag("apotheosis:spawner", ModList.get().isLoaded(ApothicSpawners.MODID));
            api.setConfigFlag("apotheosis:garden", true);
            api.setConfigFlag("apotheosis:potion", ModList.get().isLoaded(ApothicAttributes.MODID));
            api.setConfigFlag("apotheosis:village", false);
            api.setConfigFlag("apotheosis:wstloaded", ModList.get().isLoaded("wstweaks"));
            api.setConfigFlag("apotheosis:curiosloaded", ModList.get().isLoaded("curios"));
        }
    }

}
