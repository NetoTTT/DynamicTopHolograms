package org.DynamicTopHolograms.dynamicTopHolograms.database;

import java.util.List;
import java.util.UUID;

/**
 * Interface para conectores de banco de dados de plugins externos
 */
public interface DatabaseConnector {
    
    /**
     * Inicializa a conexão com o banco de dados
     * @return true se a conexão foi estabelecida com sucesso
     */
    boolean initialize();
    
    /**
     * Verifica se o conector está disponível (configurado corretamente)
     * @return true se o conector está disponível
     */
    boolean isAvailable();
    
    /**
     * Obtém o nome do conector (geralmente o nome do plugin)
     * @return nome do conector
     */
    String getName();
    
    /**
     * Lista os campos disponíveis para ranking no banco de dados
     * @return lista de nomes de campos
     */
    List<String> getAvailableFields();
    
    /**
     * Obtém os dados de ranking para um campo específico
     * @param field campo a ser consultado
     * @param limit número máximo de registros
     * @param ascending true para ordem crescente, false para decrescente
     * @return lista de entradas de dados de jogadores
     */
    List<DatabasePlayerEntry> getTopPlayers(String field, int limit, boolean ascending);
    
    /**
     * Fecha a conexão com o banco de dados
     */
    void close();
}