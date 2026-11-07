
package dev.shadowcosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.math.RotationAxis;

/** Adds lightweight 3D cosmetics to player models. */
public final class ShadowCosmeticFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier WHITE =
            Identifier.of("shadowcosmetics", "textures/cosmetic/white.png");

    private final Model<Unit> hatModel;
    private final Model<Unit> haloModel;
    private final Model<Unit> wingModel;

    public ShadowCosmeticFeatureRenderer(PlayerEntityRenderer renderer) {
        super(renderer);

        this.hatModel = new Model.SinglePartModel(
                createHatGeometry(),
                RenderLayers::entityCutoutNoCull
        );

        this.haloModel = new Model.SinglePartModel(
                createHaloGeometry(),
                RenderLayers::entityCutoutNoCull
        );

        this.wingModel = new Model.SinglePartModel(
                createBodyGeometry(),
                RenderLayers::entityCutoutNoCull
        );
    }

    @Override
    public void render(
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            PlayerEntityRenderState state,
            float limbAngle,
            float limbDistance
    ) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || state.id != client.player.getId()) {
            return;
        }

        if (!CosmeticState.hatEnabled
                && !CosmeticState.haloEnabled
                && !CosmeticState.wingsEnabled
                && !CosmeticState.capeEnabled
                && !CosmeticState.customCapeEnabled) {
            return;
        }

        if (CosmeticState.hatEnabled || CosmeticState.haloEnabled) {
            matrices.push();
            getContextModel().head.applyTransform(matrices);

            if (CosmeticState.hatEnabled) {
                int hatColor = switch (
                        CosmeticState.hatIndex % CosmeticState.HATS.length
                ) {
                    case 1 -> 0xFF8B5CF6;
                    case 2 -> 0xFF7C3AED;
                    case 3 -> 0xFFE9D5FF;
                    case 4 -> 0xFFB8A1D9;
                    default -> 0xFF5B21B6;
                };

                submit(queue, hatModel, matrices, light, hatColor);
            }

            if (CosmeticState.haloEnabled) {
                submit(queue, haloModel, matrices, light, 0xFFFFD86B);
            }

            matrices.pop();
        }

        if (CosmeticState.wingsEnabled
                || CosmeticState.capeEnabled
                || CosmeticState.customCapeEnabled) {

            matrices.push();
            getContextModel().body.applyTransform(matrices);

            if (CosmeticState.wingsEnabled) {
                submit(queue, wingModel, matrices, light, 0xFF8B5CF6);
            }

            if (CosmeticState.capeEnabled || CosmeticState.customCapeEnabled) {
                int capeColor = switch (
                        CosmeticState.capeIndex % CosmeticState.CAPES.length
                ) {
                    case 1 -> 0xFF312E81;
                    case 2 -> 0xFFA855F7;
                    case 3 -> 0xFF111827;
                    case 4 -> 0xFF38BDF8;
                    case 5 -> 0xFFDC2626;
                    default -> 0xFF4C1D95;
                };

                float t = (System.currentTimeMillis() % 100000L) / 1000.0F;
                float sway = (float) Math.sin(t * 1.65F + state.id * 0.13F);
                float flutter = (float) Math.sin(t * 3.7F + state.id * 0.21F);

                matrices.push();
                matrices.translate(0.0F, 0.0F, 0.14F);

                matrices.multiply(
                        RotationAxis.POSITIVE_X.rotationDegrees(
                                5.0F + sway * 10.0F + flutter * 2.5F
                        )
                );

                matrices.multiply(
                        RotationAxis.POSITIVE_Z.rotationDegrees(sway * 1.8F)
                );

                matrices.translate(0.0F, 0.0F, -0.14F);

                Model<Unit> customCape =
                        CosmeticTextureManager.getCustomCapeModel();

                Identifier customTexture =
                        CosmeticTextureManager.getTextureId();

                if (customCape != null && customTexture != null) {
                    queue.submitModel(
                            customCape,
                            Unit.INSTANCE,
                            matrices,
                            customCape.getLayer(customTexture),
                            light,
                            OverlayTexture.DEFAULT_UV,
                            0xFFFFFFFF,
                            null,
                            0,
                            null
                    );
                } else {
                    submitCape(queue, matrices, light, capeColor);
                }

                matrices.pop();
            }

            matrices.pop();
        }
    }

    private static void submit(
            OrderedRenderCommandQueue queue,
            Model<Unit> model,
            MatrixStack matrices,
            int light,
            int color
    ) {
        queue.submitModel(
                model,
                Unit.INSTANCE,
                matrices,
                model.getLayer(WHITE),
                light,
                OverlayTexture.DEFAULT_UV,
                color,
                null,
                0,
                null
        );
    }

    private static void submitCape(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            int light,
            int color
    ) {
        Model<Unit> cape = CapeHolder.MODEL;
        submit(queue, cape, matrices, light, color);
    }

    private static ModelPart createHatGeometry() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        root.addChild("crown_brim",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-4.7F, -9.1F, -4.7F, 9.4F, 1.0F, 9.4F),
                ModelTransform.NONE);

        root.addChild("crown_top",
                ModelPartBuilder.create().uv(0, 12)
                        .cuboid(-2.8F, -11.5F, -2.8F, 5.6F, 2.5F, 5.6F),
                ModelTransform.NONE);

        root.addChild("crown_gem",
                ModelPartBuilder.create().uv(0, 24)
                        .cuboid(-0.8F, -12.8F, -0.8F, 1.6F, 1.5F, 1.6F),
                ModelTransform.NONE);

        root.addChild("horn_left_lower",
                ModelPartBuilder.create().uv(32, 0)
                        .cuboid(-4.4F, -10.0F, -0.8F, 1.5F, 2.2F, 1.5F),
                ModelTransform.NONE);

        root.addChild("horn_left_tip",
                ModelPartBuilder.create().uv(32, 8)
                        .cuboid(-4.1F, -12.0F, -0.5F, 0.9F, 2.2F, 0.9F),
                ModelTransform.NONE);

        root.addChild("horn_right_lower",
                ModelPartBuilder.create().uv(32, 0)
                        .cuboid(2.9F, -10.0F, -0.8F, 1.5F, 2.2F, 1.5F),
                ModelTransform.NONE);

        root.addChild("horn_right_tip",
                ModelPartBuilder.create().uv(32, 8)
                        .cuboid(3.2F, -12.0F, -0.5F, 0.9F, 2.2F, 0.9F),
                ModelTransform.NONE);

        return root.createPart(64, 64);
    }

    private static ModelPart createHaloGeometry() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2.0 * i / 12.0;

            float x = (float) Math.cos(a) * 5.4F;
            float z = (float) Math.sin(a) * 5.4F;

            root.addChild(
                    "halo_" + i,
                    ModelPartBuilder.create().uv(20, 24)
                            .cuboid(-0.65F, -14.0F, -0.65F,
                                    1.3F, 0.9F, 1.3F),
                    ModelTransform.of(x, 0.0F, z, 0.0F, 0.0F, 0.0F)
            );
        }

        return root.createPart(64, 64);
    }

    private static ModelPart createBodyGeometry() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        root.addChild("left_wing",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-9.0F, -3.0F, 0.0F, 8.0F, 7.0F, 1.2F),
                ModelTransform.of(-1.8F, -2.0F, 2.0F,
                        0.0F, 0.0F, -0.28F));

        root.addChild("left_wing_feather",
                ModelPartBuilder.create().uv(0, 10)
                        .cuboid(-8.0F, 2.0F, -0.2F, 7.0F, 3.0F, 1.0F),
                ModelTransform.of(-1.8F, -2.0F, 2.0F,
                        0.0F, 0.0F, -0.28F));

        root.addChild("right_wing",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(1.0F, -3.0F, 0.0F, 8.0F, 7.0F, 1.2F),
                ModelTransform.of(1.8F, -2.0F, 2.0F,
                        0.0F, 0.0F, 0.28F));

        root.addChild("right_wing_feather",
                ModelPartBuilder.create().uv(0, 10)
                        .cuboid(1.0F, 2.0F, -0.2F, 7.0F, 3.0F, 1.0F),
                ModelTransform.of(1.8F, -2.0F, 2.0F,
                        0.0F, 0.0F, 0.28F));

        return root.createPart(64, 64);
    }

    private static final class CapeHolder {
        private static final Model<Unit> MODEL =
                new Model.SinglePartModel(
                        createCapeGeometry(),
                        RenderLayers::entityCutoutNoCull
                );
    }

    private static ModelPart createCapeGeometry() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        root.addChild("cape_main",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-5.0F, 0.0F, 2.2F, 10.0F, 12.0F, 0.65F),
                ModelTransform.NONE);

        root.addChild("cape_mantle",
                ModelPartBuilder.create().uv(0, 14)
                        .cuboid(-5.2F, -0.2F, 2.0F, 10.4F, 2.0F, 1.0F),
                ModelTransform.NONE);

        root.addChild("cape_left_trim",
                ModelPartBuilder.create().uv(24, 14)
                        .cuboid(-5.0F, 1.0F, 2.91F, 0.55F, 10.5F, 0.12F),
                ModelTransform.NONE);

        root.addChild("cape_right_trim",
                ModelPartBuilder.create().uv(24, 14)
                        .cuboid(4.45F, 1.0F, 2.91F, 0.55F, 10.5F, 0.12F),
                ModelTransform.NONE);

        root.addChild("cape_emblem",
                ModelPartBuilder.create().uv(28, 0)
                        .cuboid(-1.2F, 3.0F, 2.94F, 2.4F, 2.4F, 0.16F),
                ModelTransform.NONE);

        return root.createPart(64, 64);
    }
}
