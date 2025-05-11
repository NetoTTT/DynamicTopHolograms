package org.DynamicTopHolograms.dynamicTopHolograms.database;

import org.DynamicTopHolograms.dynamicTopHolograms.DynamicTopHolograms;
import org.DynamicTopHolograms.dynamicTopHolograms.PlayerDataEntry;
import org.DynamicTopHolograms.dynamicTopHolograms.database.connectors.*;

import java.util.*;

public class DatabaseConnectorManager {

    private final DynamicTopHolograms plugin;
    private final Map<String, DatabaseConnector> connectors = new HashMap<>();
    private final Map<String, String> fieldFriendlyNames = new HashMap<>();

    public DatabaseConnectorManager(DynamicTopHolograms plugin) {
        this.plugin = plugin;
        registerConnectors();
    }

    /**
     * Registra todos os conectores de banco de dados disponíveis
     */
    private void registerConnectors() {
        // Registrar conectores aqui
        registerConnector(new ValhallaMMOConnector(plugin));
        // Adicionar o GenericSQLConnector
        registerConnector(new GenericSQLConnector(plugin));
        // Adicionar mais conectores conforme necessário

        // Inicializar conectores disponíveis
        for (DatabaseConnector connector : connectors.values()) {
            if (connector.initialize()) {
                plugin.getLogger().info("Conector de banco de dados inicializado: " + connector.getName());

                // Carregar nomes amigáveis para campos
                if (connector instanceof ValhallaMMOConnector) {
                    ValhallaMMOConnector valhallaConnector = (ValhallaMMOConnector) connector;
                    Map<String, String> fieldsWithNames = valhallaConnector.getFieldsWithFriendlyNames();
                    for (Map.Entry<String, String> entry : fieldsWithNames.entrySet()) {
                        fieldFriendlyNames.put(
                                connector.getName().toLowerCase() + ":" + entry.getKey(),
                                entry.getValue());
                    }
                }
                // Adicionar suporte para nomes amigáveis do GenericSQLConnector
                else if (connector instanceof GenericSQLConnector) {
                    GenericSQLConnector genericConnector = (GenericSQLConnector) connector;
                    Map<String, String> fieldsWithNames = genericConnector.getFieldsWithFriendlyNames();
                    for (Map.Entry<String, String> entry : fieldsWithNames.entrySet()) {
                        fieldFriendlyNames.put(
                                connector.getName().toLowerCase() + ":" + entry.getKey(),
                                entry.getValue());
                    }
                }
            }
        }
    }

    /**
     * Obtém a lista de tabelas disponíveis para um conector
     * 
     * @param connectorName nome do conector
     * @return lista de nomes de tabelas
     */
    public List<String> getAvailableTables(String connectorName) {
        DatabaseConnector connector = getConnector(connectorName);
        if (connector == null || !connector.isAvailable()) {
            return new ArrayList<>();
        }

        if (connector instanceof ValhallaMMOConnector) {
            ValhallaMMOConnector valhallaConnector = (ValhallaMMOConnector) connector;
            return valhallaConnector.getAvailableTables();
        }

        return new ArrayList<>();
    }

    /**
     * Obtém a lista de campos disponíveis para uma tabela específica de um conector
     * 
     * @param connectorName nome do conector
     * @param tableName     nome da tabela
     * @return lista de nomes de campos
     */
    public List<String> getTableFields(String connectorName, String tableName) {
        DatabaseConnector connector = getConnector(connectorName);
        if (connector == null || !connector.isAvailable()) {
            return new ArrayList<>();
        }

        if (connector instanceof ValhallaMMOConnector) {
            ValhallaMMOConnector valhallaConnector = (ValhallaMMOConnector) connector;
            return valhallaConnector.getTableFields(tableName);
        }

        return new ArrayList<>();
    }

    /**
     * Obtém os campos disponíveis com nomes amigáveis para um conector específico
     * 
     * @param connectorName nome do conector
     * @return mapa com campos e seus nomes amigáveis
     */
    public Map<String, Map<String, String>> getConnectorFieldsWithFriendlyNames(String connectorName) {
        DatabaseConnector connector = getConnector(connectorName);
        if (connector == null || !connector.isAvailable()) {
            return new HashMap<>();
        }

        if (connector instanceof GenericSQLConnector) {
            GenericSQLConnector genericConnector = (GenericSQLConnector) connector;
            return Collections.singletonMap(connectorName, genericConnector.getFieldsWithFriendlyNames());
        }

        return new HashMap<>();
    }

