package dchc.enchext;

import org.bukkit.plugin.java.JavaPlugin;

public class EnchantExtensions extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
    }
}
