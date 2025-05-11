package org.DynamicTopHolograms.dynamicTopHolograms.messages;

import org.DynamicTopHolograms.dynamicTopHolograms.DynamicTopHolograms;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class MessageManager {

    private final DynamicTopHolograms plugin;
    private FileConfiguration messages;
    private File messagesFile;
    private String language;

    // Idiomas suportados
    // Em um proximo Update deixo modular para implementa um arquivo não catalogado
    private final String[] supportedLanguages = { "en", "pt_BR" };

    public MessageManager(DynamicTopHolograms plugin) {
        this.plugin = plugin;
        initialize();
    }

    /**
     * Inicializa o gerenciador de mensagens
     */
    private void initialize() {
        // Carregar idioma da configuração
        language = plugin.getConfigManager().getConfig().getString("language", "en");
        plugin.getLogger().info("Tentando carregar idioma: " + language);

        // Criar pasta de mensagens se não existir
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
            plugin.getLogger().info("Pasta de idiomas criada: " + langFolder.getAbsolutePath());
        } else {
            plugin.getLogger().info("Pasta de idiomas já existe: " + langFolder.getAbsolutePath());
        }

        // Listar arquivos de recursos disponíveis
        try {
            plugin.getLogger().info("Listando recursos disponíveis:");
            for (String resource : getResourceListing(plugin.getClass(), "lang/")) {
                plugin.getLogger().info("Recurso encontrado: lang/" + resource);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao listar recursos: " + e.getMessage());
        }

        // Salvar todos os arquivos de idioma padrão
        for (String lang : supportedLanguages) {
            saveDefaultLanguageFile(lang);
        }

        // Carregar arquivo de idioma selecionado
        loadLanguageFile();
    }

    /**
     * Salva um arquivo de idioma padrão se ele não existir
     */
    private void saveDefaultLanguageFile(String lang) {
        File langFile = new File(plugin.getDataFolder() + "/lang", lang + ".yml");

        if (!langFile.exists()) {
            try {
                plugin.getLogger().info("Tentando salvar arquivo de idioma: " + lang + ".yml");
                InputStream defaultLangStream = plugin.getResource("lang/" + lang + ".yml");

                if (defaultLangStream != null) {
                    plugin.getLogger().info("Recurso de idioma encontrado para: " + lang);
                    YamlConfiguration defaultLang = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(defaultLangStream, StandardCharsets.UTF_8));
                    defaultLang.save(langFile);
                    plugin.getLogger().info(
                            "Arquivo de idioma " + lang + ".yml criado com sucesso em: " + langFile.getAbsolutePath());
                } else {
                    plugin.getLogger().warning("Arquivo de idioma padrão não encontrado: " + lang + ".yml");

                    // Como fallback, criar um arquivo básico
                    YamlConfiguration emptyLang = new YamlConfiguration();
                    emptyLang.set("prefix", "&8[&bDTH&8] &r");
                    emptyLang.set("no-permission", "&cYou don't have permission to use this command.");
                    emptyLang.save(langFile);
                    plugin.getLogger().info("Criado arquivo de idioma básico para: " + lang);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar o arquivo de idioma " + lang + ".yml", e);
            }
        } else {
            plugin.getLogger().info("Arquivo de idioma já existe: " + langFile.getAbsolutePath());
        }
    }

    // Método auxiliar para listar recursos
    private Set<String> getResourceListing(Class<?> clazz, String path) throws Exception {
        Set<String> result = new HashSet<>();

        // Obter o URL do diretório de recursos
        java.net.URL dirURL = clazz.getClassLoader().getResource(path);
        if (dirURL != null && dirURL.getProtocol().equals("file")) {
            // Se estamos executando a partir de um arquivo (desenvolvimento)
            File dir = new File(dirURL.toURI());
            for (File file : dir.listFiles()) {
                result.add(file.getName());
            }
        } else {
            // Se estamos executando a partir de um JAR
            String jarPath = clazz.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            if (jarPath.endsWith(".jar")) {
                // É um JAR, vamos listar as entradas
                java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath);
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(path) && !name.equals(path)) {
                        String entry = name.substring(path.length());
                        int checkSubdir = entry.indexOf("/");
                        if (checkSubdir >= 0) {
                            entry = entry.substring(0, checkSubdir);
                        }
                        result.add(entry);
                    }
                }
                jar.close();
            }
        }

        return result;
    }

    /**
     * Carrega o arquivo de idioma selecionado
     */
    private void loadLanguageFile() {
        messagesFile = new File(plugin.getDataFolder() + "/lang", language + ".yml");

        // Se o idioma selecionado não existir, usar inglês como padrão
        if (!messagesFile.exists()) {
            plugin.getLogger()
                    .warning("Arquivo de idioma " + language + ".yml não encontrado. Usando inglês como padrão.");
            language = "en";
            messagesFile = new File(plugin.getDataFolder() + "/lang", "en.yml");
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("Arquivo de idioma " + language + ".yml carregado com sucesso.");

        // Verificar se há atualizações no arquivo de idioma padrão
        updateLanguageFile();
    }

    /**
     * Atualiza o arquivo de idioma com novas mensagens do arquivo padrão
     */
    private void updateLanguageFile() {
        InputStream defaultLangStream = plugin.getResource("lang/" + language + ".yml");
        if (defaultLangStream == null) {
            return;
        }

        YamlConfiguration defaultLang = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultLangStream, StandardCharsets.UTF_8));

        boolean updated = false;

        // Adicionar mensagens que estão faltando
        for (String key : defaultLang.getKeys(true)) {
            if (!defaultLang.isConfigurationSection(key) && !messages.contains(key)) {
                messages.set(key, defaultLang.get(key));
                updated = true;
                plugin.getLogger().info("Adicionada nova mensagem ao arquivo de idioma: " + key);
            }
        }

        if (updated) {
            try {
                messages.save(messagesFile);
                plugin.getLogger().info("Arquivo de idioma " + language + ".yml atualizado com novas mensagens.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Não foi possível salvar atualizações no arquivo de idioma", e);
            }
        }
    }

    /**
     * Obtém uma mensagem do arquivo de idioma
     * 
     * @param key chave da mensagem
     * @return mensagem formatada com códigos de cor
     */
    public String getMessage(String key) {
        String message = messages.getString(key);
        if (message == null) {
            return "Missing message: " + key;
        }
        return message.replace('&', '§');
    }

    /**
     * Obtém uma mensagem do arquivo de idioma com substituições
     * 
     * @param key          chave da mensagem
     * @param replacements mapa de substituições (placeholder -> valor)
     * @return mensagem formatada com códigos de cor e substituições
     */
    public String getMessage(String key, Map<String, String> replacements) {
        String message = getMessage(key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return message;
    }

    /**
     * Recarrega o arquivo de idioma
     */
    public void reload() {
        // Atualizar idioma da configuração
        language = plugin.getConfigManager().getConfig().getString("language", "en");
        loadLanguageFile();
    }

    /**
     * Obtém o idioma atual
     * 
     * @return código do idioma atual
     */
    public String getCurrentLanguage() {
        return language;
    }

    /**
     * Lista todos os idiomas suportados
     * 
     * @return array de códigos de idioma
     */
    public String[] getSupportedLanguages() {
        return supportedLanguages;
    }
}