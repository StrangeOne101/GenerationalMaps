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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
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
            if (!doCrafting(event.getInventory(), event.isShiftClick(), false)) { //Do the crafting thing! Returns false when we should cancel the event instead
                event.setCancelled(true);
                event.getInventory().setResult(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreCraft(PrepareItemCraftEvent event) {
        if ((event.getRecipe() == null && ((Keyed)event.getRecipe()).getKey().toString().equals("minecraft:map_cloning"))) {
            if (!doCrafting(event.getInventory(), false, true)) { //Do the crafting thing! Returns false when we should not craft anything
                event.getInventory().setResult(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent event) {
        //When they pickup a map, update the lore if it has no lore
        if (event.getItem().getItemStack().getType() == Material.FILLED_MAP) {
            if (!event.getItem().getItemStack().getItemMeta().hasLore()) {
                ItemStack stack = event.getItem().getItemStack();
                updateGenerationLore(stack);
                event.getItem().setItemStack(stack);
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
                    updatePlayerSlot(player, EquipmentSlot.HAND, event.getMap().getId());
                    updatePlayerSlot(player, EquipmentSlot.OFF_HAND, event.getMap().getId());
                }
            }
        }.runTaskLater(GenerationalMaps.INSTANCE, 1); //Do it all 1 tick later so the item will have changed from map to filled map

    }

    private void updatePlayerSlot(Player player, EquipmentSlot slot, int mapId) {
        ItemStack stack = player.getInventory().getItem(slot);

        if (stack == null) return;
        if (stack.getType() == Material.FILLED_MAP) {
            MapMeta meta = (MapMeta) stack.getItemMeta();
            if (mapId == meta.getMapView().getId()) {
                setGeneration(stack, 0);
                updateGenerationLore(stack);
                player.getInventory().setItem(slot, stack);
                return;
            }
        } else if (stack.getType() == Material.MAP) {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack newStack = player.getInventory().getItem(i);
                if (newStack != null && newStack.getType() == Material.FILLED_MAP) {
                    MapMeta meta = (MapMeta) newStack.getItemMeta();
                    if (mapId == meta.getMapView().getId()) {
                        setGeneration(stack, 0);
                        updateGenerationLore(newStack);
                        player.getInventory().setItem(i, newStack);
                        return;
                    }
                }
            }
        }
    }

    //TODO Change CraftingInventory to Inventory. That way, the CartographyInventory can be put in too
    //TODO Change the slot offset to only be offset for CraftingInventory and not CartographyInventory
    //TODO CraftingInventory Slots 0-8 are crafting, 9 is output
    //TODO CartographyInventory Slot 0 is map, 1, is extra, 2 is output
    private boolean doCrafting(CraftingInventory inventory, boolean sneak, boolean prepare) {
        int filledMapSlot = -1;
        ItemStack stack = null;
        for (int i = 0; i < inventory.getMatrix().length; i++) {
            ItemStack is = inventory.getMatrix()[i];
            if (is != null && is.getType() == Material.FILLED_MAP) {
                stack = is;
                filledMapSlot = i;
                break;
            }
        }

        //If there is no filled map, something went very wrong
        if (stack == null) return false;

        //Check that we can clone the map
        if (getGeneration(stack) >= 2) {
            return false;
        }

        int currentAmount = stack.getAmount();
        if (sneak) {
            stack.setAmount(64); //We set it to 64 so if they shift click, it won't only craft 1 set at a time
            inventory.setItem(filledMapSlot, stack); //Update the inventory
        }

        int howMany = 0;
        for (ItemStack item : inventory.getMatrix()) {
            if (item != null && item.getType() == Material.MAP) howMany++; //Count how many maps we are crafting with to clone
        }

        ItemStack result = stack.clone();
        result.setAmount(howMany); //Set x amount in the output depending on the number if unfilled input maps
        setGeneration(result, getGeneration(result) + 1); //Update the generation NBT on the lore of the output
        updateGenerationLore(result); //Update the lore to show the generation
        inventory.setResult(result); //Update the inventory


        ItemStack finalStack = stack;
        int finalFilledMapSlot = filledMapSlot;

        if (!prepare) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    finalStack.setAmount(currentAmount); //Restore the input filled map to what it was. This effectively makes it not be consumed
                    inventory.setItem(finalFilledMapSlot + 1, finalStack); //Offset by 1 as slot 0 is the output
                }
            }.runTaskLater(GenerationalMaps.INSTANCE, 1); //We do this a tick later so it happens AFTER the craft

        }


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
        int maxLines = meta.hasLore() ? meta.getLore().size() : 0;

        //Define the translation component to install on the lore
        TranslatableComponent component = new TranslatableComponent("book.generation." + getGeneration(stack));
        component.setColor(ChatColor.GRAY);
        component.setItalic(false);

        //If the item has lore, we will update the existing lore. Even if there is custom lore
        if (meta.hasLore()) {
            //Pull the true lore from the item
            List<BaseComponent[]> lore = ReflectionUtils.convertList(ReflectionUtils.getTrueLore(stack));
            System.out.println("Test0");
            out:
            for (int i = 0; i < maxLines; i++) {
                BaseComponent[] line = lore.get(i);
                System.out.println("Test1");
                //If the components on this line contain the translated book generation line
                for (BaseComponent inlineComponent : line) {
                    //Due to the fact that there is almost an infinite amount of components in the "extra" tag, we need recursion
                    TranslatableComponent tcomp = loreLoop(inlineComponent);
                    if (tcomp != null) { //It'll be null if it didn't find the component
                        lore.set(i, new BaseComponent[]{component}); //Set the entire line to the new component. Scrap the old one

                        //Convert the components back to JSON
                        List<String> convertedBack = lore.stream().map(array -> ComponentSerializer.toString(array)).collect(Collectors.toList());
                        ReflectionUtils.setTrueLore(stack, convertedBack); //Set the lore on the item
                        break out;
                    }
                }
            }
        } else {
            ReflectionUtils.setTrueLore(stack, component); //Nice and simple. Set the lore :)
        }
    }

    private TranslatableComponent loreLoop(BaseComponent component) {
        if (component instanceof TranslatableComponent) {
            System.out.println("Test3");
            //If this line is the one we are gonna update
            if (((TranslatableComponent) component).getTranslate().startsWith("book.generation.")) {
                return (TranslatableComponent) component;
            }
        }
        if (component.getExtra().size() > 0) {
            for (BaseComponent comp : component.getExtra()) {
                TranslatableComponent tcomp = loreLoop(comp);
                if (tcomp != null) return tcomp;
            }
        }
        return null;
    }


}
