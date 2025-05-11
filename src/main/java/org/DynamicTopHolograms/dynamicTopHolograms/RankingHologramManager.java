package org.DynamicTopHolograms.dynamicTopHolograms;

import org.DynamicTopHolograms.dynamicTopHolograms.database.DatabaseConnectorManager;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.bukkit.ChatColor.*;

public class RankingHologramManager {
    private final DatabaseConnectorManager databaseConnectorManager;

    private final DynamicTopHolograms plugin;
    private final ConfigManager configManager;
    private final OfflineDataManager offlineDataManager;
    // Mapeia hologramID para suas configurações. Poderia ser uma classe dedicada
    // HologramConfig.
    // Mas fiquei com preguiça de fazer -- NetoTTT
    private final Map<String, Map<String, Object>> activeHologramConfigs = new ConcurrentHashMap<>();

    // Construtor
    public RankingHologramManager(DynamicTopHolograms plugin, ConfigManager configManager,
            OfflineDataManager offlineDataManager, DatabaseConnectorManager databaseConnectorManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.offlineDataManager = offlineDataManager;
        this.databaseConnectorManager = databaseConnectorManager;
    }

    public void loadHologramsFromConfig() {
        activeHologramConfigs.clear();
        Set<String> hologramIDs = configManager.getAllHologramIDs();

        for (String id : hologramIDs) {
            Map<String, Object> config = new HashMap<>();

            // Verificar tipo de fonte de dados
            String dataSource = configManager.getHolograms().getString(id + ".dataSource", "papi");
            config.put("dataSource", dataSource);

            if ("papi".equals(dataSource)) {
                // Configuração para PlaceholderAPI
                config.put("placeholder", configManager.getHolograms().getString(id + ".placeholder"));
            } else if (dataSource.startsWith("db:")) {
                // Configuração já está no dataSource
            }

            config.put("topN", configManager.getHolograms().getInt(id + ".topN"));
            config.put("title", configManager.getHolograms().getString(id + ".title", "&6&lTop {placeholder_name}"));
            config.put("format",
                    configManager.getHolograms().getString(id + ".format", "&e{rank}. &f{player} &7- &a{value}"));
            config.put("ascending", configManager.getHolograms().getBoolean(id + ".ascending", false));

            // Verificar se precisamos recriar o holograma físico
            if (DHAPI.getHologram(id) == null) {
                // Precisamos das coordenadas do holograma para recriá-lo
                if (configManager.getHolograms().contains(id + ".location")) {
                    double x = configManager.getHolograms().getDouble(id + ".location.x");
                    double y = configManager.getHolograms().getDouble(id + ".location.y");
                    double z = configManager.getHolograms().getDouble(id + ".location.z");
                    String worldName = configManager.getHolograms().getString(id + ".location.world");

                    if (worldName != null) {
                        org.bukkit.World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            Location location = new Location(world, x, y, z);
                            List<String> initialLines = new ArrayList<>();
                            initialLines.add(translateAlternateColorCodes('&', (String) config.get("title")));
                            initialLines.add(translateAlternateColorCodes('&', "&7Carregando..."));

                            DHAPI.createHologram(id, location, initialLines);
                            plugin.getLogger().info("Recriado holograma físico: " + id);
                        } else {
                            plugin.getLogger().warning("Não foi possível recriar o holograma " + id + ": mundo "
                                    + worldName + " não encontrado");
                        }
                    }
                } else {
                    plugin.getLogger().warning(
                            "Não foi possível recriar o holograma " + id + ": informações de localização ausentes");
                }
            }

            activeHologramConfigs.put(id, config);
            plugin.getLogger().info("Carregado holograma de ranking: " + id);
        }
    }

    public boolean createHologram(String id, Location location, String title) {
        if (DHAPI.getHologram(id) != null) {
            return false; // Já existe
        }
        List<String> initialLines = new ArrayList<>();
        initialLines.add(translateAlternateColorCodes('&', title));
        initialLines.add(translateAlternateColorCodes('&', "&7Aguardando dados..."));

        Hologram hologram = DHAPI.createHologram(id, location, initialLines);

        if (hologram != null) {
            // Adicionar configuração inicial para o holograma
            // Gambiarra mal feita pra salvar o Holograma sem configuração -- NetoTTT
            Map<String, Object> config = new HashMap<>();
            config.put("placeholder", "%player_name%"); // Placeholder padrão temporário
            config.put("topN", 10); // Valor padrão temporário
            config.put("title", title);
            config.put("format", "&e{rank}. &f{player} &7- &a{value}");
            config.put("ascending", false);

            // Registrar no mapa de configurações ativas
            activeHologramConfigs.put(id, config);

            // Salvar na configuração (incluindo localização)
            configManager.saveHologramSetting(id, (String) config.get("placeholder"),
                    (int) config.get("topN"),
                    (String) config.get("title"),
                    (String) config.get("format"),
                    (boolean) config.get("ascending"));

            // Salvar a localização do holograma
            configManager.saveHologramLocation(id, location);

            plugin.getLogger().info("Criado e registrado holograma '" + id + "' com configuração inicial.");
            return true;
        }
        return false;
    }

    /**
     * Move um holograma para uma nova localização
     * 
     * @param id       ID do holograma
     * @param location Nova localização
     * @return true se movido com sucesso, false se o holograma não existir
     */
    public boolean moveHologram(String id, Location location) {
        Hologram hologram = DHAPI.getHologram(id);
        if (hologram == null || !activeHologramConfigs.containsKey(id)) {
            return false;
        }

        // Mover o holograma usando a API DecentHolograms
        DHAPI.moveHologram(hologram, location);

        // Salvar a nova localização na configuração
        configManager.saveHologramLocation(id, location);

        return true;
    }

    public void setHologramConfig(String id, String placeholder, int topN, String title, String format,
            boolean ascending) {
        Map<String, Object> config = new HashMap<>();
        config.put("placeholder", placeholder);
        config.put("topN", topN);
        config.put("title", title);
        config.put("format", format);
        config.put("ascending", ascending);
        activeHologramConfigs.put(id, config);
        configManager.saveHologramSetting(id, placeholder, topN, title, format, ascending);
        plugin.getLogger().info("Configurado holograma '" + id + "' para PAPI: " + placeholder + ", Top: " + topN);
        forceUpdateHologram(id); // Atualiza imediatamente após setar
    }

    public void setHologramTitle(String id, String title) {
        Map<String, Object> config = activeHologramConfigs.get(id);
        if (config != null) {
            config.put("title", title);
            String placeholder = (String) config.get("placeholder");
            int topN = (int) config.get("topN");
            String format = (String) config.get("format");
            boolean ascending = config.containsKey("ascending") ? (boolean) config.get("ascending") : false;
            configManager.saveHologramSetting(id, placeholder, topN, title, format, ascending);
            forceUpdateHologram(id);
        }
    }

    public void setHologramOrder(String id, boolean ascending) {
        Map<String, Object> config = activeHologramConfigs.get(id);
        if (config != null) {
            config.put("ascending", ascending);
            String placeholder = (String) config.get("placeholder");
            int topN = (int) config.get("topN");
            String title = (String) config.get("title");
            String format = (String) config.get("format");
            configManager.saveHologramSetting(id, placeholder, topN, title, format, ascending);
            forceUpdateHologram(id);
        }
    }

    public void removeHologram(String id, boolean deleteFromDH) {
        activeHologramConfigs.remove(id);
        configManager.removeHologramSetting(id);
        if (deleteFromDH) {
            Hologram hologram = DHAPI.getHologram(id);
            if (hologram != null) {
                DHAPI.removeHologram(id);
            }
        }
        plugin.getLogger().info("Removido holograma de ranking: " + id);
    }

    public void updateAllHolograms() {
        plugin.getLogger().info("Iniciando atualização de todos os hologramas de ranking...");
        for (String id : activeHologramConfigs.keySet()) {
            updateHologram(id);
        }
        plugin.getLogger().info("Atualização de hologramas concluída.");
    }

    public void forceUpdateHologram(String id) {
        if (activeHologramConfigs.containsKey(id)) {
            plugin.getLogger().info("Forçando atualização para o holograma: " + id);
            // Executar em uma nova thread para não bloquear quem chamou (ex: comando)
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> updateHologram(id));
        } else {
            plugin.getLogger().warning("Tentativa de forçar atualização para holograma desconhecido: " + id);
        }
    }

    void updateHologram(String hologramID) {
        Map<String, Object> config = activeHologramConfigs.get(hologramID);
        if (config == null) {
            plugin.getLogger()
                    .warning("Configuração não encontrada para o holograma: " + hologramID + " durante a atualização.");
            return;
        }

        Hologram hologram = DHAPI.getHologram(hologramID);
        if (hologram == null) {
            plugin.getLogger().warning("Holograma DecentHolograms '" + hologramID
                    + "' não encontrado. Removendo da lista de atualização.");
            activeHologramConfigs.remove(hologramID); // Auto-limpeza
            configManager.removeHologramSetting(hologramID);
            return;
        }

        String dataSource = (String) config.getOrDefault("dataSource", "papi");
        int topN = (int) config.get("topN");
        String titleTemplate = (String) config.get("title");
        String lineFormat = (String) config.get("format");
        boolean ascending = config.containsKey("ascending") ? (boolean) config.get("ascending") : false;

        List<PlayerDataEntry> playerData = new ArrayList<>();

        // Obter dados com base na fonte de dados configurada
        if ("papi".equals(dataSource)) {
            // Código existente para PlaceholderAPI
            String papiPlaceholder = (String) config.get("placeholder");

            // Coletar dados de jogadores online
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String valueStr = PlaceholderAPI.setPlaceholders(onlinePlayer, papiPlaceholder);
                try {
                    double value = Double.parseDouble(valueStr);
                    playerData.add(new PlayerDataEntry(onlinePlayer, value, ascending));

                    // Se offline data estiver habilitado, atualizar os dados do jogador
                    if (configManager.isOfflineDataEnabled()) {
                        offlineDataManager.updatePlayerData(onlinePlayer, papiPlaceholder, value);
                    }
                } catch (NumberFormatException e) {
                    // Ignorar valores não numéricos
                }
            }

            // Se offline data estiver habilitado, adicionar jogadores offline
            if (configManager.isOfflineDataEnabled()) {
                List<PlayerDataEntry> offlineEntries = offlineDataManager.getTopPlayers(papiPlaceholder, topN,
                        ascending);

                // Filtrar para remover duplicatas (jogadores que já estão online)
                Set<UUID> onlineUUIDs = new HashSet<>();
                for (PlayerDataEntry entry : playerData) {
                    onlineUUIDs.add(entry.getPlayerUUID());
                }

                for (PlayerDataEntry offlineEntry : offlineEntries) {
                    if (!onlineUUIDs.contains(offlineEntry.getPlayerUUID())) {
                        playerData.add(offlineEntry);
                    }
                }
            }
        } else if (dataSource.startsWith("db:")) {
            // Fonte de dados de banco de dados externo
            // Corrigido para lidar com múltiplos ":" no campo
            String dbInfo = dataSource.substring(3); // Remove "db:"
            int firstColonIndex = dbInfo.indexOf(':');

            if (firstColonIndex > 0) {
                String connectorName = dbInfo.substring(0, firstColonIndex);
                String field = dbInfo.substring(firstColonIndex + 1);

                // Obter dados do banco de dados
                playerData = databaseConnectorManager.getTopPlayersFromDatabase(connectorName, field, topN, ascending);

                if (playerData.isEmpty() && plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().warning("Nenhum dado encontrado para o conector '" + connectorName +
                            "' e campo '" + field + "'. Verifique a configuração do banco de dados.");
                }
            } else {
                plugin.getLogger().warning("Formato de fonte de dados inválido: " + dataSource);
            }
        }

        Collections.sort(playerData); // Usa o compareTo para ordenar

        // Obter nome amigável do placeholder ou campo
        String placeholderFriendlyName;
        if ("papi".equals(dataSource)) {
            String papiPlaceholder = (String) config.get("placeholder");
            placeholderFriendlyName = papiPlaceholder.replace("%", "").replace("_", " ");
        } else {
            // Para fonte de dados de banco de dados, tentar obter o nome amigável
            try {
                String dbInfo = dataSource.substring(3); // Remove "db:"
                int firstColonIndex = dbInfo.indexOf(':');

                if (firstColonIndex > 0) {
                    String connectorName = dbInfo.substring(0, firstColonIndex);
                    String field = dbInfo.substring(firstColonIndex + 1);

                    // Tentar obter o nome amigável do conector
                    placeholderFriendlyName = databaseConnectorManager.getFriendlyFieldName(connectorName, field);
                } else {
                    placeholderFriendlyName = dataSource.replace("db:", "").replace(":", " - ").replace("_", " ");
                }
            } catch (Exception e) {
                placeholderFriendlyName = dataSource.replace("db:", "").replace(":", " - ").replace("_", " ");
            }
        }

        String actualTitle = titleTemplate.replace("{placeholder_name}", placeholderFriendlyName);

        // Criar uma cópia final da lista de jogadores para usar na lambda
        final List<PlayerDataEntry> finalPlayerData = new ArrayList<>(playerData);

        // Atualiza o conteúdo do holograma (precisa ser feito na thread principal se a
        // API DH não for thread-safe para modificações)
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Limpa linhas antigas, exceto o título
            while (hologram.getPage(0).getLines().size() > 1) {
                hologram.getPage(0).removeLine(1); // Remove a segunda linha repetidamente
            }

            // Usar DHAPI para definir as linhas
            DHAPI.setHologramLine(hologram, 0, translateAlternateColorCodes('&', actualTitle));

            if (finalPlayerData.isEmpty()) {
                DHAPI.addHologramLine(hologram, translateAlternateColorCodes('&', "&7Ninguém no ranking ainda."));
            } else {
                for (int i = 0; i < Math.min(finalPlayerData.size(), topN); i++) {
                    PlayerDataEntry entry = finalPlayerData.get(i);
                    String formattedLine = lineFormat
                            .replace("{rank}", String.valueOf(i + 1))
                            .replace("{player}", entry.getPlayerName())
                            .replace("{value}", String.format("%,.0f", entry.getValue())); // Formata número
                    DHAPI.addHologramLine(hologram, translateAlternateColorCodes('&', formattedLine));
                }
            }
        });
    }

    public void setHologramDatabaseSource(String id, String connectorName, String field, int topN, String title,
            String format, boolean ascending) {
        // Armazenar a fonte de dados no formato correto
        Map<String, Object> config = new HashMap<>();
        config.put("dataSource", "db:" + connectorName + ":" + field);
        config.put("topN", topN);
        config.put("title", title);
        config.put("format", format);
        config.put("ascending", ascending);
        activeHologramConfigs.put(id, config);

        // Salvar na configuração
        configManager.saveHologramDatabaseSource(id, connectorName, field, topN, title, format, ascending);

        plugin.getLogger()
                .info("Configurado holograma '" + id + "' para banco de dados: " + connectorName + ", campo: " + field);

        // Adicionar log para depuração
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("Fonte de dados configurada: db:" + connectorName + ":" + field);
        }

        forceUpdateHologram(id); // Atualiza imediatamente após setar
    }

    public Set<String> getConfiguredHologramIDs() {
        return activeHologramConfigs.keySet();
    }

    public Map<String, Object> getHologramConfig(String id) {
        return activeHologramConfigs.get(id);
    }

    public boolean hologramExists(String id) {
        return activeHologramConfigs.containsKey(id);
    }
}