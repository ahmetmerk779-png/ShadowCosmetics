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

/**
 * Touch-friendly PNG file browser for Android and desktop launchers.
 */
public final class CosmeticFilePickerScreen extends Screen {

    private Path currentDirectory;

    private String message =
            "Choose a PNG cape (64x32 or 64x64).";

    private final Screen parent;

    private final List<Path> entries = new ArrayList<>();

    private static final int PURPLE = 0xFFB78CFF;
    private static final int PANEL = 0xF0141420;
    private static final int CARD = 0xFF20202D;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private int panelLeft;
    private int panelRight;
    private int listTop;
    private int listBottom;

    public CosmeticFilePickerScreen(Screen parent) {
        super(Text.literal("Choose Cosmetic PNG"));

        this.parent = parent;
        this.currentDirectory = defaultDirectory();
    }

    private static Path defaultDirectory() {
        Path androidDownloads =
                Path.of("/storage/emulated/0/Download");

        if (Files.isDirectory(androidDownloads)) {
            return androidDownloads;
        }

        Path sharedDownloads =
                Path.of("/sdcard/Download");

        if (Files.isDirectory(sharedDownloads)) {
            return sharedDownloads;
        }

        return Path.of(
                System.getProperty("user.home", ".")
        );
    }

    @Override
    protected void init() {
        clearChildren();

        int panelWidth = Math.min(
                440,
                Math.max(260, width - 16)
        );

        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;

        int inner = panelWidth - 24;
        int left = panelLeft + 12;

        int buttonWidth = (inner - 8) / 2;

        addDrawableChild(
                ButtonWidget.builder(
                        Text.literal("‹ UP ONE FOLDER"),
                        button -> goUp()
                )
                .dimensions(left, 54, buttonWidth, 24)
                .build()
        );

        addDrawableChild(
                ButtonWidget.builder(
                        Text.literal("REFRESH"),
                        button -> refresh()
                )
                .dimensions(
                        left + buttonWidth + 8,
                        54,
                        buttonWidth,
                        24
                )
                .build()
        );

        listTop = 88;
        listBottom = Math.max(listTop + 40, height - 54);

        refreshEntries();

        int itemHeight = 29;
        int visibleHeight = listBottom - listTop;

        int totalHeight = entries.size() * itemHeight;

        maxScroll = Math.max(
                0,
                totalHeight - visibleHeight
        );

        scrollOffset = Math.min(scrollOffset, maxScroll);

        updateEntries();
    }

    private void refreshEntries() {
        entries.clear();

        try {
            if (!Files.isDirectory(currentDirectory)) {
                message = "Folder is not accessible.";
                return;
            }

            try (var stream = Files.list(currentDirectory)) {
                stream
                        .filter(path ->
                                Files.isDirectory(path)
                                || path.getFileName()
                                        .toString()
                                        .toLowerCase()
                                        .endsWith(".png")
                        )
                        .sorted((a, b) -> {
                            boolean aDirectory =
                                    Files.isDirectory(a);

                            boolean bDirectory =
                                    Files.isDirectory(b);

                            if (aDirectory != bDirectory) {
                                return aDirectory ? -1 : 1;
                            }

                            return a.getFileName()
                                    .toString()
                                    .compareToIgnoreCase(
                                            b.getFileName().toString()
                                    );
                        })
                        .limit(100)
                        .forEach(entries::add);
            }

            message = entries.isEmpty()
                    ? "No folders or PNG files here."
                    : "Tap a folder or PNG file.";

        } catch (IOException | SecurityException exception) {
            message =
                    "Cannot read folder. Check storage permission.";
        }
    }

    private void updateEntries() {
        clearChildren();

        int itemHeight = 29;
        int left = panelLeft + 12;
        int itemWidth = panelRight - panelLeft - 24;

        for (int i = 0; i < entries.size(); i++) {
            Path entry = entries.get(i);

            int y = listTop + i * itemHeight - scrollOffset;

            if (y + 25 < listTop || y > listBottom) {
                continue;
            }

            boolean directory = Files.isDirectory(entry);

            String name = entry.getFileName().toString();

            String label = directory
                    ? "📁  " + name
                    : "PNG  " + name;

            int maxCharacters = Math.max(
                    12,
                    (itemWidth - 12) / 6
            );

            if (label.length() > maxCharacters) {
                label = label.substring(
                        0,
                        maxCharacters - 3
                ) + "...";
            }

            Path selected = entry;

            addDrawableChild(
                    ButtonWidget.builder(
                            Text.literal(label),
                            button -> select(selected, directory)
                    )
                    .dimensions(
                            left,
                            y,
                            itemWidth,
                            24
                    )
                    .build()
            );
        }

        int cancelY = height - 38;

        addDrawableChild(
                ButtonWidget.builder(
                        Text.literal("CANCEL"),
                        button -> MinecraftClient.getInstance()
                                .setScreen(parent)
                )
                .dimensions(
                        width / 2 - 55,
                        cancelY,
                        110,
                        24
                )
                .build()
        );
    }

    private void refresh() {
        refreshEntries();
        init();
    }

    private void goUp() {
        Path parentPath = currentDirectory.getParent();

        if (parentPath != null) {
            currentDirectory = parentPath;
            scrollOffset = 0;
        }

        refresh();
    }

    private void select(Path path, boolean directory) {
        if (directory) {
            currentDirectory = path;
            scrollOffset = 0;
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
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (mouseX >= panelLeft
                && mouseX <= panelRight
                && mouseY >= listTop
                && mouseY <= listBottom
                && maxScroll > 0) {

            scrollOffset = Math.max(
                    0,
                    Math.min(
                            maxScroll,
                            scrollOffset - (int) (verticalAmount * 25)
                    )
            );

            updateEntries();

            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                horizontalAmount,
                verticalAmount
        );
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
                44,
                0xFF191925
        );

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("SHADOW FILE PICKER"),
                width / 2,
                19,
                0xFFFFFFFF
        );

        String path = currentDirectory.toString();

        int maxPathLength = Math.max(
                15,
                (panelRight - panelLeft - 24) / 6
        );

        if (path.length() > maxPathLength) {
            path = "..." + path.substring(
                    path.length() - maxPathLength + 3
            );
        }

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(path),
                width / 2,
                39,
                PURPLE
        );

        context.fill(
                panelLeft + 8,
                listTop - 4,
                panelRight - 8,
                listBottom + 2,
                CARD
        );

        context.enableScissor(
                panelLeft + 8,
                listTop,
                panelRight - 8,
                listBottom
        );

        super.render(context, mouseX, mouseY, delta);

        context.disableScissor();

        if (maxScroll > 0) {
            int trackTop = listTop + 3;
            int trackBottom = listBottom - 3;
            int trackHeight = trackBottom - trackTop;

            int contentHeight = entries.size() * 29;

            int thumbHeight = Math.max(
                    18,
                    trackHeight * trackHeight
                            / Math.max(trackHeight, contentHeight)
            );

            int thumbY = trackTop
                    + (trackHeight - thumbHeight)
                    * scrollOffset / maxScroll;

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
                    thumbY + thumbHeight,
                    PURPLE
            );
        }

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(message),
                width / 2,
                height - 19,
                0xFFE8D7FF
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
