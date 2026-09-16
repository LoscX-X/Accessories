package com.blanoir.accessory.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Explicit page count and named layout selection, following ExcellentShop's page/layout model. */
public record PageSettings(int count, boolean paginated, Map<Integer, String> layouts) {
    public static final int DEFAULT_PAGE = 0;
    public static final int MAX_PAGES = 100;

    public PageSettings {
        count = Math.clamp(count, 1, MAX_PAGES);
        layouts = Map.copyOf(layouts);
    }

    public static PageSettings read(ConfigurationSection config, Logger logger) {
        Map<Integer, String> layouts = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("layout.by-page");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int page = Integer.parseInt(key);
                    if (page < 0 || page > MAX_PAGES) throw new NumberFormatException();
                    layouts.put(page, section.getString(key, "default").trim().toLowerCase(Locale.ROOT));
                } catch (NumberFormatException ex) {
                    logger.warning("Invalid layout.by-page entry: " + key + "; expected 0.." + MAX_PAGES);
                }
            }
        }
        return new PageSettings(config.getInt("pages", 1), config.getBoolean("layout.paginated", true), layouts);
    }

    public String defaultLayout() { return layouts.getOrDefault(DEFAULT_PAGE, "default"); }

    public String layoutFor(int page) {
        return paginated ? layouts.getOrDefault(page, defaultLayout()) : defaultLayout();
    }
}
