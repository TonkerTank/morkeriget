package com.morkeriget.combat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Numbers that float up from monsters when you hit them, like in Diablo. Client only
public final class DamageNumbers {
    // 20 ticks is one second
    private static final int LIFETIME = 25;
    private static final int NORMAL_COLOR = 0xFFFFFF;
    private static final int CRUSHING_COLOR = 0xFF5522;

    private record Number(Vec3 position, String text, int color, float size, int[] age) {
    }

    private static final List<Number> NUMBERS = new ArrayList<>();

    private DamageNumbers() {
    }

    public static void add(DamageNumberPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(payload.entityId());
        if (entity == null) {
            return;
        }
        TargetFrame.onHit(entity);
        // Spread the numbers a little, so several hits in a row don't land on top of each other
        Vec3 position = new Vec3(entity.getRandomX(0.6), entity.getY() + entity.getBbHeight() + 0.2, entity.getRandomZ(0.6));
        String text = payload.amount() >= 10 ? String.valueOf(Math.round(payload.amount())) : String.format("%.1f", payload.amount());
        if (payload.crushing()) {
            NUMBERS.add(new Number(position, text + "!", CRUSHING_COLOR, 1.6f, new int[1]));
        } else {
            NUMBERS.add(new Number(position, text, NORMAL_COLOR, 1.0f, new int[1]));
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        Iterator<Number> iterator = NUMBERS.iterator();
        while (iterator.hasNext()) {
            Number number = iterator.next();
            if (++number.age[0] >= LIFETIME) {
                iterator.remove();
            }
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || NUMBERS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        for (Number number : NUMBERS) {
            float age = number.age[0] + partialTick;
            float progress = age / LIFETIME;
            // Rises fast at first and then slows down, and fades out at the end
            double rise = 0.8 * (1 - (1 - progress) * (1 - progress));
            int alpha = (int) (255 * Math.min(1, (1 - progress) * 3));
            if (alpha < 16) {
                continue;
            }
            pose.pushPose();
            pose.translate(number.position.x - cameraPosition.x, number.position.y + rise - cameraPosition.y, number.position.z - cameraPosition.z);
            // Always face the camera, like name tags
            pose.mulPose(camera.rotation());
            float scale = 0.03f * number.size;
            pose.scale(scale, -scale, scale);
            float x = -font.width(number.text) / 2f;
            font.drawInBatch(number.text, x, 0, (alpha << 24) | number.color, true, pose.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
