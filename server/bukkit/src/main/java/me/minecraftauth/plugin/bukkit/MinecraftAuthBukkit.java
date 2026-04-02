/*
 * Copyright 2021-2026 MinecraftAuth.me
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.minecraftauth.plugin.bukkit;

import github.scarsz.configuralize.DynamicConfig;
import github.scarsz.configuralize.ParseException;
import lombok.Getter;
import me.minecraftauth.plugin.common.service.AuthenticationService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;

public final class MinecraftAuthBukkit extends JavaPlugin {

    @Getter private static MinecraftAuthBukkit instance;
    @Getter private AuthenticationService service;

    @Override
    public void onEnable() {
        MinecraftAuthBukkit.instance = this;

        DynamicConfig config = new DynamicConfig();
        try {
            config.addSource(MinecraftAuthBukkit.class, "game-config", new File(getDataFolder(), "config.yml"));
            config.saveAllDefaults();
            config.loadAll();
        } catch (IOException | ParseException e) {
            getLogger().log(Level.SEVERE, "Failed to load config", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            service = new AuthenticationService.Builder()
                    .withConfig(config)
                    .withLogger(new BukkitLogger(config, getLogger()))
                    .build();
        } catch (IOException | ParseException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize AuthenticationService", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(new BukkitEventsListener(), this);
        Bukkit.getPluginCommand("minecraftauth").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <reload>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload":
                if (sender.isOp()) {
                    try {
                        service.fullReload();
                        sender.sendMessage("MinecraftAuth config reloaded");
                    } catch (IOException e) {
                        sender.sendMessage("IO exception while reading config: " + e.getMessage());
                        getLogger().log(Level.SEVERE, "IO exception during config reload", e);
                    } catch (ParseException e) {
                        sender.sendMessage("Exception while parsing config:");
                        for (String line : e.getMessage().split("\n")) sender.sendMessage(line);
                        getLogger().log(Level.SEVERE, "Parse exception during config reload", e);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Server operator-only command");
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand");
                return false;
        }

        return true;
    }

}
