package com.blanoir.accessory.module.inventory.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public class AccessoryInventoryHolder implements InventoryHolder {
    private final UUID ownerId;
    private final int currentPage;
    private final int totalPages;
    private Inventory inventory;
    public AccessoryInventoryHolder(UUID ownerId,int currentPage,int totalPages){
        this.ownerId = ownerId;
        this.totalPages = Math.max(1,totalPages);
        this.currentPage = Math.max(1,Math.min(this.totalPages,currentPage));
    }
    public UUID getOwnerId(){
        return ownerId;
    }
    public int currentPage(){
        return currentPage;
    }
    public int totalPages(){
        return totalPages;
    }
    public boolean hasPreviousPage(){
        return currentPage > 1;
    }
    public boolean hasNextPage(){
        return currentPage < totalPages;
    }
    public void bindInventory(Inventory inventory){
        this.inventory = inventory;
    }
    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Inventory has not been bound yet");
    }

}