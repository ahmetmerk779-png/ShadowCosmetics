package dev.shadowcosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Touch-friendly PNG file browser for Android and desktop launchers.
 * Folders open on tap; PNG files show their size and are imported on tap.
 */
public final class CosmeticFilePickerScreen extends ScrollableScreen {

    private static final String P = "shadowcosmetics.";
    private static final int MAX_ENTRIES = 300;
    private static final int ROW = 28;

    private final Screen parent;

    private Path currentDirectory;

    public CosmeticFilePickerScreen(Screen parent) {
        super(net.minecraft.text.Text.literal("Shadow File Picker"));

        this.parent = parent;
        this.currentDirectory = defaultDirectory();
        this.status = Lang.tr(P + "picker.choose");
    }

    private static Path defaultDirectory() {
        for (String candidate : new String[]{
                "/storage/emulated/0/Download", "/sdcard/Download"}) {
            Path path = Path.of(candidate);

            if (Files.isDirectory(path)) {
                return path;
            }
        }

        Path home = Path.of(System.getProperty("user.home", "."));
        Path downloads = home.resolve("Downloads");

        return Files.isDirectory(downloads) ? downloads : home;
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    @Override
    protected String headerTitle() {
        return Lang.tr(P + "picker.title");
    }

    @Override
    protected String headerSubtitle() {
        int characters = Math.max(15, (panelRight - panelLeft - 40) / 6);

        return CapeFormat.ellipsizeStart(currentDirectory.toString(), characters);
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        buttons.clear();
        armedKey = null;

        layoutFrame(440);

        int inner = panelRight - panelLeft - 32;
        int left = panelLeft + 16;
        int gap = 6;
        int third = (inner - 2 * gap) / 3;
        int y = contentTop + 10;

        addBtn(left, y, third, 26, "‹ " + Lang.tr(P + "picker.up"), this::goUp);
        addBtn(left + third + gap, y, third, 26, Lang.tr(P + "picker.refresh"), this::reload);
        addBtn(left + 2 * (third + gap), y, third, 26, Lang.tr(P + "picker.cancel"), this::close);
        y += 34;

        List<Path> entries = new ArrayList<>();
        boolean readable = listEntries(entries);

        for (Path entry : entries) {
            boolean directory = Files.isDirectory(entry);
            String name = entry.getFileName().toString();

            if (directory) {
                addBtn(left, y, inner, 24, "▸  " + name, () -> open(entry));
            } else {
                int[] size = CosmeticLibrary.peekSize(entry);
                boolean valid = size != null && CapeFormat.isValidSize(size[0], size[1]);
                String label = "PNG  " + name + "  ·  " + CapeFormat.sizeLabel(size);

                addBtn(left, y, inner, 24, () -> label, () -> valid, null,
                        () -> choose(entry));
            }

            y += ROW;
        }

        if (!readable) {
            setStatus(Lang.tr(P + "picker.cannot_read"), true);
        } else if (entries.isEmpty()) {
            setStatus(Lang.tr(P + "picker.empty"), false);
        } else if (entries.size() >= MAX_ENTRIES) {
            setStatus(Lang.tr(P + "picker.limit", MAX_ENTRIES), false);
        } else if (statusIsError || status.isEmpty()) {
            setStatus(Lang.tr(P + "picker.choose"), false);
        }

        finishLayout(y + 8 - contentTop);
    }

    /** Fills {@code out} with folders first, then PNG files. False when unreadable. */
    private boolean listEntries(List<Path> out) {
        if (!Files.isDirectory(currentDirectory)) {
            return false;
        }

        try (var stream = Files.list(currentDirectory)) {
            stream.filter(path -> !hidden(path))
                    .filter(path -> Files.isDirectory(path) || isPng(path))
                    .sorted(Comparator
                            .comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_ENTRIES)
                    .forEach(out::add);

            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean hidden(Path path) {
        Path name = path.getFileName();

        return name == null || name.toString().startsWith(".");
    }

    private static boolean isPng(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")
                && Files.isRegularFile(path);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void reload() {
        clearChildren();
        init();
    }

    private void open(Path directory) {
        currentDirectory = directory;
        scrollOffset = 0;
        reload();
    }

    private void goUp() {
        Path up = currentDirectory.getParent();

        if (up != null) {
            open(up);
        }
    }

    private void choose(Path file) {
        try {
            Path imported = CosmeticLibrary.importPng(file);

            CosmeticState.setCustomCape(imported);
            MinecraftClient.getInstance().setScreen(parent);
        } catch (IOException | RuntimeException exception) {
            setStatus(Lang.tr(P + "ui.import_failed", Lang.describe(exception)), true);
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    protected void onTap(double x, double y) {
        tapButtons(x, y);
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY) {
        drawButtons(context, mouseX, mouseY);
    }
}
