package com.morkeriget.dummy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Elites;
import com.morkeriget.skill.Minions;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// A training dummy, to see how hard you hit: a husk that stands still, never dies and counts as a monster, so every
// skill hits it. While you hit it, the action bar shows your damage per second, also what your minions and wolves do,
// and when you stop a summary comes in the chat. Placed with the item, taken down again with sneak and an empty hand
public final class TrainingDummy {
    private static final String DUMMY_TAG = "morkeriget_training_dummy";
    private static final double HEALTH = 1000;
    // 20 ticks is one second. A session ends this long after the last hit
    private static final int SESSION_END = 4 * 20;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> ITEM = ITEMS.registerItem("training_dummy", TrainingDummyItem::new, new Item.Properties().stacksTo(16));

    // What one player has done to one dummy since they started hitting it
    private static final class Session {
        final ServerLevel level;
        final long first;
        long last;
        double total;
        int hits;
        float biggest;

        Session(ServerLevel level, long now) {
            this.level = level;
            this.first = now;
            this.last = now;
        }

        // Per second from the first hit, and at least over one second, so a single hit isn't a huge number
        double perSecond(long until) {
            return total / Math.max(1.0, (until - first) / 20.0);
        }
    }

    private record Key(UUID dummy, UUID player) {
    }

    private static final Map<Key, Session> SESSIONS = new HashMap<>();

    private TrainingDummy() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.COMBAT) {
                event.accept(ITEM);
            }
        });
        NeoForge.EVENT_BUS.addListener(TrainingDummy::onDamagePost);
        NeoForge.EVENT_BUS.addListener(TrainingDummy::onServerTick);
        NeoForge.EVENT_BUS.addListener(TrainingDummy::onInteract);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> SESSIONS.clear());
    }

    public static boolean isDummy(Entity entity) {
        return entity.getPersistentData().getBoolean(DUMMY_TAG);
    }

    // Stands on the spot, looking the way it is given. Never an elite, and no monster level, so it is the same for everyone
    public static Husk spawn(ServerLevel level, Vec3 at, float yaw) {
        Husk dummy = EntityType.HUSK.create(level);
        if (dummy == null) {
            return null;
        }
        dummy.moveTo(at.x, at.y, at.z, yaw, 0);
        Elites.markChecked(dummy);
        dummy.getPersistentData().putBoolean(DUMMY_TAG, true);
        dummy.setNoAi(true);
        dummy.setSilent(true);
        dummy.setPersistenceRequired();
        dummy.setCanPickUpLoot(false);
        dummy.setCustomName(Component.literal("Træningsdukke").withStyle(ChatFormatting.YELLOW));
        dummy.setCustomNameVisible(true);
        AttributeInstance health = dummy.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(HEALTH);
        }
        AttributeInstance knockback = dummy.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(1);
        }
        dummy.setHealth(dummy.getMaxHealth());
        level.addFreshEntity(dummy);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 10, 0.3, 0.5, 0.3, 0.02);
        return dummy;
    }

    // Counts the damage for the player behind it, then heals it all back, so it never dies. /kill and the void still
    // get rid of it
    private static void onDamagePost(LivingDamageEvent.Post event) {
        if (!isDummy(event.getEntity()) || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.getEntity().setHealth(event.getEntity().getMaxHealth());
        }
        // Burning has no attacker, so it counts for whoever hit the dummy last, like a kill would
        Entity attacker = event.getSource().getEntity() != null ? event.getSource().getEntity() : event.getEntity().getKillCredit();
        ServerPlayer player = credit(level, attacker);
        float damage = event.getNewDamage();
        if (player == null || damage <= 0) {
            return;
        }
        long now = level.getGameTime();
        Session session = SESSIONS.computeIfAbsent(new Key(event.getEntity().getUUID(), player.getUUID()), key -> new Session(level, now));
        session.last = now;
        session.total += damage;
        session.hits++;
        session.biggest = Math.max(session.biggest, damage);
    }

    // The player who did it: themselves, or the owner of the skeleton or wolf that did
    static ServerPlayer credit(ServerLevel level, Entity attacker) {
        if (attacker instanceof ServerPlayer player) {
            return player;
        }
        if (attacker != null && Minions.isAlly(attacker)) {
            return Minions.owner(level, attacker);
        }
        return attacker instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner ? owner : null;
    }

    // Once a second: the numbers so far on the action bar, and the summary in the chat when a session is over
    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        Iterator<Map.Entry<Key, Session>> sessions = SESSIONS.entrySet().iterator();
        while (sessions.hasNext()) {
            Map.Entry<Key, Session> entry = sessions.next();
            Session session = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey().player());
            long now = session.level.getGameTime();
            boolean over = now - session.last > SESSION_END;
            if (over) {
                sessions.remove();
            }
            if (player == null) {
                continue;
            }
            if (over) {
                player.sendSystemMessage(Component.literal("Træningsdukke: ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                        .append(Component.literal(number(session.perSecond(session.last)) + " skade/sek").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                        .append(Component.literal(" - " + number(session.total) + " skade på " + seconds(session.last - session.first)
                                + ", " + session.hits + " slag, største " + number(session.biggest)).withStyle(ChatFormatting.GRAY)));
            } else {
                player.displayClientMessage(Component.literal(number(session.perSecond(now)) + " skade/sek").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("  i alt " + number(session.total) + "  største " + number(session.biggest))
                                .withStyle(ChatFormatting.GRAY)), true);
            }
        }
    }

    // E.g. "1.234", or "12,5" below a hundred
    private static String number(double value) {
        return value < 100 ? String.format("%.1f", value).replace('.', ',') : String.format("%,d", Math.round(value)).replace(',', '.');
    }

    private static String seconds(long ticks) {
        return String.format("%.1f sek.", Math.max(1.0, ticks / 20.0)).replace('.', ',');
    }

    // Sneak and right-click it with an empty hand to take it down and get the item back
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isDummy(event.getTarget()) || !event.getEntity().isShiftKeyDown() || !event.getEntity().getMainHandItem().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity dummy = event.getTarget();
        player.serverLevel().sendParticles(ParticleTypes.POOF, dummy.getX(), dummy.getY() + 1, dummy.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        player.serverLevel().playSound(null, dummy.blockPosition(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
        dummy.discard();
        if (!player.isCreative()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(ITEM.get()));
        }
    }

    // For the tests: what the player has done to the dummy in the session going on now, or 0
    public static double total(Entity dummy, ServerPlayer player) {
        Session session = SESSIONS.get(new Key(dummy.getUUID(), player.getUUID()));
        return session != null ? session.total : 0;
    }
}
