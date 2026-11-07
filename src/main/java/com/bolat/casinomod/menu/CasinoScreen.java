package com.bolat.casinomod.menu;

import com.bolat.casinomod.network.CasinoNetwork;
import com.bolat.casinomod.network.PickPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class CasinoScreen extends AbstractContainerScreen<CasinoMenu> {
    // ---- palette ----
    private static final int GOLD = 0xFFFFC83D, GOLD_DARK = 0xFF8A6414;
    private static final int GREEN = 0xFF2ECC71, RED = 0xFFB03040, RED_BRIGHT = 0xFFFF5566;
    private static final int TEXT_DIM = 0xFFB8A9D9, INK = 0xFF2A1A05;

    // ---- layout ----
    private static final int WHEEL_X = 89, WHEEL_Y = 54;
    private static final int COLS = 5, ROWS = 7, PER_PAGE = COLS * ROWS, CELL = 20;
    private static final int PICK_X = 188, PICK_Y = 28;
    private static final int SPIN_TICKS = 70;

    private EditBox search;
    private String lastSearch = "";
    private final List<Integer> filtered = new ArrayList<>();
    private ItemStack[] cache;
    private int page = 0;

    // ---- wheel animation state ----
    private int seenRound = 0, pendingStart = 0, spinTicks = -1, shownChance = 0;
    private boolean frozen = false, revealed = false, lastWon = false;
    private double finalAngle = 0;
    private float partial = 0;

    public CasinoScreen(CasinoMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 300;
        this.imageHeight = 218;
        this.inventoryLabelY = 126;
    }

    /** gold casino-style button */
    private static class StyledButton extends Button {
        StyledButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hot = isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, 0xFF5A3E08);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, hot ? 0xFFFFE27A : GOLD, hot ? 0xFFE0A21A : 0xFFB8860B);
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, INK);
        }
    }

    @Override
    protected void init() {
        super.init();
        cache = new ItemStack[CasinoMenu.pool().size()];

        addRenderableWidget(new StyledButton(leftPos + 59, topPos + 90, 60, 18, Component.literal("PLAY"),
                b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, CasinoMenu.BTN_PLAY)));

        search = new EditBox(this.font, leftPos + 186, topPos + 6, 106, 14, Component.literal("Search"));
        search.setMaxLength(32);
        search.setValue(lastSearch);
        search.setResponder(s -> { lastSearch = s; refilter(); });
        addRenderableWidget(search);

        addRenderableWidget(new StyledButton(leftPos + 186, topPos + 176, 24, 16, Component.literal("<"),
                b -> { if (page > 0) page--; }));
        addRenderableWidget(new StyledButton(leftPos + 268, topPos + 176, 24, 16, Component.literal(">"),
                b -> { if (page < pages() - 1) page++; }));
        refilter();
    }

    private void refilter() {
        filtered.clear();
        String q = lastSearch.toLowerCase(Locale.ROOT).trim();
        List<Item> pool = CasinoMenu.pool();
        for (int i = 0; i < pool.size(); i++) {
            Item it = pool.get(i);
            if (q.isEmpty()
                    || ForgeRegistries.ITEMS.getKey(it).getPath().contains(q.replace(' ', '_'))
                    || it.getDescription().getString().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(i);
            }
        }
        page = 0;
    }

    private int pages() {
        return Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private ItemStack stackFor(int poolIndex) {
        if (cache[poolIndex] == null) cache[poolIndex] = new ItemStack(CasinoMenu.pool().get(poolIndex));
        return cache[poolIndex];
    }

    private int cellAt(double mx, double my) {
        double x = mx - leftPos - PICK_X, y = my - topPos - PICK_Y;
        if (x < 0 || y < 0 || x >= COLS * CELL || y >= ROWS * CELL) return -1;
        int n = page * PER_PAGE + (int) (y / CELL) * COLS + (int) (x / CELL);
        return n < filtered.size() ? filtered.get(n) : -1;
    }

    // ---------------- animation logic ----------------
    @Override
    public void containerTick() {
        super.containerTick();
        int round = menu.getRound();
        if (round != seenRound) {
            seenRound = round;
            pendingStart = 2; // wait 2 ticks so every synced value has arrived
        }
        if (pendingStart > 0 && --pendingStart == 0) startSpin();

        if (spinTicks >= 0) {
            spinTicks++;
            if (spinTicks >= SPIN_TICKS) {
                spinTicks = -1;
                revealed = true;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                        lastWon ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, 1.0F));
            }
        } else if (frozen && menu.getSlot(CasinoMenu.STAKE).hasItem()) {
            frozen = false;
            revealed = false;
        }
        if (!frozen && spinTicks < 0) shownChance = menu.getChance();
    }

    private void startSpin() {
        frozen = true;
        revealed = false;
        shownChance = menu.getLastChance();
        lastWon = menu.getStatus() == CasinoMenu.ST_WON;
        double greenDeg = shownChance * 3.6;
        double r = 0.12 + 0.76 * new Random(menu.getRound() * 7919L).nextDouble();
        finalAngle = lastWon ? greenDeg * r : greenDeg + (360 - greenDeg) * r;
        spinTicks = 0;
    }

    // ---------------- drawing ----------------
    private void drawWheel(GuiGraphics g) {
        int cx = leftPos + WHEEL_X, cy = topPos + WHEEL_Y;

        // dark disc
        for (int dy = -19; dy <= 19; dy++) {
            int dx = (int) Math.sqrt(19 * 19 - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xFF0B0814);
        }
        // outer rim (gold, or green/red glow after a result)
        int rim = !revealed ? GOLD : (lastWon ? GREEN : RED_BRIGHT);
        for (int i = 0; i < 300; i++) {
            double a = Math.toRadians(i * 1.2);
            for (int r = 30; r <= 31; r++) {
                int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(x, y, x + 1, y + 1, rim);
            }
        }
        // zones
        double greenDeg = shownChance * 3.6;
        for (int i = 0; i < 240; i++) {
            double deg = i * 1.5;
            double a = Math.toRadians(deg - 90);
            int col = deg < greenDeg ? GREEN : RED;
            for (int r = 21; r <= 27; r += 2) {
                int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(x - 1, y - 1, x + 1, y + 1, col);
            }
        }
        // needle
        double ang;
        if (spinTicks >= 0) {
            double t = Math.min(1.0, (spinTicks + partial) / SPIN_TICKS);
            double ease = 1 - Math.pow(1 - t, 3);
            ang = (3 * 360 + finalAngle) * ease;
        } else if (frozen) {
            ang = finalAngle;
        } else {
            ang = (System.currentTimeMillis() / 30) % 360;
        }
        double a = Math.toRadians(ang - 90);
        for (int r = 11; r <= 24; r++) {
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 2, y + 2, 0xFFFFFFFF);
        }
        int tx = cx + (int) Math.round(Math.cos(a) * 26), ty = cy + (int) Math.round(Math.sin(a) * 26);
        g.fill(tx - 2, ty - 2, tx + 3, ty + 3, GOLD);
        // fixed marker at the top
        g.fill(cx - 1, cy - 36, cx + 2, cy - 33, GOLD);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        this.partial = partialTick;
        int L = leftPos, T = topPos, W = imageWidth, H = imageHeight;

        // frame + background
        g.fill(L, T, L + W, T + H, GOLD_DARK);
        g.fill(L + 1, T + 1, L + W - 1, T + H - 1, GOLD);
        g.fillGradient(L + 2, T + 2, L + W - 2, T + H - 2, 0xFF2C1B4D, 0xFF0D0916);
        g.fill(L + 179, T + 6, L + 181, T + H - 6, GOLD_DARK);
        // soft box behind the player inventory
        g.fill(L + 6, T + 134, L + 172, T + 214, 0x55000000);

        // slot frames
        ItemStack chosen = this.menu.getSlot(CasinoMenu.TARGET).getItem();
        for (Slot s : this.menu.slots) {
            boolean special = s.index < 2;
            int outer = special ? (s.index == CasinoMenu.TARGET && !chosen.isEmpty() ? GOLD : GOLD_DARK) : 0xFF3A2D5C;
            int inner = special ? 0xFF0F0B1A : 0xFF171126;
            g.fill(L + s.x - 1, T + s.y - 1, L + s.x + 17, T + s.y + 17, outer);
            g.fill(L + s.x, T + s.y, L + s.x + 16, T + s.y + 16, inner);
        }
        // arrows between the slots and the wheel
        g.drawCenteredString(this.font, ">", L + 44, T + 50, GOLD);
        g.drawCenteredString(this.font, ">", L + 134, T + 50, GOLD);

        drawWheel(g);

        // item picker
        int hover = cellAt(mouseX, mouseY);
        int start = page * PER_PAGE;
        for (int n = start; n < Math.min(filtered.size(), start + PER_PAGE); n++) {
            int k = n - start;
            int cx = L + PICK_X + (k % COLS) * CELL;
            int cy = T + PICK_Y + (k / COLS) * CELL;
            int idx = filtered.get(n);
            ItemStack st = stackFor(idx);
            boolean sel = !chosen.isEmpty() && chosen.getItem() == st.getItem();
            int border = sel ? GOLD : (idx == hover ? 0xFFB59AF0 : 0xFF3A2D5C);
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, border);
            g.fill(cx + 1, cy + 1, cx + CELL - 2, cy + CELL - 2, sel ? 0xFF3A2B08 : 0xFF140F22);
            g.renderItem(st, cx + 1, cy + 1);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(this.font, Component.literal("UPGRADER").withStyle(ChatFormatting.BOLD), 89, 8, GOLD);
        g.drawCenteredString(this.font, "STAKE", 26, 35, TEXT_DIM);
        g.drawCenteredString(this.font, "TARGET", 150, 35, TEXT_DIM);
        g.drawCenteredString(this.font, shownChance + "%", WHEEL_X, WHEEL_Y - 4, 0xFFFFFFFF);

        String text;
        int color = TEXT_DIM;
        if (spinTicks >= 0) {
            text = "Spinning...";
            color = GOLD;
        } else if (revealed) {
            text = lastWon ? "YOU WON!" : "You lost. Try again";
            color = lastWon ? GREEN : RED_BRIGHT;
        } else {
            switch (this.menu.getStatus()) {
                case CasinoMenu.ST_NEED_STAKE -> { text = "Put a stake first"; color = RED_BRIGHT; }
                case CasinoMenu.ST_NEED_TARGET -> { text = "Pick a target item ->"; color = RED_BRIGHT; }
                default -> text = "Stake + target, then PLAY";
            }
        }
        g.drawCenteredString(this.font, text, 89, 113, color);
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, TEXT_DIM, false);
        g.drawCenteredString(this.font, (page + 1) + " / " + pages(), 240, 180, TEXT_DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        int idx = cellAt(mouseX, mouseY);
        if (idx >= 0) {
            g.renderTooltip(this.font, stackFor(idx), mouseX, mouseY);
        } else {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // check the picker FIRST: AbstractContainerScreen.mouseClicked always returns true,
        // so code placed after super.mouseClicked would never run
        int idx = cellAt(mx, my);
        if (idx >= 0 && button == 0) {
            CasinoNetwork.CHANNEL.sendToServer(new PickPacket(idx));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx > leftPos + 182) {
            if (delta < 0 && page < pages() - 1) page++;
            else if (delta > 0 && page > 0) page--;
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (search != null && search.isFocused() && key != 256) {
            search.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
