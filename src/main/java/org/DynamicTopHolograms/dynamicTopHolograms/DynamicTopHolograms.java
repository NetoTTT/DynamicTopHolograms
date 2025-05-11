package org.DynamicTopHolograms.dynamicTopHolograms;

import org.DynamicTopHolograms.dynamicTopHolograms.database.DatabaseConnectorManager;
import org.DynamicTopHolograms.dynamicTopHolograms.messages.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
// Importações da API DecentHolograms
import eu.decentsoftware.holograms.api.DHAPI;

public class DynamicTopHolograms extends JavaPlugin {

    private static DynamicTopHolograms instance;
    private RankingHologramManager rankingHologramManager;
    private ConfigManager configManager;
    private OfflineDataManager offlineDataManager;
    private MessageManager messageManager;
    private long updateIntervalTicks;
    private DatabaseConnectorManager databaseConnectorManager;

    @Override
    public void onEnable() {
        instance = this;

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("PlaceholderAPI não encontrado! Desabilitando DynamicTopHolograms.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") == null) {
            getLogger().severe("DecentHolograms não encontrado! Desabilitando DynamicTopHolograms.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Inicializar gerenciadores
        configManager = new ConfigManager(this);
        
        // Inicializar o gerenciador de mensagens
        messageManager = new MessageManager(this);
        
        offlineDataManager = new OfflineDataManager(this);
        databaseConnectorManager = new DatabaseConnectorManager(this);
        rankingHologramManager = new RankingHologramManager(this, configManager, offlineDataManager,
                databaseConnectorManager);

        // Configurar comandos
        CommandHandler commandHandler = new CommandHandler(this, rankingHologramManager, configManager);
        this.getCommand("dth").setExecutor(commandHandler);
        this.getCommand("dth").setTabCompleter(commandHandler);

        // Carregar hologramas da config
        rankingHologramManager.loadHologramsFromConfig();

        // Configurar intervalo de atualização
        updateIntervalTicks = 20L * 60 * configManager.getUpdateIntervalMinutes();

        // Agendar tarefa de atualização
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            rankingHologramManager.updateAllHolograms();
        }, 20L * 10, updateIntervalTicks); // Delay inicial de 10 segundos

        // Agendar limpeza de dados expirados (uma vez por dia)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            offlineDataManager.cleanupExpiredData();
        }, 20L * 60 * 60, 20L * 60 * 60 * 24); // 1 hora inicial, depois a cada 24 horas

        getLogger().info("DynamicTopHolograms habilitado!");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);

        // Fechar conexões de banco de dados
        if (databaseConnectorManager != null) {
            databaseConnectorManager.closeAll();
        }

        getLogger().info("DynamicTopHolograms desabilitado!");
    }

    public DatabaseConnectorManager getDatabaseConnectorManager() {
        return databaseConnectorManager;
    }

    public static DynamicTopHolograms getInstance() {
        return instance;
    }

    public RankingHologramManager getRankingHologramManager() {
        return rankingHologramManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public OfflineDataManager getOfflineDataManager() {
        return offlineDataManager;
    }
}