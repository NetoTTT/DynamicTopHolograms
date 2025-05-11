package org.DynamicTopHolograms.dynamicTopHolograms;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OfflineDataManager {
    private final DynamicTopHolograms plugin;
    private final File dataFolder;
    private final Map<String, Map<UUID, OfflinePlayerData>> placeholderData = new ConcurrentHashMap<>();
    private final long expiryDays;
    
    public OfflineDataManager(DynamicTopHolograms plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.expiryDays = plugin.getConfigManager().getConfig().getLong("offline-data-expiry-days", 30);
        loadAllData();
    }
    
    public void updatePlayerData(Player player, String placeholder, double value) {
        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();
        
        // Obter ou criar o mapa para este placeholder
        Map<UUID, OfflinePlayerData> playersForPlaceholder = placeholderData.computeIfAbsent(
                placeholder, k -> new ConcurrentHashMap<>());
        
        // Atualizar os dados do jogador
        playersForPlaceholder.put(playerUUID, new OfflinePlayerData(playerUUID, playerName, value, Instant.now()));
        
        // Salvar os dados de forma assíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayerData(placeholder, playerUUID));
    }
    
    public List<PlayerDataEntry> getTopPlayers(String placeholder, int topN, boolean ascending) {
        Map<UUID, OfflinePlayerData> playersForPlaceholder = placeholderData.get(placeholder);
        if (playersForPlaceholder == null || playersForPlaceholder.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<PlayerDataEntry> entries = new ArrayList<>();
        for (OfflinePlayerData data : playersForPlaceholder.values()) {
            // Verificar se os dados expiraram
            if (Instant.now().minusSeconds(expiryDays * 86400).isAfter(data.lastSeen)) {
                continue;
            }
            
            // Tentar obter o jogador do Bukkit (pode ser null se estiver offline)
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(data.uuid);
            
            // Criar entrada com dados offline
            entries.add(new PlayerDataEntry(
                    data.uuid, 
                    data.playerName, 
                    data.value, 
                    ascending));
        }
        
        // Ordenar a lista
        Collections.sort(entries);
        
        // Limitar ao número desejado
        if (entries.size() > topN) {
            entries = entries.subList(0, topN);
        }
        
        return entries;
    }
    
    private void loadAllData() {
        plugin.getLogger().info("Carregando dados offline de jogadores...");
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        
        for (File file : files) {
            String placeholder = file.getName().replace(".yml", "");
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            
            Map<UUID, OfflinePlayerData> playersForPlaceholder = new ConcurrentHashMap<>();
            
            for (String uuidStr : config.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String name = config.getString(uuidStr + ".name");
                    double value = config.getDouble(uuidStr + ".value");
                    long lastSeenTimestamp = config.getLong(uuidStr + ".lastSeen");
                    Instant lastSeen = Instant.ofEpochSecond(lastSeenTimestamp);
                    
                    // Verificar se os dados expiraram
                    if (Instant.now().minusSeconds(expiryDays * 86400).isAfter(lastSeen)) {
                        continue;
                    }
                    
                    playersForPlaceholder.put(uuid, new OfflinePlayerData(uuid, name, value, lastSeen));
                } catch (Exception e) {
                    plugin.getLogger().warning("Erro ao carregar dados para UUID " + uuidStr + ": " + e.getMessage());
                }
            }
            
            if (!playersForPlaceholder.isEmpty()) {
                placeholderData.put(placeholder, playersForPlaceholder);
                plugin.getLogger().info("Carregados " + playersForPlaceholder.size() + " jogadores para placeholder " + placeholder);
            }
        }
    }
    
    private void savePlayerData(String placeholder, UUID playerUUID) {
        Map<UUID, OfflinePlayerData> playersForPlaceholder = placeholderData.get(placeholder);
        if (playersForPlaceholder == null) return;
        
        OfflinePlayerData data = playersForPlaceholder.get(playerUUID);
        if (data == null) return;
        
        File file = new File(dataFolder, placeholder + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        String path = playerUUID.toString();
        config.set(path + ".name", data.playerName);
        config.set(path + ".value", data.value);
        config.set(path + ".lastSeen", data.lastSeen.getEpochSecond());
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao salvar dados para jogador " + playerUUID + ": " + e.getMessage());
        }
    }
    
    public void cleanupExpiredData() {
        plugin.getLogger().info("Limpando dados expirados de jogadores...");
        
        for (String placeholder : placeholderData.keySet()) {
            Map<UUID, OfflinePlayerData> playersForPlaceholder = placeholderData.get(placeholder);
            if (playersForPlaceholder == null) continue;
            
            // Remover jogadores expirados da memória
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, OfflinePlayerData> entry : playersForPlaceholder.entrySet()) {
                if (Instant.now().minusSeconds(expiryDays * 86400).isAfter(entry.getValue().lastSeen)) {
                    toRemove.add(entry.getKey());
                }
            }
            
            for (UUID uuid : toRemove) {
                playersForPlaceholder.remove(uuid);
            }
            
            // Se houver remoções, salvar o arquivo
            if (!toRemove.isEmpty()) {
                File file = new File(dataFolder, placeholder + ".yml");
                if (file.exists()) {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    
                    for (UUID uuid : toRemove) {
                        config.set(uuid.toString(), null);
                    }
                    
                    try {
                        config.save(file);
                        plugin.getLogger().info("Removidos " + toRemove.size() + " jogadores expirados para placeholder " + placeholder);
                    } catch (IOException e) {
                        plugin.getLogger().severe("Erro ao salvar limpeza de dados para placeholder " + placeholder + ": " + e.getMessage());
                    }
                }
            }
        }
    }
    
    private static class OfflinePlayerData {
        final UUID uuid;
        final String playerName;
        final double value;
        final Instant lastSeen;
        
        OfflinePlayerData(UUID uuid, String playerName, double value, Instant lastSeen) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.value = value;
            this.lastSeen = lastSeen;
        }
    }
}