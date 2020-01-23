package me.josvth.randomspawn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import me.josvth.randomspawn.handlers.CommandHandler;
import me.josvth.randomspawn.handlers.YamlHandler;
import me.josvth.randomspawn.listeners.*;

public class RandomSpawn extends JavaPlugin {

    public YamlHandler yamlHandler;
    CommandHandler commandHandler;

    RespawnListener respawnListener;
    JoinListener joinListener;
    WorldChangeListener worldChangeListener;
    SignListener signListener;
    DamageListener damageListener;

    @Override
    public void onEnable() {

        //setup handlers
        yamlHandler = new YamlHandler(this);
        logDebug("Yamls loaded!");

        commandHandler = new CommandHandler(this);
        logDebug("Commands registered!");

        //setup listeners
        respawnListener = new RespawnListener(this);
        joinListener = new JoinListener(this);
        worldChangeListener = new WorldChangeListener(this);
        signListener = new SignListener(this);
        damageListener = new DamageListener(this);

    }

    public void logInfo(String message) {
        getLogger().info(message);
    }

    public void logDebug(String message) {
        if (yamlHandler.config.getBoolean("debug", false)) {
            getLogger().info("(DEBUG) " + message);
        }
    }

    public void logWarning(String message) {
        getLogger().warning(message);
    }

    public void playerInfo(Player player, String message) {
        player.sendMessage(ChatColor.AQUA + "[RandomSpawn] " + ChatColor.RESET + message);
    }

    // *------------------------------------------------------------------------------------------------------------*
    // | The following chooseSpawn method contains code made by NuclearW                                            |
    // | based on his SpawnArea plugin:                                                                             |
    // | http://forums.bukkit.org/threads/tp-spawnarea-v0-1-spawns-targetPlayers-in-a-set-area-randomly-1060.20408/ |
    // *------------------------------------------------------------------------------------------------------------*

    public Location chooseSpawn(World world) {

        String worldName = world.getName();

        // I don't like this method
        List<Material> blacklist = new ArrayList<Material>();

        if (yamlHandler.worlds.contains(worldName + ".spawnblacklist")) {
            blacklist = yamlHandler.worlds.getStringList(worldName + ".spawnblacklist")
                    .parallelStream().map(Material::getMaterial).collect(Collectors.toList());

        } else {
            blacklist = Arrays.asList(Material.WATER, Material.LAVA, Material.OAK_LEAVES, Material.ACACIA_LEAVES,
                    Material.BIRCH_LEAVES, Material.DARK_OAK_LEAVES, Material.JUNGLE_LEAVES, Material.SPRUCE_LEAVES, Material.FIRE, Material.CACTUS
//                    new Integer[]{8, 9, 10, 11, 18, 51, 81}
            );
        }

        double xmin = yamlHandler.worlds.getDouble(worldName + ".spawnarea.x-min", -100);
        double xmax = yamlHandler.worlds.getDouble(worldName + ".spawnarea.x-max", 100);
        double zmin = yamlHandler.worlds.getDouble(worldName + ".spawnarea.z-min", -100);
        double zmax = yamlHandler.worlds.getDouble(worldName + ".spawnarea.z-max", 100);

        // Spawn area thickness near border. If 0 spawns whole area
        int thickness = yamlHandler.worlds.getInt(worldName + ".spawnarea.thickness", 0);

        String type = yamlHandler.worlds.getString(worldName + ".spawnarea.type", "square");

        double xrand = 0;
        double zrand = 0;
        double y = -1;

        if (type.equalsIgnoreCase("circle")) {

            double xcenter = xmin + (xmax - xmin) / 2;
            double zcenter = zmin + (zmax - zmin) / 2;

            do {

                double r = Math.random() * (xmax - xcenter);
                double phi = Math.random() * 2 * Math.PI;

                xrand = xcenter + Math.cos(phi) * r;
                zrand = zcenter + Math.sin(phi) * r;

                y = getValidHighestY(world, xrand, zrand, blacklist);

            } while (y == -1);


        } else {

            if (thickness <= 0) {

                do {

                    xrand = xmin + Math.random() * (xmax - xmin + 1);
                    zrand = zmin + Math.random() * (zmax - zmin + 1);

                    y = getValidHighestY(world, xrand, zrand, blacklist);

                } while (y == -1);

            } else {

                do {

                    int side = (int) (Math.random() * 4d);
                    double borderOffset = Math.random() * (double) thickness;
                    if (side == 0) {
                        xrand = xmin + borderOffset;
                        // Also balancing probability considering thickness
                        zrand = zmin + Math.random() * (zmax - zmin + 1 - 2 * thickness) + thickness;
                    } else if (side == 1) {
                        xrand = xmax - borderOffset;
                        zrand = zmin + Math.random() * (zmax - zmin + 1 - 2 * thickness) + thickness;
                    } else if (side == 2) {
                        xrand = xmin + Math.random() * (xmax - xmin + 1);
                        zrand = zmin + borderOffset;
                    } else {
                        xrand = xmin + Math.random() * (xmax - xmin + 1);
                        zrand = zmax - borderOffset;
                    }

                    y = getValidHighestY(world, xrand, zrand, blacklist);

                } while (y == -1);

            }
        }

//        logInfo("Choose y: " + y);

        Location location = new Location(world, xrand, y + 1, zrand);

        return location;
    }

    private int getValidHighestY(World world, double x, double z, List<Material> blacklist) {

        world.getChunkAt(new Location(world, x, 0, z)).load();

        int y = 0;
        Material blockType = Material.AIR;

        if (world.getEnvironment().equals(Environment.NETHER)) {
            Material blockYType = world.getBlockAt((int) x, (int) y, (int) z).getType();
            Material blockY2Type = world.getBlockAt((int) x, (int) (y + 1), (int) z).getType();
            while (y < 128 && !(
                    (blockYType == Material.AIR || blockYType == Material.VOID_AIR || blockYType == Material.CAVE_AIR)
                            && (blockY2Type == Material.AIR || blockY2Type == Material.VOID_AIR || blockY2Type == Material.CAVE_AIR))) {
                y++;
                blockYType = blockY2Type;
                blockY2Type = world.getBlockAt((int) x, (int) (y + 1), (int) z).getType();
            }
            if (y == 127) return -1;
        } else {
            y = 257;
            while (y >= 0 && (blockType == Material.AIR || blockType == Material.VOID_AIR || blockType == Material.CAVE_AIR)) {
                y--;
                blockType = world.getBlockAt((int) x, (int) y, (int) z).getType();
//                logInfo(y + " blockType: " + String.valueOf(blockType));
            }
            if (y == 0) {
                return -1;
            }
        }

        if (blacklist.contains(blockType)) {
            return -1;
        }
        if (blacklist.contains(Material.CACTUS) && world.getBlockAt((int) x, (int) (y + 1), (int) z).getType() == Material.CACTUS) {
            return -1; // Check for cacti
        }

        return y;
    }

    // Methods for a save landing :)

    public void sendGround(Player player, Location location) {

        location.getChunk().load();

        World world = location.getWorld();

        for (int y = 0; y <= location.getBlockY() + 4; y++) {
            Block block = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
            player.sendBlockChange(block.getLocation(), block.getBlockData());
        }

    }
}
