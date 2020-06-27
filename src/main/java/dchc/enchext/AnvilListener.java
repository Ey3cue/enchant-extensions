package dchc.enchext;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.HashMap;
import java.util.Map;

public class AnvilListener implements Listener {
    protected EnchantExtensions plugin;

    protected static final Map<Enchantment, MinMax> ALLOWED_EXTENSIONS;
    protected static final Map<Integer, Integer> XP_COST;

    static {
        // Contains the first normally unavailable level up to the maximum
        // TODO Make configurable
        ALLOWED_EXTENSIONS = new HashMap<>();
        ALLOWED_EXTENSIONS.put(Enchantment.DURABILITY, new MinMax(4, 10));
        ALLOWED_EXTENSIONS.put(Enchantment.DIG_SPEED, new MinMax(6, 10));
        ALLOWED_EXTENSIONS.put(Enchantment.PROTECTION_FALL, new MinMax(5, 10));

        XP_COST = new HashMap<>();
        XP_COST.put(4, 5);
        XP_COST.put(5, 10);
        XP_COST.put(6, 15);
        XP_COST.put(7, 20);
        XP_COST.put(8, 30);
        XP_COST.put(9, 40);
        XP_COST.put(10, 50);
    }

    public AnvilListener(EnchantExtensions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        ItemStack lhs = e.getInventory().getItem(0);
        ItemStack rhs = e.getInventory().getItem(1);

        if (lhs == null || rhs == null) {
            return;
        }
        // Don't allow an item enchantment to be merged into a book
        if (rhs.getType() == Material.ENCHANTED_BOOK && lhs.getType() != Material.ENCHANTED_BOOK) {
            return;
        }

        ItemStack result = lhs.clone();
        Map<Enchantment, Integer> rhsEnchants = rhs.getType() == Material.ENCHANTED_BOOK ?
                ((EnchantmentStorageMeta) rhs.getItemMeta()).getStoredEnchants() : rhs.getEnchantments();

        if (lhs.getType() == Material.ENCHANTED_BOOK) {
            // Need to check for combining stored enchantments if both sides are books
            EnchantmentStorageMeta lhsEnchantMeta = (EnchantmentStorageMeta) lhs.getItemMeta();
            Map<Enchantment, Integer> lhsEnchants = lhsEnchantMeta.getStoredEnchants();
            Map.Entry<Enchantment, Integer> selected = findMatchingEnchant(lhsEnchants, rhsEnchants);

            if (selected != null) {
                MinMax allowed = ALLOWED_EXTENSIONS.get(selected.getKey());
                if (allowed != null && selected.getValue() >= allowed.min - 1 && selected.getValue() < allowed.max) {
                    EnchantmentStorageMeta resultEnchantMeta = lhsEnchantMeta.clone();
                    resultEnchantMeta.addStoredEnchant(selected.getKey(), selected.getValue() + 1,
                            true /* ignore level restriction */);
                    result.setItemMeta(resultEnchantMeta);
                    setResult(e, result, selected.getValue() + 1);
                }
            }
        } else {
            // Otherwise the LHS is presumed to be something enchantable (tool/armor/etc)
            Map<Enchantment, Integer> lhsEnchants = lhs.getEnchantments();
            Map.Entry<Enchantment, Integer> selected = findMatchingEnchant(lhsEnchants, rhsEnchants);
            MinMax allowed = (selected == null ? null : ALLOWED_EXTENSIONS.get(selected.getKey()));

            if (selected == null) {
                // Apply first applicable enchantment the book has as is
                for (Map.Entry<Enchantment, Integer> entry : rhsEnchants.entrySet()) {
                    try {
                        // Try to add a level 2 of the given enchantment; level 2 will ensure it is a scalable
                        // enchantment (not silk touch for example)
                        result.addEnchantment(entry.getKey(), 2);
                        // It worked, so the enchantment is applicable
                        selected = entry;
                        allowed = ALLOWED_EXTENSIONS.get(selected.getKey());
                        break;
                    } catch (IllegalArgumentException exception) {
                        // Pass
                    }
                }

                if (selected != null && selected.getValue() >= allowed.min && selected.getValue() <= allowed.max) {
                    result.addUnsafeEnchantment(selected.getKey(), selected.getValue());
                    setResult(e, result, selected.getValue());
                }

            } else if (allowed != null && selected.getValue() >= allowed.min -1 && selected.getValue() < allowed.max) {
                // Two matching enchantments; increment final result
                result.addUnsafeEnchantment(selected.getKey(), selected.getValue() + 1);
                setResult(e, result, selected.getValue() + 1);
            }
        }
    }

    protected Map.Entry<Enchantment, Integer> findMatchingEnchant(Map<Enchantment, Integer> first, Map<Enchantment, Integer> second) {
        for (Map.Entry<Enchantment, Integer> firstEntry : first.entrySet()) {
            for (Map.Entry<Enchantment, Integer> secondEntry : second.entrySet()) {
                if (firstEntry.getKey().equals(secondEntry.getKey()) && firstEntry.getValue().equals(secondEntry.getValue())) {
                    return secondEntry;
                }
            }
        }
        return null;
    }

    protected void setResult(PrepareAnvilEvent e, ItemStack item, int costLevel) {
        int cost = XP_COST.get(costLevel);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            e.getInventory().setRepairCost(cost);
            e.getInventory().setMaximumRepairCost(cost);
            HumanEntity human = e.getView().getPlayer();
            if (human instanceof Player) {
                ((Player) human).updateInventory();
            }
        });
        e.setResult(item);
    }

    private static class MinMax {
        int min;
        int max;

        public MinMax(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }
}
