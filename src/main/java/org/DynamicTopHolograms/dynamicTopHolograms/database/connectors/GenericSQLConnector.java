package org.DynamicTopHolograms.dynamicTopHolograms.database.connectors;

import org.DynamicTopHolograms.dynamicTopHolograms.DynamicTopHolograms;
import org.DynamicTopHolograms.dynamicTopHolograms.database.DatabaseConnector;
import org.DynamicTopHolograms.dynamicTopHolograms.database.DatabasePlayerEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class GenericSQLConnector implements DatabaseConnector {

    private final DynamicTopHolograms plugin;
    private final Map<String, SQLConnectorInstance> connectors = new HashMap<>();
    private boolean available = false;
    private FileConfiguration databaseConfig;
    private boolean debugMode = false;

    public GenericSQLConnector(DynamicTopHolograms plugin) {
        this.plugin = plugin;
        loadDatabaseConfig();
        // Verificar se o modo de debug está ativado na configuração principal
        try {
            this.debugMode = plugin.getConfig().getBoolean("debug-mode", false);
        } catch (Exception e) {
            this.debugMode = false;
        }
    }

    /**
     * Carrega a configuração de banco de dados do arquivo database.yml
     */
    private void loadDatabaseConfig() {
        File databaseFile = new File(plugin.getDataFolder(), "database.yml");

        if (!databaseFile.exists()) {
            plugin.saveResource("database.yml", false);
            plugin.getLogger().info("Arquivo database.yml criado com configurações padrão.");
        }

        databaseConfig = YamlConfiguration.loadConfiguration(databaseFile);
        plugin.getLogger().info("Configurações de banco de dados carregadas.");
    }

    @Override
    public boolean initialize() {
        // Verificar se a configuração de banco de dados existe
        if (!databaseConfig.contains("database.generic-sql")) {
            plugin.getLogger().warning("Configuração de SQL genérico não encontrada.");
            return false;
        }

        // Obter todas as seções de conectores
        ConfigurationSection connectorsSection = databaseConfig.getConfigurationSection("database.generic-sql");
        if (connectorsSection == null) {
            plugin.getLogger().warning("Nenhum conector SQL genérico configurado.");
            return false;
        }

        // Inicializar cada conector
        boolean anyConnectorAvailable = false;
        for (String connectorName : connectorsSection.getKeys(false)) {
            String connectorPath = "database.generic-sql." + connectorName;

            // Verificar se o conector está habilitado
            if (!databaseConfig.getBoolean(connectorPath + ".enabled", false)) {
                plugin.getLogger().info("Conector SQL '" + connectorName + "' desabilitado na configuração.");
                continue;
            }

            // Inicializar o conector
            SQLConnectorInstance connector = new SQLConnectorInstance(plugin, connectorName, databaseConfig,
                    connectorPath, debugMode);
            if (connector.initialize()) {
                connectors.put(connectorName, connector);
                anyConnectorAvailable = true;
                plugin.getLogger().info("Conector SQL '" + connectorName + "' inicializado com sucesso.");
            } else {
                plugin.getLogger().warning("Falha ao inicializar conector SQL '" + connectorName + "'.");
            }
        }

        available = anyConnectorAvailable;
        if (available) {
            plugin.getLogger().info("Conectores SQL genéricos inicializados: " + connectors.size());
            // Listar todos os campos disponíveis para facilitar a depuração
            if (debugMode) {
                logAvailableFields();
            }
        } else {
            plugin.getLogger().warning("Nenhum conector SQL genérico disponível.");
        }

        return available;
    }

    /**
     * Registra todos os campos disponíveis no log para facilitar a depuração
     */
    private void logAvailableFields() {
        plugin.getLogger().info("=== CAMPOS DISPONÍVEIS PARA HOLOGRAMAS ===");
        for (Map.Entry<String, SQLConnectorInstance> entry : connectors.entrySet()) {
            String connectorName = entry.getKey();
            SQLConnectorInstance connector = entry.getValue();
            
            plugin.getLogger().info("Conector: " + connectorName);
            for (String field : connector.getAvailableFields()) {
                String friendlyName = connector.getFriendlyFieldName(field);
                plugin.getLogger().info("  - {top:" + connectorName + ":" + field + ":10} → " + friendlyName);
            }
        }
        plugin.getLogger().info("=========================================");
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getName() {
        return "GenericSQL";
    }

    @Override
    public List<String> getAvailableFields() {
        List<String> fields = new ArrayList<>();

        for (Map.Entry<String, SQLConnectorInstance> connectorEntry : connectors.entrySet()) {
            String connectorName = connectorEntry.getKey();
            SQLConnectorInstance connector = connectorEntry.getValue();

            for (String field : connector.getAvailableFields()) {
                fields.add(connectorName + ":" + field);
            }
        }

        return fields;
    }

    /**
     * Obtém um mapa de campos disponíveis com nomes amigáveis para autocompletar
     * 
     * @return mapa onde a chave é o campo técnico e o valor é o nome amigável
     */
    public Map<String, String> getFieldsWithFriendlyNames() {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, SQLConnectorInstance> connectorEntry : connectors.entrySet()) {
            String connectorName = connectorEntry.getKey();
            SQLConnectorInstance connector = connectorEntry.getValue();

            Map<String, String> connectorFields = connector.getFieldsWithFriendlyNames();
            for (Map.Entry<String, String> fieldEntry : connectorFields.entrySet()) {
                String key = connectorName + ":" + fieldEntry.getKey();
                String value = connectorName + " - " + fieldEntry.getValue();

                result.put(key, value);
            }
        }

        return result;
    }

    @Override
    public List<DatabasePlayerEntry> getTopPlayers(String field, int limit, boolean ascending) {
        // O campo deve estar no formato connector:table.field
        String[] parts = field.split(":");

        String connectorName;
        String tableField;

        if (parts.length == 1) {
            // Se não houver conector especificado, usar o conector "default"
            connectorName = "default";
            tableField = parts[0];
        } else if (parts.length == 2) {
            connectorName = parts[0];
            tableField = parts[1];
        } else {
            plugin.getLogger().warning("Formato de campo inválido: " + field);
            return new ArrayList<>();
        }

        // Verificar se o conector existe
        SQLConnectorInstance connector = connectors.get(connectorName);
        if (connector == null) {
            plugin.getLogger().warning("Conector não encontrado: " + connectorName);
            return new ArrayList<>();
        }

        // Obter os jogadores do conector
        List<DatabasePlayerEntry> result = connector.getTopPlayers(tableField, limit, ascending);
        
        // Log de diagnóstico
        if (debugMode) {
            plugin.getLogger().info("Consulta para {top:" + field + ":" + limit + "} retornou " + result.size() + " resultados");
            if (result.isEmpty()) {
                plugin.getLogger().warning("Nenhum resultado encontrado para " + field + ". Verifique a configuração e o banco de dados.");
            }
        }
        
        return result;
    }

    @Override
    public void close() {
        for (SQLConnectorInstance connector : connectors.values()) {
            connector.close();
        }
        connectors.clear();
        plugin.getLogger().info("Todos os conectores SQL genéricos foram fechados.");
    }

    /**
     * Obtém o nome amigável de um campo
     */
    public String getFriendlyFieldName(String field) {
        // O campo deve estar no formato connector:table.field
        String[] parts = field.split(":");

        String connectorName;
        String tableField;

        if (parts.length == 1) {
            // Se não houver conector especificado, usar o conector "default"
            connectorName = "default";
            tableField = parts[0];
        } else if (parts.length == 2) {
            connectorName = parts[0];
            tableField = parts[1];
        } else {
            return field;
        }

        // Verificar se o conector existe
        SQLConnectorInstance connector = connectors.get(connectorName);
        if (connector == null) {
            return field;
        }

        // Obter o nome amigável do campo
        String friendlyName = connector.getFriendlyFieldName(tableField);
        return connectorName + " - " + friendlyName;
    }

    /**
     * Executa uma consulta de diagnóstico para verificar se há dados no banco
     * @param connectorName Nome do conector
     * @param tableName Nome da tabela
     * @return true se a tabela contém dados
     */
    public boolean testTableData(String connectorName, String tableName) {
        SQLConnectorInstance connector = connectors.get(connectorName);
        if (connector == null) {
            plugin.getLogger().warning("Conector não encontrado para teste: " + connectorName);
            return false;
        }
        
        return connector.testTableData(tableName);
    }

    /**
     * Classe interna para representar uma instância de conector SQL
     */
    private class SQLConnectorInstance {
        private final DynamicTopHolograms plugin;
        private final String name;
        private Connection connection;
        private boolean available = false;
        private String databaseType;
        private final FileConfiguration config;
        private final boolean debugMode;

        // Armazena informações sobre tabelas e campos
        private final Map<String, TableInfo> tables = new HashMap<>();

        public SQLConnectorInstance(DynamicTopHolograms plugin, String name, FileConfiguration config,
                String configPath, boolean debugMode) {
            this.plugin = plugin;
            this.name = name;
            this.config = config;
            this.debugMode = debugMode;
            this.databaseType = config.getString(configPath + ".type", "sqlite").toLowerCase();
        }

        /**
         * Inicializa o conector SQL
         */
        public boolean initialize() {
            plugin.getLogger().info("Inicializando conector SQL '" + name + "' com tipo: " + databaseType);

            try {
                if ("sqlite".equals(databaseType)) {
                    // Configuração SQLite
                    String dbPath = config.getString("database.generic-sql." + name + ".sqlite.path",
                            "plugins/DynamicTopHolograms/" + name + ".db");
                    plugin.getLogger().info("Verificando banco de dados SQLite em: " + dbPath);

                    // Verificar se o arquivo existe
                    File dbFile = new File(dbPath);
                    if (!dbFile.exists()) {
                        plugin.getLogger()
                                .severe("Arquivo de banco de dados SQLite não encontrado: " + dbFile.getAbsolutePath());
                        plugin.getLogger().severe(
                                "Verifique se o caminho está correto e se o arquivo foi criado pelo plugin correspondente.");
                        return false;
                    }

                    // Verificar permissões de acesso ao arquivo
                    if (!dbFile.canRead()) {
                        plugin.getLogger().severe("Sem permissão de leitura no arquivo de banco de dados SQLite: "
                                + dbFile.getAbsolutePath());
                        return false;
                    }

                    plugin.getLogger().info("Conectando ao SQLite em: " + dbFile.getAbsolutePath());

                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

                } else if ("mysql".equals(databaseType)) {
                    // Configuração MySQL
                    String host = config.getString("database.generic-sql." + name + ".mysql.host", "localhost");
                    int port = config.getInt("database.generic-sql." + name + ".mysql.port", 3306);
                    String database = config.getString("database.generic-sql." + name + ".mysql.database", "minecraft");
                    String username = config.getString("database.generic-sql." + name + ".mysql.username", "root");
                    String password = config.getString("database.generic-sql." + name + ".mysql.password", "");

                    plugin.getLogger().info("Conectando ao MySQL em: " + host + ":" + port + "/" + database);

                    try {
                        Class.forName("com.mysql.jdbc.Driver");
                    } catch (ClassNotFoundException e) {
                        // Tentar o driver mais recente se o antigo não estiver disponível
                        try {
                            Class.forName("com.mysql.cj.jdbc.Driver");
                        } catch (ClassNotFoundException e2) {
                            throw new ClassNotFoundException(
                                    "Nenhum driver MySQL encontrado. Verifique se o MySQL está instalado corretamente.");
                        }
                    }

                    String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
                    connection = DriverManager.getConnection(url, username, password);

                } else {
                    plugin.getLogger().warning(
                            "Tipo de banco de dados não suportado para conector '" + name + "': " + databaseType);
                    return false;
                }

                // Testar a conexão
                if (!testConnection()) {
                    plugin.getLogger().severe("Falha no teste de conexão para o conector '" + name + "'");
                    return false;
                }

                // Carregar configurações de tabelas e campos
                if (!loadTableConfigurations()) {
                    return false;
                }

                // Verificar se as tabelas existem
                if (!validateTables()) {
                    return false;
                }

                // Testar se há dados nas tabelas
                testTablesData();

                available = true;
                plugin.getLogger()
                        .info("Conexão com banco de dados SQL '" + name + "' estabelecida (" + databaseType + ")");
                plugin.getLogger().info("Tabelas carregadas para conector '" + name + "': " + tables.size());
                return true;

            } catch (ClassNotFoundException e) {
                plugin.getLogger().severe("Driver JDBC não encontrado para conector '" + name + "': " + e.getMessage());
                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao conectar ao banco de dados SQL '" + name + "': " + e.getMessage());
                return false;
            } catch (Exception e) {
                plugin.getLogger()
                        .severe("Erro inesperado ao inicializar conector SQL '" + name + "': " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        /**
         * Testa a conexão com o banco de dados
         */
        private boolean testConnection() {
            try {
                if (connection == null || connection.isClosed()) {
                    plugin.getLogger().severe("Conexão com banco de dados está fechada ou nula");
                    return false;
                }
                
                try (Statement stmt = connection.createStatement()) {
                    // Executar uma consulta simples para testar a conexão
                    try (ResultSet rs = stmt.executeQuery("SELECT 1")) {
                        if (rs.next()) {
                            plugin.getLogger().info("Teste de conexão bem-sucedido para o conector '" + name + "'");
                            return true;
                        }
                    }
                }
                
                plugin.getLogger().severe("Teste de conexão falhou para o conector '" + name + "'");
                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("Erro ao testar conexão para o conector '" + name + "': " + e.getMessage());
                return false;
            }
        }

        /**
         * Testa se há dados nas tabelas configuradas
         */
        private void testTablesData() {
            for (Map.Entry<String, TableInfo> entry : tables.entrySet()) {
                String tableName = entry.getKey();
                TableInfo tableInfo = entry.getValue();
                
                try {
                    String query = "SELECT COUNT(*) FROM " + tableInfo.name;
                    try (Statement stmt = connection.createStatement();
                         ResultSet rs = stmt.executeQuery(query)) {
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            plugin.getLogger().info("Tabela '" + tableInfo.name + "' contém " + count + " registros");
                            
                            if (count == 0) {
                                plugin.getLogger().warning("A tabela '" + tableInfo.name + 
                                    "' está vazia. Não haverá dados para exibir no ranking.");
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("Erro ao contar registros na tabela '" + tableInfo.name + 
                        "': " + e.getMessage());
                }
            }
        }

        /**
         * Testa se uma tabela específica contém dados
         */
        public boolean testTableData(String tableName) {
            TableInfo tableInfo = tables.get(tableName);
            if (tableInfo == null) {
                plugin.getLogger().warning("Tabela '" + tableName + "' não encontrada no conector '" + name + "'");
                return false;
            }
            
            try {
                String query = "SELECT COUNT(*) FROM " + tableInfo.name;
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        return count > 0;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Erro ao contar registros na tabela '" + tableInfo.name + 
                    "': " + e.getMessage());
            }
            
            return false;
        }

        /**
         * Carrega as configurações de tabelas do arquivo de configuração
         * @return true se pelo menos uma tabela foi carregada com sucesso
         */
        private boolean loadTableConfigurations() {
            String tablesPath = "database.generic-sql." + name + ".tables";

            if (!config.contains(tablesPath)) {
                plugin.getLogger().warning("Nenhuma tabela configurada para o conector SQL '" + name + "'.");
                return false;
            }

            ConfigurationSection tablesSection = config.getConfigurationSection(tablesPath);
            if (tablesSection == null) {
                plugin.getLogger().warning("Seção de tabelas inválida para o conector SQL '" + name + "'.");
                return false;
            }

            plugin.getLogger().info("Carregando configurações de tabelas para conector '" + name + "'...");
            boolean anyTableLoaded = false;

            for (String tableKey : tablesSection.getKeys(false)) {
                String tablePath = tablesPath + "." + tableKey;

                // Verificar campos obrigatórios
                if (!config.contains(tablePath + ".player-id-column") || !config.contains(tablePath + ".player-name-column")) {
                    plugin.getLogger().warning("Tabela '" + tableKey + "' no conector '" + name + 
                        "' não possui as colunas obrigatórias de ID e nome do jogador configuradas.");
                    continue;
                }

                TableInfo tableInfo = new TableInfo();
                tableInfo.name = tableKey;
                tableInfo.playerIdColumn = config.getString(tablePath + ".player-id-column", "uuid");
                tableInfo.playerNameColumn = config.getString(tablePath + ".player-name-column", "name");

                plugin.getLogger().info("Tabela: " + tableKey + " (ID: " + tableInfo.playerIdColumn + ", Nome: "
                        + tableInfo.playerNameColumn + ")");

                // Carregar campos
                if (config.contains(tablePath + ".fields")) {
                    ConfigurationSection fieldsSection = config.getConfigurationSection(tablePath + ".fields");
                    if (fieldsSection != null) {
                        for (String fieldKey : fieldsSection.getKeys(false)) {
                            String fieldPath = tablePath + ".fields." + fieldKey;
                            
                            // Verificar se a coluna está configurada
                            if (!config.contains(fieldPath + ".column")) {
                                plugin.getLogger().warning("Campo '" + fieldKey + "' na tabela '" + tableKey + 
                                    "' do conector '" + name + "' não possui a coluna configurada.");
                                continue;
                            }

                            FieldInfo fieldInfo = new FieldInfo();
                            fieldInfo.column = config.getString(fieldPath + ".column");
                            fieldInfo.displayName = config.getString(fieldPath + ".display-name", fieldKey);

                            tableInfo.fields.put(fieldKey, fieldInfo);
                            plugin.getLogger().info("  Campo: " + fieldKey + " -> " + fieldInfo.column + " (Display: "
                                    + fieldInfo.displayName + ")");
                        }
                    }
                }

                // Só adicionar a tabela se tiver pelo menos um campo configurado
                if (!tableInfo.fields.isEmpty()) {
                    tables.put(tableKey, tableInfo);
                    anyTableLoaded = true;
                } else {
                    plugin.getLogger().warning("Tabela '" + tableKey + "' no conector '" + name + 
                        "' não possui campos configurados e será ignorada.");
                }
            }

            if (!anyTableLoaded) {
                plugin.getLogger().warning("Nenhuma tabela válida configurada para o conector SQL '" + name + "'.");
            }
            
            return anyTableLoaded;
        }

        /**
         * Valida se as tabelas configuradas existem no banco de dados
         * @return true se pelo menos uma tabela válida foi encontrada
         */
        private boolean validateTables() {
            if (tables.isEmpty()) {
                plugin.getLogger().warning("Nenhuma tabela configurada para validar no conector '" + name + "'.");
                return false;
            }

            boolean anyTableValid = false;
            try {
                boolean isSQLite = connection.getMetaData().getDriverName().toLowerCase().contains("sqlite");

                for (Iterator<Map.Entry<String, TableInfo>> it = tables.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, TableInfo> entry = it.next();
                    String tableName = entry.getKey();
                    TableInfo tableInfo = entry.getValue();
                    boolean tableValid = true;

                    // Verificar se a tabela existe
                    boolean tableExists = false;
                    try (Statement stmt = connection.createStatement()) {
                        if (isSQLite) {
                            ResultSet rs = stmt.executeQuery(
                                    "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableInfo.name + "'");
                            tableExists = rs.next();
                            rs.close();
                        } else {
                            ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE '" + tableInfo.name + "'");
                            tableExists = rs.next();
                            rs.close();
                        }
                    }

                    if (!tableExists) {
                        plugin.getLogger().warning("Tabela '" + tableInfo.name
                                + "' não encontrada no banco de dados do conector '" + name + "'.");
                        it.remove();  // Remover tabela inválida
                        continue;
                    }

                    // Verificar colunas
                    Set<String> columns = new HashSet<>();
                    try (Statement stmt = connection.createStatement()) {
                        if (isSQLite) {
                            ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableInfo.name + ")");
                            while (rs.next()) {
                                columns.add(rs.getString("name").toLowerCase());
                            }
                            rs.close();
                        } else {
                            ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + tableInfo.name);
                            while (rs.next()) {
                                columns.add(rs.getString("Field").toLowerCase());
                            }
                            rs.close();
                        }
                    }

                    // Log de todas as colunas encontradas para depuração
                    if (debugMode) {
                        plugin.getLogger().info("Colunas encontradas na tabela '" + tableInfo.name + "':");
                        for (String column : columns) {
                            plugin.getLogger().info("  - " + column);
                        }
                    }

                    // Verificar coluna de ID do jogador
                    if (!columns.contains(tableInfo.playerIdColumn.toLowerCase())) {
                        plugin.getLogger().warning("Coluna de ID do jogador '" + tableInfo.playerIdColumn +
                                "' não encontrada na tabela '" + tableInfo.name + "' do conector '" + name + "'");
                        tableValid = false;
                    }

                    // Verificar coluna de nome do jogador
                    if (!columns.contains(tableInfo.playerNameColumn.toLowerCase())) {
                        plugin.getLogger().warning("Coluna de nome do jogador '" + tableInfo.playerNameColumn +
                                "' não encontrada na tabela '" + tableInfo.name + "' do conector '" + name + "'");
                        tableValid = false;
                    }

                    // Verificar colunas de campos e remover campos inválidos
                    for (Iterator<Map.Entry<String, FieldInfo>> fieldIt = tableInfo.fields.entrySet().iterator(); fieldIt.hasNext();) {
                        Map.Entry<String, FieldInfo> fieldEntry = fieldIt.next();
                        String fieldKey = fieldEntry.getKey();
                        FieldInfo fieldInfo = fieldEntry.getValue();
                        
                        if (!columns.contains(fieldInfo.column.toLowerCase())) {
                            plugin.getLogger().warning("Coluna '" + fieldInfo.column +
                                    "' não encontrada na tabela '" + tableInfo.name + "' do conector '" + name + "'");
                            fieldIt.remove();  // Remover campo inválido
                        }
                    }
                    
                    // Se não houver campos válidos, remover a tabela
                    if (tableInfo.fields.isEmpty()) {
                        plugin.getLogger().warning("Nenhum campo válido encontrado na tabela '" + tableInfo.name + 
                                "' do conector '" + name + "'. A tabela será ignorada.");
                        it.remove();
                        continue;
                    }

                    if (tableValid) {
                        anyTableValid = true;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Erro ao validar tabelas do conector '" + name + "': " + e.getMessage(), e);
                return false;
            }

            if (!anyTableValid) {
                plugin.getLogger().warning("Nenhuma tabela válida encontrada para o conector '" + name + "'.");
            }
            
            return anyTableValid;
        }

        /**
         * Obtém a lista de campos disponíveis neste conector
         */
        public List<String> getAvailableFields() {
            List<String> fields = new ArrayList<>();

            for (Map.Entry<String, TableInfo> tableEntry : tables.entrySet()) {
                String tableName = tableEntry.getKey();
                TableInfo tableInfo = tableEntry.getValue();

                for (String fieldKey : tableInfo.fields.keySet()) {
                    fields.add(tableName + "." + fieldKey);
                }
            }

            return fields;
        }

        /**
         * Obtém um mapa de campos disponíveis com nomes amigáveis
         */
        public Map<String, String> getFieldsWithFriendlyNames() {
            Map<String, String> result = new HashMap<>();

            for (Map.Entry<String, TableInfo> tableEntry : tables.entrySet()) {
                String tableName = tableEntry.getKey();
                TableInfo tableInfo = tableEntry.getValue();

                for (Map.Entry<String, FieldInfo> fieldEntry : tableInfo.fields.entrySet()) {
                    String fieldKey = fieldEntry.getKey();
                    FieldInfo fieldInfo = fieldEntry.getValue();

                    String key = tableName + "." + fieldKey;
                    String value = capitalize(tableName) + " - " + fieldInfo.displayName;

                    result.put(key, value);
                }
            }

            return result;
        }

        /**
         * Obtém o nome amigável de um campo
         */
        public String getFriendlyFieldName(String field) {
            String[] parts = field.split("\\.");
            if (parts.length != 2) {
                return field;
            }

            String tableName = parts[0];
            String fieldKey = parts[1];

            TableInfo tableInfo = tables.get(tableName);
            if (tableInfo == null) {
                return field;
            }

            FieldInfo fieldInfo = tableInfo.fields.get(fieldKey);
            if (fieldInfo == null) {
                return field;
            }

            return capitalize(tableName) + " - " + fieldInfo.displayName;
        }

        /**
         * Obtém os jogadores do topo para um campo específico
         */
        public List<DatabasePlayerEntry> getTopPlayers(String field, int limit, boolean ascending) {
            List<DatabasePlayerEntry> entries = new ArrayList<>();

            if (!available || connection == null) {
                plugin.getLogger().warning("Conector SQL '" + name + "' não está disponível ou conexão é nula");
                return entries;
            }

            // Separar tabela e campo
            String[] parts = field.split("\\.");
            if (parts.length != 2) {
                plugin.getLogger().warning("Formato de campo inválido para conector SQL '" + name + "': " + field);
                return entries;
            }

            String tableName = parts[0];
            String fieldKey = parts[1];

            // Verificar se a tabela existe no mapeamento
            TableInfo tableInfo = tables.get(tableName);
            if (tableInfo == null) {
                plugin.getLogger().warning("Tabela não configurada no conector '" + name + "': " + tableName);
                return entries;
            }

            // Verificar se o campo existe no mapeamento
            FieldInfo fieldInfo = tableInfo.fields.get(fieldKey);
            if (fieldInfo == null) {
                plugin.getLogger().warning(
                        "Campo não configurado no conector '" + name + "': " + fieldKey + " na tabela " + tableName);
                return entries;
            }

            try {
                // Consultar os dados
                String orderDirection = ascending ? "ASC" : "DESC";
                
                // Construir a consulta com proteção extra contra SQL injection
                String query = "SELECT " + tableInfo.playerIdColumn + ", " + tableInfo.playerNameColumn + ", "
                        + fieldInfo.column +
                        " FROM " + tableInfo.name +
                        " WHERE " + fieldInfo.column + " IS NOT NULL " +
                        " ORDER BY " + fieldInfo.column + " " + orderDirection +
                        " LIMIT ?";

                // Mostrar a consulta completa no modo de depuração
                if (debugMode) {
                    plugin.getLogger().info("Executando consulta no conector '" + name + "': " + query.replace("?", String.valueOf(limit)));
                }

                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setInt(1, limit);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        // Verificar se há resultados
                        boolean hasResults = false;
                        
                        while (rs.next()) {
                            hasResults = true;
                            String uuidString = rs.getString(tableInfo.playerIdColumn);
                            String playerName = rs.getString(tableInfo.playerNameColumn);
                            double value;
                            
                            try {
                                value = rs.getDouble(fieldInfo.column);
                            } catch (SQLException e) {
                                // Tentar converter de outro tipo se falhar como double
                                String strValue = rs.getString(fieldInfo.column);
                                try {
                                    value = Double.parseDouble(strValue);
                                } catch (NumberFormatException ex) {
                                    plugin.getLogger().warning("Valor não numérico encontrado para o campo '" + 
                                        fieldInfo.column + "': " + strValue);
                                    value = 0;
                                }
                            }
                            
                            // Se o nome do jogador for nulo, usar um valor padrão
                            if (playerName == null || playerName.isEmpty()) {
                                playerName = "Desconhecido";
                            }

                            UUID uuid;
                            try {
                                // Tentar converter para UUID
                                if (uuidString != null && !uuidString.isEmpty()) {
                                    // Se o UUID estiver sem hífens, adicionar
                                    if (uuidString.length() == 32) {
                                        uuidString = uuidString.substring(0, 8) + "-" +
                                                uuidString.substring(8, 12) + "-" +
                                                uuidString.substring(12, 16) + "-" +
                                                uuidString.substring(16, 20) + "-" +
                                                uuidString.substring(20);
                                    }
                                    uuid = UUID.fromString(uuidString);
                                } else {
                                    // Se o UUID for nulo, gerar um baseado no nome do jogador
                                    uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
                                }
                            } catch (IllegalArgumentException e) {
                                // Se não for um UUID válido, gerar um baseado no valor
                                uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
                                if (debugMode) {
                                    plugin.getLogger().warning("UUID inválido para jogador " + playerName + " no conector '" + name
                                            + "': " + uuidString);
                                }
                            }

                            entries.add(new DatabasePlayerEntry(uuid, playerName, value));
                            
                            if (debugMode) {
                                plugin.getLogger().info("Adicionado jogador ao ranking do conector '" + name + "': " + playerName
                                        + " com valor " + value);
                            }
                        }
                        
                        // Avisar se não houver resultados
                        if (!hasResults && debugMode) {
                            plugin.getLogger().warning("A consulta não retornou resultados para " + tableName + "." + fieldKey);
                            // Executar uma consulta de diagnóstico para verificar se há dados na tabela
                            try (Statement diagStmt = connection.createStatement();
                                 ResultSet diagRs = diagStmt.executeQuery("SELECT COUNT(*) FROM " + tableInfo.name)) {
                                if (diagRs.next()) {
                                    int count = diagRs.getInt(1);
                                    plugin.getLogger().info("A tabela " + tableInfo.name + " contém " + count + " registros");
                                    
                                    if (count > 0) {
                                        // Verificar se a coluna tem valores não nulos
                                        try (ResultSet colRs = diagStmt.executeQuery("SELECT COUNT(*) FROM " + tableInfo.name + 
                                                " WHERE " + fieldInfo.column + " IS NOT NULL")) {
                                            if (colRs.next()) {
                                                int colCount = colRs.getInt(1);
                                                plugin.getLogger().info("A coluna " + fieldInfo.column + " tem " + 
                                                    colCount + " valores não nulos");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Erro ao consultar dados do conector SQL '" + name + "': " + e.getMessage(), e);
            }

            if (debugMode) {
                plugin.getLogger()
                        .info("Retornando " + entries.size() + " entradas para o ranking do conector '" + name + "'");
            }
            return entries;
        }

        /**
         * Fecha a conexão com o banco de dados
         */
        public void close() {
            if (connection != null) {
                try {
                    connection.close();
                    plugin.getLogger().info("Conexão com banco de dados SQL '" + name + "' fechada.");
                } catch (SQLException e) {
                    plugin.getLogger()
                            .warning("Erro ao fechar conexão com banco de dados SQL '" + name + "': " + e.getMessage());
                }
            }
        }

        private String capitalize(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }

    // Classe para armazenar informações de tabela
    private static class TableInfo {
        String name;
        String playerIdColumn;
        String playerNameColumn;
        Map<String, FieldInfo> fields = new HashMap<>();
    }

    // Classe para armazenar informações de campo
    private static class FieldInfo {
        String column;
        String displayName;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}