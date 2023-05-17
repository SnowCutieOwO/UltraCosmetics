package be.isach.ultracosmetics.menu.buttons;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.UltraCosmeticsData;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.type.CosmeticType;
import be.isach.ultracosmetics.cosmetics.type.GadgetType;
import be.isach.ultracosmetics.menu.Button;
import be.isach.ultracosmetics.menu.ClickData;
import be.isach.ultracosmetics.menu.PurchaseData;
import be.isach.ultracosmetics.menu.menus.MenuPurchase;
import be.isach.ultracosmetics.permissions.PermissionManager;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.util.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public abstract class CosmeticButton implements Button {
    protected final UltraCosmetics ultraCosmetics;
    protected final PermissionManager pm;
    protected final CosmeticType<?> cosmeticType;
    private final int price;
    private final boolean ignoreTooltip;
    private final boolean allowPurchase = SettingsManager.getConfig().getBoolean("No-Permission.Allow-Purchase");
    private final String noPermissionMessage = MessageManager.getMessage("No-Permission");
    private final String clickToPurchaseLore;
    private final String itemName;
    private ItemStack stack = null;

    public static CosmeticButton fromType(CosmeticType<?> cosmeticType, UltraPlayer ultraPlayer, UltraCosmetics ultraCosmetics) {
        if (SettingsManager.getConfig().getBoolean("No-Permission.Custom-Item.enabled")
                && !ultraCosmetics.getPermissionManager().hasPermission(ultraPlayer, cosmeticType)) {
            return new CosmeticNoPermissionButton(ultraCosmetics, cosmeticType);
        }
        if (cosmeticType instanceof GadgetType) {
            return new ToggleGadgetCosmeticButton(ultraCosmetics, (GadgetType) cosmeticType);
        }
        return new ToggleCosmeticButton(ultraCosmetics, cosmeticType);
    }

    public CosmeticButton(UltraCosmetics ultraCosmetics, CosmeticType<?> cosmeticType, boolean ignoreTooltip) {
        this.ultraCosmetics = ultraCosmetics;
        pm = ultraCosmetics.getPermissionManager();
        this.cosmeticType = cosmeticType;
        this.price = SettingsManager.getConfig().getInt(cosmeticType.getConfigPath() + ".Purchase-Price");
        this.ignoreTooltip = ignoreTooltip;
        String itemName = MessageManager.getMessage("Buy-Cosmetic-Description");
        itemName = itemName.replace("%price%", String.valueOf(price));
        itemName = itemName.replace("%gadgetname%", cosmeticType.getName());
        this.itemName = itemName;
        this.clickToPurchaseLore = MessageManager.getMessage("Click-To-Purchase").replace("%price%", String.valueOf(price));
    }

    @Override
    public void onClick(ClickData clickData) {
        boolean success = handleClick(clickData);
        if (success && UltraCosmeticsData.get().shouldCloseAfterSelect()) {
            clickData.getClicker().getBukkitPlayer().closeInventory();
        }
    }

    protected ItemStack generateDisplayItem(UltraPlayer ultraPlayer) {
        ItemStack stack = getBaseItem(ultraPlayer);
        addPurchaseLore(stack, ultraPlayer);
        return stack;
    }

    @Override
    public ItemStack getDisplayItem(UltraPlayer ultraPlayer) {
        if (stack == null) {
            stack = generateDisplayItem(ultraPlayer);
        }
        return stack;
    }

    protected abstract ItemStack getBaseItem(UltraPlayer ultraPlayer);

    /**
     * Handles clicking on cosmetics in the GUI
     *
     * @param data The ClickData from the event
     * @return true if closing the inventory now is OK
     */
    protected boolean handleClick(ClickData data) {
        UltraPlayer ultraPlayer = data.getClicker();
        ItemStack clicked = data.getClicked();
        if (data.getClick().isRightClick()) {
            if (pm.hasPermission(ultraPlayer, cosmeticType)) {
                handleRightClick(data);
                return false;
            }
        }

        if (ignoreTooltip || startsWithColorless(clicked.getItemMeta().getDisplayName(), cosmeticType.getCategory().getActivateTooltip())) {
            if (pm.hasPermission(ultraPlayer, cosmeticType)) {
                cosmeticType.equip(ultraPlayer, ultraCosmetics);
                if (ultraPlayer.hasCosmetic(cosmeticType.getCategory())) {
                    return handleActivate(data);
                }
                return true;
            }
            if (!allowPurchase || price <= 0) {
                ultraPlayer.sendMessage(noPermissionMessage);
                return true;
            }

            ItemStack display = ItemFactory.rename(cosmeticType.getItemStack(), itemName);
            PurchaseData pd = new PurchaseData();
            pd.setPrice(price);
            pd.setShowcaseItem(display);
            pd.setOnPurchase(() -> {
                pm.setPermission(ultraPlayer, cosmeticType);
                // Delay by five ticks so the command processes
                // TODO: Remove this?
                Bukkit.getScheduler().runTaskLater(ultraCosmetics, () -> {
                    cosmeticType.equip(ultraPlayer, ultraCosmetics);
                    data.getMenu().refresh(ultraPlayer);
                }, 5);
            });
            pd.setOnCancel(() -> data.getMenu().refresh(ultraPlayer));
            MenuPurchase mp = new MenuPurchase(ultraCosmetics, "Purchase " + cosmeticType.getName(), pd);
            ultraPlayer.getBukkitPlayer().openInventory(mp.getInventory(ultraPlayer));
            return false; // We just opened another inventory, don't close it
        } else if (startsWithColorless(clicked.getItemMeta().getDisplayName(), cosmeticType.getCategory().getDeactivateTooltip())) {
            ultraPlayer.removeCosmetic(cosmeticType.getCategory());
            if (!UltraCosmeticsData.get().shouldCloseAfterSelect()) {
                data.getMenu().refresh(ultraPlayer);
            }
        }
        return true;
    }

    private void addPurchaseLore(ItemStack stack, UltraPlayer player) {
        if (price > 0 && !pm.hasPermission(player, cosmeticType) && allowPurchase) {
            ItemMeta meta = stack.getItemMeta();
            List<String> lore = meta.getLore();
            lore.add("");
            lore.add(clickToPurchaseLore);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
    }

    protected boolean handleActivate(ClickData clickData) {
        if (!UltraCosmeticsData.get().shouldCloseAfterSelect()) {
            clickData.getMenu().refresh(clickData.getClicker());
        }
        return true;
    }

    protected void handleRightClick(ClickData clickData) {
    }

    protected boolean startsWithColorless(String a, String b) {
        return ChatColor.stripColor(a).startsWith(ChatColor.stripColor(b));
    }
}