package com.morkeriget.progress;

import java.util.List;

import com.morkeriget.Text;
import com.morkeriget.glyph.ClientGlyphs;
import com.morkeriget.glyph.Glyph;
import com.morkeriget.glyph.GlyphActionPayload;
import com.morkeriget.glyph.Glyphs;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// Spending paragon points, opened from the skill screen once you are level 100. The stats are in two columns, each
// with a + button. Shift-click spends 10 points at once. Below them are the glyph slots, see Glyphs. Client only
public class ParagonScreen extends Screen {
    private static final int COLUMN_WIDTH = 190;
    private static final int COLUMN_GAP = 20;
    private static final int TOTAL_WIDTH = 2 * COLUMN_WIDTH + COLUMN_GAP;
    private static final int ROW_HEIGHT = 30;
    private static final int COMPACT_ROW_HEIGHT = 25;
    private static final int ROWS = (Paragon.values().length + 1) / 2;
    // The glyph section: a heading and a line for each slot
    private static final int GLYPH_ROW = 14;
    private static final int GLYPHS_HEIGHT = 18 + Glyph.MAX_SLOTS * GLYPH_ROW;
    // The paragon level each slot opens at, see Glyph.slots
    private static final int[] SLOT_LEVELS = {1, 10, 25, 50};

    public ParagonScreen() {
        super(Text.tr("paragon.title"));
    }

    private int left() {
        return (width - TOTAL_WIDTH) / 2;
    }

    private int columnLeft(Paragon stat) {
        return left() + (stat.ordinal() / ROWS) * (COLUMN_WIDTH + COLUMN_GAP);
    }

    // In the middle of the screen. The panel starts 8 above this
    private int top() {
        return Math.max(10, (height - panelHeight(rowHeight())) / 2 + 8);
    }

    // On a low screen the rows are closer together, so every stat still fits
    private int rowHeight() {
        return height < panelHeight(ROW_HEIGHT) ? COMPACT_ROW_HEIGHT : ROW_HEIGHT;
    }

    // The title and points, the rows, the glyphs and "Tilbage"
    private static int panelHeight(int rowHeight) {
        return 8 + 36 + ROWS * rowHeight + GLYPHS_HEIGHT + 32;
    }

    private int rowTop(int row) {
        return top() + 36 + row * rowHeight();
    }

    private int rowTop(Paragon stat) {
        return rowTop(stat.ordinal() % ROWS);
    }

    private int glyphsTop() {
        return rowTop(ROWS) + 4;
    }

    private int glyphRowTop(int slot) {
        return glyphsTop() + 16 + slot * GLYPH_ROW;
    }

    private int bottom() {
        return glyphsTop() + GLYPHS_HEIGHT;
    }

    @Override
    protected void init() {
        ProgressPayload progress = ClientProgress.get();
        for (Paragon stat : Paragon.values()) {
            Button button = Button.builder(Component.literal("+"),
                            pressed -> PacketDistributor.sendToServer(new ParagonPayload(stat, hasShiftDown() ? 10 : 1)))
                    .bounds(columnLeft(stat) + COLUMN_WIDTH - 20, rowTop(stat) + 4, 20, 20)
                    .build(ThemedButton::new);
            button.active = progress.paragonPoints() > 0 && progress.rank(stat) < stat.maxRank;
            addRenderableWidget(button);
        }
        List<Glyphs.Socketed> glyphs = ClientGlyphs.get(Glyph.slots(progress.paragonLevel()));
        for (int slot = 0; slot < glyphs.size(); slot++) {
            if (glyphs.get(slot) != null) {
                int index = slot;
                addRenderableWidget(Button.builder(Text.tr("paragon.take_out"), pressed -> PacketDistributor.sendToServer(new GlyphActionPayload(index)))
                        .bounds(left() + TOTAL_WIDTH - 50, glyphRowTop(slot) - 2, 50, 12)
                        .build(ThemedButton::new));
            }
        }
        addRenderableWidget(Button.builder(Text.tr("button.back"), pressed -> minecraft.setScreen(new SkillScreen()))
                .bounds(width / 2 - 40, bottom() + 6, 80, 18)
                .build(ThemedButton::new));
    }

    // Called when the server sends new values, e.g. right after clicking + or taking a glyph out
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.panel(graphics, left() - 8, top() - 8, TOTAL_WIDTH + 16, bottom() + 32 - (top() - 8));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ProgressPayload progress = ClientProgress.get();
        int center = width / 2;
        UiTheme.title(graphics, font, Text.tr("paragon.level", progress.paragonLevel()), center, top(), 0x55FFFF);
        int pointsColor = progress.paragonPoints() > 0 ? UiTheme.GOOD : UiTheme.MUTED;
        graphics.drawCenteredString(font, Text.tr("paragon.points", progress.paragonPoints()), center, top() + 14, pointsColor);
        UiTheme.divider(graphics, left(), rowTop(0) - 6, TOTAL_WIDTH);
        for (Paragon stat : Paragon.values()) {
            int x = columnLeft(stat);
            int y = rowTop(stat);
            int rank = progress.rank(stat);
            graphics.drawString(font, stat.displayName().append("  " + rank + "/" + stat.maxRank), x, y + 4,
                    rank >= stat.maxRank ? 0xFFD700 : UiTheme.TEXT);
            graphics.drawString(font, stat.description(), x, y + 16, UiTheme.MUTED);
        }
        renderGlyphs(graphics, progress.paragonLevel());
    }

    // A line for each of the four slots: the glyph and what it gives, a free slot, or the paragon level that opens it
    private void renderGlyphs(GuiGraphics graphics, int paragonLevel) {
        UiTheme.divider(graphics, left(), glyphsTop() - 2, TOTAL_WIDTH);
        int slots = Glyph.slots(paragonLevel);
        graphics.drawString(font, Text.tr("paragon.glyphs", slots, Glyph.MAX_SLOTS),
                left(), glyphsTop() + 2, UiTheme.TEXT);
        List<Glyphs.Socketed> glyphs = ClientGlyphs.get(slots);
        for (int slot = 0; slot < Glyph.MAX_SLOTS; slot++) {
            int y = glyphRowTop(slot);
            if (slot >= slots) {
                graphics.drawString(font, Text.tr("paragon.locked", SLOT_LEVELS[slot]), left(), y, UiTheme.MUTED);
                continue;
            }
            Glyphs.Socketed socketed = glyphs.get(slot);
            if (socketed == null) {
                graphics.drawString(font, Text.tr("paragon.free_slot"), left(), y, UiTheme.MUTED);
                continue;
            }
            Glyph glyph = socketed.glyph();
            graphics.drawString(font, glyph.displayName().append(" " + socketed.level() + "/" + Glyph.MAX_LEVEL), left(), y, glyph.color.getColor());
            graphics.drawString(font, glyph.describe(socketed.level()), left() + 110, y, UiTheme.TEXT);
        }
    }

    // K closes the screen, just like the skill screen
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SkillKeys.SKILLS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
