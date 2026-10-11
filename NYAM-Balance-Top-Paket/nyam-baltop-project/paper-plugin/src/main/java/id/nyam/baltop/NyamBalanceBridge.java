package id.nyam.baltop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NyamBalanceBridge extends JavaPlugin {
    private Economy economy;
    private HttpClient httpClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault/Economy belum tersedia. Pastikan Vault dan plugin ekonomi aktif.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        FileConfiguration config = getConfig();
        String apiUrl = config.getString("api-url", "").trim();
        String secret = config.getString("shared-secret", "").trim();
        if (apiUrl.isEmpty() || secret.isEmpty() || secret.equals("GANTI_DENGAN_SECRET_YANG_SAMA_DENGAN_VERCEL")) {
            getLogger().warning("Atur api-url dan shared-secret pada plugins/NYAMBalanceBridge/config.yml.");
        }
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        long interval = Math.max(30L, getConfig().getLong("update-interval-seconds", 60L));
        // Read Bukkit/Vault data on the server thread; send HTTP in a background task.
        getServer().getScheduler().runTaskTimer(this, this::publishSnapshot, 40L, interval * 20L);
        getLogger().info("NYAM Balance Bridge aktif. Pembaruan setiap " + interval + " detik.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) return false;
        economy = registration.getProvider();
        return economy != null;
    }

    private void publishSnapshot() {
        String apiUrl = getConfig().getString("api-url", "").trim();
        String secret = getConfig().getString("shared-secret", "").trim();
        if (apiUrl.isEmpty() || secret.isEmpty() || secret.equals("GANTI_DENGAN_SECRET_YANG_SAMA_DENGAN_VERCEL")) return;

        List<PlayerBalance> balances = new ArrayList<>();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            UUID id = player.getUniqueId();
            if (!player.isOnline() && !player.hasPlayedBefore()) continue;
            String name = player.getName();
            if (name == null || name.isBlank()) continue;
            try {
                if (!economy.hasAccount(player)) continue;
                double balance = economy.getBalance(player);
                if (Double.isFinite(balance) && balance >= 0) balances.add(new PlayerBalance(id, name, balance));
            } catch (RuntimeException ex) {
                getLogger().fine("Tidak dapat membaca saldo untuk " + name);
            }
        }
        balances.sort(Comparator.comparingDouble(PlayerBalance::balance).reversed());
        int max = Math.min(10, balances.size());
        JsonArray players = new JsonArray();
        for (int i = 0; i < max; i++) {
            PlayerBalance entry = balances.get(i);
            JsonObject player = new JsonObject();
            player.addProperty("name", entry.name());
            player.addProperty("balance", entry.balance());
            players.add(player);
        }
        JsonObject payload = new JsonObject();
        payload.add("players", players);
        String body = payload.toString();

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + secret)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    getLogger().fine("Balance Top berhasil dikirim (" + max + " pemain).");
                } else {
                    getLogger().warning("API Balance Top mengembalikan HTTP " + response.statusCode() + ". Periksa api-url dan secret.");
                }
            } catch (Exception ex) {
                getLogger().warning("Gagal mengirim Balance Top: " + ex.getMessage());
            }
        });
    }

    private record PlayerBalance(UUID uuid, String name, double balance) { }
}
