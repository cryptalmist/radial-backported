package dev.velolib.radial.ui.screen.iconpicker.tabs;

import dev.velolib.radial.RadialClient;
import dev.velolib.radial.ui.screen.iconpicker.GridIconTab;
import dev.velolib.radial.ui.screen.iconpicker.IconSearch;
import dev.velolib.radial.util.EncoderUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

public class ItemIconTab extends GridIconTab<ItemIconTab.ItemSearchEntry> {

    private static List<ItemSearchEntry> ITEM_INDEX;

    // The registries the index was built against; a new world or server means different variants
    private static RegistryWrapper.WrapperLookup indexedRegistries;

    private String lastQuery = "";
    private List<ItemSearchEntry> lastResults = new ArrayList<>();

    public ItemIconTab(String currentId, Consumer<String> onSelect, Runnable onClose) {
        super(currentId, onSelect, onClose);
        ensureItemIndex();
    }

    @Override
    public Text getTitle() {
        return Text.translatable("screen.radial.editor.icon_picker.items");
    }

    @Override
    public boolean accepts(String iconId) {
        return !iconId.startsWith("radial:");
    }

    @Override
    protected List<ItemSearchEntry> search(String query) {
        if (query.isEmpty()) {
            lastQuery = "";
            lastResults = ITEM_INDEX;
            return ITEM_INDEX;
        }

        List<ItemSearchEntry> source = !lastQuery.isEmpty() && query.startsWith(lastQuery) ? lastResults : ITEM_INDEX;

        List<ItemSearchEntry> results = new ArrayList<>();

        for (ItemSearchEntry entry : source) {
            if (entry.searchText().contains(query)) {
                results.add(entry);
            }
        }

        // Narrowing keeps index order; ranking happens on a separate copy so ties stay in index order
        lastQuery = query;
        lastResults = results;

        return IconSearch.rank(results, query, ItemSearchEntry::lowerName, ItemSearchEntry::searchText);
    }

    @Override
    protected void renderIcon(
            DrawContext graphics, int x, int y, int mouseX, int mouseY, ItemSearchEntry item, boolean hovered) {

        graphics.drawItem(item.stack(), x + 2, y + 2);

        if (hovered) {
            MinecraftClient client = MinecraftClient.getInstance();
            graphics.drawTooltip(
                    client.textRenderer,
                    Screen.getTooltipFromItem(client, item.stack()),
                    item.stack().getTooltipData(),
                    mouseX,
                    mouseY);
        }
    }

    @Override
    protected String getIconId(ItemSearchEntry item) {
        return item.id();
    }

    @Override
    protected Text getItemNarration(ItemSearchEntry item) {
        return item.stack().getName();
    }

    private static void ensureItemIndex() {
        MinecraftClient client = MinecraftClient.getInstance();
        RegistryWrapper.WrapperLookup registries = client.world != null ? client.world.getRegistryManager() : null;

        if (ITEM_INDEX != null && indexedRegistries == registries) return;

        Map<String, ItemSearchEntry> index = new LinkedHashMap<>();

        // Creative search tab contents include variants such as potions, enchanted books and goat horns
        if (registries != null && client.player != null) {
            for (ItemStack stack : getCreativeSearchItems(client, registries)) {
                addEntry(index, stack, encodeId(stack, registries));
            }
        }

        // Anything the creative tabs leave out (air, operator items without permission, unlisted mod items)
        for (Item item : Registries.ITEM) {
            String id = Registries.ITEM.getId(item).toString();
            if (!index.containsKey(id)) {
                addEntry(index, item.getDefaultStack(), id);
            }
        }

        ITEM_INDEX = List.copyOf(index.values());
        indexedRegistries = registries;
    }

    private static List<ItemStack> getCreativeSearchItems(
            MinecraftClient client, RegistryWrapper.WrapperLookup registries) {
        try {
            boolean hasPermissions = client.player.isCreativeLevelTwoOp()
                    && client.options.getOperatorItemsTab().getValue();

            net.minecraft.item.ItemGroups.updateDisplayContext(
                    client.player.networkHandler.getEnabledFeatures(), hasPermissions, registries);

            return List.copyOf(net.minecraft.item.ItemGroups.getSearchGroup().getDisplayStacks());
        } catch (Exception e) {
            RadialClient.LOGGER.warn("Failed to read creative tab items, listing default items only", e);
            return List.of();
        }
    }

    private static String encodeId(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        try {
            return EncoderUtils.toGiveCommandString(stack, registries);
        } catch (Exception e) {
            return Registries.ITEM.getId(stack.getItem()).toString();
        }
    }

    private static void addEntry(Map<String, ItemSearchEntry> index, ItemStack stack, String id) {
        String lowerName = stack.getName().getString().toLowerCase();
        index.putIfAbsent(id, new ItemSearchEntry(stack, id, lowerName, (id + " " + lowerName).toLowerCase()));
    }

    public record ItemSearchEntry(ItemStack stack, String id, String lowerName, String searchText) {}
}
