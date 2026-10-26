package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.morkeriget.Morkeriget;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.progress.Progression;
import com.morkeriget.smithy.Durability;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// The bonuses for wearing two or four pieces of a set. Stat bonuses are put on the player once a second,
// the rest is checked where it is used (PlayerHits, Mana and Skills) with has(). Server only
public final class SetBonuses {
    private static final String PIECES_TAG = "morkeriget_set_pieces_";

    private SetBonuses() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SetBonuses::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SetBonuses::onDamagePre);
        NeoForge.EVENT_BUS.addListener(SetBonuses::registerCommands);
    }

    // "/morkeriget set guardian" gives all four pieces of a set at your level, for testing. Needs cheats
    private static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").requires(source -> source.hasPermission(2));
        for (ItemSet itemSet : ItemSet.values()) {
            set.then(Commands.literal(itemSet.name().toLowerCase()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                int level = Progression.level(player);
                for (ItemSet.Piece piece : itemSet.pieces) {
                    player.getInventory().placeItemBackInInventory(Loot.createSet(player.getRandom(), level, itemSet, piece));
                }
                context.getSource().sendSuccess(() -> Component.literal("Du fik hele " + itemSet.displayName + " sæt"), false);
                return 1;
            }));
        }
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(set));

        // "/morkeriget loot legendary 50 minecraft:netherite_sword" gives a piece of loot of that rarity, level and item.
        // Without the item it is a random one, like a monster would drop
        LiteralArgumentBuilder<CommandSourceStack> loot = Commands.literal("loot").requires(source -> source.hasPermission(2));
        for (Rarity rarity : List.of(Rarity.COMMON, Rarity.MAGIC, Rarity.RARE, Rarity.LEGENDARY)) {
            loot.then(Commands.literal(rarity.name().toLowerCase())
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, Progression.MAX_LEVEL + 2))
                            .executes(context -> giveLoot(context.getSource(), rarity, IntegerArgumentType.getInteger(context, "level"), null))
                            .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                    .executes(context -> giveLoot(context.getSource(), rarity, IntegerArgumentType.getInteger(context, "level"),
                                            ItemArgument.getItem(context, "item").getItem())))));
        }
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(loot));

        // "/morkeriget legendary maelstrom" gives a legendary with that power at your level.
        // Without a power it lists them, since the command uses the English names
        LiteralArgumentBuilder<CommandSourceStack> legendary = Commands.literal("legendary").requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Skriv /morkeriget legendary og en af disse:").withStyle(ChatFormatting.GOLD), false);
                    for (LegendaryPower power : LegendaryPower.values()) {
                        context.getSource().sendSuccess(() -> Component.literal(power.name().toLowerCase()).withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(" - " + power.displayName + ": " + power.description).withStyle(ChatFormatting.GRAY)), false);
                    }
                    return 1;
                });
        for (LegendaryPower power : LegendaryPower.values()) {
            legendary.then(Commands.literal(power.name().toLowerCase()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ItemStack stack = Loot.createLegendary(player.getRandom(), Progression.level(player), power);
                player.getInventory().placeItemBackInInventory(stack);
                context.getSource().sendSuccess(() -> Component.literal("Du fik ").append(stack.getHoverName()), false);
                return 1;
            }));
        }
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(legendary));

        // "/morkeriget mythic doombringer" gives that mythic item at your level. Without a name it lists them
        LiteralArgumentBuilder<CommandSourceStack> mythic = Commands.literal("mythic").requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Skriv /morkeriget mythic og en af disse:").withStyle(ChatFormatting.LIGHT_PURPLE), false);
                    for (MythicItem item : MythicItem.values()) {
                        context.getSource().sendSuccess(() -> Component.literal(item.name().toLowerCase()).withStyle(ChatFormatting.LIGHT_PURPLE)
                                .append(Component.literal(" - " + item.displayName + ": " + item.description).withStyle(ChatFormatting.GRAY)), false);
                    }
                    return 1;
                });
        for (MythicItem item : MythicItem.values()) {
            mythic.then(Commands.literal(item.name().toLowerCase()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ItemStack stack = Loot.createMythic(player.getRandom(), Progression.level(player), item);
                player.getInventory().placeItemBackInInventory(stack);
                context.getSource().sendSuccess(() -> Component.literal("Du fik ").append(stack.getHoverName()), false);
                return 1;
            }));
        }
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(mythic));
    }

    private static int giveLoot(CommandSourceStack source, Rarity rarity, int level, Item item) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = item != null ? Loot.create(player.getRandom(), rarity, level, item) : Loot.create(player.getRandom(), rarity, level);
        player.getInventory().placeItemBackInInventory(stack);
        source.sendSuccess(() -> Component.literal("Du fik ").append(stack.getHoverName()), false);
        return 1;
    }

    // Whether the player wears at least this many different pieces of the set
    public static boolean has(Player player, ItemSet set, int pieces) {
        return pieces(player, set) >= pieces;
    }

    // How many different pieces of the set the player wears in the right place. Two rings from the same set count once
    private static int pieces(Player player, ItemSet set) {
        Set<ItemSet.Piece> worn = EnumSet.noneOf(ItemSet.Piece.class);
        for (ItemStack stack : wornItems(player)) {
            if (ItemSet.of(stack) == set && ItemSet.piece(stack) != null) {
                worn.add(ItemSet.piece(stack));
            }
        }
        return worn.size();
    }

    // Armour on the body, a weapon or staff in the main hand and the jewelry slots. Other things in the hand don't count,
    // and nor do worn-out items, see Durability
    private static List<ItemStack> wornItems(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack armor : player.getArmorSlots()) {
            if (ItemKind.of(armor) == ItemKind.ARMOR) {
                items.add(armor);
            }
        }
        if (ItemKind.of(player.getMainHandItem()) == ItemKind.MELEE) {
            items.add(player.getMainHandItem());
        }
        items.addAll(Jewelry.worn(player));
        items.removeIf(Durability::isBroken);
        return items;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        for (ItemSet set : ItemSet.values()) {
            int pieces = pieces(player, set);
            announce(player, set, pieces);
            boolean two = pieces >= 2;
            boolean four = pieces >= 4;
            switch (set) {
                case GUARDIAN -> {
                    modifier(player, Attributes.ARMOR, set, 2, 4, AttributeModifier.Operation.ADD_VALUE, two);
                    modifier(player, Attributes.MAX_HEALTH, set, 2, 4, AttributeModifier.Operation.ADD_VALUE, two);
                }
                case BLOODHUNTER -> {
                    modifier(player, Attributes.ATTACK_DAMAGE, set, 4, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, four);
                    modifier(player, Attributes.ATTACK_SPEED, set, 4, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, four);
                }
                case STORMCALLER -> {
                    // Mana and cooldowns, see Mana and Skills
                }
            }
        }
        // Max health may have gone down
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    // A message when a bonus turns on or off, so you notice when the next piece makes a difference
    private static void announce(ServerPlayer player, ItemSet set, int pieces) {
        String tag = PIECES_TAG + set.name();
        int before = player.getPersistentData().getInt(tag);
        if (before == pieces) {
            return;
        }
        player.getPersistentData().putInt(tag, pieces);
        int bonusesBefore = bonuses(before);
        int bonusesNow = bonuses(pieces);
        if (bonusesNow > bonusesBefore) {
            String bonus = bonusesNow == 2 ? set.fourPieceBonus : set.twoPieceBonus;
            player.sendSystemMessage(Component.literal(set.displayName + " sæt (" + pieces + " dele): ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(Component.literal(bonus).withStyle(ChatFormatting.GREEN)));
            player.playNotifySound(SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
        } else if (bonusesNow < bonusesBefore) {
            player.displayClientMessage(Component.literal(set.displayName + " sæt: bonus slået fra (" + pieces + " dele)")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        }
    }

    // 0, 1 or 2 bonuses for this many pieces
    private static int bonuses(int pieces) {
        return pieces >= 4 ? 2 : pieces >= 2 ? 1 : 0;
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, ItemSet set, int pieces, double amount,
                                 AttributeModifier.Operation operation, boolean active) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID,
                "set_" + set.name().toLowerCase() + "_" + pieces + "_" + attribute.unwrapKey().map(key -> key.location().getPath()).orElse("stat"));
        if (active) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        } else {
            instance.removeModifier(id);
        }
    }

    // Guardian 4 pieces: 20% less damage
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && has(player, ItemSet.GUARDIAN, 4)) {
            event.setNewDamage(event.getNewDamage() * 0.8f);
        }
    }
}
