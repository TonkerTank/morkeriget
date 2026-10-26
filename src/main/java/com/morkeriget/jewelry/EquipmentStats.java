package com.morkeriget.jewelry;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.combat.PlayerHits;
import com.morkeriget.loot.ItemKind;
import com.morkeriget.loot.Loot;
import com.morkeriget.skill.Mana;
import com.morkeriget.skill.Skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// The stats shown next to the equipment screen, like the character sheet in Diablo 4. They are worked out on the
// server, which knows all the bonuses, and sent to the client a couple of times a second while the screen is open.
// The attack stats are for the weapon in the weapon slot, also when you hold something else
public final class EquipmentStats {
    private EquipmentStats() {
    }

    // One line, e.g. "Health 34". The group and name are keys in the language files (stats.group.<group> and
    // stats.<name>), so the screen shows them in the player's language. Higher is always better, so the screen can show
    // green and red changes
    public record Line(String group, String name, float value, int decimals, String unit) {
        public static final StreamCodec<ByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Line::group,
                ByteBufCodecs.STRING_UTF8, Line::name,
                ByteBufCodecs.FLOAT, Line::value,
                ByteBufCodecs.VAR_INT, Line::decimals,
                ByteBufCodecs.STRING_UTF8, Line::unit,
                Line::new);
    }

    public record Payload(List<Line> lines) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "equipment_stats"));
        public static final StreamCodec<ByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                Line.STREAM_CODEC.apply(ByteBufCodecs.list()), Payload::lines, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Payload(lines(player)));
    }

    private static List<Line> lines(ServerPlayer player) {
        ItemStack slotItem = player.getInventory().getItem(JewelryMenu.WEAPON_SLOT);
        ItemStack weapon = JewelryMenu.isWeapon(slotItem) ? slotItem : ItemStack.EMPTY;
        List<ItemStack> items = worn(player, weapon);
        List<Line> lines = new ArrayList<>();

        float damage = (float) withWeapon(player, Attributes.ATTACK_DAMAGE, weapon);
        float speed = (float) withWeapon(player, Attributes.ATTACK_SPEED, weapon);
        lines.add(new Line("attack", "dps", damage * speed, 1, ""));
        lines.add(new Line("attack", "damage", damage, 1, ""));
        lines.add(new Line("attack", "attack_speed", speed, 2, ""));
        special(lines, "attack", "crushing", items, Loot.CRUSHING_TAG, "%");
        special(lines, "attack", "lifesteal", items, Loot.LIFESTEAL_TAG, "%");
        special(lines, "attack", "fire", items, Loot.FIRE_TAG, "s");
        special(lines, "attack", "arrow_damage", items, Loot.ARROW_DAMAGE_TAG, "%");

        lines.add(new Line("defense", "health", player.getMaxHealth(), 0, ""));
        lines.add(new Line("defense", "armor", (float) player.getAttributeValue(Attributes.ARMOR), 0, ""));
        lines.add(new Line("defense", "toughness", (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0, ""));
        special(lines, "defense", "thorns", items, Loot.THORNS_TAG, "%");

        lines.add(new Line("other", "mana", Mana.regenPerSecond(player, items), 1, ""));
        lines.add(new Line("other", "cooldowns", (1 - Skills.cooldownScale(player)) * 100, 0, "%"));
        // Walking speed is 0.1 without any bonuses
        lines.add(new Line("other", "speed", (float) (player.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1 * 100), 0, "%"));
        float luck = (float) player.getAttributeValue(Attributes.LUCK);
        if (luck > 0) {
            lines.add(new Line("other", "luck", luck, 0, ""));
        }
        return lines;
    }

    // The special bonuses only show up once you have some of them
    private static void special(List<Line> lines, String group, String name, List<ItemStack> items, String tag, String unit) {
        int total = PlayerHits.total(items, tag);
        if (total > 0) {
            lines.add(new Line(group, name, total, 0, unit));
        }
    }

    // The weapon slot item plus everything worn in the right place, the same items PlayerHits counts in a fight
    private static List<ItemStack> worn(ServerPlayer player, ItemStack weapon) {
        List<ItemStack> items = new ArrayList<>();
        items.add(weapon);
        if (ItemKind.of(player.getOffhandItem()) == ItemKind.SHIELD) {
            items.add(player.getOffhandItem());
        }
        for (ItemStack armor : player.getArmorSlots()) {
            if (ItemKind.of(armor) == ItemKind.ARMOR) {
                items.add(armor);
            }
        }
        items.addAll(Jewelry.worn(player));
        return items;
    }

    // The attribute as it would be with the weapon in your hand, worked out on a copy so the player isn't changed
    private static double withWeapon(ServerPlayer player, Holder<Attribute> attribute, ItemStack weapon) {
        AttributeInstance current = player.getAttribute(attribute);
        if (current == null) {
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held == weapon) {
            return current.getValue();
        }
        AttributeInstance copy = new AttributeInstance(attribute, instance -> {
        });
        copy.replaceFrom(current);
        held.forEachModifier(EquipmentSlot.MAINHAND, (heldAttribute, modifier) -> {
            if (heldAttribute.equals(attribute)) {
                copy.removeModifier(modifier.id());
            }
        });
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (weaponAttribute, modifier) -> {
            if (weaponAttribute.equals(attribute)) {
                copy.addOrUpdateTransientModifier(modifier);
            }
        });
        return copy.getValue();
    }
}
