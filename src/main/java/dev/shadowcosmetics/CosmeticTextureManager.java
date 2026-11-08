package dev.shadowcosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads custom cape textures from local PNG files.
 * Texture operations are performed on Minecraft's render thread.
 */
public final class CosmeticTextureManager {

    private static final Identifier TEXTURE_ID =
            Identifier.of("shadowcosmetics", "dynamic/custom_cape");

    private static String loadedPath = "";
    private static Identifier textureId;
    private static Model<Unit> capeModel;

    /** A path that failed to load. Prevents re-reading a broken file every frame. */
    private static String failedPath = "";
    private static String lastError = "";

    private CosmeticTextureManager() {
    }

    public static Model<Unit> getCustomCapeModel() {
        if (!CosmeticState.customCapeEnabled
                || !CosmeticState.hasCustomCape()) {
            return null;
        }

        String wanted = CosmeticState.customCapePath;

        if (wanted.equals(loadedPath) && capeModel != null) {
            return capeModel;
        }

        if (wanted.equals(failedPath)) {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        if (!client.isOnThread()) {
            return null;
        }

        NativeImage image = null;

        try {
            Path path = Path.of(wanted);

            if (!Files.isRegularFile(path)) {
                return fail(wanted, "shadowcosmetics.error.file_missing");
            }

            try (var input = Files.newInputStream(path)) {
                image = NativeImage.read(input);
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (!CapeFormat.isValidSize(width, height)) {
                image.close();
                image = null;
                return fail(wanted, "shadowcosmetics.error.bad_size");
            }

            releaseTexture();

            NativeImageBackedTexture texture =
                    new NativeImageBackedTexture(
                            () -> "shadowcosmetics-custom-cape",
                            image
                    );

            // The texture now owns the image.
            image = null;

            client.getTextureManager().registerTexture(TEXTURE_ID, texture);
            textureId = TEXTURE_ID;

            capeModel = new Model.SinglePartModel(
                    createCapeGeometry(CapeFormat.logicalHeight(width, height)),
                    RenderLayers::entityCutoutNoCull
            );

            loadedPath = wanted;
            failedPath = "";
            lastError = "";

            return capeModel;

        } catch (IOException | RuntimeException exception) {
            if (image != null) {
                image.close();
            }

            return fail(wanted, "shadowcosmetics.error.bad_image");
        }
    }

    public static Identifier getTextureId() {
        return textureId;
    }

    /** Translation key of the last load error, or empty when the last load worked. */
    public static String lastErrorKey() {
        return lastError;
    }

    /** Unloads the texture and forgets any load error so the file is tried again. */
    public static void invalidate() {
        releaseTexture();

        failedPath = "";
        lastError = "";
    }

    private static Model<Unit> fail(String path, String errorKey) {
        releaseTexture();

        failedPath = path;
        lastError = errorKey;

        return null;
    }

    private static void releaseTexture() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (textureId != null && client.isOnThread()) {
            client.getTextureManager().destroyTexture(textureId);
        }

        loadedPath = "";
        textureId = null;
        capeModel = null;
    }

    /**
     * Builds a vanilla-shaped cape (10 x 16 x 1 pixels) that reads the standard
     * cape atlas: the outer face comes from the top-left panel of the PNG.
     *
     * <p>The model texture grid is always 64 pixels wide, so HD capes
     * (128x64, 256x128 ...) work by scale alone. {@code logicalHeight} is 32 for
     * half-height atlases and 64 for square ones.
     *
     * <p>The cuboid is built on the far side of the origin and turned half a
     * turn around the vertical axis, exactly like the vanilla cape model, so the
     * outer panel faces away from the player's back.
     */
    private static ModelPart createCapeGeometry(int logicalHeight) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        root.addChild(
                "cape",
                ModelPartBuilder.create()
                        .uv(0, 0)
                        .cuboid(-5.0F, 0.0F, -3.2F, 10.0F, 16.0F, 1.0F),
                ModelTransform.of(
                        0.0F, 0.0F, 0.0F,
                        0.0F, (float) Math.PI, 0.0F
                )
        );

        return root.createPart(64, logicalHeight);
    }
}
