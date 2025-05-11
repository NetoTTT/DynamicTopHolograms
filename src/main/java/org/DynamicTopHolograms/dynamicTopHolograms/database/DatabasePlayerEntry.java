package org.DynamicTopHolograms.dynamicTopHolograms.database;

import java.util.UUID;

/**
 * Representa uma entrada de dados de jogador obtida de um banco de dados externo
 */
public class DatabasePlayerEntry {
    private final UUID playerUUID;
    private final String playerName;
    private final double value;
    
    public DatabasePlayerEntry(UUID playerUUID, String playerName, double value) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.value = value;
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
}