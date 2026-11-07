package com.bolat.casinomod;

import com.bolat.casinomod.network.CasinoNetwork;
import com.bolat.casinomod.registry.ModItems;
import com.bolat.casinomod.registry.ModMenus;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CasinoMod.MODID)
public class CasinoMod {
    public static final String MODID = "casinomod";

    public CasinoMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModMenus.MENUS.register(bus);
        CasinoNetwork.register();
        bus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.CASINO_KEY);
        }
    }
}
