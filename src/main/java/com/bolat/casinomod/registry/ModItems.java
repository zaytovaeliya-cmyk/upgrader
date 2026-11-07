package com.bolat.casinomod.registry;

import com.bolat.casinomod.CasinoMod;
import com.bolat.casinomod.item.CasinoKeyItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CasinoMod.MODID);
    public static final RegistryObject<Item> CASINO_KEY =
            ITEMS.register("casino_key", () -> new CasinoKeyItem(new Item.Properties().stacksTo(1)));
}
