package dev.shadowcosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** In-game touch-friendly PNG browser; useful on Android launchers without a desktop file dialog. */
public final class CosmeticFilePickerScreen extends Screen {
    private Path currentDirectory;
    private String message = "Choose a PNG cape (64x32 or 64x64).";
    private final Screen parent;
    private final List<Path> entries = new ArrayList<>();

    public CosmeticFilePickerScreen(Screen parent) {
        super(Text.literal("Choose Cosmetic PNG"));
        this.parent = parent;
        this.currentDirectory = defaultDirectory();
    }

    private static Path defaultDirectory() {
        Path androidDownloads = Path.of("/storage/emulated/0/Download");
        if (Files.isDirectory(androidDownloads)) return androidDownloads;
        Path sharedDownloads = Path.of("/sdcard/Download");
        if (Files.isDirectory(sharedDownloads)) return sharedDownloads;
        return Path.of(System.getProperty("user.home", "."));
    }

    @Override
    protected void init() {
        clearChildren();
        int cx = width / 2;
        int y = 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Up one folder"), b -> goUp())
                .dimensions(cx - 108, y, 104, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> refresh())
                .dimensions(cx + 4, y, 104, 20).build());
        y += 47;
        refreshEntries();
        int shown = Math.min(entries.size(), 8);
        for (int i = 0; i < shown; i++) {
            Path entry = entries.get(i);
            boolean directory = Files.isDirectory(entry);
            String label = (directory ? "[Folder] " : "[PNG] ") + entry.getFileName();
            if (label.length() > 34) label = label.substring(0, 31) + "...";
            final Path selected = entry;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> select(selected, directory))
                    .dimensions(cx - 108, y + i * 23, 216, 20).build());
        }
        int bottom = y + shown * 23 + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> MinecraftClient.getInstance().setScreen(parent))
                .dimensions(cx - 50, Math.min(height - 28, bottom), 100, 20).build());
    }

    private void refreshEntries() {
        entries.clear();
        try {
            if (!Files.isDirectory(currentDirectory)) {
                message = "Folder is not accessible.";
                return;
            }
            try (var stream = Files.list(currentDirectory)) {
                stream.filter(path -> Files.isDirectory(path)
                                || path.getFileName().toString().toLowerCase().endsWith(".png"))
                        .sorted((a, b) -> {
                            boolean ad = Files.isDirectory(a), bd = Files.isDirectory(b);
                            if (ad != bd) return ad ? -1 : 1;
                            return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                        })
                        .limit(40).forEach(entries::add);
            }
            message = entries.isEmpty() ? "No folders or PNG files here." : "Tap a folder or PNG file.";
        } catch (IOException | SecurityException exception) {
            message = "Cannot read this folder. Check Android storage permission.";
        }
    }

    private void refresh() {
        refreshEntries();
        init();
    }

    private void goUp() {
        Path parentPath = currentDirectory.getParent();
        if (parentPath != null) currentDirectory = parentPath;
        refresh();
    }

    private void select(Path path, boolean directory) {
        if (directory) {
            currentDirectory = path;
            refresh();
            return;
        }
        try {
            Path imported = CosmeticLibrary.importPng(path);
            CosmeticState.setCustomCape(imported);
            MinecraftClient.getInstance().setScreen(parent);
        } catch (Exception exception) {
            message = "Import failed: " + exception.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(width / 2 - 118, 8, width / 2 + 118, height - 8, 0xE8101018);
        context.drawCenteredTextWithShadow(textRenderer, "§5SHADOW §fFILE PICKER", width / 2, 13, 0xFFFFFF);
        String path = currentDirectory.toString();
        if (path.length() > 42) path = "..." + path.substring(path.length() - 39);
        context.drawCenteredTextWithShadow(textRenderer, path, width / 2, 51, 0xFFB58CFF);
        context.drawCenteredTextWithShadow(textRenderer, message, width / 2, height - 17, 0xFFE8D7FF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
