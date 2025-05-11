package org.DynamicTopHolograms.dynamicTopHolograms.commands;

import org.DynamicTopHolograms.dynamicTopHolograms.DynamicTopHolograms;
import org.DynamicTopHolograms.dynamicTopHolograms.database.DatabaseConnectorManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TabCompleterOn implements org.bukkit.command.TabCompleter {

    private final DynamicTopHolograms plugin;
    private final DatabaseConnectorManager databaseConnectorManager;

    public TabCompleterOn(DynamicTopHolograms plugin, DatabaseConnectorManager databaseConnectorManager) {
        this.plugin = plugin;
        this.databaseConnectorManager = databaseConnectorManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("dth")) {
            if (args.length == 1) {
                // Comandos principais
                return filterCompletions(new String[] {
                        "create", "set", "title", "order", "remove", "list", "reload", "refresh",
                        "movehere", "move", "setdb", "dbset", "dblist", "help"
                }, args[0]);
            } else if (args.length == 2) {
                // Subcomandos específicos
                switch (args[0].toLowerCase()) {
                    case "set":
                    case "title":
                    case "order":
                    case "remove":
                    case "refresh":
                    case "movehere":
                    case "move":
                    case "setdb":
                    case "dbset":
                        // Listar hologramas existentes
                        return plugin.getRankingHologramManager().getConfiguredHologramIDs().stream()
                                .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                }
            } else if (args.length == 3) {
                // Opções específicas para cada comando
                switch (args[0].toLowerCase()) {
                    case "order":
                        // Sugerir opções de ordenação
                        return filterCompletions(new String[] { "asc", "desc" }, args[2]);
                    case "setdb":
                    case "dbset":
                        // Listar conectores de banco de dados disponíveis
                        return databaseConnectorManager.getAvailableConnectors().stream()
                                .filter(connector -> connector.toLowerCase().startsWith(args[2].toLowerCase()))
                                .collect(Collectors.toList());
                }
            } else if (args.length == 4) {
                // Mais opções específicas
                switch (args[0].toLowerCase()) {
                    case "setdb":
                    case "dbset":
                        // Listar campos disponíveis com formato tabela.campo
                        String connectorName = args[2];
                        List<String> suggestions = new ArrayList<>();

                        // Obter todas as tabelas
                        List<String> tables = databaseConnectorManager.getAvailableTables(connectorName);

                        // Para cada tabela, obter seus campos e criar sugestões no formato tabela.campo
                        for (String table : tables) {
                            List<String> fields = databaseConnectorManager.getTableFields(connectorName, table);
                            for (String field : fields) {
                                String fullField = table + "." + field;
                                if (fullField.toLowerCase().startsWith(args[3].toLowerCase())) {
                                    suggestions.add(fullField);
                                }
                            }
                        }

                        return suggestions;
                }
            } else if (args.length == 5) {
                // Sugestões para o número de jogadores (top N)
                switch (args[0].toLowerCase()) {
                    case "setdb":
                    case "dbset":
                        return Arrays.asList("5", "10", "15", "20", "25", "30");
                }
            }
        }

        return completions;
    }

    private List<String> filterCompletions(String[] options, String input) {
        return Arrays.stream(options)
                .filter(option -> option.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}