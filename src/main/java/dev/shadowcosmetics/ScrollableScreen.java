package dev.shadowcosmetics;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Shared frame for the Shadow Cosmetics screens: dark panel, header, scrolling
 * content area and status bar.
 *
 * <p>Touch friendly: the content scrolls with the mouse wheel and by dragging.
 * A press that does not move turns into a tap when the finger is released, so
 * starting a drag on top of a button scrolls instead of pressing it.
 */
abstract class ScrollableScreen extends Screen {

    protected static final int PURPLE = 0xFFB994FF;
    protected static final int BG = 0xF00C0D14;
    protected static final int PANEL = 0xFF171923;
    protected static final int CARD = 0xFF202331;
    protected static final int CARD_HOVER = 0xFF2B2E40;
    protected static final int BORDER = 0xFF34384A;
    protected static final int MUTED = 0xFF969BAE;
    protected static final int WHITE = 0xFFF3F1FA;
    protected static final int DISABLED = 0xFF5F6375;
    protected static final int DANGER = 0xFFF87171;

    /** Movement (in GUI pixels) before a press counts as a drag. */
    private static final double DRAG_THRESHOLD = 5.0;

    protected int panelLeft;
    protected int panelRight;
    protected int contentTop;
    protected int contentBottom;
    protected int contentHeight;
    protected int maxScroll;
    protected int scrollOffset;

    protected String status = "";
    protected boolean statusIsError = false;

    private boolean pressed;
    private boolean dragged;
    private double pressX;
    private double pressY;
    private double dragRemainder;

    protected ScrollableScreen(Text title) {
        super(title);
    }

    // ------------------------------------------------------------------
    // Hooks for subclasses
    // ------------------------------------------------------------------

    /** Large title in the header (left). */
    protected abstract String headerTitle();

    /** Small coloured line under the title. */
    protected abstract String headerSubtitle();

    /** Text on the right side of the header. May be empty. */
    protected String headerRight() {
        return "";
    }

    /** Draws everything that scrolls. Called with the scissor already set. */
    protected abstract void renderContent(DrawContext context, int mouseX, int mouseY);

    /** A tap inside the content area (screen coordinates). */
    protected abstract void onTap(double x, double y);

    /** True when a real widget (e.g. a text field) sits at this position. */
    protected boolean isWidgetAt(double x, double y) {
        return false;
    }

