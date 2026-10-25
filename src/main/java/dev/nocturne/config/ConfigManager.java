package dev.nocturne.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nocturne.module.Module;
import dev.nocturne.module.ModuleManager;
import dev.nocturne.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("nocturne.json"); }

    public static void save() {
        Map<String, Map<String, Object>> root = new LinkedHashMap<>();
        for (Module m : ModuleManager.ALL) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("_enabled", m.isEnabled());
            o.put("_key", m.key);
            for (Setting<?> s : m.settings) o.put(s.name, s.save());
            root.put(m.name, o);
        }
        try { Files.writeString(file(), GSON.toJson(root)); } catch (Exception ignored) {}
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            Type t = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
            Map<String, Map<String, Object>> root = GSON.fromJson(r, t);
            if (root == null) return;
            for (Module m : ModuleManager.ALL) {
                Map<String, Object> o = root.get(m.name);
                if (o == null) continue;
                for (Setting<?> s : m.settings)
                    if (o.containsKey(s.name)) { try { s.load(o.get(s.name)); } catch (Exception ignored) {} }
                if (o.get("_key") instanceof Number kn) m.key = kn.intValue();
                if (m.persistEnabled() && o.get("_enabled") instanceof Boolean b) m.setEnabled(b);
            }
        } catch (Exception ignored) {}
    }
}
