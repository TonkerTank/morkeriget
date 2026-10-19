package com.morkeriget.skill;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Draws a StormBolt: a jagged bolt from 30 blocks up down to where it hits, built in code from ten segments. Each segment
// is two crossed strips, so it looks the same from every side, in three layers: a wide faint blue glow, a narrower bright
// cyan and a thin white-blue core. It uses vanilla's lightning render type, which adds its light on top and needs no
// light from the world, so the bolt is bright in the dark and shaders can make it bloom. It flickers and fades out over
// StormBolt.LIFETIME, with a soft blue glow on the ground where it hits. Client only
public class StormBoltRenderer extends EntityRenderer<StormBolt> {
    private static final float HEIGHT = 30;
    private static final int SEGMENTS = 10;
    // How far each bend can go to the side
    private static final float JITTER = 1.4f;
    private static final float GROUND_GLOW_RADIUS = 2.5f;
    private static final int GROUND_GLOW_SIDES = 16;

    // Half the width and the colour of each layer, widest first
    private record Layer(float halfWidth, float red, float green, float blue, float alpha) {
    }

    private static final Layer[] LAYERS = {
            new Layer(0.55f, 0.25f, 0.45f, 1.0f, 0.22f),
            new Layer(0.22f, 0.35f, 0.9f, 1.0f, 0.6f),
            new Layer(0.07f, 0.85f, 0.95f, 1.0f, 1.0f)
    };

    public StormBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // The entity is tiny, but the bolt reaches far above it
    @Override
    public boolean shouldRender(StormBolt bolt, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        if (!bolt.shouldRender(cameraX, cameraY, cameraZ)) {
            return false;
        }
        AABB box = bolt.getBoundingBox().inflate(JITTER + 1, 0, JITTER + 1).expandTowards(0, HEIGHT, 0);
        return frustum.isVisible(box);
    }

    @Override
    public void render(StormBolt bolt, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = bolt.tickCount + partialTick;
        if (age >= StormBolt.LIFETIME) {
            return;
        }
        // Fades out over its life, and flickers a little differently each tick. The flicker comes from the seed too,
        // so it is the same for everyone watching
        float fade = 1 - age / StormBolt.LIFETIME;
        float flicker = 0.55f + 0.45f * RandomSource.create(bolt.seed() + 31L * bolt.tickCount).nextFloat();
        float brightness = fade * flicker;

        Vec3[] points = points(bolt.seed());
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = poseStack.last().pose();
        for (Layer layer : LAYERS) {
            float alpha = layer.alpha * brightness;
            for (int i = 0; i < SEGMENTS; i++) {
                // The thinner it gets towards the top, the more it looks like it comes out of the clouds
                float taper = 0.6f + 0.4f * (1 - (float) i / SEGMENTS);
                float width = layer.halfWidth * taper;
                strip(buffer, pose, points[i], points[i + 1], width, 0, layer, alpha);
                strip(buffer, pose, points[i], points[i + 1], 0, width, layer, alpha);
            }
        }
        groundGlow(buffer, pose, 0.5f * fade);
        super.render(bolt, yaw, partialTick, poseStack, buffers, packedLight);
    }

    // SEGMENTS + 1 points from where it hits (0, 0, 0) to the top, each bend pushed a random way to the side. The bottom
    // one stays in the middle, so the bolt always hits the exact spot
    private static Vec3[] points(long seed) {
        RandomSource random = RandomSource.create(seed);
        Vec3[] points = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float y = HEIGHT * i / SEGMENTS;
            boolean end = i == 0;
            float x = end ? 0 : (random.nextFloat() * 2 - 1) * JITTER;
            float z = end ? 0 : (random.nextFloat() * 2 - 1) * JITTER;
            points[i] = new Vec3(x, y, z);
        }
        return points;
    }

    // One strip from a to b, as wide as (dx, dz) to each side. Drawn from both sides, since the render type hides backs
    private static void strip(VertexConsumer buffer, Matrix4f pose, Vec3 a, Vec3 b, float dx, float dz, Layer layer, float alpha) {
        float ax1 = (float) a.x - dx, az1 = (float) a.z - dz, ax2 = (float) a.x + dx, az2 = (float) a.z + dz;
        float bx1 = (float) b.x - dx, bz1 = (float) b.z - dz, bx2 = (float) b.x + dx, bz2 = (float) b.z + dz;
        float ay = (float) a.y, by = (float) b.y;
        // Front
        vertex(buffer, pose, ax1, ay, az1, layer, alpha);
        vertex(buffer, pose, ax2, ay, az2, layer, alpha);
        vertex(buffer, pose, bx2, by, bz2, layer, alpha);
        vertex(buffer, pose, bx1, by, bz1, layer, alpha);
        // Back
        vertex(buffer, pose, bx1, by, bz1, layer, alpha);
        vertex(buffer, pose, bx2, by, bz2, layer, alpha);
        vertex(buffer, pose, ax2, ay, az2, layer, alpha);
        vertex(buffer, pose, ax1, ay, az1, layer, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, Layer layer, float alpha) {
        buffer.addVertex(pose, x, y, z).setColor(layer.red, layer.green, layer.blue, alpha);
    }

    // A soft disc of blue light on the ground, bright in the middle and gone at the edge. Each slice is a quad with
    // two corners in the middle, since the render type only draws quads
    private static void groundGlow(VertexConsumer buffer, Matrix4f pose, float alpha) {
        float y = 0.05f;
        for (int i = 0; i < GROUND_GLOW_SIDES; i++) {
            float a1 = Mth.TWO_PI * i / GROUND_GLOW_SIDES;
            float a2 = Mth.TWO_PI * (i + 1) / GROUND_GLOW_SIDES;
            float x1 = Mth.cos(a1) * GROUND_GLOW_RADIUS, z1 = Mth.sin(a1) * GROUND_GLOW_RADIUS;
            float x2 = Mth.cos(a2) * GROUND_GLOW_RADIUS, z2 = Mth.sin(a2) * GROUND_GLOW_RADIUS;
            // Top side, then the same from below
            buffer.addVertex(pose, 0, y, 0).setColor(0.3f, 0.6f, 1.0f, alpha);
            buffer.addVertex(pose, x2, y, z2).setColor(0.2f, 0.4f, 1.0f, 0);
            buffer.addVertex(pose, x1, y, z1).setColor(0.2f, 0.4f, 1.0f, 0);
            buffer.addVertex(pose, 0, y, 0).setColor(0.3f, 0.6f, 1.0f, alpha);
            buffer.addVertex(pose, 0, y, 0).setColor(0.3f, 0.6f, 1.0f, alpha);
            buffer.addVertex(pose, x1, y, z1).setColor(0.2f, 0.4f, 1.0f, 0);
            buffer.addVertex(pose, x2, y, z2).setColor(0.2f, 0.4f, 1.0f, 0);
            buffer.addVertex(pose, 0, y, 0).setColor(0.3f, 0.6f, 1.0f, alpha);
        }
    }

    // Never used, since the bolt has no texture, but every entity renderer has to name one
    @Override
    public ResourceLocation getTextureLocation(StormBolt bolt) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
