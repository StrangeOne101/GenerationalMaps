package com.strangeone101.generationalmaps;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;


import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReflectionUtils {

    public static final String craft = "org.bukkit.craftbukkit." + Bukkit.getServer().getClass().getPackage().getName().substring(23);

    private static boolean setup = false;

    static Field loreField;
    static Field displayNameField;

    static Field playerConnection;

    static Method asNMSCopy;
    static Method getHandle;
    static Method sendPacket;
    static Constructor setSlotPacket;


    static void setup() {
        if (setup) return;
        try {
            Class craftMetaClass = Class.forName(craft + ".inventory.CraftMetaItem");

            loreField = craftMetaClass.getDeclaredField("lore");
            if (!loreField.isAccessible()) loreField.setAccessible(true);
            displayNameField = craftMetaClass.getDeclaredField("displayName");
            if (!displayNameField.isAccessible()) displayNameField.setAccessible(true);

            Class craftStackClass = Class.forName(craft + ".inventory.CraftItemStack");
            Class craftPlayerClass = Class.forName(craft + ".entity.CraftPlayer");

            asNMSCopy = craftStackClass.getDeclaredMethod("asNMSCopy", ItemStack.class);
            getHandle = craftPlayerClass.getDeclaredMethod("getHandle");

            // !!! The classes bellow use the current 1.17 spigot mapping names !!!
            Class nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
            Class nmsSetSlotPacketClass = Class.forName("net.minecraft.network.protocol.game.PacketPlayOutSetSlot");
            setSlotPacket = nmsSetSlotPacketClass.getDeclaredConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE, nmsItemStackClass);

            Class nmsPlayerClass = Class.forName("net.minecraft.server.level.EntityPlayer");
            Class playerConnectionClass = Class.forName("net.minecraft.server.network.PlayerConnection");

            //The way to find playerConnection USED to be simpler since it used to always be named that.
            //BUT THEN THE MAPPINGS CHANGED AAAAAAAAAAAAAAAAAAAAAAAHHHHH
            for (Field field : nmsPlayerClass.getDeclaredFields()) {
                if (field.getType().equals(playerConnectionClass)) {
                    playerConnection = field;
                    break;
                }
            }

            sendPacket = playerConnectionClass.getDeclaredMethod("sendPacket", Class.forName("net.minecraft.network.protocol.Packet"));


            setup = true;
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getTrueLore(ItemStack stack) {
        if (!setup) setup();

        ItemMeta meta = stack.getItemMeta();

        try {
            Object object = loreField.get(meta);
            if (object == null) return new ArrayList<>();
            return (List<String>) object;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<BaseComponent[]> convertList(List<String> lore) {
        return lore.stream().map(string -> ComponentSerializer.parse(string)).collect(Collectors.toList());
    }

    public static void setTrueLore(ItemStack item, BaseComponent... components) {
        if (!setup) setup();
        List<String> trueLore = new ArrayList<>(components.length);
        for (BaseComponent component : components) {
            if (component.toLegacyText().length() == 0) {
                trueLore.add(ComponentSerializer.toString(new TextComponent(""))); //Don't append the white and
                continue;                                                          //non italic prefix
            }
            BaseComponent lineBase = new ComponentBuilder().append("").color(ChatColor.WHITE)
                    .italic(false).getCurrentComponent();
            lineBase.addExtra(component);
            trueLore.add(ComponentSerializer.toString(lineBase));
        }
        try {
            ItemMeta meta = item.getItemMeta();
            loreField.set(meta, trueLore);
            item.setItemMeta(meta);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static void setTrueLore(ItemStack item, List<String> jsonList) {
        if (!setup) setup();
        try {
            ItemMeta meta = item.getItemMeta();
            loreField.set(meta, jsonList);

            item.setItemMeta(meta);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static String getTrueDisplayName(ItemStack stack) {
        if (!setup) setup();

        ItemMeta meta = stack.getItemMeta();

        try {
            Object object = displayNameField.get(meta);
            if (object == null) return null;
            return (String) object;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void setTrueDisplayName(ItemStack stack, BaseComponent component) {
        if (!setup) setup();

        ItemMeta meta = stack.getItemMeta();

        try {
            displayNameField.set(meta, ComponentSerializer.toString(component));
            stack.setItemMeta(meta);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static void sendSlotUpdate(Player player, int windowId, int slot, ItemStack stack) {
        if (!setup) setup();

        try {
            //Create a NMS version of the itemstack
            Object nmsStack = asNMSCopy.invoke(null, stack);
            //Create the packet with the constructor
            Object packet = setSlotPacket.newInstance(windowId, 0, slot, nmsStack);
            //Get the NMS player object from the bukkit player
            Object handlePlayer = getHandle.invoke(player);
            //Get the connection from the NMS player
            Object connection = playerConnection.get(handlePlayer);
            //Let the packet fly!
            sendPacket.invoke(connection, packet);
            player.sendMessage("Sent you the packet");
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.getTargetException().printStackTrace();
        }
    }
}
