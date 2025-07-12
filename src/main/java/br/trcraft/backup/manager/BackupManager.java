package br.trcraft.backup.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class BackupManager {

    private final JavaPlugin plugin;
    private final Backup fileBackup;
    private MySQLBackupMethod mySQLBackupMethod;
    private SFTPManager sftp;

    private volatile boolean backupRunning = false;
    private volatile boolean cancelRequested = false;

    public BackupManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.fileBackup = new Backup(plugin);
        initMySQLBackupMethod();
        setupSFTP();
    }

    private void initMySQLBackupMethod() {
        FileConfiguration config = plugin.getConfig();

        if (!config.getBoolean("mysql.enable", false)) {
            plugin.getLogger().info("Backup MySQL desabilitado.");
            this.mySQLBackupMethod = null;
            return;
        }

        String method = config.getString("mysql.method", "mysqldump").toLowerCase();

        switch (method) {
            case "jdbc":
                plugin.getLogger().info("Backup MySQL via JDBC habilitado.");
                this.mySQLBackupMethod = new MySQLJdbcBackup(plugin);
                break;
            case "mysqldump":
            default:
                plugin.getLogger().info("Backup MySQL via mysqldump habilitado.");
                this.mySQLBackupMethod = new MySQLDumpBackup(plugin);
                break;
        }
    }

    private void setupSFTP() {
        FileConfiguration config = plugin.getConfig();

        if (config.getBoolean("ftp.enable", false)) {
            String hostname = config.getString("ftp.hostname", "localhost");
            int port = config.getInt("ftp.port", 22);
            String username = config.getString("ftp.username", "root");
            String password = config.getString("ftp.password", "");
            String saveFolder = config.getString("ftp.saveLocation", "BACKUP");

            this.sftp = new SFTPManager(hostname, port, username, password, saveFolder, plugin);
            plugin.getLogger().info("SFTP ativado e configurado.");
        } else {
            this.sftp = null;
            plugin.getLogger().info("SFTP desativado nas configurações.");
        }
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        setupSFTP();
        initMySQLBackupMethod();
        plugin.getLogger().info("Configurações recarregadas com sucesso.");
    }

    public synchronized boolean isBackupRunning() {
        return backupRunning;
    }

    private synchronized void setBackupRunning(boolean running) {
        this.backupRunning = running;
    }

    /**
     * Solicita o cancelamento do backup em andamento.
     */
    public synchronized void cancelBackup(CommandSender sender) {
        if (!backupRunning) {
            if (sender != null) {
                sendMessage(sender, "§cNão há backup em andamento para cancelar.");
            }
            return;
        }
        cancelRequested = true;
        if (sender != null) {
            sendMessage(sender, "§eSolicitação de cancelamento do backup recebida. Abortando...");
        }
        plugin.getLogger().info("Solicitação de cancelamento de backup recebida.");
    }

    private boolean isCancelRequested() {
        return cancelRequested;
    }

    public void startBackupAsync(CommandSender sender) {
        synchronized (this) {
            if (backupRunning) {
                if (sender != null) {
                    sendMessage(sender, "§cJá existe um backup em andamento, aguarde ele terminar.");
                }
                return;
            }
            setBackupRunning(true);
            cancelRequested = false; // resetar cancelamento toda vez que iniciar
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("🔄 Backup iniciado...");

                if (isCancelRequested()) {
                    plugin.getLogger().info("Backup cancelado antes de iniciar o backup de arquivos.");
                    if (sender != null) sendMessage(sender, "§cBackup cancelado antes de iniciar.");
                    return;
                }

                // Backup de arquivos
                plugin.getLogger().info("📁 Iniciando backup de arquivos...");
                File backupFile = fileBackup.createBackup();

                if (isCancelRequested()) {
                    plugin.getLogger().info("Backup cancelado após criação do backup de arquivos.");
                    if (sender != null) sendMessage(sender, "§cBackup cancelado após criar backup de arquivos.");
                    return;
                }

                if (backupFile != null && backupFile.exists()) {
                    plugin.getLogger().info("✅ Backup de arquivos criado em: " + backupFile.getAbsolutePath());

                    if (sftp != null) {
                        try {
                            sftp.uploadFile(backupFile, sender);
                            plugin.getLogger().info("📤 Backup de arquivos enviado via SFTP.");
                        } catch (Exception e) {
                            plugin.getLogger().severe("Erro ao enviar backup de arquivos via SFTP: " + e.getMessage());
                            e.printStackTrace();
                            if (sender != null) {
                                sendMessage(sender, "§cErro ao enviar backup de arquivos via SFTP: " + e.getMessage());
                            }
                        }
                    }
                } else {
                    plugin.getLogger().warning("❌ Falha ao criar backup de arquivos.");
                    if (sender != null) sendMessage(sender, "§cFalha ao criar backup de arquivos.");
                }

                if (isCancelRequested()) {
                    plugin.getLogger().info("Backup cancelado antes do backup MySQL.");
                    if (sender != null) sendMessage(sender, "§cBackup cancelado antes do backup MySQL.");
                    return;
                }

                // Backup MySQL
                if (mySQLBackupMethod != null) {
                    plugin.getLogger().info("💾 Iniciando backup MySQL...");
                    File mysqlBackupFile = null;
                    try {
                        mysqlBackupFile = mySQLBackupMethod.createBackup();
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erro ao criar backup MySQL: " + e.getMessage());
                        e.printStackTrace();
                        if (sender != null) sendMessage(sender, "§cErro ao criar backup MySQL: " + e.getMessage());
                    }

                    if (isCancelRequested()) {
                        plugin.getLogger().info("Backup cancelado após tentativa de backup MySQL.");
                        if (sender != null) sendMessage(sender, "§cBackup cancelado após iniciar backup MySQL.");
                        return;
                    }

                    if (mysqlBackupFile != null && mysqlBackupFile.exists()) {
                        plugin.getLogger().info("✅ Backup MySQL criado em: " + mysqlBackupFile.getAbsolutePath());

                        if (sftp != null) {
                            try {
                                sftp.uploadFile(mysqlBackupFile, sender);
                                plugin.getLogger().info("📤 Backup MySQL enviado via SFTP.");
                            } catch (Exception e) {
                                plugin.getLogger().severe("Erro ao enviar backup MySQL via SFTP: " + e.getMessage());
                                e.printStackTrace();
                                if (sender != null) {
                                    sendMessage(sender, "§cErro ao enviar backup MySQL via SFTP: " + e.getMessage());
                                }
                            }
                        }
                    } else if (mysqlBackupFile == null) {
                        // erro já tratado no catch
                    } else {
                        plugin.getLogger().warning("❌ Falha ao criar backup MySQL.");
                        if (sender != null) sendMessage(sender, "§cFalha ao criar backup MySQL.");
                    }
                } else {
                    plugin.getLogger().info("ℹ️ Backup MySQL está desativado nas configurações.");
                }

                if (isCancelRequested()) {
                    plugin.getLogger().info("Backup cancelado no final do processo.");
                    if (sender != null) sendMessage(sender, "§cBackup cancelado.");
                    return;
                }

                plugin.getLogger().info("✅ Backup completo finalizado.");

                if (sender != null) sendMessage(sender, "§aBackup finalizado com sucesso.");

            } finally {
                setBackupRunning(false);
                cancelRequested = false; // resetar para próxima vez
            }
        });
    }

    public void startBackupAsync() {
        startBackupAsync(null);
    }

    private void sendMessage(CommandSender sender, String message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }

    // Getters

    public Backup getFileBackup() {
        return fileBackup;
    }

    public MySQLBackupMethod getMySQLBackupMethod() {
        return mySQLBackupMethod;
    }

    public SFTPManager getSftp() {
        return sftp;
    }
}
