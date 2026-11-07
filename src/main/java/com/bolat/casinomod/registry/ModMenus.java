package com.bolat.casinomod.registry;

import com.bolat.casinomod.CasinoMod;
import com.bolat.casinomod.menu.CasinoMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, CasinoMod.MODID);
    public static final RegistryObject<MenuType<CasinoMenu>> CASINO_MENU =
            MENUS.register("casino", () -> IForgeMenuType.create((id, inv, data) -> new CasinoMenu(id, inv)));
}
