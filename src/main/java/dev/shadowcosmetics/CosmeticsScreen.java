
package dev.shadowcosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CosmeticsScreen extends Screen {
    private static final int PURPLE = 0xFFB78CFF;
    private static final int PANEL = 0xF0141420;
    private static final int CARD = 0xFF20202D;

    private String status = "COSMETICS • LOCAL LIBRARY • ONLINE SEARCH";
    private TextFieldWidget searchField;
    private TextFieldWidget fileField;

    private volatile List<CosmeticLibrary.Result> results = List.of();
    private volatile boolean searching = false;

    private int selectedResult = 0;
    private int scrollOffset = 0;

    private int panelLeft, panelRight;
    private int contentTop, contentBottom;
    private int contentHeight, maxScroll;

    private final Map<ClickableWidget, Integer> widgetBaseY =
            new LinkedHashMap<>();

    private final Map<String, int[]> sectionLabels =
            new LinkedHashMap<>();

    public CosmeticsScreen() {
        super(Text.literal("SHADOW COSMETICS"));
    }

    @Override
    protected void init() {
        widgetBaseY.clear();
        sectionLabels.clear();

        int panelWidth = Math.min(440, Math.max(260, width - 16));

        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;

        contentTop = 52;
        contentBottom = Math.max(contentTop + 70, height - 34);

        int inner = panelWidth - 28;
        int left = panelLeft + 14;
        int gap = 8;
        int half = (inner - gap) / 2;

        int y = contentTop + 12;

        searchField = add(
                new TextFieldWidget(
                        textRenderer,
                        left,
                        y,
                        inner - 78,
                        22,
                        Text.literal("Search capes")
                ),
                y
        );

        searchField.setMaxLength(80);

        addButton(
                "SEARCH",
                left + inner - 72,
                y,
                72,
                22,
                b -> searchOnline()
        );

        y += 34;

        addButton("‹ PREVIOUS", left, y, half, 22, b -> {
            if (!results.isEmpty()) {
                selectedResult = Math.floorMod(
                        selectedResult - 1,
                        results.size()
                );
            }
        });

        addButton("NEXT ›", left + half + gap, y, half, 22, b -> {
            if (!results.isEmpty()) {
                selectedResult = (selectedResult + 1) % results.size();
            }
        });

        y += 28;

        addButton(
                "DOWNLOAD & EQUIP SELECTED",
                left,
                y,
                inner,
                24,
                b -> downloadSelected()
        );

        y += 38;

        y = section("YOUR COSMETICS", left, y, inner);

        addButton(capeLabel(), left, y, half, 24, b -> {
            CosmeticState.capeEnabled = !CosmeticState.capeEnabled;
            b.setMessage(capeLabel());
        });

        addButton(hatLabel(), left + half + gap, y, half, 24, b -> {
            CosmeticState.hatEnabled = !CosmeticState.hatEnabled;
            b.setMessage(hatLabel());
        });

        y += 30;

        addButton(wingsLabel(), left, y, half, 24, b -> {
            CosmeticState.wingsEnabled = !CosmeticState.wingsEnabled;
            b.setMessage(wingsLabel());
        });

        addButton(haloLabel(), left + half + gap, y, half, 24, b -> {
            CosmeticState.haloEnabled = !CosmeticState.haloEnabled;
            b.setMessage(haloLabel());
        });

        y += 30;

        addButton(
                "CAPE STYLE: " + currentCape(),
                left,
                y,
                half,
                24,
                b -> {
                    CosmeticState.capeIndex =
                            (CosmeticState.capeIndex + 1)
                                    % CosmeticState.CAPES.length;

                    b.setMessage(Text.literal(
                            "CAPE STYLE: " + currentCape()
                    ));
                }
        );

        addButton(
                "HAT STYLE: " + currentHat(),
                left + half + gap,
                y,
                half,
                24,
                b -> {
                    CosmeticState.hatIndex =
                            (CosmeticState.hatIndex + 1)
                                    % CosmeticState.HATS.length;

                    b.setMessage(Text.literal(
                            "HAT STYLE: " + currentHat()
                    ));
                }
        );

        y += 30;

        addButton(customCapeLabel(), left, y, inner, 24, b -> {
            CosmeticState.customCapeEnabled =
                    !CosmeticState.customCapeEnabled;

            if (CosmeticState.customCapeEnabled
                    && !CosmeticState.customCapePath.isBlank()) {

                status = "Custom cape equipped. Press F5 to preview.";

            } else {
                CosmeticState.customCapeEnabled = false;
                status = "Built-in cape selected.";
            }

            b.setMessage(customCapeLabel());
        });

        y += 38;

        y = section("IMPORT A PNG CAPE", left, y, inner);

        fileField = add(
                new TextFieldWidget(
                        textRenderer,
                        left,
                        y,
                        inner - 82,
                        22,
                        Text.literal("PNG file path")
                ),
                y
        );

        fileField.setMaxLength(240);

        addButton(
                "IMPORT",
                left + inner - 76,
                y,
                76,
                22,
                b -> importFile()
        );

        y += 30;

        addButton(
                "BROWSE DEVICE FOLDER",
                left,
                y,
                inner,
                24,
                b -> MinecraftClient.getInstance().setScreen(
                        new CosmeticFilePickerScreen(this)
                )
        );

        y += 38;

        y = section("ONLINE GALLERY", left, y, inner);

        addButton(
                "OPEN COSMETIC LIBRARY FOLDER",
                left,
                y,
                inner,
                24,
                b -> status = "Library folder: "
                        + CosmeticLibrary.libraryDirectory()
        );

        contentHeight = y + 36 - contentTop;

        maxScroll = Math.max(
                0,
                contentHeight - (contentBottom - contentTop)
        );

        scrollOffset = Math.min(scrollOffset, maxScroll);

        updateWidgetPositions();
    }

    private int section(String title, int x, int y, int w) {
        sectionLabels.put(
                title + "@" + y,
                new int[]{x, y, w}
        );

        return y + 20;
    }

    private <T extends ClickableWidget> T add(
            T widget,
            int baseY
    ) {
        addDrawableChild(widget);
        widgetBaseY.put(widget, baseY);
        return widget;
    }

    // String alan butonlar
    private ButtonWidget addButton(
            String label,
            int x,
            int y,
            int w,
            int h,
            java.util.function.Consumer<ButtonWidget> action
    ) {
        ButtonWidget button = ButtonWidget.builder(
                Text.literal(label),
                action::accept
        ).dimensions(x, y, w, h).build();

        return add(button, y);
    }

    // Text alan butonlar - derleme hatasının düzeltmesi
    private ButtonWidget addButton(
            Text label,
            int x,
            int y,
            int w,
            int h,
            java.util.function.Consumer<ButtonWidget> action
    ) {
        ButtonWidget button = ButtonWidget.builder(
                label,
                action::accept
        ).dimensions(x, y, w, h).build();

        return add(button, y);
    }

    private void updateWidgetPositions() {
        for (var entry : widgetBaseY.entrySet()) {
            ClickableWidget widget = entry.getKey();

            int y = entry.getValue() - scrollOffset;

            widget.setY(y);

            boolean visible =
                    y + widget.getHeight() > contentTop
                            && y < contentBottom;

            widget.visible = visible;
            widget.active = visible;
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (mouseX >= panelLeft
                && mouseX <= panelRight
                && mouseY >= contentTop
                && mouseY <= contentBottom
                && maxScroll > 0) {

            scrollOffset = Math.max(
                    0,
                    Math.min(
                            maxScroll,
                            scrollOffset - (int) (verticalAmount * 22)
                    )
            );

            updateWidgetPositions();
            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                horizontalAmount,
                verticalAmount
        );
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
                List<CosmeticLibrary.Result> found =
                        CosmeticLibrary.searchOnline(query);

                MinecraftClient.getInstance().execute(() -> {
                    results = found;
                    searching = false;

                    status = found.isEmpty()
                            ? "No compatible 64x32 / 64x64 PNG found. Try another term."
                            : "Found " + found.size()
                            + " compatible PNG file(s).";
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

        CosmeticLibrary.Result result =
                results.get(Math.min(
                        selectedResult,
                        results.size() - 1
                ));

        status = "Downloading: " + result.title();

        Thread thread = new Thread(() -> {
            try {
                Path saved = CosmeticLibrary.download(result);

                MinecraftClient.getInstance().execute(() -> {
                    CosmeticState.setCustomCape(saved);
                    status = "Downloaded and equipped. Press F5 to preview.";
                });

            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() ->
                        status = "Download failed: " + safeMessage(e)
                );
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

        if (message == null) {
            return e.getClass().getSimpleName();
        }

        message = message.replace('\n', ' ');

        return message.substring(
                0,
                Math.min(70, message.length())
        );
    }

    private static Text capeLabel() {
        return Text.literal(
                "CAPE: " + (CosmeticState.capeEnabled ? "ON" : "OFF")
        );
    }

    private static Text hatLabel() {
        return Text.literal(
                "HAT: " + (CosmeticState.hatEnabled ? "ON" : "OFF")
        );
    }

    private static Text wingsLabel() {
        return Text.literal(
                "WINGS: " + (CosmeticState.wingsEnabled ? "ON" : "OFF")
        );
    }

    private static Text haloLabel() {
        return Text.literal(
                "HALO: " + (CosmeticState.haloEnabled ? "ON" : "OFF")
        );
    }

    private static Text customCapeLabel() {
        return Text.literal(
                "CUSTOM PNG CAPE: "
                        + (CosmeticState.customCapeEnabled ? "ON" : "OFF")
        );
    }

    private static String currentCape() {
        return CosmeticState.CAPES[CosmeticState.capeIndex];
    }

    private static String currentHat() {
        return CosmeticState.HATS[CosmeticState.hatIndex];
    }

    @Override
    public void render(
            DrawContext context,
            int mouseX,
            int mouseY,
            float delta
    ) {
        renderBackground(context, mouseX, mouseY, delta);

        context.fill(
                panelLeft,
                8,
                panelRight,
                height - 8,
                PANEL
        );

        context.fill(
                panelLeft,
                8,
                panelRight,
                46,
                0xFF191925
        );

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("SHADOW COSMETICS"),
                width / 2,
                17,
                0xFFFFFFFF
        );

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("MODERN COSMETIC STUDIO"),
                width / 2,
                32,
                PURPLE
        );

        context.fill(
                panelLeft + 10,
                contentTop - 4,
                panelRight - 10,
                contentBottom + 1,
                CARD
        );

        context.enableScissor(
                panelLeft + 6,
                contentTop,
                panelRight - 6,
                contentBottom
        );

        for (var entry : sectionLabels.entrySet()) {
            int[] p = entry.getValue();

            int y = p[1] - scrollOffset;

            if (y >= contentTop - 14 && y <= contentBottom) {
                String title = entry.getKey().substring(
                        0,
                        entry.getKey().lastIndexOf('@')
                );

                context.drawTextWithShadow(
                        textRenderer,
                        Text.literal(title),
                        p[0],
                        y + 3,
                        PURPLE
                );
            }
        }

        super.render(context, mouseX, mouseY, delta);

        if (!results.isEmpty()) {
            CosmeticLibrary.Result chosen =
                    results.get(Math.min(
                            selectedResult,
                            results.size() - 1
                    ));

            String label = chosen.title().length() > 44
                    ? chosen.title().substring(0, 44) + "…"
                    : chosen.title();

            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(
                            "RESULT " + (selectedResult + 1)
                                    + "/" + results.size()
                                    + "  " + label
                    ),
                    panelLeft + 14,
                    contentBottom - 16,
                    0xFFE8D7FF
            );

        } else if (searching) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("SEARCHING…"),
                    panelLeft + 14,
                    contentBottom - 16,
                    PURPLE
            );
        }

        context.disableScissor();

        context.fill(
                panelLeft,
                height - 29,
                panelRight,
                height - 8,
                0xFF191925
        );

        String shown = status.length()
                > Math.max(12, (panelRight - panelLeft) / 5)
                ? status.substring(
                        0,
                        Math.max(12, (panelRight - panelLeft) / 5) - 1
                ) + "…"
                : status;

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(shown),
                width / 2,
                height - 22,
                PURPLE
        );

        if (maxScroll > 0) {
            int trackTop = contentTop + 4;
            int trackBottom = contentBottom - 4;
            int trackH = trackBottom - trackTop;

            int thumbH = Math.max(
                    18,
                    trackH * trackH / Math.max(trackH, contentHeight)
            );

            int thumbY = trackTop
                    + (trackH - thumbH) * scrollOffset / maxScroll;

            context.fill(
                    panelRight - 7,
                    trackTop,
                    panelRight - 4,
                    trackBottom,
                    0xFF373744
            );

            context.fill(
                    panelRight - 7,
                    thumbY,
                    panelRight - 4,
                    thumbY + thumbH,
                    PURPLE
            );
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
