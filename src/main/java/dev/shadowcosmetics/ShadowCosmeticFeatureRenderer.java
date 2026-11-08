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

/**
 * Draws the 3D cosmetics on the local player: five hats, a halo, wings and
 * a cape. All animation is done with matrix transforms.
 */
public final class ShadowCosmeticFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier WHITE =
            Identifier.of("shadowcosmetics", "textures/cosmetic/white.png");

    /** Default colours of the built-in capes (same order as CosmeticState.CAPES). */
    private static final int[] CAPE_COLORS = {
            0xFF4C1D95, 0xFF312E81, 0xFFA855F7,
            0xFF111827, 0xFF38BDF8, 0xFFDC2626
    };

    private static final int HALO_COLOR = 0xFFFFD86B;
    private static final int WING_COLOR = 0xFF8B5CF6;

    /** Wing pose in degrees: how far the wings are spread back / lifted up. */
    private static final float WING_SPREAD_DEGREES = 24.0F;
    private static final float WING_LIFT_DEGREES = 17.0F;

    /**
     * One hat style: a main model and an optional trim model that is drawn in a
     * second colour. Colours are ARGB.
     */
    private record Hat(Model<Unit> main, Model<Unit> trim, int color, int trimColor) {}

    private final Hat[] hats;
    private final Model<Unit> haloModel;
    private final Model<Unit> wingModel;
    private final Model<Unit> wingTrimModel;
    private final Model<Unit> mirroredWingModel;
    private final Model<Unit> mirroredWingTrimModel;

    public ShadowCosmeticFeatureRenderer(PlayerEntityRenderer renderer) {
        super(renderer);

        // Order matches CosmeticState.HATS.
        this.hats = new Hat[]{
                new Hat(model(createCrownGeometry()),
                        model(createCrownGemGeometry()),
                        0xFF5B21B6, 0xFFFFD86B),
                new Hat(model(createBeanieGeometry()),
                        model(createBeanieTrimGeometry()),
                        0xFF6D28D9, 0xFFC4B5FD),
                new Hat(model(createHornsGeometry()),
                        null,
                        0xFFB91C1C, 0),
                new Hat(model(createWizardGeometry()),
                        model(createWizardBandGeometry()),
                        0xFF3730A3, 0xFFFBBF24),
                new Hat(model(createTopHatGeometry()),
                        model(createTopHatBandGeometry()),
                        0xFF1F1F2E, 0xFF8B5CF6)
        };

        this.haloModel = model(createHaloGeometry());

        this.wingModel = model(createWingGeometry(true, false));
        this.wingTrimModel = model(createWingGeometry(true, true));
        this.mirroredWingModel = model(createWingGeometry(false, false));
        this.mirroredWingTrimModel = model(createWingGeometry(false, true));
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

        if (!CosmeticState.masterEnabled) {
            return;
        }

        boolean hat = CosmeticState.hatEnabled;
        boolean halo = CosmeticState.haloEnabled;
        boolean wings = CosmeticState.wingsEnabled;
        boolean cape = CosmeticState.capeEnabled;

        if (!hat && !halo && !wings && !cape) {
            return;
        }

        // Seconds. The wrap length (2000 * pi seconds) keeps every sine used
        // below continuous, so animations never jump.
        float time = (System.currentTimeMillis() % 6283185L) / 1000.0F;

        boolean animated = CosmeticState.animationsEnabled;
        int accent = CosmeticState.accentColor();

        // 0 = standing still, 1 = running. Makes cape and wings react to movement.
        float walk = Math.max(0.0F, Math.min(1.0F, limbDistance));

        if (hat || halo) {
            matrices.push();
            getContextModel().head.applyTransform(matrices);

            if (hat) {
                renderHat(queue, matrices, light, accent);
            }

            if (halo) {
                renderHalo(queue, matrices, light, accent, time, animated);
            }

            matrices.pop();
        }

        if (wings || cape) {
            matrices.push();
            getContextModel().body.applyTransform(matrices);

            if (wings) {
                renderWings(queue, matrices, light, accent, time, walk, animated);
            }

            if (cape) {
                renderCape(queue, matrices, light, accent, state.id, time, walk, animated);
            }

            matrices.pop();
        }
    }

    // ------------------------------------------------------------------
    // Hats and halo
    // ------------------------------------------------------------------

    private void renderHat(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            int light,
            int accent
    ) {
        Hat hat = hats[Math.floorMod(CosmeticState.hatIndex, hats.length)];

        submit(queue, hat.main(), matrices, light,
                Colors.resolve(hat.color(), accent));

        if (hat.trim() != null) {
            int trimColor = accent != 0
                    ? Colors.lighten(accent, 0.55F)
                    : hat.trimColor();

            submit(queue, hat.trim(), matrices, light, trimColor);
        }
    }

    private void renderHalo(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            int light,
            int accent,
            float time,
            boolean animated
    ) {
        matrices.push();

        if (animated) {
            // Gentle bob (blocks) and a slow spin.
            matrices.translate(0.0F, (float) Math.sin(time * 2.0F) * 0.6F / 16.0F, 0.0F);

            matrices.multiply(
                    RotationAxis.POSITIVE_Y.rotationDegrees(
                            (float) Math.toDegrees(time * 0.7F)
                    )
            );
        }

        submit(queue, haloModel, matrices, light,
                Colors.resolve(HALO_COLOR, accent));

        matrices.pop();
    }

    // ------------------------------------------------------------------
    // Wings
    // ------------------------------------------------------------------

    private void renderWings(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            int light,
            int accent,
            float time,
            float walk,
            boolean animated
    ) {
        float flap = animated ? (float) Math.sin(time * 3.0F) : 0.0F;
        float amplitude = 10.0F + 18.0F * walk;

        float yaw = WING_SPREAD_DEGREES + flap * amplitude;

        int color = Colors.resolve(WING_COLOR, accent);
        int trimColor = Colors.lighten(color, 0.45F);

        // Left wing extends to -x, right wing to +x.
        renderWing(queue, matrices, light, -1.0F, yaw,
                wingModel, wingTrimModel, color, trimColor);

        renderWing(queue, matrices, light, 1.0F, yaw,
                mirroredWingModel, mirroredWingTrimModel, color, trimColor);
    }

    private static void renderWing(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            int light,
            float side,
            float yawDegrees,
            Model<Unit> main,
            Model<Unit> trim,
            int color,
            int trimColor
    ) {
        matrices.push();

        // Shoulder blade position, in blocks (pixels / 16).
        matrices.translate(side * 1.5F / 16.0F, 1.0F / 16.0F, 2.4F / 16.0F);

        matrices.multiply(
                RotationAxis.POSITIVE_Z.rotationDegrees(-side * WING_LIFT_DEGREES)
        );

        matrices.multiply(
                RotationAxis.POSITIVE_Y.rotationDegrees(-side * yawDegrees)
        );

        submit(queue, main, matrices, light, color);
        submit(queue, trim, matrices, light, trimColor);

        matrices.pop();
    }

    // ------------------------------------------------------------------
    // Cape
    // ------------------------------------------------------------------

    private void renderCape(
            OrderedRenderCommandQueue queue,
            MatrixStack matrices,
            int light,
            int accent,
            int entityId,
            float time,
            float walk,
            boolean animated
    ) {
        float sway = animated
                ? (float) Math.sin(time * 1.65F + entityId * 0.13F)
                : 0.0F;

        float flutter = animated
                ? (float) Math.sin(time * 3.7F + entityId * 0.21F)
                : 0.0F;

        // The cape hangs at 6 degrees, lifts backwards while moving and
        // keeps swaying a little more the faster the player goes.
        float pitch = 6.0F
                + walk * 22.0F
                + sway * (2.0F + 6.0F * walk)
                + flutter * (0.5F + 2.0F * walk);

        float roll = sway * (1.0F + 1.5F * walk);

        matrices.push();
        matrices.translate(0.0F, 0.0F, 0.14F);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));

        matrices.translate(0.0F, 0.0F, -0.14F);

        Model<Unit> customCape = CosmeticTextureManager.getCustomCapeModel();
        Identifier customTexture = CosmeticTextureManager.getTextureId();

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
            int style = Math.floorMod(CosmeticState.capeIndex, CAPE_COLORS.length);

            submit(queue, CapeHolder.MODEL, matrices, light,
                    Colors.resolve(CAPE_COLORS[style], accent));
        }

        matrices.pop();
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static Model<Unit> model(ModelPart part) {
        return new Model.SinglePartModel(part, RenderLayers::entityCutoutNoCull);
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

    private static void box(
            ModelPartData root,
            String name,
            float x,
            float y,
            float z,
            float width,
            float height,
            float depth
    ) {
        root.addChild(
                name,
                ModelPartBuilder.create()
                        .uv(0, 0)
                        .cuboid(x, y, z, width, height, depth),
                ModelTransform.NONE
        );
    }

    /** Mirrors a box across the x = 0 plane. */
    private static void mirroredBox(
            ModelPartData root,
            String name,
            float x,
            float y,
            float z,
            float width,
            float height,
            float depth
    ) {
        box(root, name, -x - width, y, z, width, height, depth);
    }

    // ------------------------------------------------------------------
    // Hat geometry (head space, y points down, the head spans y -8 .. 0)
    // ------------------------------------------------------------------

    private static ModelPart createCrownGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "band", -4.6F, -9.6F, -4.6F, 9.2F, 1.8F, 9.2F);

        float[] corner = {-4.6F, 3.4F};
        int index = 0;

        for (float x : corner) {
            for (float z : corner) {
                box(root, "corner_" + index++, x, -12.2F, z, 1.2F, 2.6F, 1.2F);
            }
        }

        box(root, "spike_front", -0.6F, -13.0F, -4.6F, 1.2F, 3.4F, 1.2F);
        box(root, "spike_back", -0.6F, -13.0F, 3.4F, 1.2F, 3.4F, 1.2F);
        box(root, "spike_left", -4.6F, -13.0F, -0.6F, 1.2F, 3.4F, 1.2F);
        box(root, "spike_right", 3.4F, -13.0F, -0.6F, 1.2F, 3.4F, 1.2F);

        return root.createPart(64, 64);
    }

    private static ModelPart createCrownGemGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "gem_front", -1.0F, -9.5F, -5.1F, 2.0F, 1.6F, 0.9F);
        box(root, "gem_back", -1.0F, -9.5F, 4.2F, 2.0F, 1.6F, 0.9F);
        box(root, "gem_left", -5.1F, -9.5F, -1.0F, 0.9F, 1.6F, 2.0F);
        box(root, "gem_right", 4.2F, -9.5F, -1.0F, 0.9F, 1.6F, 2.0F);

        return root.createPart(64, 64);
    }

    private static ModelPart createBeanieGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "cap", -4.6F, -10.4F, -4.6F, 9.2F, 3.0F, 9.2F);
        box(root, "dome", -3.6F, -11.4F, -3.6F, 7.2F, 1.0F, 7.2F);

        return root.createPart(64, 64);
    }

    private static ModelPart createBeanieTrimGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "fold", -4.9F, -8.6F, -4.9F, 9.8F, 1.6F, 9.8F);
        box(root, "pompom", -1.3F, -13.2F, -1.3F, 2.6F, 2.0F, 2.6F);

        return root.createPart(64, 64);
    }

    private static ModelPart createHornsGeometry() {
        ModelPartData root = new ModelData().getRoot();

        // Left horn; the tip curves in towards the centre.
        box(root, "horn_left_base", -4.5F, -9.4F, -1.2F, 2.0F, 2.2F, 2.4F);
        box(root, "horn_left_mid", -4.1F, -11.4F, -0.9F, 1.6F, 2.2F, 1.8F);
        box(root, "horn_left_tip", -3.3F, -13.4F, -0.6F, 1.2F, 2.2F, 1.2F);

        mirroredBox(root, "horn_right_base", -4.5F, -9.4F, -1.2F, 2.0F, 2.2F, 2.4F);
        mirroredBox(root, "horn_right_mid", -4.1F, -11.4F, -0.9F, 1.6F, 2.2F, 1.8F);
        mirroredBox(root, "horn_right_tip", -3.3F, -13.4F, -0.6F, 1.2F, 2.2F, 1.2F);

        return root.createPart(64, 64);
    }

    private static ModelPart createWizardGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "brim", -6.2F, -8.8F, -6.2F, 12.4F, 0.9F, 12.4F);
        box(root, "layer_1", -3.6F, -12.0F, -3.6F, 7.2F, 3.3F, 7.2F);
        box(root, "layer_2", -2.7F, -15.0F, -2.7F, 5.4F, 3.1F, 5.4F);

        // The tip bends backwards.
        box(root, "layer_3", -1.7F, -17.8F, -1.2F, 3.4F, 2.9F, 3.4F);
        box(root, "tip", -0.9F, -19.8F, -0.4F, 1.8F, 2.2F, 1.8F);

        return root.createPart(64, 64);
    }

    private static ModelPart createWizardBandGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "band", -3.9F, -10.4F, -3.9F, 7.8F, 1.2F, 7.8F);

        return root.createPart(64, 64);
    }

    private static ModelPart createTopHatGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "brim", -5.8F, -8.8F, -5.8F, 11.6F, 0.9F, 11.6F);
        box(root, "tube", -3.8F, -15.6F, -3.8F, 7.6F, 6.9F, 7.6F);

        return root.createPart(64, 64);
    }

    private static ModelPart createTopHatBandGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "band", -4.0F, -11.0F, -4.0F, 8.0F, 1.6F, 8.0F);

        return root.createPart(64, 64);
    }

    // ------------------------------------------------------------------
    // Halo and wing geometry
    // ------------------------------------------------------------------

    private static ModelPart createHaloGeometry() {
        ModelPartData root = new ModelData().getRoot();

        int segments = 16;

        for (int i = 0; i < segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;

            float x = (float) Math.cos(angle) * 5.8F;
            float z = (float) Math.sin(angle) * 5.8F;

            // Turn each piece so it lies along the ring.
            float yaw = (float) -(angle + Math.PI / 2.0);

            root.addChild(
                    "halo_" + i,
                    ModelPartBuilder.create()
                            .uv(0, 0)
                            .cuboid(-1.3F, -15.5F, -0.5F, 2.6F, 0.8F, 1.0F),
                    ModelTransform.of(x, 0.0F, z, 0.0F, yaw, 0.0F)
            );
        }

        return root.createPart(64, 64);
    }

    /**
     * One wing, built around the shoulder pivot (0, 0, 0). The left wing reaches
     * towards -x, the right wing towards +x.
     *
     * @param left true for the left wing
     * @param trim true for the lighter bones, false for the feather rows
     */
    private static ModelPart createWingGeometry(boolean left, boolean trim) {
        ModelPartData root = new ModelData().getRoot();

        if (trim) {
            wingBox(root, left, "spar_top", 9.5F, -3.0F, -0.3F, 1.8F, 1.6F);
            wingBox(root, left, "spar_inner", 1.4F, -3.0F, -0.3F, 10.0F, 1.6F);
        } else {
            // length, top y, height of each feather row; shorter towards the bottom.
            float[][] rows = {
                    {9.0F, -1.5F, 2.0F},
                    {8.0F, 0.5F, 2.0F},
                    {6.5F, 2.5F, 2.0F},
                    {5.0F, 4.5F, 2.0F},
                    {3.2F, 6.5F, 1.8F}
            };

            for (int i = 0; i < rows.length; i++) {
                wingBox(root, left, "feather_" + i,
                        rows[i][0], rows[i][1], 0.0F, rows[i][2], 1.0F);
            }
        }

        return root.createPart(64, 64);
    }

    /** A wing box that starts at the pivot and reaches {@code length} outwards. */
    private static void wingBox(
            ModelPartData root,
            boolean left,
            String name,
            float length,
            float y,
            float z,
            float height,
            float depth
    ) {
        float x = left ? -length : 0.0F;

        box(root, name, x, y, z, length, height, depth);
    }

    // ------------------------------------------------------------------
    // Built-in cape (tinted, uses the white texture)
    // ------------------------------------------------------------------

    private static final class CapeHolder {
        private static final Model<Unit> MODEL =
                new Model.SinglePartModel(
                        createCapeGeometry(),
                        RenderLayers::entityCutoutNoCull
                );
    }

    private static ModelPart createCapeGeometry() {
        ModelPartData root = new ModelData().getRoot();

        box(root, "cape_main", -5.0F, 0.0F, 2.2F, 10.0F, 13.0F, 0.65F);
        box(root, "cape_mantle", -5.2F, -0.2F, 2.0F, 10.4F, 2.0F, 1.0F);
        box(root, "cape_left_trim", -5.0F, 1.0F, 2.91F, 0.55F, 11.5F, 0.12F);
        box(root, "cape_right_trim", 4.45F, 1.0F, 2.91F, 0.55F, 11.5F, 0.12F);
        box(root, "cape_emblem", -1.2F, 3.0F, 2.94F, 2.4F, 2.4F, 0.16F);

        return root.createPart(64, 64);
    }
}
