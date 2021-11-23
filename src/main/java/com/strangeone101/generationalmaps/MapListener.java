package com.strangeone101.generationalmaps;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.stream.Collectors;

public class MapListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onCraft(CraftItemEvent event) {
        if (((Keyed)event.getRecipe()).getKey().toString().equals("minecraft:map_cloning")) {
            if (!doCrafting(event.getInventory())) { //Do the crafting thing! Returns false when we should cancel the event instead
                event.setCancelled(true);
                event.getInventory().setResult(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreCraft(PrepareItemCraftEvent event) {
        if (((Keyed)event.getRecipe()).getKey().toString().equals("minecraft:map_cloning")) {
            if (!doCrafting(event.getInventory())) { //Do the crafting thing! Returns false when we should not craft anything
                event.getInventory().setResult(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(InventoryPickupItemEvent event) {
        if (event.getInventory() instanceof PlayerInventory) {
            //When they pickup a map, update the lore if it has no lore
            if (event.getItem().getItemStack().getType() == Material.FILLED_MAP) {
                if (!event.getItem().getItemStack().getItemMeta().hasLore()) {
                    ItemStack stack = event.getItem().getItemStack();
                    updateGenerationLore(stack);
                    event.getItem().setItemStack(stack);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMapFilled(MapInitializeEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                //Loop through all players in the same world when a new map is made
                for (Player player : event.getMap().getWorld().getPlayers()) {
                    ItemStack stack = player.getInventory().getItemInMainHand();

                    if (stack != null && stack.getType() == Material.FILLED_MAP) {
                        MapMeta meta = (MapMeta) stack.getItemMeta();
                        if (event.getMap().getId() == meta.getMapView().getId()) {
                            updateGenerationLore(stack);
                            player.getInventory().setItemInMainHand(stack);
                        }
                    }

                    stack = player.getInventory().getItemInOffHand();

                    if (stack != null && stack.getType() == Material.FILLED_MAP) {
                        MapMeta meta = (MapMeta) stack.getItemMeta();
                        if (event.getMap().getId() == meta.getMapView().getId()) {
                            updateGenerationLore(stack);
                            player.getInventory().setItemInOffHand(stack);
                        }
                    }
                }
            }
        }.runTaskLater(GenerationalMaps.INSTANCE, 1); //Do it all 1 tick later so the item will have changed from map to filled map

    }

    private boolean doCrafting(CraftingInventory inventory) {
        int filledMapSlot = inventory.first(Material.FILLED_MAP);
        ItemStack stack = inventory.getItem(filledMapSlot);

        //Check that we can clone the map
        if (getGeneration(stack) >= 2) {
            return false;
        }
        int currentAmount = stack.getAmount();
        stack.setAmount(64); //We set it to 64 so if they shift click, it won't only craft 1 set at a time
        inventory.setItem(filledMapSlot, stack); //Update the inventory

        int howMany = 0;
        for (ItemStack item : inventory.getMatrix()) {
            if (item.getType() == Material.MAP) howMany++; //Count how many maps we are crafting with to clone
        }

        ItemStack result = stack.clone();
        result.setAmount(howMany); //Set x amount in the output depending on the number if unfilled input maps
        setGeneration(result, getGeneration(result) + 1); //Update the generation NBT on the lore of the output
        updateGenerationLore(result); //Update the lore to show the generation
        inventory.setResult(result); //Update the inventory

        new BukkitRunnable() {
            @Override
            public void run() {
                stack.setAmount(currentAmount); //Restore the input filled map to what it was. This effectively makes it not be consumed
                inventory.setItem(filledMapSlot, stack);
            }
        }.runTaskLater(GenerationalMaps.INSTANCE, 1); //We do this a tick later so it happens AFTER the craft
        return true;
    }

    private int getGeneration(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().getOrDefault(GenerationalMaps.GENERATION, PersistentDataType.BYTE, (byte)0);
    }

    private void setGeneration(ItemStack stack, int generation) {
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(GenerationalMaps.GENERATION, PersistentDataType.BYTE, (byte)generation);
        stack.setItemMeta(meta);
    }

    private void updateGenerationLore(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        int maxLines = meta.getLore().size();

        //Define the translation component to install on the lore
        TranslatableComponent component = new TranslatableComponent("book.generation." + getGeneration(stack));
        component.setColor(ChatColor.GRAY);
        component.setItalic(false);

        //If the item has lore, we will update the existing lore. Even if there is custom lore
        if (meta.hasLore()) {
            //Pull the true lore from the item
            List<BaseComponent[]> lore = ReflectionUtils.convertList(ReflectionUtils.getTrueLore(stack));

            out:
            for (int i = 0; i < maxLines; i++) {
                BaseComponent[] line = lore.get(i);

                //If the components on this line contain the translated book generation line
                for (BaseComponent inlineComponent : line) {
                    if (inlineComponent instanceof TranslatableComponent) {
                        //If this line is the one we are gonna update
                        if (((TranslatableComponent) inlineComponent).getTranslate().startsWith("book.generation.")) {
                            lore.set(i, new BaseComponent[] {component}); //Set the entire line to the new component. Scrap the old one

                            //Convert the components back to JSON
                            List<String> convertedBack = lore.stream().map(array -> ComponentSerializer.toString(array)).collect(Collectors.toList());
                            ReflectionUtils.setTrueLore(stack, convertedBack); //Set the lore on the item
                            break out;
                        }
                    }
                }
            }
        } else {
            ReflectionUtils.setTrueLore(stack, component); //Nice and simple. Set the lore :)
        }
    }


}
