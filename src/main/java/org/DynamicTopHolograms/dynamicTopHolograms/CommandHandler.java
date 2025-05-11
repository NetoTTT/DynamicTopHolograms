package org.DynamicTopHolograms.dynamicTopHolograms;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final DynamicTopHolograms plugin;
    private final RankingHologramManager hologramManager;
    private final ConfigManager configManager;

    public CommandHandler(DynamicTopHolograms plugin, RankingHologramManager hologramManager,
            ConfigManager configManager) {
        this.plugin = plugin;
        this.hologramManager = hologramManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreate(sender, args);
            case "set":
                return handleSet(sender, args);
            case "title":
                return handleTitle(sender, args);
            case "order":
                return handleOrder(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "reload":
                return handleReload(sender);
            case "refresh":
                return handleRefresh(sender, args);
            case "movehere":
                return handleMoveHere(sender, args);
            case "move":
                return handleMove(sender, args);
            case "dbset":
            case "setdb":
                return handleSetDbCommand(sender, args);
            case "dblist":
                return handleDbList(sender, args);
            case "language":
            case "lang":
                if (!sender.hasPermission("dynamictopholograms.admin")) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                return handleLanguageCommand(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    // Modificação no método handleSetDbCommand para corrigir o problema com o
    // formato do comando
    private boolean handleSetDbCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.dbset")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(getMessage("dbset-usage"));
            return true;
        }

        String hologramId = args[1];
        String connectorName = args[2];
        String fullField = args[3]; // Campo completo (ex: power.level)

        // Verificar se o campo tem o formato correto (contém um ponto)
        if (!fullField.contains(".")) {
            sender.sendMessage(getMessage("dbset-invalid-field-format"));
            return true;
        }

        int topN;
        try {
            topN = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(getMessage("dbset-invalid-number"));
            return true;
        }

        // Verificar se o holograma existe
        if (!hologramManager.hologramExists(hologramId)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramId);
            sender.sendMessage(getMessage("dbset-not-found", replacements));
            return true;
        }

        // Verificar se o conector está disponível
        if (!plugin.getDatabaseConnectorManager().isConnectorAvailable(connectorName)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{connector}", connectorName);
            sender.sendMessage(getMessage("dbset-connector-not-available", replacements));
            return true;
        }

        // Obter configuração atual para preservar título e ordem
        Map<String, Object> currentConfig = hologramManager.getHologramConfig(hologramId);
        String title = (String) currentConfig.getOrDefault("title", "&6&lTop {placeholder_name}");
        String format = "&e{rank}. &f{player} &7- &a{value}"; // Formato padrão
        boolean ascending = (boolean) currentConfig.getOrDefault("ascending", false);

        // Se houver um formato personalizado
        if (args.length > 5) {
            StringBuilder formatBuilder = new StringBuilder();
            for (int i = 5; i < args.length; i++) {
                if (i > 5)
                    formatBuilder.append(" ");
                formatBuilder.append(args[i]);
            }
            format = formatBuilder.toString();
        }

        // Configurar o holograma
        hologramManager.setHologramDatabaseSource(hologramId, connectorName, fullField, topN, title, format, ascending);

        // Obter nome amigável do campo para a mensagem
        String friendlyName = plugin.getDatabaseConnectorManager().getFriendlyFieldName(connectorName, fullField);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("{id}", hologramId);
        replacements.put("{connector}", connectorName);
        replacements.put("{field}", friendlyName);
        replacements.put("{topN}", String.valueOf(topN));
        sender.sendMessage(getMessage("dbset-success", replacements));

        return true;
    }

    // Adicione este método ao CommandHandler
    private boolean handleLanguageCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(getMessage("language-usage"));
            return true;
        }

        String language = args[1].toLowerCase();
        String[] supportedLanguages = plugin.getMessageManager().getSupportedLanguages();

        boolean supported = false;
        for (String lang : supportedLanguages) {
            if (lang.equalsIgnoreCase(language)) {
                supported = true;
                language = lang; // Usar o case correto
                break;
            }
        }

        if (!supported) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{language}", language);
            replacements.put("{supported}", String.join(", ", supportedLanguages));
            sender.sendMessage(getMessage("language-not-supported", replacements));
            return true;
        }

        // Atualizar configuração
        plugin.getConfigManager().updateLanguageSetting(language);

        // Recarregar mensagens
        plugin.getMessageManager().reload();

        Map<String, String> replacements = new HashMap<>();
        replacements.put("{language}", language);
        sender.sendMessage(getMessage("language-changed", replacements));
        return true;
    }

    // Metodo com banco de dados antigo
    private boolean handleDbSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.dbset")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(getMessage("dbset-usage"));
            return true;
        }

        String hologramID = args[1];
        String connectorName = args[2];
        String field = args[3];

        if (!hologramManager.hologramExists(hologramID)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("dbset-not-found", replacements));
            return true;
        }

        // Verificar se o conector está disponível
        if (!plugin.getDatabaseConnectorManager().isConnectorAvailable(connectorName)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{connector}", connectorName);
            sender.sendMessage(getMessage("dbset-connector-not-available", replacements));
            return true;
        }

        // Verificar se o campo está disponível
        List<String> availableFields = plugin.getDatabaseConnectorManager().getAvailableFields(connectorName);
        if (!availableFields.contains(field)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{field}", field);
            replacements.put("{connector}", connectorName);
            sender.sendMessage(getMessage("dbset-field-not-available", replacements));
            return true;
        }

        int topN;
        try {
            topN = Integer.parseInt(args[4]);
            if (topN <= 0) {
                sender.sendMessage(getMessage("dbset-invalid-number"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(getMessage("dbset-invalid-number"));
            return true;
        }

        // Obter configuração atual para preservar título e ordem
        Map<String, Object> currentConfig = hologramManager.getHologramConfig(hologramID);
        String title = (String) currentConfig.getOrDefault("title", "&6&lTop {placeholder_name}");
        String format = "&e{rank}. &f{player} &7- &a{value}"; // Formato padrão
        boolean ascending = (boolean) currentConfig.getOrDefault("ascending", false);

        // Se houver um formato personalizado
        if (args.length > 5) {
            StringBuilder formatBuilder = new StringBuilder();
            for (int i = 5; i < args.length; i++) {
                if (i > 5)
                    formatBuilder.append(" ");
                formatBuilder.append(args[i]);
            }
            format = formatBuilder.toString();
        }

        hologramManager.setHologramDatabaseSource(hologramID, connectorName, field, topN, title, format, ascending);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("{id}", hologramID);
        replacements.put("{connector}", connectorName);
        replacements.put("{field}", field);
        replacements.put("{topN}", String.valueOf(topN));
        sender.sendMessage(getMessage("dbset-success", replacements));

        return true;
    }

    // Metodo para lista as variaveis do banco
    private boolean handleDbList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.dblist")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        List<String> connectors = plugin.getDatabaseConnectorManager().getAvailableConnectors();

        if (connectors.isEmpty()) {
            sender.sendMessage(getMessage("dblist-no-connectors"));
            return true;
        }

        sender.sendMessage(getMessage("dblist-title"));

        for (String connectorName : connectors) {
            sender.sendMessage(getMessage("dblist-connector").replace("{connector}", connectorName));

            // Obter campos com nomes amigáveis
            Map<String, String> fieldsWithNames = plugin.getDatabaseConnectorManager()
                    .getFieldsWithFriendlyNames(connectorName);

            if (fieldsWithNames.isEmpty()) {
                sender.sendMessage(getMessage("dblist-no-fields"));
            } else {
                // Agrupar por categoria (para ValhallaMMO, isso é o tipo de perfil)
                Map<String, List<String>> categorizedFields = new HashMap<>();

                for (Map.Entry<String, String> entry : fieldsWithNames.entrySet()) {
                    String field = entry.getKey();
                    String description = entry.getValue();

                    String category = "Geral";
                    if (description.contains(" - ")) {
                        String[] parts = description.split(" - ", 2);
                        category = parts[0];
                    }

                    if (!categorizedFields.containsKey(category)) {
                        categorizedFields.put(category, new ArrayList<>());
                    }

                    categorizedFields.get(category).add(field + " &8- &7" + description);
                }

                // Exibir campos agrupados
                for (Map.Entry<String, List<String>> entry : categorizedFields.entrySet()) {
                    String category = entry.getKey();
                    List<String> fields = entry.getValue();

                    // Ordenar campos alfabeticamente
                    Collections.sort(fields);

                    sender.sendMessage(getMessage("dblist-category").replace("{category}", category));

                    for (String fieldInfo : fields) {
                        sender.sendMessage(getMessage("dblist-field").replace("{field}", fieldInfo));
                    }
                }
            }
        }

        return true;
    }

    private boolean handleMoveHere(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.move")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("player-only"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(getMessage("movehere-usage"));
            return true;
        }

        String hologramID = args[1];

        if (!hologramManager.hologramExists(hologramID)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("move-not-found", replacements));
            return true;
        }

        Player player = (Player) sender;
        Location location = player.getLocation();

        boolean moved = hologramManager.moveHologram(hologramID, location);

        if (moved) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("move-success", replacements));
        } else {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("move-failed", replacements));
        }

        return true;
    }

    private boolean handleMove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.move")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage(getMessage("move-usage"));
            return true;
        }

        String hologramID = args[1];

        if (!hologramManager.hologramExists(hologramID)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("move-not-found", replacements));
            return true;
        }

        // Obter as coordenadas
        double x, y, z;
        try {
            x = Double.parseDouble(args[2]);
            y = Double.parseDouble(args[3]);
            z = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(getMessage("move-invalid-coords"));
            return true;
        }

        // Obter o mundo (opcional, usa o mundo do jogador ou o mundo padrão)
        org.bukkit.World world;
        if (args.length > 5) {
            world = Bukkit.getWorld(args[5]);
            if (world == null) {
                Map<String, String> replacements = new HashMap<>();
                replacements.put("{world}", args[5]);
                sender.sendMessage(getMessage("move-invalid-world", replacements));
                return true;
            }
        } else if (sender instanceof Player) {
            world = ((Player) sender).getWorld();
        } else {
            world = Bukkit.getWorlds().get(0); // Mundo padrão
        }

        Location location = new Location(world, x, y, z);
        boolean moved = hologramManager.moveHologram(hologramID, location);

        if (moved) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            replacements.put("{x}", String.format("%.2f", x));
            replacements.put("{y}", String.format("%.2f", y));
            replacements.put("{z}", String.format("%.2f", z));
            replacements.put("{world}", world.getName());
            sender.sendMessage(getMessage("move-success-coords", replacements));
        } else {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("move-failed", replacements));
        }

        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.create")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("player-only"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(getMessage("create-usage"));
            return true;
        }

        Player player = (Player) sender;
        String hologramID = args[1];

        // Combina todos os argumentos restantes como título (permitindo espaços)
        StringBuilder titleBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2)
                titleBuilder.append(" ");
            titleBuilder.append(args[i]);
        }
        String title = titleBuilder.toString();

        Location location = player.getLocation();

        boolean created = hologramManager.createHologram(hologramID, location, title);

        if (created) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("create-success", replacements));
        } else {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("create-exists", replacements));
        }

        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.set")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(getMessage("set-usage"));
            return true;
        }

        String hologramID = args[1];
        String placeholder = args[2];

        if (!hologramManager.hologramExists(hologramID)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("set-not-found", replacements));
            return true;
        }

        int topN;
        try {
            topN = Integer.parseInt(args[3]);
            if (topN <= 0) {
                sender.sendMessage(getMessage("set-invalid-number"));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(getMessage("set-invalid-number"));
            return true;
        }

        // Obter configuração atual para preservar título e ordem
        Map<String, Object> currentConfig = hologramManager.getHologramConfig(hologramID);
        String title = (String) currentConfig.getOrDefault("title", "&6&lTop {placeholder_name}");
        String format = "&e{rank}. &f{player} &7- &a{value}"; // Formato padrão
        boolean ascending = (boolean) currentConfig.getOrDefault("ascending", false);

        // Se houver um formato personalizado
        if (args.length > 4) {
            StringBuilder formatBuilder = new StringBuilder();
            for (int i = 4; i < args.length; i++) {
                if (i > 4)
                    formatBuilder.append(" ");
                formatBuilder.append(args[i]);
            }
            format = formatBuilder.toString();
        }

        hologramManager.setHologramConfig(hologramID, placeholder, topN, title, format, ascending);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("{id}", hologramID);
        replacements.put("{placeholder}", placeholder);
        replacements.put("{topN}", String.valueOf(topN));
        sender.sendMessage(getMessage("set-success", replacements));

        return true;
    }

    private boolean handleTitle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.title")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(getMessage("title-usage"));
            return true;
        }

        String hologramID = args[1];

        if (!hologramManager.hologramExists(hologramID)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("title-not-found", replacements));
            return true;
        }

        // Combina todos os argumentos restantes como título
        StringBuilder titleBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2)
                titleBuilder.append(" ");
            titleBuilder.append(args[i]);
        }
        String title = titleBuilder.toString();

        hologramManager.setHologramTitle(hologramID, title);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("{id}", hologramID);
        replacements.put("{title}", title);
        sender.sendMessage(getMessage("title-success", replacements));

        return true;
    }

    private boolean handleOrder(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.order")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(getMessage("order-usage"));
            return true;
        }

        String hologramID = args[1];
        String orderType = args[2].toLowerCase();

        if (!hologramManager.hologramExists(hologramID)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("order-not-found", replacements));
            return true;
        }

        boolean ascending;
        String orderName;

        if (orderType.equals("asc") || orderType.equals("ascending")) {
            ascending = true;
            orderName = getMessage("order-asc");
        } else if (orderType.equals("desc") || orderType.equals("descending")) {
            ascending = false;
            orderName = getMessage("order-desc");
        } else {
            sender.sendMessage(getMessage("order-usage"));
            return true;
        }

        hologramManager.setHologramOrder(hologramID, ascending);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("{id}", hologramID);
        replacements.put("{order}", orderName);
        sender.sendMessage(getMessage("order-success", replacements));

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.remove")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(getMessage("remove-usage"));
            return true;
        }

        String hologramID = args[1];

        if (!hologramManager.hologramExists(hologramID)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("remove-not-found", replacements));
            return true;
        }

        boolean deletePhysical = true; // Por padrão, remove o holograma físico também

        hologramManager.removeHologram(hologramID, deletePhysical);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("{id}", hologramID);
        sender.sendMessage(getMessage("remove-success", replacements));

        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("dynamictopholograms.list")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        Set<String> hologramIDs = hologramManager.getConfiguredHologramIDs();

        if (hologramIDs.isEmpty()) {
            sender.sendMessage(getMessage("list-empty"));
            return true;
        }

        sender.sendMessage(getMessage("list-title"));
        for (String id : hologramIDs) {
            Map<String, Object> config = hologramManager.getHologramConfig(id);
            if (config != null) {
                Map<String, String> replacements = new HashMap<>();
                replacements.put("{id}", id);
                replacements.put("{placeholder}", (String) config.get("placeholder"));
                replacements.put("{topN}", String.valueOf(config.get("topN")));
                sender.sendMessage(getMessage("list-entry", replacements));
            }
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("dynamictopholograms.reload")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        configManager.reloadAll();
        sender.sendMessage(getMessage("reload-success"));

        return true;
    }

    private boolean handleRefresh(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dynamictopholograms.refresh")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length > 1) {
            String hologramID = args[1];

            if (!hologramManager.hologramExists(hologramID)) {
                Map<String, String> replacements = new HashMap<>();
                replacements.put("{id}", hologramID);
                sender.sendMessage(getMessage("refresh-not-found", replacements));
                return true;
            }

            hologramManager.forceUpdateHologram(hologramID);

            Map<String, String> replacements = new HashMap<>();
            replacements.put("{id}", hologramID);
            sender.sendMessage(getMessage("refresh-specific", replacements));
        } else {
            hologramManager.updateAllHolograms();
            sender.sendMessage(getMessage("refresh-all"));
        }

        return true;
    }

    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(getMessage("help-title"));
        sender.sendMessage(getMessage("help-create"));
        sender.sendMessage(getMessage("help-set"));
        sender.sendMessage(getMessage("help-title-cmd"));
        sender.sendMessage(getMessage("help-order"));
        sender.sendMessage(getMessage("help-remove"));
        sender.sendMessage(getMessage("help-list"));
        sender.sendMessage(getMessage("help-reload"));
        sender.sendMessage(getMessage("help-refresh"));
        sender.sendMessage(getMessage("help-movehere"));
        sender.sendMessage(getMessage("help-move"));
        sender.sendMessage(getMessage("help-dbset"));
        sender.sendMessage(getMessage("help-dblist"));
    }

    private String getMessage(String key) {
        return plugin.getMessageManager().getMessage(key);
    }

    private String getMessage(String key, Map<String, String> replacements) {
        return plugin.getMessageManager().getMessage(key, replacements);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcomandos principais
            String[] subCommands = { "help", "create", "set", "title", "order", "remove", "list", "reload", "refresh",
                    "movehere", "move", "dbset", "dblist" };
            return filterCompletions(subCommands, args[0]);
        } else if (args.length == 2) {
            // Para comandos que precisam de hologramID
            if (args[0].equalsIgnoreCase("set") ||
                    args[0].equalsIgnoreCase("title") ||
                    args[0].equalsIgnoreCase("order") ||
                    args[0].equalsIgnoreCase("remove") ||
                    args[0].equalsIgnoreCase("refresh") ||
                    args[0].equalsIgnoreCase("movehere") ||
                    args[0].equalsIgnoreCase("move") ||
                    args[0].equalsIgnoreCase("dbset")) {
                return filterCompletions(
                        hologramManager.getConfiguredHologramIDs().toArray(new String[0]),
                        args[1]);
            }
        } else if (args.length == 3) {
            // Para o comando order, sugerir asc/desc
            if (args[0].equalsIgnoreCase("order")) {
                return filterCompletions(new String[] { "asc", "desc" }, args[2]);
            }
            // Para o comando dbset, sugerir conectores disponíveis
            else if (args[0].equalsIgnoreCase("dbset")) {
                return filterCompletions(
                        plugin.getDatabaseConnectorManager().getAvailableConnectors().toArray(new String[0]),
                        args[2]);
            }
        } else if (args.length == 4) {
            // Para o comando dbset, sugerir campos disponíveis com nomes amigáveis
            if (args[0].equalsIgnoreCase("dbset")) {
                String connectorName = args[2];
                if (plugin.getDatabaseConnectorManager().isConnectorAvailable(connectorName)) {
                    Map<String, String> fieldsWithNames = plugin.getDatabaseConnectorManager()
                            .getFieldsWithFriendlyNames(connectorName);

                    // Criar lista de sugestões com descrições
                    List<String> suggestions = new ArrayList<>();
                    for (Map.Entry<String, String> entry : fieldsWithNames.entrySet()) {
                        String field = entry.getKey();
                        String description = entry.getValue();

                        // Adicionar à lista se corresponder ao que o usuário está digitando
                        if (field.toLowerCase().startsWith(args[3].toLowerCase())) {
                            suggestions.add(field);
                        }
                    }

                    return suggestions;
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