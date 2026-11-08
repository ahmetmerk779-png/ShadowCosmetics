package dev.shadowcosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Main Shadow Cosmetics menu: gallery search, saved capes, toggles and import. */
public final class CosmeticsScreen extends ScrollableScreen {

    private static final String P = "shadowcosmetics.";
    private static final int MAX_LIBRARY_ROWS = 30;
    private static final int ROW = 26;

    private TextFieldWidget searchField;
    private TextFieldWidget importField;

    private String searchText = "";
    private String importText = "";

    private int searchFieldY;
    private int importFieldY;

    /** Section headings and plain text lines, in unscrolled content coordinates. */
    private record Label(int x, int y, String text, int color, boolean section) {
    }

    private final java.util.List<Label> labels = new java.util.ArrayList<>();

    private volatile List<CosmeticLibrary.Result> results = List.of();
    private volatile boolean busy = false;
    private volatile int requestId = 0;
    private int selectedResult = 0;

    private int resultLabelY;
    private String shownTextureError = "";

    public CosmeticsScreen() {
        super(Text.literal("Shadow Cosmetics"));
        status = Lang.tr(P + "ui.ready");
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    @Override
    protected String headerTitle() {
        return Lang.tr(P + "ui.title");
    }

    @Override
    protected String headerSubtitle() {
        return Lang.tr(P + "ui.subtitle");
    }

    @Override
    protected String headerRight() {
        return "FABRIC / 1.21.11";
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        buttons.clear();
        labels.clear();
        armedKey = null;

        layoutFrame(500);

        int inner = panelRight - panelLeft - 32;
        int left = panelLeft + 16;
        int gap = 8;
        int half = (inner - gap) / 2;
        int right = left + half + gap;
        int y = contentTop + 12;

        // ---- Online gallery -------------------------------------------
        y = section(Lang.tr(P + "section.gallery"), left, y);

        int smallWidth = Math.min(86, Math.max(66, inner / 4));

        searchFieldY = y;
        searchField = new TextFieldWidget(textRenderer, left, y,
                inner - smallWidth - gap, 24, Text.literal("search"));
        searchField.setMaxLength(80);
        searchField.setPlaceholder(Text.literal(Lang.tr(P + "ui.search_hint")));
        searchField.setText(searchText);
        addDrawableChild(searchField);

        addBtn(left + inner - smallWidth, y, smallWidth, 24,
                () -> Lang.tr(P + "ui.search"), () -> !busy, null, this::searchOnline);
        y += 32;

        addBtn(left, y, half, 26, "‹  " + Lang.tr(P + "ui.previous"), () -> moveSelection(-1));
        addBtn(right, y, half, 26, Lang.tr(P + "ui.next") + "  ›", () -> moveSelection(1));
        y += 32;

        addBtn(left, y, half, 28, () -> Lang.tr(P + "ui.popular"),
                () -> !busy, null, this::loadPopular);
        addBtn(right, y, half, 28, () -> Lang.tr(P + "ui.download"),
                () -> !results.isEmpty(), null, this::downloadSelected);
        y += 34;

        resultLabelY = y;
        y += 22;

        // ---- Saved capes ----------------------------------------------
        y = section(Lang.tr(P + "section.library"), left, y);

        List<Path> library = CosmeticLibrary.listLibrary();

        if (library.isEmpty()) {
            labels.add(new Label(left, y + 2, Lang.tr(P + "ui.library_empty"), MUTED, false));
            y += 24;
        } else {
            int shown = Math.min(library.size(), MAX_LIBRARY_ROWS);
            int deleteWidth = 34;

            for (int i = 0; i < shown; i++) {
                Path file = library.get(i);
                String name = CapeFormat.ellipsizeEnd(
                        CapeFormat.baseName(file.getFileName().toString()), 26);
                int[] size = CosmeticLibrary.peekSize(file);
                String text = name + "  ·  " + CapeFormat.sizeLabel(size);
                boolean valid = size != null && CapeFormat.isValidSize(size[0], size[1]);
                String key = "delete:" + file;

                addBtn(left, y, inner - deleteWidth - 4, 24,
                        () -> (isEquipped(file) ? "●  " : "") + text,
                        () -> valid, null, () -> equip(file));

                addBtn(left + inner - deleteWidth, y, deleteWidth, 24,
                        () -> key.equals(armedKey) ? "?" : "✕",
                        () -> true, key,
                        () -> confirmOrArm(key, () -> deleteSaved(file)));
                y += ROW;
            }

            if (library.size() > shown) {
                labels.add(new Label(left, y + 2,
                        Lang.tr(P + "ui.library_more", library.size() - shown), MUTED, false));
                y += 20;
            }

            y += 4;
        }

        addBtn(left, y, inner, 28, () -> Lang.tr(P + "ui.remove_custom"),
                CosmeticState::hasCustomCape, null, this::removeCustomCape);
        y += 40;

        // ---- Toggles ---------------------------------------------------
        y = section(Lang.tr(P + "section.cosmetics"), left, y);

        addToggle(left, y, inner, "ui.master", () -> CosmeticState.masterEnabled,
                v -> CosmeticState.masterEnabled = v);
        y += 34;

        addToggle(left, y, half, "ui.cape", () -> CosmeticState.capeEnabled,
                v -> CosmeticState.capeEnabled = v);
        addToggle(right, y, half, "ui.hat", () -> CosmeticState.hatEnabled,
                v -> CosmeticState.hatEnabled = v);
        y += 34;

        addToggle(left, y, half, "ui.wings", () -> CosmeticState.wingsEnabled,
                v -> CosmeticState.wingsEnabled = v);
        addToggle(right, y, half, "ui.halo", () -> CosmeticState.haloEnabled,
                v -> CosmeticState.haloEnabled = v);
        y += 34;

        addToggle(left, y, half, "ui.animations", () -> CosmeticState.animationsEnabled,
                v -> CosmeticState.animationsEnabled = v);
        addBtn(right, y, half, 28,
                () -> toggleLabel(Lang.tr(P + "ui.custom_cape"), CosmeticState.customCapeEnabled),
                () -> true, null, this::toggleCustomCape);
        y += 34;

        addBtn(left, y, half, 28,
                () -> Lang.tr(P + "ui.cape_style", CosmeticState.CAPES[
                        Math.floorMod(CosmeticState.capeIndex, CosmeticState.CAPES.length)]),
                () -> true, null, () -> change(CosmeticState::cycleCape));
        addBtn(right, y, half, 28,
                () -> Lang.tr(P + "ui.hat_style", CosmeticState.HATS[
                        Math.floorMod(CosmeticState.hatIndex, CosmeticState.HATS.length)]),
                () -> true, null, () -> change(CosmeticState::cycleHat));
        y += 34;

        addBtn(left, y, inner, 28,
                () -> Lang.tr(P + "ui.accent",
                        Lang.tr(P + "accent." + Math.floorMod(
                                CosmeticState.accentIndex, CosmeticState.ACCENTS.length))),
                () -> true, null, () -> change(CosmeticState::cycleAccent));
        y += 42;

        // ---- Import ----------------------------------------------------
        y = section(Lang.tr(P + "section.import"), left, y);

        importFieldY = y;
        importField = new TextFieldWidget(textRenderer, left, y,
                inner - smallWidth - gap, 24, Text.literal("path"));
        importField.setMaxLength(240);
        importField.setPlaceholder(Text.literal(Lang.tr(P + "ui.import_hint")));
        importField.setText(importText);
        addDrawableChild(importField);

        addBtn(left + inner - smallWidth, y, smallWidth, 24,
                () -> Lang.tr(P + "ui.import"), () -> true, null, this::importFile);
        y += 32;

        addBtn(left, y, inner, 28, "▣   " + Lang.tr(P + "ui.browse"), () -> {
            rememberText();
            MinecraftClient.getInstance().setScreen(new CosmeticFilePickerScreen(this));
        });
        y += 40;

        // ---- Footer ----------------------------------------------------
        addBtn(left, y, half, 28, Lang.tr(P + "ui.library_folder"),
                () -> setStatus(Lang.tr(P + "ui.library_path",
                        CosmeticLibrary.libraryDirectory().toString()), false));

        String resetKey = "reset";
        addBtn(right, y, half, 28,
                () -> resetKey.equals(armedKey)
                        ? Lang.tr(P + "ui.reset_confirm") : Lang.tr(P + "ui.reset"),
                () -> true, resetKey,
                () -> confirmOrArm(resetKey, this::resetAll));
        y += 28;

        finishLayout(y + 16 - contentTop);
    }

    private int section(String title, int x, int y) {
        labels.add(new Label(x, y, title, PURPLE, true));
        return y + 22;
    }

    private void addToggle(int x, int y, int w, String key,
                           java.util.function.BooleanSupplier getter,
                           java.util.function.Consumer<Boolean> setter) {
        addBtn(x, y, w, 28,
                () -> toggleLabel(Lang.tr(P + key), getter.getAsBoolean()),
                () -> true, null,
                () -> change(() -> setter.accept(!getter.getAsBoolean())));
    }

    private static String toggleLabel(String name, boolean enabled) {
        return (enabled ? "●  " : "○  ") + name + "   "
                + Lang.tr(P + (enabled ? "ui.on" : "ui.off"));
    }

    // ------------------------------------------------------------------
    // Scrolling text fields
    // ------------------------------------------------------------------

    @Override
    protected void onScrolled() {
        position(searchField, searchFieldY);
        position(importField, importFieldY);
    }

    private void position(TextFieldWidget field, int baseY) {
        if (field == null) {
            return;
        }

        int y = baseY - scrollOffset;

        field.setY(y);
        field.visible = y + field.getHeight() > contentTop && y < contentBottom;
    }

    @Override
    protected boolean isWidgetAt(double x, double y) {
        return hits(searchField, x, y) || hits(importField, x, y);
    }

    private static boolean hits(TextFieldWidget field, double x, double y) {
        return field != null && field.visible
                && x >= field.getX() && x <= field.getX() + field.getWidth()
                && y >= field.getY() && y <= field.getY() + field.getHeight();
    }

    private void rememberText() {
        if (searchField != null) {
            searchText = searchField.getText();
        }

        if (importField != null) {
            importText = importField.getText();
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void change(Runnable action) {
        action.run();
        CosmeticState.save();
    }

    private void toggleCustomCape() {
        if (!CosmeticState.hasCustomCape()) {
            setStatus(Lang.tr(P + "ui.choose_first"), true);
            return;
        }

        change(() -> CosmeticState.customCapeEnabled = !CosmeticState.customCapeEnabled);
        setStatus(Lang.tr(P + (CosmeticState.customCapeEnabled
                ? "ui.equipped" : "ui.custom_off")), false);
    }

    private boolean isEquipped(Path file) {
        return CosmeticState.customCapeEnabled
                && file.toAbsolutePath().normalize().toString()
                .equals(CosmeticState.customCapePath);
    }

    private void equip(Path file) {
        CosmeticState.setCustomCape(file);
        setStatus(Lang.tr(P + "ui.equipped"), false);
    }

    private void deleteSaved(Path file) {
        boolean wasEquipped = file.toAbsolutePath().normalize().toString()
                .equals(CosmeticState.customCapePath);

        try {
            CosmeticLibrary.deleteFromLibrary(file);

            if (wasEquipped) {
                CosmeticState.clearCustomCape();
            }

            setStatus(Lang.tr(P + "ui.deleted", file.getFileName().toString()), false);
        } catch (IOException | RuntimeException exception) {
            setStatus(Lang.describe(exception), true);
        }

        rebuild();
    }

    private void removeCustomCape() {
        CosmeticState.clearCustomCape();
        setStatus(Lang.tr(P + "ui.custom_removed"), false);
    }

    private void resetAll() {
        CosmeticState.resetDefaults();
        setStatus(Lang.tr(P + "ui.reset_done"), false);
    }

    private void moveSelection(int step) {
        List<CosmeticLibrary.Result> current = results;

        if (!current.isEmpty()) {
            selectedResult = Math.floorMod(selectedResult + step, current.size());
        }
    }

    private void searchOnline() {
        String query = searchField.getText().trim();

        if (query.isEmpty()) {
            setStatus(Lang.tr(P + "ui.type_search"), true);
            return;
        }

        runSearch(() -> CosmeticLibrary.searchOnline(query));
    }

    private void loadPopular() {
        runSearch(CosmeticLibrary::popular);
    }

    private interface SearchCall {
        List<CosmeticLibrary.Result> run() throws IOException;
    }

    private void runSearch(SearchCall call) {
        int id = ++requestId;

        busy = true;
        results = List.of();
        selectedResult = 0;
        setStatus(Lang.tr(P + "ui.searching"), false);

        startThread("ShadowCosmetics-Search", () -> {
            try {
                List<CosmeticLibrary.Result> found = call.run();

                MinecraftClient.getInstance().execute(() -> {
                    if (id != requestId) {
                        return;
                    }

                    results = found;
                    busy = false;

                    if (found.isEmpty()) {
                        setStatus(Lang.tr(P + "ui.no_results"), true);
                    } else {
                        setStatus(Lang.tr(P + "ui.found", found.size()), false);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                MinecraftClient.getInstance().execute(() -> {
                    if (id != requestId) {
                        return;
                    }

                    busy = false;
                    setStatus(Lang.tr(P + "ui.search_failed", Lang.describe(exception)), true);
                });
            }
        });
    }

    private void downloadSelected() {
        List<CosmeticLibrary.Result> current = results;

        if (current.isEmpty()) {
            setStatus(Lang.tr(P + "ui.pick_result"), true);
            return;
        }

        CosmeticLibrary.Result result =
                current.get(Math.min(selectedResult, current.size() - 1));

        setStatus(Lang.tr(P + "ui.downloading", result.title()), false);

        startThread("ShadowCosmetics-Download", () -> {
            try {
                Path saved = CosmeticLibrary.download(result);

                MinecraftClient.getInstance().execute(() -> {
                    CosmeticState.setCustomCape(saved);
                    setStatus(Lang.tr(P + "ui.equipped"), false);

                    if (MinecraftClient.getInstance().currentScreen == this) {
                        rebuild();
                    }
                });
            } catch (IOException | RuntimeException exception) {
                MinecraftClient.getInstance().execute(() ->
                        setStatus(Lang.tr(P + "ui.download_failed",
                                Lang.describe(exception)), true));
            }
        });
    }

    private void importFile() {
        String raw = importField.getText().trim();

        if (raw.isEmpty()) {
            setStatus(Lang.tr(P + "ui.import_empty"), true);
            return;
        }

        try {
            Path saved = CosmeticLibrary.importPng(Path.of(raw));
            CosmeticState.setCustomCape(saved);
            setStatus(Lang.tr(P + "ui.equipped"), false);
            rebuild();
        } catch (IOException | RuntimeException exception) {
            setStatus(Lang.tr(P + "ui.import_failed", Lang.describe(exception)), true);
        }
    }

    private static void startThread(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Re-creates the layout (for example after the saved capes changed). */
    private void rebuild() {
        rememberText();
        clearChildren();
        init();
    }

    // ------------------------------------------------------------------
    // Frame hooks
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        // Surface cape load problems (wrong size, missing file) found by the renderer.
        String error = CosmeticTextureManager.lastErrorKey();

        if (!error.equals(shownTextureError)) {
            shownTextureError = error;

            if (!error.isEmpty()) {
                int[] size = CosmeticLibrary.peekSize(Path.of(CosmeticState.customCapePath));
                setStatus(Lang.tr(error, CapeFormat.sizeLabel(size)), true);
            }
        }
    }

    @Override
    protected void onTap(double x, double y) {
        tapButtons(x, y);
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY) {
        for (Label label : labels) {
            int y = label.y() - scrollOffset;

            if (y < contentTop - 24 || y > contentBottom) {
                continue;
            }

            if (label.section()) {
                drawSectionTitle(context, label.x(), y, label.text());
            } else {
                context.drawTextWithShadow(textRenderer,
                        Text.literal(fit(label.text(), panelRight - panelLeft - 40)),
                        label.x(), y, label.color());
            }
        }

        drawButtons(context, mouseX, mouseY);
        drawResultLine(context);
    }

    private void drawResultLine(DrawContext context) {
        int y = resultLabelY - scrollOffset;

        if (y < contentTop - 12 || y > contentBottom) {
            return;
        }

        int x = panelLeft + 16;
        int width = panelRight - panelLeft - 40;
        List<CosmeticLibrary.Result> current = results;
        String text;
        int color;

        if (!current.isEmpty()) {
            CosmeticLibrary.Result chosen =
                    current.get(Math.min(selectedResult, current.size() - 1));
            text = Lang.tr(P + "ui.result", selectedResult + 1, current.size(), chosen.title());
            color = 0xFFE8D7FF;
        } else if (busy) {
            text = Lang.tr(P + "ui.searching");
            color = PURPLE;
        } else {
            text = Lang.tr(P + "ui.scroll_hint");
            color = MUTED;
        }

        context.drawTextWithShadow(textRenderer, Text.literal(fit(text, width)), x, y + 4, color);
    }

    @Override
    public void removed() {
        rememberText();
        CosmeticState.save();
        super.removed();
    }
}
