package dev.shadowcosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class CosmeticsScreen extends Screen {
    private static final int PURPLE = 0xFFB994FF;
    private static final int BG = 0xF00C0D14;
    private static final int PANEL = 0xFF171923;
    private static final int CARD = 0xFF202331;
    private static final int CARD_HOVER = 0xFF2B2E40;
    private static final int BORDER = 0xFF34384A;
    private static final int MUTED = 0xFF969BAE;
    private static final int WHITE = 0xFFF3F1FA;

    private static final class ActionButton {
        final int x, y, width, height;
        final Supplier<String> label;
        final Runnable action;
        ActionButton(int x, int y, int width, int height, Supplier<String> label, Runnable action) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.label = label; this.action = action;
        }
    }

    private String status = "READY  •  LOCAL COSMETICS";
    private TextFieldWidget searchField;
    private TextFieldWidget fileField;
    private final List<ActionButton> buttons = new ArrayList<>();
    private final List<int[]> sectionLabels = new ArrayList<>();
    private volatile List<CosmeticLibrary.Result> results = List.of();
    private volatile boolean searching = false;
    private int selectedResult = 0;
    private int scrollOffset = 0;
    private int panelLeft, panelRight, contentTop, contentBottom;
    private int contentHeight, maxScroll;
    private boolean draggingContent = false;
    private double lastDragY;
    private int searchBaseY;
    private int fileBaseY;

    public CosmeticsScreen() {
        super(Text.literal("SHADOW COSMETICS"));
    }

    @Override
    protected void init() {
        buttons.clear();
        sectionLabels.clear();
        sectionTitles.clear();

        int panelWidth = Math.min(500, Math.max(260, width - 16));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        contentTop = 61;
        contentBottom = Math.max(contentTop + 64, height - 42);

        int inner = panelWidth - 32;
        int left = panelLeft + 16;
        int gap = 8;
        int half = (inner - gap) / 2;
        int y = contentTop + 12;

        int searchButtonWidth = Math.min(82, Math.max(66, inner / 4));
        searchBaseY = y;
        searchField = new TextFieldWidget(textRenderer, left, y, inner - searchButtonWidth - 8, 24, Text.literal("Search capes"));
        searchField.setMaxLength(80);
        addDrawableChild(searchField);
        addButton("SEARCH", left + inner - searchButtonWidth, y, searchButtonWidth, 24, this::searchOnline);
        y += 36;

        addButton("‹  PREVIOUS", left, y, half, 26, () -> {
            if (!results.isEmpty()) selectedResult = Math.floorMod(selectedResult - 1, results.size());
        });
        addButton("NEXT  ›", left + half + gap, y, half, 26, () -> {
            if (!results.isEmpty()) selectedResult = (selectedResult + 1) % results.size();
        });
        y += 34;
        addButton("↓   DOWNLOAD & EQUIP SELECTED", left, y, inner, 30, this::downloadSelected);
        y += 42;

        y = section("YOUR COSMETICS", left, y, inner);
        addButton(() -> toggleLabel("CAPE", CosmeticState.capeEnabled), left, y, half, 29,
                () -> CosmeticState.capeEnabled = !CosmeticState.capeEnabled);
        addButton(() -> toggleLabel("HAT", CosmeticState.hatEnabled), left + half + gap, y, half, 29,
                () -> CosmeticState.hatEnabled = !CosmeticState.hatEnabled);
        y += 36;
        addButton(() -> toggleLabel("WINGS", CosmeticState.wingsEnabled), left, y, half, 29,
                () -> CosmeticState.wingsEnabled = !CosmeticState.wingsEnabled);
        addButton(() -> toggleLabel("HALO", CosmeticState.haloEnabled), left + half + gap, y, half, 29,
                () -> CosmeticState.haloEnabled = !CosmeticState.haloEnabled);
        y += 36;
        addButton(() -> "CAPE STYLE   ·   " + currentCape(), left, y, half, 29, () -> {
            CosmeticState.capeIndex = (CosmeticState.capeIndex + 1) % CosmeticState.CAPES.length;
        });
        addButton(() -> "HAT STYLE   ·   " + currentHat(), left + half + gap, y, half, 29, () -> {
            CosmeticState.hatIndex = (CosmeticState.hatIndex + 1) % CosmeticState.HATS.length;
        });
        y += 36;
        addButton(() -> toggleLabel("CUSTOM PNG CAPE", CosmeticState.customCapeEnabled), left, y, inner, 29, () -> {
            CosmeticState.customCapeEnabled = !CosmeticState.customCapeEnabled;
            if (CosmeticState.customCapeEnabled && !CosmeticState.customCapePath.isBlank()) {
                status = "Custom cape equipped. Press F5 to preview.";
            } else {
                CosmeticState.customCapeEnabled = false;
                status = "Choose a custom cape first.";
            }
        });
        y += 42;

        y = section("IMPORT A PNG CAPE", left, y, inner);
        int importButtonWidth = Math.min(82, Math.max(66, inner / 4));
        fileBaseY = y;
        fileField = new TextFieldWidget(textRenderer, left, y, inner - importButtonWidth - 8, 24, Text.literal("PNG file path"));
        fileField.setMaxLength(240);
        addDrawableChild(fileField);
        addButton("IMPORT", left + inner - importButtonWidth, y, importButtonWidth, 24, this::importFile);
        y += 34;
        addButton("▣   BROWSE DEVICE FOLDER", left, y, inner, 29,
                () -> MinecraftClient.getInstance().setScreen(new CosmeticFilePickerScreen(this)));
        y += 42;

        y = section("ONLINE GALLERY", left, y, inner);
        addButton("OPEN COSMETIC LIBRARY FOLDER", left, y, inner, 29,
                () -> status = "Library folder: " + CosmeticLibrary.libraryDirectory());

        contentHeight = y + 36 - contentTop;
        maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
        scrollOffset = Math.min(scrollOffset, maxScroll);
        updateFieldPositions();
    }

    private static String toggleLabel(String name, boolean enabled) {
        return (enabled ? "●  " : "○  ") + name + (enabled ? "   ON" : "   OFF");
    }

    private void addButton(String label, int x, int y, int w, int h, Runnable action) {
        addButton(() -> label, x, y, w, h, action);
    }

    private void addButton(Supplier<String> label, int x, int y, int w, int h, Runnable action) {
        buttons.add(new ActionButton(x, y, w, h, label, action));
    }

    private int section(String title, int x, int y, int w) {
        sectionLabels.add(new int[]{x, y, w, title.hashCode()});
        sectionTitles.add(title);
        return y + 22;
    }

    private final List<String> sectionTitles = new ArrayList<>();

    private void updateFieldPositions() {
        if (searchField != null) searchField.setY(searchBaseY - scrollOffset);
        if (fileField != null) fileField.setY(fileBaseY - scrollOffset);
    }

    private void changeScroll(int amount) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + amount));
        updateFieldPositions();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelLeft && mouseX <= panelRight && mouseY >= contentTop && mouseY <= contentBottom && maxScroll > 0) {
            changeScroll((int) (-verticalAmount * 28));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static boolean isInside(TextFieldWidget field, double x, double y) {
        return field != null && field.visible && x >= field.getX() && x <= field.getX() + field.getWidth()
                && y >= field.getY() && y <= field.getY() + field.getHeight();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0 && mouseX >= panelLeft && mouseX <= panelRight
                && mouseY >= contentTop && mouseY <= contentBottom) {
            if (isInside(searchField, mouseX, mouseY) || isInside(fileField, mouseX, mouseY)) {
                return super.mouseClicked(click, doubled);
            }
            for (ActionButton item : buttons) {
                int drawY = item.y - scrollOffset;
                if (mouseX >= item.x && mouseX <= item.x + item.width
                        && mouseY >= drawY && mouseY <= drawY + item.height) {
                    item.action.run();
                    return true;
                }
            }
            draggingContent = maxScroll > 0;
            return draggingContent || super.mouseClicked(click, doubled);
        }
        return super.mouseClicked(click, doubled);
    }


    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingContent && click.button() == 0) {
            changeScroll((int) -Math.round(offsetY));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        boolean wasDragging = draggingContent;
        draggingContent = false;
        return wasDragging || super.mouseReleased(click);
    }

    private void searchOnline() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            status = "Type a search term first.";
            return;
        }
        searching = true;
        status = "Searching online gallery...";
        results = List.of();
        selectedResult = 0;
        Thread thread = new Thread(() -> {
            try {
                List<CosmeticLibrary.Result> found = CosmeticLibrary.searchOnline(query);
                MinecraftClient.getInstance().execute(() -> {
                    results = found;
                    searching = false;
                    status = found.isEmpty()
                            ? "No compatible 64x32 / 64x64 PNG found. Try another term."
                            : "Found " + found.size() + " compatible PNG file(s).";
                });
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> {
                    searching = false;
                    status = "Search failed: " + safeMessage(e);
                });
            }
        }, "ShadowCosmetics-Search");
        thread.setDaemon(true);
        thread.start();
    }

    private void downloadSelected() {
        if (results.isEmpty()) {
            status = "Search and select a result first.";
            return;
        }
        CosmeticLibrary.Result result = results.get(Math.min(selectedResult, results.size() - 1));
        status = "Downloading: " + result.title();
        Thread thread = new Thread(() -> {
            try {
                Path saved = CosmeticLibrary.download(result);
                MinecraftClient.getInstance().execute(() -> {
                    CosmeticState.setCustomCape(saved);
                    status = "Downloaded and equipped. Press F5 to preview.";
                });
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> status = "Download failed: " + safeMessage(e));
            }
        }, "ShadowCosmetics-Download");
        thread.setDaemon(true);
        thread.start();
    }

    private void importFile() {
        String raw = fileField.getText().trim();
        if (raw.isEmpty()) {
            status = "Enter the full PNG file path.";
            return;
        }
        try {
            Path saved = CosmeticLibrary.importPng(Path.of(raw));
            CosmeticState.setCustomCape(saved);
            status = "Imported and equipped. Press F5 to preview.";
        } catch (Exception e) {
            status = "Import failed: " + safeMessage(e);
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) return e.getClass().getSimpleName();
        message = message.replace('\n', ' ');
        return message.substring(0, Math.min(70, message.length()));
    }

    private static String currentCape() { return CosmeticState.CAPES[CosmeticState.capeIndex]; }
    private static String currentHat() { return CosmeticState.HATS[CosmeticState.hatIndex]; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        context.fill(0, 0, width, height, 0x66070810);
        context.fill(panelLeft, 8, panelRight, height - 8, BG);
        context.fill(panelLeft, 8, panelRight, 54, PANEL);
        context.fill(panelLeft, 8, panelLeft + 4, height - 8, PURPLE);
        context.drawTextWithShadow(textRenderer, Text.literal("SHADOW"), panelLeft + 18, 17, WHITE);
        context.drawTextWithShadow(textRenderer, Text.literal("COSMETIC STUDIO"), panelLeft + 18, 32, PURPLE);
        context.drawTextWithShadow(textRenderer, Text.literal("FABRIC  /  1.21.11"), panelRight - 112, 24, MUTED);

        context.fill(panelLeft + 10, contentTop - 5, panelRight - 10, contentBottom + 2, PANEL);
        context.enableScissor(panelLeft + 5, contentTop, panelRight - 5, contentBottom);

        for (int i = 0; i < sectionLabels.size(); i++) {
            int[] section = sectionLabels.get(i);
            int y = section[1] - scrollOffset;
            if (y >= contentTop - 24 && y <= contentBottom) {
                context.fill(section[0], y + 8, section[0] + 3, y + 19, PURPLE);
                context.drawTextWithShadow(textRenderer, Text.literal(sectionTitles.get(i)), section[0] + 9, y + 7, PURPLE);
            }
        }

        for (ActionButton item : buttons) {
            int y = item.y - scrollOffset;
            if (y + item.height < contentTop || y > contentBottom) continue;
            boolean hover = mouseX >= item.x && mouseX <= item.x + item.width && mouseY >= y && mouseY <= y + item.height;
            context.fill(item.x, y, item.x + item.width, y + item.height, hover ? CARD_HOVER : CARD);
            context.fill(item.x, y, item.x + item.width, y + 1, hover ? PURPLE : BORDER);
            context.fill(item.x, y + item.height - 1, item.x + item.width, y + item.height, BORDER);
            String label = item.label.get();
            int maxChars = Math.max(8, (item.width - 16) / 6);
            if (label.length() > maxChars) label = label.substring(0, maxChars - 1) + "…";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), item.x + item.width / 2, y + (item.height - 8) / 2, hover ? WHITE : 0xFFD8D9E4);
        }

        super.render(context, mouseX, mouseY, delta);

        if (!results.isEmpty()) {
            CosmeticLibrary.Result chosen = results.get(Math.min(selectedResult, results.size() - 1));
            String label = chosen.title().length() > 36 ? chosen.title().substring(0, 36) + "…" : chosen.title();
            context.drawTextWithShadow(textRenderer, Text.literal("RESULT " + (selectedResult + 1) + "/" + results.size() + "  " + label),
                    panelLeft + 16, contentBottom - 16, 0xFFE8D7FF);
        } else if (searching) {
            context.drawTextWithShadow(textRenderer, Text.literal("SEARCHING…"), panelLeft + 16, contentBottom - 16, PURPLE);
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("SCROLL OR DRAG TO EXPLORE"), panelLeft + 16, contentBottom - 16, MUTED);
        }
        context.disableScissor();

        context.fill(panelLeft, height - 34, panelRight, height - 8, PANEL);
        String shown = status.length() > Math.max(12, (panelRight - panelLeft) / 5)
                ? status.substring(0, Math.max(12, (panelRight - panelLeft) / 5) - 1) + "…" : status;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(shown), width / 2, height - 24, PURPLE);

        if (maxScroll > 0) {
            int trackTop = contentTop + 4, trackBottom = contentBottom - 4;
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(22, trackH * trackH / Math.max(trackH, contentHeight));
            int thumbY = trackTop + (trackH - thumbH) * scrollOffset / maxScroll;
            context.fill(panelRight - 7, trackTop, panelRight - 4, trackBottom, BORDER);
            context.fill(panelRight - 7, thumbY, panelRight - 4, thumbY + thumbH, PURPLE);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
