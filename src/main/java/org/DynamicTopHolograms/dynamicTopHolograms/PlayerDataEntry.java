package org.DynamicTopHolograms.dynamicTopHolograms;

import org.bukkit.OfflinePlayer;

import java.util.UUID;

public class PlayerDataEntry implements Comparable<PlayerDataEntry> {
    private final OfflinePlayer player; // Usar OfflinePlayer para obter nome mesmo se offline
    private final String playerName;    // Nome do jogador armazenado
    private final UUID playerUUID;      // UUID do jogador
    private final double value;
    private boolean ascending;          // Ordem de classificação

    public PlayerDataEntry(OfflinePlayer player, double value, boolean ascending) {
        this.player = player;
        this.playerUUID = player.getUniqueId();
        this.playerName = player.getName() != null ? player.getName() : playerUUID.toString().substring(0, 8) + "(Nome?)";
        this.value = value;
        this.ascending = ascending;
    }
    
    // Construtor para dados offline
    public PlayerDataEntry(UUID uuid, String name, double value, boolean ascending) {
        this.player = null;
        this.playerUUID = uuid;
        this.playerName = name != null ? name : uuid.toString().substring(0, 8) + "(Nome?)";
        this.value = value;
        this.ascending = ascending;
    }

    public OfflinePlayer getPlayer() {
        return player;
    }
    
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getValue() {
        return value;
    }
    
    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    @Override
    public int compareTo(PlayerDataEntry other) {
        if (ascending) {
            // Ordem crescente (menor valor primeiro)
            return Double.compare(this.value, other.value);
        } else {
            // Ordem decrescente (maior valor primeiro)
            return Double.compare(other.value, this.value);
        }
    }
}