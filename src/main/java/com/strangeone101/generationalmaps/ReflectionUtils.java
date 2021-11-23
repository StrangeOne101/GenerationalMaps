package com.strangeone101.generationalmaps;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReflectionUtils {

    public static final String craft = "org.bukkit.craftbukkit." + Bukkit.getServer().getClass().getPackage().getName().substring(23);

    private static boolean setup = false;

    static Field loreField;
    static Field displayNameField;

    static Method asNMSCopy;
    static Method getHandle;

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
}
