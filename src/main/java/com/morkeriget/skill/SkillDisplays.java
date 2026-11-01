package com.morkeriget.skill;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.math.Transformation;
import com.morkeriget.mixin.BlockDisplayAccessor;
import com.morkeriget.mixin.DisplayAccessor;
import com.morkeriget.mixin.ItemDisplayAccessor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Blocks and items shown in the world for a moment by a skill, like the paladin's hammer and pillars of light. They are
// vanilla display entities, so every client draws them without anything of ours: they can be scaled, turned and
// lit up fully, and the client glides them smoothly from one shape or spot to the next. Each one has a time it goes
// away, so none are left behind, also not one that was saved with a chunk. Server only
public final class SkillDisplays {
    private static final String UNTIL_TAG = "morkeriget_display_until";
    private static final Brightness FULL_BRIGHT = new Brightness(15, 15);

    // A change to a display that waits for its tick, see later()
    private record Step(Display display, long at, Transformation to, int duration) {
    }

    private static final List<Step> STEPS = new ArrayList<>();

    // A plate that keeps turning, maybe following an entity, see spin()
    private record Spinner(Display display, float size, float thickness, float yaw, float speed, long start, long until, Entity follow, double lift) {
    }

    private static final List<Spinner> SPINNERS = new ArrayList<>();
    // How often a spinning plate is turned. Small steps, since the client glides the corner it is turned around in a
    // straight line, which would make a big turn wobble
    private static final int SPIN_TIME = 2;
    // A spinning plate grows in and shrinks out over this many ticks
    private static final int SPIN_FADE = 5;

    private SkillDisplays() {
    }

