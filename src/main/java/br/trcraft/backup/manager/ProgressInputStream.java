package br.trcraft.backup.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;

public class ProgressInputStream extends InputStream {

    private final InputStream wrapped;
    private final long totalSize;
    private final CommandSender sender;
    private final JavaPlugin plugin;

    private long bytesRead = 0;
    private int lastPercentSent = -10;

    public ProgressInputStream(InputStream wrapped, long totalSize, CommandSender sender, JavaPlugin plugin) {
        this.wrapped = wrapped;
        this.totalSize = totalSize;
        this.sender = sender;
        this.plugin = plugin;
    }

    @Override
    public int read() throws IOException {
        int b = wrapped.read();
        if (b != -1) {
            bytesRead++;
            checkProgress();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = wrapped.read(b, off, len);
        if (n > 0) {
            bytesRead += n;
            checkProgress();
        }
        return n;
    }

    private void checkProgress() {
        if (sender == null) return;

        int percent = (int) ((bytesRead * 100) / totalSize);

        // Se o progresso é maior que a última mensagem enviada + 10% (a cada 10%)
        if (percent >= lastPercentSent + 10 || percent == 100) {
            lastPercentSent = (percent / 10) * 10;
            sendProgressMessage(lastPercentSent);
        }
    }

    private void sendProgressMessage(int percent) {
        int progressBars = percent / 10;
        StringBuilder progressBar = new StringBuilder("§a");
        for (int i = 0; i < progressBars; i++) progressBar.append("■");
        progressBar.append("§7");
        for (int i = progressBars; i < 10; i++) progressBar.append("■");

        String message = "§c§l BACKUP §e» §aUpload: " + progressBar + " §f" + percent + "%";

        // Envia para console com cores ANSI
        plugin.getLogger().info(convertMinecraftColorsToAnsi(message) + "\u001B[0m");

        // Envia para jogadores com permissão backup.staff no servidor (async -> sync)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("backup.staff"))
                    .forEach(p -> p.sendMessage(message));
        });

        // Se quem iniciou o backup foi um jogador, envia a ele também
        if (sender instanceof Player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        // Garantir que a mensagem 100% foi enviada ao finalizar
        if (lastPercentSent < 100) {
            sendProgressMessage(100);
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
}
