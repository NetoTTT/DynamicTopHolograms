package org.DynamicTopHolograms.dynamicTopHolograms.database.connectors;

import org.DynamicTopHolograms.dynamicTopHolograms.DynamicTopHolograms;
import org.DynamicTopHolograms.dynamicTopHolograms.database.DatabaseConnector;
import org.DynamicTopHolograms.dynamicTopHolograms.database.DatabasePlayerEntry;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;

public class ValhallaMMOConnector implements DatabaseConnector {

    private final DynamicTopHolograms plugin;
    private Connection connection;
    private boolean available = false;
    private String dbPath;

    // Mapeamento de tabelas e seus campos para nomes amigáveis
    private final Map<String, Map<String, String>> tableFieldsMap = new HashMap<>();

    // Lista de tabelas de perfil do ValhallaMMO
    private final List<String> profileTables = Arrays.asList(
            "profiles_power", "profiles_alchemy", "profiles_smithing", "profiles_enchanting",
            "profiles_heavy_weapons", "profiles_light_weapons", "profiles_archery",
            "profiles_heavy_armor", "profiles_light_armor", "profiles_mining",
            "profiles_farming", "profiles_woodcutting", "profiles_digging", "profiles_fishing");

    public ValhallaMMOConnector(DynamicTopHolograms plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean initialize() {
        // Verificar se o plugin ValhallaMMO está presente
        if (Bukkit.getPluginManager().getPlugin("ValhallaMMO") == null) {
            plugin.getLogger().info("ValhallaMMO não encontrado, conector não disponível.");
            return false;
        }

        // Tentar encontrar o arquivo de configuração do ValhallaMMO
        File valhallaConfigFile = new File(
                plugin.getServer().getPluginManager().getPlugin("ValhallaMMO").getDataFolder(), "config.yml");
        if (!valhallaConfigFile.exists()) {
            plugin.getLogger().warning("Arquivo de configuração do ValhallaMMO não encontrado.");
            return false;
        }

        // Carregar configuração do ValhallaMMO
        FileConfiguration valhallaConfig = YamlConfiguration.loadConfiguration(valhallaConfigFile);

        // Verificar o tipo de armazenamento
        String storageType = valhallaConfig.getString("storage-method", "sqlite");

        try {
            if ("sqlite".equalsIgnoreCase(storageType)) {
                // Configurar para SQLite
                dbPath = new File(plugin.getServer().getPluginManager().getPlugin("ValhallaMMO").getDataFolder(),
                        "player_data.db").getAbsolutePath();

                // Carregar o driver JDBC para SQLite
                Class.forName("org.sqlite.JDBC");

                // Estabelecer conexão
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            } else if ("mysql".equalsIgnoreCase(storageType)) {
                // Configurar para MySQL
                String host = valhallaConfig.getString("mysql.host", "localhost");
                int port = valhallaConfig.getInt("mysql.port", 3306);
                String database = valhallaConfig.getString("mysql.database", "minecraft");
                String username = valhallaConfig.getString("mysql.username", "root");
                String password = valhallaConfig.getString("mysql.password", "");

                // Carregar o driver JDBC para MySQL
                Class.forName("com.mysql.jdbc.Driver");

                // Estabelecer conexão
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
                connection = DriverManager.getConnection(url, username, password);

            } else {
                plugin.getLogger().warning("Método de armazenamento não suportado: " + storageType);
                return false;
            }

            // Inicializar mapeamento de campos
            initializeFieldMappings();

            available = true;
            plugin.getLogger().info("Conexão com banco de dados do ValhallaMMO estabelecida com sucesso.");
            return true;

        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().severe("Erro ao conectar ao banco de dados do ValhallaMMO: " + e.getMessage());
            return false;
        }
    }

    /**
     * Inicializa o mapeamento de campos para nomes amigáveis
     */
    private void initializeFieldMappings() {
        try {
            // Para cada tabela de perfil
            for (String tableName : profileTables) {
                Map<String, String> fieldMap = new HashMap<>();

                // Verificar se estamos usando SQLite ou MySQL
                boolean isSQLite = connection.getMetaData().getDriverName().toLowerCase().contains("sqlite");
                plugin.getLogger().info("Banco de dados detectado: " + (isSQLite ? "SQLite" : "MySQL"));

                // Verificar se a tabela existe antes de continuar
                boolean tableExists = false;
                try (Statement stmt = connection.createStatement()) {
                    if (isSQLite) {
                        ResultSet rs = stmt.executeQuery(
                                "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'");
                        tableExists = rs.next();
                        rs.close();
                    } else {
                        ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE '" + tableName + "'");
                        tableExists = rs.next();
                        rs.close();
                    }
                }

                if (!tableExists) {
                    plugin.getLogger().warning("Tabela " + tableName + " não encontrada no banco de dados.");
                    continue;
                }

                // Depuração: imprimir informações da tabela
                plugin.getLogger().info("Analisando tabela: " + tableName);

                // Lista para armazenar todas as colunas da tabela
                List<String> allColumns = new ArrayList<>();

                // Primeiro, colete todas as colunas da tabela
                try (Statement stmt = connection.createStatement()) {
                    ResultSet columns;

                    if (isSQLite) {
                        columns = stmt.executeQuery("PRAGMA table_info(" + tableName + ")");
                        while (columns.next()) {
                            String columnName = columns.getString("name");
                            String dataType = columns.getString("type");
                            plugin.getLogger().info("- Coluna: " + columnName + " (" + dataType + ")");

                            // Ignorar colunas específicas que sabemos que não são para ranking
                            if (!shouldIgnoreColumn(columnName)) {
                                allColumns.add(columnName);
                            }
                        }
                    } else {
                        columns = stmt.executeQuery("SHOW COLUMNS FROM " + tableName);
                        while (columns.next()) {
                            String columnName = columns.getString("Field");
                            String dataType = columns.getString("Type");
                            plugin.getLogger().info("- Coluna: " + columnName + " (" + dataType + ")");

                            // Filtrar apenas campos numéricos e ignorar campos específicos
                            if (isNumericType(dataType) && !shouldIgnoreColumn(columnName)) {
                                allColumns.add(columnName);
                            }
                        }
                    }
                    columns.close();
                }

                // Agora, verifique cada coluna para ver se é numérica
                for (String columnName : allColumns) {
                    try (Statement stmt = connection.createStatement()) {
                        boolean isNumeric = false;

                        // Para SQLite, precisamos verificar se a coluna contém valores numéricos
                        if (isSQLite) {
                            try {
                                // Tentativa 1: Verificar se podemos fazer operações numéricas com a coluna
                                String testQuery = "SELECT " + columnName + " + 0 FROM " + tableName + " LIMIT 1";
                                ResultSet testRs = stmt.executeQuery(testQuery);
                                isNumeric = true; // Se não lançar exceção, é numérico
                                testRs.close();
                            } catch (SQLException e) {
                                // Se ocorrer um erro, tentar outra abordagem
                                try {
                                    // Tentativa 2: Verificar o tipo dos valores na coluna
                                    String typeQuery = "SELECT typeof(" + columnName + ") AS type FROM " + tableName +
                                            " WHERE " + columnName + " IS NOT NULL LIMIT 1";
                                    ResultSet typeRs = stmt.executeQuery(typeQuery);
                                    if (typeRs.next()) {
                                        String type = typeRs.getString("type").toLowerCase();
                                        isNumeric = type.equals("integer") || type.equals("real")
                                                || type.equals("numeric");
                                    }
                                    typeRs.close();
                                } catch (SQLException e2) {
                                    // Se ambas as tentativas falharem, a coluna provavelmente não é numérica
                                    plugin.getLogger().info("  > Coluna " + columnName + " não parece ser numérica: "
                                            + e2.getMessage());
                                    continue;
                                }
                            }
                        } else {
                            // Para MySQL, já filtramos pelo tipo acima, então todas as colunas em
                            // allColumns são numéricas
                            isNumeric = true;
                        }

                        if (isNumeric) {
                            // Criar nome amigável (converter snake_case para Title Case)
                            String friendlyName = makeNameFriendly(columnName);
                            fieldMap.put(columnName, friendlyName);
                            plugin.getLogger()
                                    .info("  > Campo numérico adicionado: " + columnName + " -> " + friendlyName);
                        } else {
                            plugin.getLogger().info("  > Coluna " + columnName + " não contém valores numéricos");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erro ao verificar coluna " + columnName + ": " + e.getMessage());
                    }
                }

                // Adicionar ao mapa de tabelas
                if (!fieldMap.isEmpty()) {
                    String profileType = tableName.replace("profiles_", "");
                    tableFieldsMap.put(profileType, fieldMap);
                    plugin.getLogger().info("Adicionado perfil " + profileType + " com " + fieldMap.size() + " campos");
                } else {
                    plugin.getLogger().info("Nenhum campo numérico encontrado para " + tableName);
                }
            }

            plugin.getLogger().info("Mapeamento de campos do ValhallaMMO inicializado com " +
                    tableFieldsMap.size() + " tipos de perfil.");

            // Depuração: listar todos os campos mapeados
            for (Map.Entry<String, Map<String, String>> profileEntry : tableFieldsMap.entrySet()) {
                plugin.getLogger().info("Perfil: " + profileEntry.getKey() + " - Campos: " +
                        String.join(", ", profileEntry.getValue().keySet()));
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Erro ao inicializar mapeamento de campos do ValhallaMMO: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Verifica se uma coluna deve ser ignorada para ranking
     */
    private boolean shouldIgnoreColumn(String columnName) {
        return columnName.equals("owner") ||
                columnName.equals("player_uuid") ||
                columnName.equals("player_name") ||
                columnName.equals("last_updated") ||
                columnName.contains("unlocked") ||
                columnName.contains("effects") ||
                columnName.contains("blocks") ||
                columnName.contains("recipes") ||
                columnName.contains("perks") ||
                columnName.equals("newgameplus") ||
                columnName.equals("maxallowedlevel");
    }

    /**
     * Verifica se um tipo de dados MySQL é numérico
     */
    private boolean isNumericType(String dataType) {
        String upperType = dataType.toUpperCase();
        return upperType.contains("INT") ||
                upperType.contains("FLOAT") ||
                upperType.contains("DOUBLE") ||
                upperType.contains("DECIMAL") ||
                upperType.contains("NUMERIC");
    }

    /**
     * Obtém a lista de tabelas disponíveis (que têm campos numéricos)
     * 
     * @return lista de nomes de tabelas
     */
    public List<String> getAvailableTables() {
        return new ArrayList<>(tableFieldsMap.keySet());
    }

    /**
     * Obtém a lista de campos disponíveis para uma tabela específica
     * 
     * @param tableName nome da tabela (sem o prefixo "profiles_")
     * @return lista de nomes de campos
     */
    public List<String> getTableFields(String tableName) {
        Map<String, String> fields = tableFieldsMap.get(tableName);
        if (fields == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(fields.keySet());
    }

    /**
     * Converte um nome de campo do banco de dados para um formato mais amigável
     */
    private String makeNameFriendly(String dbFieldName) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = true;

        for (char c : dbFieldName.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getName() {
        return "ValhallaMMO";
    }

    @Override
    public List<String> getAvailableFields() {
        List<String> fields = new ArrayList<>();

        // Para cada tipo de perfil
        for (Map.Entry<String, Map<String, String>> profileEntry : tableFieldsMap.entrySet()) {
            String profileType = profileEntry.getKey();

            // Para cada campo no perfil
            for (Map.Entry<String, String> fieldEntry : profileEntry.getValue().entrySet()) {
                String dbField = fieldEntry.getKey();

                // Adicionar no formato "tipo_perfil.campo"
                fields.add(profileType + "." + dbField);
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

        // Para cada tipo de perfil
        for (Map.Entry<String, Map<String, String>> profileEntry : tableFieldsMap.entrySet()) {
            String profileType = profileEntry.getKey();

            // Para cada campo no perfil
            for (Map.Entry<String, String> fieldEntry : profileEntry.getValue().entrySet()) {
                String dbField = fieldEntry.getKey();
                String friendlyName = fieldEntry.getValue();

                // Adicionar no formato "tipo_perfil.campo" -> "Tipo Perfil - Nome Amigável"
                String key = profileType + "." + dbField;
                String value = capitalize(profileType) + " - " + friendlyName;
                result.put(key, value);
            }
        }

        return result;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // Modificação no método getTopPlayers para aceitar colunas vazias (sem dados)
    @Override
    public List<DatabasePlayerEntry> getTopPlayers(String field, int limit, boolean ascending) {
        List<DatabasePlayerEntry> entries = new ArrayList<>();

        if (!available || connection == null) {
            plugin.getLogger().warning("Conector ValhallaMMO não está disponível ou conexão é nula");
            return entries;
        }

        try {
            // Verificar se estamos usando SQLite ou MySQL
            boolean isSQLite = connection.getMetaData().getDriverName().toLowerCase().contains("sqlite");

            // Separar o tipo de perfil e o campo
            String[] parts = field.split("\\.");
            if (parts.length != 2) {
                plugin.getLogger().warning("Formato de campo inválido para ValhallaMMO: " + field);
                return entries;
            }

            String profileType = parts[0];
            String columnName = parts[1];
            String tableName = "profiles_" + profileType;

            plugin.getLogger().info("Consultando tabela " + tableName + ", campo " + columnName);

            // Verificar se a tabela existe
            boolean tableExists = false;
            try (Statement stmt = connection.createStatement()) {
                if (isSQLite) {
                    ResultSet rs = stmt.executeQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'");
                    tableExists = rs.next();
                    rs.close();
                } else {
                    ResultSet rs = stmt.executeQuery(
                            "SHOW TABLES LIKE '" + tableName + "'");
                    tableExists = rs.next();
                    rs.close();
                }
            }

            if (!tableExists) {
                plugin.getLogger().warning("Tabela não encontrada: " + tableName);
                return entries;
            }

            // Verificar se a coluna existe
            boolean columnExists = false;
            try (Statement stmt = connection.createStatement()) {
                if (isSQLite) {
                    ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")");
                    while (rs.next()) {
                        if (columnName.equals(rs.getString("name"))) {
                            columnExists = true;
                            break;
                        }
                    }
                    rs.close();
                } else {
                    ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM " + tableName + " LIKE '" + columnName + "'");
                    columnExists = rs.next();
                    rs.close();
                }
            }

            // Se a coluna não existir, retornar lista vazia
            if (!columnExists) {
                plugin.getLogger().warning("Coluna não encontrada: " + columnName + " na tabela " + tableName);
                return entries;
            }

            // Verificar se a coluna tem dados não nulos
            boolean hasData = false;
            try (Statement stmt = connection.createStatement()) {
                String countQuery = "SELECT COUNT(*) as count FROM " + tableName +
                        " WHERE " + columnName + " IS NOT NULL";
                ResultSet rs = stmt.executeQuery(countQuery);
                if (rs.next()) {
                    int count = rs.getInt("count");
                    hasData = count > 0;
                    plugin.getLogger().info("Coluna " + columnName + " tem " + count + " registros não nulos");
                }
                rs.close();
            }

            // Se a coluna existe mas não tem dados, retornar lista vazia
            // mas não lançar erro, para permitir configuração prévia
            if (!hasData) {
                plugin.getLogger().info("Coluna " + columnName + " existe mas não tem dados ainda. " +
                        "Retornando lista vazia para permitir configuração prévia.");
                return entries;
            }

            // Consultar os dados
            String orderDirection = ascending ? "ASC" : "DESC";
            String query = "SELECT owner, " + columnName +
                    " FROM " + tableName +
                    " WHERE " + columnName + " IS NOT NULL " +
                    " ORDER BY " + columnName + " " + orderDirection +
                    " LIMIT ?";

            plugin.getLogger().info("Executando consulta: " + query.replace("?", String.valueOf(limit)));

            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, limit);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String uuidString = rs.getString("owner");
                double value = rs.getDouble(columnName);

                // ValhallaMMO armazena UUIDs sem hífens
                if (uuidString != null && uuidString.length() == 32) {
                    uuidString = uuidString.substring(0, 8) + "-" +
                            uuidString.substring(8, 12) + "-" +
                            uuidString.substring(12, 16) + "-" +
                            uuidString.substring(16, 20) + "-" +
                            uuidString.substring(20);
                }

                UUID uuid;
                try {
                    if (uuidString == null) {
                        plugin.getLogger().warning("UUID nulo encontrado no banco de dados");
                        continue;
                    }
                    uuid = UUID.fromString(uuidString);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID inválido no banco de dados: " + uuidString);
                    continue;
                }

                // Obter nome do jogador (pode ser necessário consultar outra tabela)
                String playerName = getPlayerName(uuid);

                entries.add(new DatabasePlayerEntry(uuid, playerName, value));
                plugin.getLogger().info("Adicionado jogador ao ranking: " + playerName + " com valor " + value);
            }

            rs.close();
            stmt.close();

        } catch (SQLException e) {
            plugin.getLogger().warning("Erro ao consultar dados do ValhallaMMO: " + e.getMessage());
            e.printStackTrace();
        }

        plugin.getLogger().info("Retornando " + entries.size() + " entradas para o ranking");
        return entries;
    }

    /**
     * Obtém o nome do jogador a partir do UUID
     */
    private String getPlayerName(UUID uuid) {
        // Primeiro tenta obter do cache do Bukkit
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }

        // Se não encontrar, tenta consultar a tabela de jogadores do ValhallaMMO
        try {
            String uuidString = uuid.toString().replace("-", "");

            // Verificar se a tabela valhalla_players existe
            boolean tableExists = false;
            try (Statement stmt = connection.createStatement()) {
                boolean isSQLite = connection.getMetaData().getDriverName().toLowerCase().contains("sqlite");

                if (isSQLite) {
                    ResultSet rs = stmt.executeQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' AND name='valhalla_players'");
                    tableExists = rs.next();
                    rs.close();
                } else {
                    ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'valhalla_players'");
                    tableExists = rs.next();
                    rs.close();
                }
            }

            if (tableExists) {
                String query = "SELECT player_name FROM valhalla_players WHERE player_uuid = ?";
                PreparedStatement stmt = connection.prepareStatement(query);
                stmt.setString(1, uuidString);

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String playerName = rs.getString("player_name");
                    rs.close();
                    stmt.close();
                    return playerName;
                }

                rs.close();
                stmt.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Erro ao consultar nome do jogador: " + e.getMessage());
        }

        // Se tudo falhar, retorna um nome baseado no UUID
        return "Player-" + uuid.toString().substring(0, 8);
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Conexão com banco de dados do ValhallaMMO fechada.");
            } catch (SQLException e) {
                plugin.getLogger()
                        .warning("Erro ao fechar conexão com banco de dados do ValhallaMMO: " + e.getMessage());
            }
        }
    }

    /**
     * Obtém o nome amigável de um campo
     */
    public String getFriendlyFieldName(String field) {
        String[] parts = field.split("\\.");
        if (parts.length != 2) {
            return field;
        }

        String profileType = parts[0];
        String columnName = parts[1];

        Map<String, String> fieldMap = tableFieldsMap.get(profileType);
        if (fieldMap == null) {
            return field;
        }

        String friendlyName = fieldMap.get(columnName);
        if (friendlyName == null) {
            return field;
        }

        return capitalize(profileType) + " - " + friendlyName;
    }
}