    // A block shown at pos for life ticks, fully lit
    static Display.BlockDisplay block(ServerLevel level, Vec3 pos, BlockState state, Transformation shape, int life) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        ((BlockDisplayAccessor) display).morkeriget$setBlockState(state);
        return spawn(level, display, pos, shape, life);
    }

    // An item shown at pos for life ticks, fully lit. The item is drawn centered on pos
    static Display.ItemDisplay item(ServerLevel level, Vec3 pos, ItemStack stack, Transformation shape, int life) {
        Display.ItemDisplay display = EntityType.ITEM_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        ((ItemDisplayAccessor) display).morkeriget$setItemStack(stack);
        ((ItemDisplayAccessor) display).morkeriget$setItemTransform(ItemDisplayContext.FIXED);
        return spawn(level, display, pos, shape, life);
    }

    private static <T extends Display> T spawn(ServerLevel level, T display, Vec3 pos, Transformation shape, int life) {
        DisplayAccessor accessor = (DisplayAccessor) display;
        accessor.morkeriget$setTransformation(shape);
        accessor.morkeriget$setBrightnessOverride(FULL_BRIGHT);
        display.moveTo(pos.x, pos.y, pos.z);
        display.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + life);
        level.addFreshEntity(display);
        return display;
    }

    // Always turned to face whoever looks at it
    static void faceCamera(Display display) {
        ((DisplayAccessor) display).morkeriget$setBillboardConstraints(Display.BillboardConstraints.CENTER);
    }

    // An outline in this color that shows through walls, like the Glowing effect
    static void glow(Display display, int color) {
        ((DisplayAccessor) display).morkeriget$setGlowColorOverride(color);
        display.setGlowingTag(true);
    }

    // The client glides the display to where it is moved over this many ticks
    static void smoothMoves(Display display, int ticks) {
        ((DisplayAccessor) display).morkeriget$setPosRotInterpolationDuration(ticks);
    }

    // Changes the shape now, and the client glides to it over duration ticks. Not in the tick the display is made, since
    // the change would come with it and not be seen, see later()
    static void reshape(Display display, Transformation to, int duration) {
        if (display == null || display.isRemoved()) {
            return;
        }
        DisplayAccessor accessor = (DisplayAccessor) display;
        accessor.morkeriget$setInterpolationDuration(duration);
        accessor.morkeriget$setTransformation(to);
        accessor.morkeriget$setInterpolationDelay(0);
    }

    // A flat plate of the block lying at pos, life ticks long, turning speed degrees a tick. It grows in and shrinks out.
    // With follow it moves along with that entity, lift above its feet
    static Display.BlockDisplay spin(ServerLevel level, Vec3 pos, BlockState state, float size, float thickness, float yaw, float speed, int life,
                                     Entity follow, double lift) {
        Display.BlockDisplay display = block(level, pos, state, plate(0, thickness, yaw), life);
        if (display != null) {
            smoothMoves(display, 1);
            long now = level.getGameTime();
            SPINNERS.add(new Spinner(display, size, thickness, yaw, speed, now, now + life, follow, lift));
        }
        return display;
    }

    // Changes the shape after delay ticks (at least 1), gliding over duration ticks
    static void later(Display display, int delay, Transformation to, int duration) {
        if (display != null) {
            STEPS.add(new Step(display, display.level().getGameTime() + Math.max(1, delay), to, duration));
        }
    }

    // A shape that is only scaled and turned around its own middle, for items, which are drawn centered
    static Transformation centered(float scale, Quaternionf rotation) {
        return new Transformation(new Vector3f(), rotation, new Vector3f(scale), new Quaternionf());
    }

    // A block stretched into a column width wide and height high, standing on pos
    static Transformation column(float width, float height) {
        return new Transformation(new Vector3f(-width / 2, 0, -width / 2), new Quaternionf(), new Vector3f(width, height, width), new Quaternionf());
    }

    // A column like the one above, but only from bottom to top above pos
    static Transformation column(float width, float bottom, float top) {
        return new Transformation(new Vector3f(-width / 2, bottom, -width / 2), new Quaternionf(), new Vector3f(width, top - bottom, width), new Quaternionf());
    }

    // A block flattened into a square plate size wide and thickness high, centered on pos and turned yaw degrees
    static Transformation plate(float size, float thickness, float yaw) {
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(yaw));
        Vector3f scale = new Vector3f(size, thickness, size);
        // Turns around the middle of the plate rather than its corner
        Vector3f corner = rotation.transform(new Vector3f(scale.x / 2, 0, scale.z / 2)).negate();
        return new Transformation(corner, rotation, scale, new Quaternionf());
    }

    // A block stretched into a bar length long and width thick along way, centered on pos. A block that points up,
    // like an end rod, then points along way
    static Transformation bar(Vec3 way, float width, float length) {
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), way.normalize().toVector3f());
        Vector3f scale = new Vector3f(width, length, width);
        Vector3f middle = rotation.transform(new Vector3f(scale.x / 2, scale.y / 2, scale.z / 2)).negate();
        return new Transformation(middle, rotation, scale, new Quaternionf());
    }

    static void onServerTick(ServerTickEvent.Post event) {
        for (Iterator<Spinner> it = SPINNERS.iterator(); it.hasNext(); ) {
            Spinner spinner = it.next();
            Display display = spinner.display;
            long now = display.level().getGameTime();
            if (display.isRemoved() || (spinner.follow != null && !spinner.follow.isAlive())) {
                display.discard();
                it.remove();
                continue;
            }
            if (spinner.follow != null) {
                display.moveTo(spinner.follow.getX(), spinner.follow.getY() + spinner.lift, spinner.follow.getZ());
            }
            if (now > spinner.start && (now - spinner.start) % SPIN_TIME == 0) {
                float fade = Math.min(1, Math.min((now - spinner.start) / (float) SPIN_FADE, (spinner.until - now - SPIN_TIME) / (float) SPIN_FADE));
                float yaw = spinner.yaw + spinner.speed * (now - spinner.start + SPIN_TIME);
                reshape(display, plate(spinner.size * Math.max(0, fade), spinner.thickness, yaw), SPIN_TIME);
            }
        }
        for (Iterator<Step> it = STEPS.iterator(); it.hasNext(); ) {
            Step step = it.next();
            if (step.display.isRemoved()) {
                it.remove();
            } else if (step.display.level().getGameTime() >= step.at) {
                reshape(step.display, step.to, step.duration);
                it.remove();
            }
        }
    }

    // Takes each display away when its time is up
    static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Display display && !display.level().isClientSide()) {
            long until = display.getPersistentData().getLong(UNTIL_TAG);
            if (until > 0 && display.level().getGameTime() >= until) {
                display.discard();
            }
        }
    }
}