    /**
     * Registra um conector de banco de dados
     * 
     * @param connector o conector a ser registrado
     */
    public void registerConnector(DatabaseConnector connector) {
        connectors.put(connector.getName().toLowerCase(), connector);
    }

    /**
     * Obtém um conector de banco de dados pelo nome
     * 
     * @param name nome do conector
     * @return o conector, ou null se não encontrado
     */
    public DatabaseConnector getConnector(String name) {
        return connectors.get(name.toLowerCase());
    }

    /**
     * Verifica se um conector está disponível
     * 
     * @param name nome do conector
     * @return true se o conector está disponível
     */
    public boolean isConnectorAvailable(String name) {
        DatabaseConnector connector = getConnector(name);
        return connector != null && connector.isAvailable();
    }

    /**
     * Obtém os dados de ranking de um banco de dados
     * 
     * @param connectorName nome do conector
     * @param field         campo a ser consultado
     * @param limit         número máximo de registros
     * @param ascending     true para ordem crescente, false para decrescente
     * @return lista de entradas de dados de jogadores
     */
    public List<PlayerDataEntry> getTopPlayersFromDatabase(String connectorName, String field, int limit,
            boolean ascending) {
        List<PlayerDataEntry> result = new ArrayList<>();

        // Log para depuração
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("Buscando dados do banco: conector=" + connectorName + ", campo=" + field +
                    ", limite=" + limit + ", ascendente=" + ascending);
        }

        DatabaseConnector connector = connectors.get(connectorName);
        if (connector == null) {
            plugin.getLogger().warning("Conector de banco de dados não encontrado: " + connectorName);
            return result;
        }

        if (!connector.isAvailable()) {
            plugin.getLogger().warning("Conector de banco de dados não está disponível: " + connectorName);
            return result;
        }

        List<DatabasePlayerEntry> entries = connector.getTopPlayers(field, limit, ascending);

        // Converter DatabasePlayerEntry para PlayerDataEntry
        for (DatabasePlayerEntry entry : entries) {
            result.add(new PlayerDataEntry(entry.getPlayerUUID(), entry.getPlayerName(), entry.getValue(), ascending));
        }

        // Log para depuração
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("Encontrados " + result.size() + " jogadores no ranking do banco de dados");
        }

        return result;
    }

    /**
     * Obtém a lista de conectores disponíveis
     * 
     * @return lista de nomes de conectores
     */
    public List<String> getAvailableConnectors() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, DatabaseConnector> entry : connectors.entrySet()) {
            if (entry.getValue().isAvailable()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Obtém a lista de campos disponíveis para um conector
     * 
     * @param connectorName nome do conector
     * @return lista de nomes de campos
     */
    public List<String> getAvailableFields(String connectorName) {
        DatabaseConnector connector = getConnector(connectorName);
        if (connector == null || !connector.isAvailable()) {
            return new ArrayList<>();
        }
        return connector.getAvailableFields();
    }

    /**
     * Obtém um mapa de campos disponíveis com seus nomes amigáveis
     * 
     * @param connectorName nome do conector
     * @return mapa de campos e seus nomes amigáveis
     */
    public Map<String, String> getFieldsWithFriendlyNames(String connectorName) {
        Map<String, String> result = new HashMap<>();
        String prefix = connectorName.toLowerCase() + ":";

        for (Map.Entry<String, String> entry : fieldFriendlyNames.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String field = entry.getKey().substring(prefix.length());
                result.put(field, entry.getValue());
            }
        }

        return result;
    }

    /**
     * Obtém o nome amigável de um campo
     * 
     * @param connectorName nome do conector
     * @param field         nome do campo
     * @return nome amigável do campo, ou o próprio campo se não encontrado
     */
    public String getFriendlyFieldName(String connectorName, String field) {
        String key = connectorName.toLowerCase() + ":" + field;
        return fieldFriendlyNames.getOrDefault(key, field);
    }

    /**
     * Fecha todas as conexões de banco de dados
     */
    public void closeAll() {
        for (DatabaseConnector connector : connectors.values()) {
            try {
                connector.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Erro ao fechar conector " + connector.getName() + ": " + e.getMessage());
            }
        }
    }
}