package dev.shadowcosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

public final class CosmeticsScreen extends Screen {
    private String status = "LOCAL COSMETICS • ONLINE SEARCH";
    private TextFieldWidget searchField;
    private TextFieldWidget fileField;
    private volatile List<CosmeticLibrary.Result> results = List.of();
    private volatile boolean searching = false;
    private int selectedResult = 0;

    public CosmeticsScreen() {
        super(Text.literal("SHADOW COSMETICS"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = Math.max(8, this.height / 2 - 150);

        searchField = new TextFieldWidget(textRenderer, cx - 112, top, 150, 20, Text.literal("Search cosmetics"));
        searchField.setMaxLength(80);
        addDrawableChild(searchField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Search"), b -> searchOnline())
                .dimensions(cx + 42, top, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("< Result"), b -> {
            if (!results.isEmpty()) selectedResult = Math.floorMod(selectedResult - 1, results.size());
        }).dimensions(cx - 112, top + 24, 108, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next Result >"), b -> {
            if (!results.isEmpty()) selectedResult = (selectedResult + 1) % results.size();
        }).dimensions(cx + 4, top + 24, 108, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Download selected"), b -> downloadSelected())
                .dimensions(cx - 112, top + 46, 224, 20).build());

        fileField = new TextFieldWidget(textRenderer, cx - 112, top + 90, 150, 20,
                Text.literal("PNG file path"));
        fileField.setMaxLength(240);
        addDrawableChild(fileField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Import path"), b -> importFile())
                .dimensions(cx + 42, top + 90, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Browse phone"), b ->
                MinecraftClient.getInstance().setScreen(new CosmeticFilePickerScreen(this)))
                .dimensions(cx - 112, top + 114, 224, 20).build());

        int y = top + 144;
        int left = cx - 112;
        int right = cx + 2;
        addDrawableChild(ButtonWidget.builder(capeLabel(), b -> {
            CosmeticState.capeEnabled = !CosmeticState.capeEnabled;
            b.setMessage(capeLabel());
        }).dimensions(left, y, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(hatLabel(), b -> {
            CosmeticState.hatEnabled = !CosmeticState.hatEnabled;
            b.setMessage(hatLabel());
        }).dimensions(right, y, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(wingsLabel(), b -> {
            CosmeticState.wingsEnabled = !CosmeticState.wingsEnabled;
            b.setMessage(wingsLabel());
        }).dimensions(left, y + 24, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(haloLabel(), b -> {
            CosmeticState.haloEnabled = !CosmeticState.haloEnabled;
            b.setMessage(haloLabel());
        }).dimensions(right, y + 24, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cape: " + currentCape()), b -> {
            CosmeticState.capeIndex = (CosmeticState.capeIndex + 1) % CosmeticState.CAPES.length;
            b.setMessage(Text.literal("Cape: " + currentCape()));
        }).dimensions(left, y + 48, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Hat: " + currentHat()), b -> {
            CosmeticState.hatIndex = (CosmeticState.hatIndex + 1) % CosmeticState.HATS.length;
            b.setMessage(Text.literal("Hat: " + currentHat()));
        }).dimensions(right, y + 48, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(customCapeLabel(), b -> {
            CosmeticState.customCapeEnabled = !CosmeticState.customCapeEnabled;
            if (CosmeticState.customCapeEnabled && !CosmeticState.customCapePath.isBlank()) {
                b.setMessage(customCapeLabel());
                status = "Custom cape enabled. Use F5 for the 3D in-game preview.";
            } else {
                CosmeticState.customCapeEnabled = false;
                b.setMessage(customCapeLabel());
                status = "Built-in animated cape selected.";
            }
        }).dimensions(left, y + 72, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(cx - 50, y + 98, 100, 20).build());
    }

    private void searchOnline() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            status = "Enter a search term first.";
            return;
        }
        searching = true;
        status = "Searching Minecraft cape gallery + Commons...";
        results = List.of();
        selectedResult = 0;
        Thread thread = new Thread(() -> {
            try {
                List<CosmeticLibrary.Result> found = CosmeticLibrary.searchOnline(query);
                MinecraftClient.getInstance().execute(() -> {
                    results = found;
                    searching = false;
                    status = found.isEmpty()
                            ? "No PNG results. Try another search term."
                            : "Found " + found.size() + " PNG result(s). First result can be downloaded.";
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
            status = "Search for a PNG cosmetic first.";
            return;
        }
        CosmeticLibrary.Result result = results.get(Math.min(selectedResult, results.size() - 1));
        status = "Downloading: " + result.title();
        Thread thread = new Thread(() -> {
            try {
                Path saved = CosmeticLibrary.download(result);
                MinecraftClient.getInstance().execute(() -> {
                    CosmeticState.setCustomCape(saved);
                    status = "Downloaded and equipped. Use F5 for 3D preview.";
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
            status = "Enter the full path to a PNG file.";
            return;
        }
        try {
            Path saved = CosmeticLibrary.importPng(Path.of(raw));
            CosmeticState.setCustomCape(saved);
            status = "Imported and equipped. Use F5 for 3D preview.";
        } catch (Exception e) {
            status = "Import failed: " + safeMessage(e);
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName()
                : message.replace('\n', ' ').substring(0, Math.min(70, message.length()));
    }

    private static Text capeLabel() {
        return Text.literal("Cape: " + (CosmeticState.capeEnabled ? "ON" : "OFF"));
    }
    private static Text hatLabel() {
        return Text.literal("Hat: " + (CosmeticState.hatEnabled ? "ON" : "OFF"));
    }
    private static Text wingsLabel() {
        return Text.literal("Wings: " + (CosmeticState.wingsEnabled ? "ON" : "OFF"));
    }
    private static Text haloLabel() {
        return Text.literal("Halo: " + (CosmeticState.haloEnabled ? "ON" : "OFF"));
    }
    private static Text customCapeLabel() {
        return Text.literal("Custom PNG Cape: " + (CosmeticState.customCapeEnabled ? "ON" : "OFF"));
    }
    private static String currentCape() {
        return CosmeticState.CAPES[CosmeticState.capeIndex];
    }
    private static String currentHat() {
        return CosmeticState.HATS[CosmeticState.hatIndex];
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = Math.max(8, this.height / 2 - 150);
        context.fill(cx - 124, top - 17, cx + 124, top + 266, 0xE8101018);
        context.drawCenteredTextWithShadow(textRenderer, "§5SHADOW §fCOSMETICS", cx, top - 13, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(status), cx - 112, top + 245, 0xFFB58CFF);
        if (!results.isEmpty()) {
            CosmeticLibrary.Result chosen = results.get(Math.min(selectedResult, results.size() - 1));
            String title = chosen.title().length() > 34 ? chosen.title().substring(0, 34) + "..." : chosen.title();
            context.drawTextWithShadow(textRenderer, Text.literal("Result " + (selectedResult + 1) + "/" + results.size() + ": " + title), cx - 112, top + 68, 0xFFE8D7FF);
        } else if (searching) {
            context.drawTextWithShadow(textRenderer, Text.literal("Searching..."), cx - 112, top + 68, 0xFFE8D7FF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