    /** Called whenever the scroll position changes. */
    protected void onScrolled() {
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /** Computes the panel and content rectangle. Call at the start of init(). */
    protected final void layoutFrame(int maxPanelWidth) {
        int panelWidth = Math.min(maxPanelWidth, Math.max(260, width - 16));

        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;

        contentTop = 61;
        contentBottom = Math.max(contentTop + 64, height - 42);
    }

    /** Sets the total content height and keeps the scroll position valid. */
    protected final void finishLayout(int totalContentHeight) {
        contentHeight = totalContentHeight;
        maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        onScrolled();
    }

    protected final boolean insideContent(double x, double y) {
        return x >= panelLeft && x <= panelRight
                && y >= contentTop && y <= contentBottom;
    }

    protected final void scrollBy(int amount) {
        int next = Math.max(0, Math.min(maxScroll, scrollOffset + amount));

        if (next != scrollOffset) {
            scrollOffset = next;
            onScrolled();
        }
    }

    protected final void setStatus(String message, boolean error) {
        status = message;
        statusIsError = error;
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (insideContent(mouseX, mouseY) && maxScroll > 0) {
            scrollBy((int) (-verticalAmount * 28));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double x = click.x();
        double y = click.y();

        if (click.button() == 0
                && insideContent(x, y)
                && !isWidgetAt(x, y)) {

            pressed = true;
            dragged = false;
            pressX = x;
            pressY = y;
            dragRemainder = 0.0;

            return true;
        }

        pressed = false;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (pressed && click.button() == 0) {
            if (!dragged
                    && (Math.abs(click.x() - pressX) > DRAG_THRESHOLD
                    || Math.abs(click.y() - pressY) > DRAG_THRESHOLD)) {
                dragged = true;
            }

            if (dragged && maxScroll > 0) {
                // Keep fractional movement so slow drags still scroll smoothly.
                dragRemainder += -offsetY;

                int whole = (int) dragRemainder;
                dragRemainder -= whole;

                if (whole != 0) {
                    scrollBy(whole);
                }
            }

            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (pressed && click.button() == 0) {
            boolean tap = !dragged;
            double x = pressX;
            double y = pressY;

            pressed = false;
            dragged = false;

            if (tap) {
                onTap(x, y);
            }

            return true;
        }

        return super.mouseReleased(click);
    }

    // ------------------------------------------------------------------
    // Simple scrolling buttons
    // ------------------------------------------------------------------

    /** A button laid out in unscrolled content coordinates. */
    protected static final class Btn {
        final int x;
        final int y;
        final int w;
        final int h;
        final Supplier<String> label;
        final BooleanSupplier enabled;
        final String armKey;
        final Runnable action;

        Btn(int x, int y, int w, int h, Supplier<String> label,
            BooleanSupplier enabled, String armKey, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.enabled = enabled;
            this.armKey = armKey;
            this.action = action;
        }

        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    protected final List<Btn> buttons = new ArrayList<>();

    /** Key of the destructive button waiting for a second tap, or null. */
    protected String armedKey;

    protected final void addBtn(int x, int y, int w, int h, String label, Runnable action) {
        addBtn(x, y, w, h, () -> label, () -> true, null, action);
    }

    protected final void addBtn(int x, int y, int w, int h, Supplier<String> label,
                                BooleanSupplier enabled, String armKey, Runnable action) {
        buttons.add(new Btn(x, y, w, h, label, enabled, armKey, action));
    }

    protected final void drawButtons(DrawContext context, int mouseX, int mouseY) {
        for (Btn button : buttons) {
            int y = button.y - scrollOffset;

            if (y + button.h < contentTop || y > contentBottom) {
                continue;
            }

            boolean hover = insideContent(mouseX, mouseY)
                    && button.contains(mouseX, mouseY + scrollOffset);

            boolean armed = button.armKey != null && button.armKey.equals(armedKey);

            drawButton(context, button.x, y, button.w, button.h,
                    button.label.get(), hover, button.enabled.getAsBoolean(), armed);
        }
    }

    /** Runs the button under the tap. Returns true when one was hit. */
    protected final boolean tapButtons(double x, double y) {
        double contentY = y + scrollOffset;

        for (Btn button : buttons) {
            if (button.contains(x, contentY)) {
                if (button.armKey == null || !button.armKey.equals(armedKey)) {
                    armedKey = null;
                }

                button.action.run();
                return true;
            }
        }

        armedKey = null;
        return false;
    }

    /** First tap arms a destructive button, the second tap runs {@code action}. */
    protected final void confirmOrArm(String key, Runnable action) {
        if (key.equals(armedKey)) {
            armedKey = null;
            action.run();
        } else {
            armedKey = key;
        }
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    /** Shortens a string with an ellipsis so it fits the pixel width. */
    protected final String fit(String text, int maxWidth) {
        if (text == null || text.isEmpty() || textRenderer.getWidth(text) <= maxWidth) {
            return text == null ? "" : text;
        }

        String ellipsis = "…";
        int available = Math.max(0, maxWidth - textRenderer.getWidth(ellipsis));

        return textRenderer.trimToWidth(text, available) + ellipsis;
    }

    protected final void drawButton(
            DrawContext context,
            int x,
            int y,
            int w,
            int h,
            String label,
            boolean hover,
            boolean enabled,
            boolean armed
    ) {
        boolean lit = hover && enabled;

        context.fill(x, y, x + w, y + h, lit ? CARD_HOVER : CARD);

        int topEdge = armed ? DANGER : lit ? PURPLE : BORDER;

        context.fill(x, y, x + w, y + 1, topEdge);
        context.fill(x, y + h - 1, x + w, y + h, armed ? DANGER : BORDER);

        int textColor = !enabled
                ? DISABLED
                : armed ? DANGER : lit ? WHITE : 0xFFD8D9E4;

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(fit(label, w - 12)),
                x + w / 2,
                y + (h - 8) / 2,
                textColor
        );
    }

    protected final void drawSectionTitle(DrawContext context, int x, int y, String title) {
        context.fill(x, y + 8, x + 3, y + 19, PURPLE);

        context.drawTextWithShadow(
                textRenderer,
                Text.literal(title),
                x + 9,
                y + 7,
                PURPLE
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        context.fill(0, 0, width, height, 0x66070810);
        context.fill(panelLeft, 8, panelRight, height - 8, BG);
        context.fill(panelLeft, 8, panelRight, 54, PANEL);
        context.fill(panelLeft, 8, panelLeft + 4, height - 8, PURPLE);

        context.drawTextWithShadow(
                textRenderer, Text.literal(headerTitle()),
                panelLeft + 18, 17, WHITE
        );

        context.drawTextWithShadow(
                textRenderer, Text.literal(fit(headerSubtitle(), panelRight - panelLeft - 40)),
                panelLeft + 18, 32, PURPLE
        );

        String right = headerRight();

        if (!right.isEmpty()) {
            context.drawTextWithShadow(
                    textRenderer, Text.literal(right),
                    panelRight - textRenderer.getWidth(right) - 14, 24, MUTED
            );
        }

        context.fill(panelLeft + 10, contentTop - 5, panelRight - 10, contentBottom + 2, PANEL);

        context.enableScissor(panelLeft + 5, contentTop, panelRight - 5, contentBottom);

        renderContent(context, mouseX, mouseY);

        // Real widgets (text fields) are drawn inside the clip as well.
        super.render(context, mouseX, mouseY, delta);

        context.disableScissor();

        context.fill(panelLeft, height - 34, panelRight, height - 8, PANEL);

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(fit(status, panelRight - panelLeft - 24)),
                width / 2,
                height - 24,
                statusIsError ? DANGER : PURPLE
        );

        if (maxScroll > 0) {
            int trackTop = contentTop + 4;
            int trackBottom = contentBottom - 4;
            int trackHeight = trackBottom - trackTop;

            int thumbHeight = Math.max(
                    22,
                    trackHeight * trackHeight / Math.max(trackHeight, contentHeight)
            );

            int thumbY = trackTop
                    + (trackHeight - thumbHeight) * scrollOffset / maxScroll;

            context.fill(panelRight - 7, trackTop, panelRight - 4, trackBottom, BORDER);
            context.fill(panelRight - 7, thumbY, panelRight - 4, thumbY + thumbHeight, PURPLE);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
