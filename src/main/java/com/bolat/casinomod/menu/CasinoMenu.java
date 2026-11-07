package com.bolat.casinomod.menu;

import com.bolat.casinomod.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UPGRADER.
 * Slot 0 = STAKE  : player puts items here (the whole stack is bet).
 * Slot 1 = TARGET : the item the player WANTS. It is a display-only slot; the player chooses it
 *                   in the item picker on the right side of the screen (menu button id 100 + poolIndex).
 * Chance = value(stake) / value(target) * 95%, limited to 1..90%.
 * Win  -> the target item is given to the player. Lose -> the stake is gone.
 */
public class CasinoMenu extends AbstractContainerMenu {
    public static final int STAKE = 0;
    public static final int TARGET = 1;
    public static final int BTN_PLAY = 0;

    public static final int ST_IDLE = 0, ST_WON = 1, ST_LOST = 2, ST_NEED_STAKE = 3, ST_NEED_TARGET = 4;

    private final Player owner;
    private final DataSlot chanceData = DataSlot.standalone();
    private final DataSlot statusData = DataSlot.standalone();
    private final DataSlot roundData = DataSlot.standalone();
    private final DataSlot lastChanceData = DataSlot.standalone();

    private final SimpleContainer casino = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            CasinoMenu.this.slotsChanged(this);
        }
    };

    public CasinoMenu(int id, Inventory inv) {
        super(ModMenus.CASINO_MENU.get(), id);
        this.owner = inv.player;

        addSlot(new Slot(casino, STAKE, 18, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty();
            }
        });
        // display-only slot: nothing can be put in or taken out by hand (no duplication possible)
        addSlot(new Slot(casino, TARGET, 142, 46) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 136 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, 8 + c * 18, 194));
        }
        addDataSlot(chanceData);
        addDataSlot(statusData);
        addDataSlot(roundData);
        addDataSlot(lastChanceData);
    }

    public int getChance() { return chanceData.get(); }
    public int getStatus() { return statusData.get(); }
    public int getRound() { return roundData.get(); }
    public int getLastChance() { return lastChanceData.get(); }

    // ---------------- item values ----------------
    private static final Map<Item, Integer> VALUE = Map.ofEntries(
            Map.entry(Items.COAL, 2), Map.entry(Items.COPPER_INGOT, 2), Map.entry(Items.IRON_INGOT, 5),
            Map.entry(Items.GOLD_INGOT, 10), Map.entry(Items.LAPIS_LAZULI, 4), Map.entry(Items.REDSTONE, 2),
            Map.entry(Items.QUARTZ, 3), Map.entry(Items.AMETHYST_SHARD, 3), Map.entry(Items.EMERALD, 20),
            Map.entry(Items.DIAMOND, 50), Map.entry(Items.NETHERITE_SCRAP, 80), Map.entry(Items.NETHERITE_INGOT, 400),
            Map.entry(Items.IRON_BLOCK, 45), Map.entry(Items.GOLD_BLOCK, 90), Map.entry(Items.EMERALD_BLOCK, 180),
            Map.entry(Items.DIAMOND_BLOCK, 450), Map.entry(Items.NETHERITE_BLOCK, 3600),
            Map.entry(Items.GOLDEN_APPLE, 40), Map.entry(Items.ENCHANTED_GOLDEN_APPLE, 500),
            Map.entry(Items.NETHER_STAR, 600), Map.entry(Items.BEACON, 900), Map.entry(Items.ELYTRA, 700),
            Map.entry(Items.TOTEM_OF_UNDYING, 300), Map.entry(Items.DRAGON_EGG, 2000));
    private static final List<String> GEAR = List.of("_sword", "_pickaxe", "_axe", "_shovel", "_hoe",
            "_helmet", "_chestplate", "_leggings", "_boots");
    private static final Map<String, Integer> GEAR_VALUE = Map.of("netherite_", 500, "diamond_", 130,
            "iron_", 30, "golden_", 22, "chainmail_", 20, "stone_", 4, "wooden_", 2, "leather_", 3);

    public static int valueOf(Item item) {
        Integer v = VALUE.get(item);
        if (v != null) return v;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (key != null) {
            String p = key.getPath();
            for (String g : GEAR) {
                if (p.endsWith(g)) {
                    for (Map.Entry<String, Integer> e : GEAR_VALUE.entrySet()) {
                        if (p.startsWith(e.getKey())) return e.getValue();
                    }
                }
            }
        }
        return switch (new ItemStack(item).getRarity()) {
            case UNCOMMON -> 6;
            case RARE -> 15;
            case EPIC -> 40;
            default -> 1;
        };
    }

    public static int chanceFor(ItemStack stake, ItemStack target) {
        if (stake.isEmpty() || target.isEmpty()) return 0;
        double sv = (double) valueOf(stake.getItem()) * stake.getCount();
        double tv = valueOf(target.getItem());
        int c = (int) Math.floor(sv / tv * 95.0);
        return Math.max(1, Math.min(90, c));
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (!owner.level().isClientSide()) {
            chanceData.set(chanceFor(casino.getItem(STAKE), casino.getItem(TARGET)));
        }
    }

    // ---------------- item picker pool (same order on client and server) ----------------
    private static final Set<String> BANNED = Set.of(
            "air", "barrier", "bedrock", "light", "debug_stick", "knowledge_book", "spawner",
            "reinforced_deepslate", "end_portal_frame", "budding_amethyst", "written_book", "filled_map",
            "farmland", "frogspawn", "chorus_plant", "petrified_oak_slab");
    private static final List<String> BANNED_PARTS = List.of(
            "command_block", "structure_", "jigsaw", "spawn_egg", "infested", "suspicious");

    private static List<Item> pool;

    public static List<Item> pool() {
        if (pool == null) {
            List<Item> list = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                if (key == null || !key.getNamespace().equals("minecraft")) continue;
                String path = key.getPath();
                if (BANNED.contains(path)) continue;
                boolean bad = false;
                for (String part : BANNED_PARTS) {
                    if (path.contains(part)) { bad = true; break; }
                }
                if (!bad) list.add(item);
            }
            list.sort(Comparator.comparing(i -> ForgeRegistries.ITEMS.getKey(i).getPath()));
            pool = list;
        }
        return pool;
    }

    // ---------------- buttons ----------------
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player != owner) return false;
        if (id == BTN_PLAY) {
            if (player instanceof ServerPlayer sp) play(sp);
            return true;
        }
        return false;
    }

    /** Called on the server by PickPacket. */
    public void pick(int idx) {
        List<Item> p = pool();
        if (owner.level().isClientSide() || idx < 0 || idx >= p.size()) return;
        casino.setItem(TARGET, new ItemStack(p.get(idx)));
        statusData.set(ST_IDLE);
        broadcastChanges();
    }

    private void play(ServerPlayer player) {
        ItemStack stake = casino.getItem(STAKE);
        ItemStack target = casino.getItem(TARGET);
        if (stake.isEmpty()) {
            statusData.set(ST_NEED_STAKE);
            broadcastChanges();
            return;
        }
        if (target.isEmpty()) {
            statusData.set(ST_NEED_TARGET);
            broadcastChanges();
            return;
        }
        int chance = chanceFor(stake, target);
        ItemStack prize = new ItemStack(target.getItem());
        casino.setItem(STAKE, ItemStack.EMPTY); // the whole stake is spent
        lastChanceData.set(chance);
        roundData.set(roundData.get() + 1);

        if (player.getRandom().nextInt(100) < chance) {
            player.getInventory().placeItemBackInInventory(prize);
            statusData.set(ST_WON);
        } else {
            statusData.set(ST_LOST);
        }
        broadcastChanges();
    }

    // ---------------- shift-click ----------------
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == TARGET) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == STAKE) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, STAKE, STAKE + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            // only the real stake is returned; the target slot is just a picture
            ItemStack st = casino.removeItemNoUpdate(STAKE);
            if (!st.isEmpty()) player.getInventory().placeItemBackInInventory(st);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
