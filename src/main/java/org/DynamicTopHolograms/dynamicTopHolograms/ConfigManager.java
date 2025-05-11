package org.DynamicTopHolograms.dynamicTopHolograms;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    private final DynamicTopHolograms plugin;
    private File configFile;
    private File hologramsFile;
    private File messagesFile;
    private FileConfiguration config;
    private FileConfiguration holograms;
    private FileConfiguration messages;

    public ConfigManager(DynamicTopHolograms plugin) {
        this.plugin = plugin;
        setupConfigs();
        ensureDefaultMessages();
    }

    private void setupConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }

        // Configuração principal
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Configuração de hologramas
        hologramsFile = new File(plugin.getDataFolder(), "holograms.yml");
        if (!hologramsFile.exists()) {
            plugin.saveResource("holograms.yml", false);
        }
        holograms = YamlConfiguration.loadConfiguration(hologramsFile);

        // Configuração de mensagens
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getHolograms() {
        return holograms;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar o config.yml: " + e.getMessage());
        }
    }

    public void saveHolograms() {
        try {
            holograms.save(hologramsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar o holograms.yml: " + e.getMessage());
        }
    }

    public void saveMessages() {
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar o messages.yml: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("config.yml recarregado.");
    }


    public void updateLanguageSetting(String language) {
        config.set("language", language);
        saveConfig();
        plugin.getLogger().info("Idioma alterado para: " + language);
    }

    public void saveHologramDatabaseSource(String id, String connectorName, String field, int topN, String title,
            String format, boolean ascending) {
        String path = id;
        holograms.set(path + ".dataSource", "db:" + connectorName + ":" + field);
        holograms.set(path + ".topN", topN);
        holograms.set(path + ".title", title);
        holograms.set(path + ".format", format);
        holograms.set(path + ".ascending", ascending);
        saveHolograms();
    }

    public void reloadHolograms() {
        holograms = YamlConfiguration.loadConfiguration(hologramsFile);
        plugin.getRankingHologramManager().loadHologramsFromConfig();
        plugin.getLogger().info("holograms.yml recarregado.");
    }

    public void saveHologramLocation(String id, Location location) {
        holograms.set(id + ".location.x", location.getX());
        holograms.set(id + ".location.y", location.getY());
        holograms.set(id + ".location.z", location.getZ());
        holograms.set(id + ".location.world", location.getWorld().getName());
        saveHolograms();
    }

    public void reloadMessages() {
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("messages.yml recarregado.");
    }


    public void ensureDefaultMessages() {
        // Verificar se a mensagem para formato de campo inválido existe
        if (!messages.contains("dbset-invalid-field-format")) {
            messages.set("dbset-invalid-field-format",
                    "&cFormato de campo inválido. Use o formato 'tabela.campo', por exemplo: 'power.level'");
            saveMessages();
        }

        // Verificar se a mensagem de uso do comando dbset está correta
        if (messages.contains("dbset-usage")) {
            String usage = messages.getString("dbset-usage");
            if (!usage.contains("tabela.campo")) {
                messages.set("dbset-usage", "&cUso: /dth dbset <id> <conector> <tabela.campo> <topN> [formato]");
                saveMessages();
            }
        } else {
            messages.set("dbset-usage", "&cUso: /dth dbset <id> <conector> <tabela.campo> <topN> [formato]");
            saveMessages();
        }
    }

    public void reloadAll() {
        reloadConfig();
        reloadHolograms();
        reloadMessages();
        ensureDefaultMessages();

        // Recarregar o gerenciador de mensagens
        if (plugin.getMessageManager() != null) {
            plugin.getMessageManager().reload();
        }

        plugin.getLogger().info("Todas as configurações foram recarregadas.");
    }

    public void saveHologramSetting(String id, String placeholder, int topN, String title, String format,
            boolean ascending) {
        String path = id;
        holograms.set(path + ".placeholder", placeholder);
        holograms.set(path + ".topN", topN);
        holograms.set(path + ".title", title);
        holograms.set(path + ".format", format);
        holograms.set(path + ".ascending", ascending);
        saveHolograms();
    }

    public void removeHologramSetting(String id) {
        holograms.set(id, null); // Remove a seção
        saveHolograms();
    }

    public Map<String, Object> getHologramSettings(String hologramID) {
        ConfigurationSection section = holograms.getConfigurationSection(hologramID);
        if (section == null) {
            return null;
        }
        Map<String, Object> settings = new HashMap<>();
        settings.put("placeholder", section.getString("placeholder"));
        settings.put("topN", section.getInt("topN"));
        settings.put("title", section.getString("title"));
        settings.put("format", section.getString("format"));
        settings.put("ascending", section.getBoolean("ascending", false));
        return settings;
    }

    public Set<String> getAllHologramIDs() {
        return holograms.getKeys(false);
    }

    public long getUpdateIntervalMinutes() {
        return config.getLong("update-interval-minutes", 5);
    }

    public boolean isOfflineDataEnabled() {
        return config.getBoolean("enable-offline-data", true);
    }

    public String getMessage(String key) {
        String message = messages.getString(key);
        if (message == null) {
            return "Missing message: " + key;
        }
        return message.replace('&', '§');
    }

    public String getMessage(String key, Map<String, String> replacements) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return message;
    }
}