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

    private static String loadedPath = "";
    private static Identifier textureId;
    private static Model<Unit> capeModel;

    private CosmeticTextureManager() {
    }

    public static Model<Unit> getCustomCapeModel() {
        if (!CosmeticState.customCapeEnabled
                || CosmeticState.customCapePath == null
                || CosmeticState.customCapePath.isBlank()) {
            return null;
        }

        if (CosmeticState.customCapePath.equals(loadedPath)
                && capeModel != null) {
            return capeModel;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        if (!client.isOnThread()) {
            return null;
        }

        Path path = Path.of(CosmeticState.customCapePath);

        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (var input = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(input);

            int width = image.getWidth();
            int height = image.getHeight();

            if (width != 64 || (height != 32 && height != 64)) {
                image.close();
                return null;
            }

            invalidate();

            NativeImageBackedTexture texture =
                    new NativeImageBackedTexture(
                            () -> "shadowcosmetics-custom-cape",
                            image
                    );

            textureId = Identifier.of(
                    "shadowcosmetics",
                    "dynamic/custom_cape"
            );

            client.getTextureManager().registerTexture(
                    textureId,
                    texture
            );

            capeModel = new Model.SinglePartModel(
                    createCapeGeometry(height),
                    RenderLayers::entityCutoutNoCull
            );

            loadedPath = CosmeticState.customCapePath;

            return capeModel;

        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    public static Identifier getTextureId() {
        return textureId;
    }

    /**
     * Unloads the currently registered texture.
     */
    public static void invalidate() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (textureId != null && client.isOnThread()) {
            client.getTextureManager().destroyTexture(textureId);
        }

        loadedPath = "";
        textureId = null;
        capeModel = null;
    }

    /**
     * Creates the 3D cape geometry.
     */
    private static ModelPart createCapeGeometry(int textureHeight) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        root.addChild(
                "cape_main",
                ModelPartBuilder.create()
                        .uv(0, 0)
                        .cuboid(
                                -5.0F,
                                0.0F,
                                2.2F,
                                10.0F,
                                12.0F,
                                0.65F
                        ),
                ModelTransform.NONE
        );

        root.addChild(
                "cape_mantle",
                ModelPartBuilder.create()
                        .uv(0, 14)
                        .cuboid(
                                -5.2F,
                                -0.2F,
                                2.0F,
                                10.4F,
                                2.0F,
                                1.0F
                        ),
                ModelTransform.NONE
        );

        root.addChild(
                "cape_left_trim",
                ModelPartBuilder.create()
                        .uv(24, 14)
                        .cuboid(
                                -5.0F,
                                1.0F,
                                2.91F,
                                0.55F,
                                10.5F,
                                0.12F
                        ),
                ModelTransform.NONE
        );

        root.addChild(
                "cape_right_trim",
                ModelPartBuilder.create()
                        .uv(24, 14)
                        .cuboid(
                                4.45F,
                                1.0F,
                                2.91F,
                                0.55F,
                                10.5F,
                                0.12F
                        ),
                ModelTransform.NONE
        );

        return root.createPart(64, textureHeight);
    }
}
