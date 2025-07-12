package br.trcraft.backup.manager;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class SFTPManager {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String remoteDir;
    private final JavaPlugin plugin;

    public SFTPManager(String host, int port, String user, String password, String remoteDir, JavaPlugin plugin) {
        this.host = host;
        this.port = port > 0 ? port : 22;
        this.user = user;
        this.password = password;
        this.remoteDir = remoteDir;
        this.plugin = plugin;
    }

    public void uploadFile(File file, CommandSender sender) throws Exception {
        Session session = null;
        ChannelSftp channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(10000);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(10000);

            try {
                channel.cd(remoteDir);
            } catch (Exception e) {
                channel.mkdir(remoteDir);
                channel.cd(remoteDir);
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                InputStream progressStream = new ProgressInputStream(fis, file.length(), sender, plugin);
                channel.put(progressStream, file.getName());
            }

            sendMessage(sender, "§c§l BACKUP §e» §aUpload concluído! §2✅");

        } catch (Exception e) {
            sendMessage(sender, "§c§l BACKUP §e» §cErro no upload SFTP: " + e.getMessage());
            throw e;
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    private void sendMessage(CommandSender sender, String message) {
        String ansiMessage = convertMinecraftColorsToAnsi(message) + "\u001B[0m";

        if (sender instanceof Player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(message));
            plugin.getLogger().info(ansiMessage);
        } else {
            plugin.getLogger().info(ansiMessage);
        }
    }

    private String convertMinecraftColorsToAnsi(String message) {
        return message
                .replace("§0", "\u001B[30m")
                .replace("§1", "\u001B[34m")
                .replace("§2", "\u001B[32m")
                .replace("§3", "\u001B[36m")
                .replace("§4", "\u001B[31m")
                .replace("§5", "\u001B[35m")
                .replace("§6", "\u001B[33m")
                .replace("§7", "\u001B[37m")
                .replace("§8", "\u001B[90m")
                .replace("§9", "\u001B[94m")
                .replace("§a", "\u001B[92m")
                .replace("§b", "\u001B[96m")
                .replace("§c", "\u001B[91m")
                .replace("§d", "\u001B[95m")
                .replace("§e", "\u001B[93m")
                .replace("§f", "\u001B[97m")
                .replace("§l", "\u001B[1m")
                .replace("§o", "\u001B[3m")
                .replace("§n", "\u001B[4m")
                .replace("§r", "\u001B[0m");
    }

    // A classe ProgressInputStream também pode ser colocada aqui (igual sua versão) para acompanhar o progresso do upload.
}
