package com.strangeone101.generationalmaps;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public final class GenerationalMaps extends JavaPlugin {

    public static NamespacedKey GENERATION;

    public static Plugin INSTANCE;

    @Override
    public void onEnable() {
        INSTANCE = this;
        GENERATION = new NamespacedKey(this, "generation");

        Bukkit.getPluginManager().registerEvents(new MapListener(), this);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
