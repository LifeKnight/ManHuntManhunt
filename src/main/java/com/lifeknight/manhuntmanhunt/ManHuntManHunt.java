package com.lifeknight.manhuntmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ManHuntManHunt extends JavaPlugin implements Listener, CommandExecutor {
    private Map<UUID, Integer> hunterToIndex;
    private List<UUID> speedrunners;
    private List<UUID> queuedUUIDs;

    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
        for (String command : this.getDescription().getCommands().keySet()) {
            this.getServer().getPluginCommand(command).setExecutor(this);
        }
        this.hunterToIndex = new HashMap<>();
        this.speedrunners = new ArrayList<>();
        this.queuedUUIDs = new ArrayList<>();
    }

    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] arguments) {
        if (command.getName().equalsIgnoreCase("hunter")) {
            if (arguments.length < 2) {
                return sendInvalid(sender);
            }
            return this.processHunterCommand(sender, arguments);
        } else if (command.getName().equalsIgnoreCase("hunterinfo")) {
            if (arguments.length == 0) {
                sender.sendMessage(ChatColor.RED + "Invalid usage. Use: " + ChatColor.YELLOW + "/hunterinfo <name>");
                return false;
            }
            Player player = Bukkit.getPlayer(arguments[0]);
            Integer index;
            if (player == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return false;
            } else if ((index = this.hunterToIndex.get(player.getUniqueId())) == null) {
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.RED + " is not a hunter.");
                return false;
            }

            List<Player> players = this.getPlayersOfIndex(index - 1);
            if (player.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.RED + " is hunting nobody.");
            } else if (players.size() == 1) {
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " is hunting " + ChatColor.YELLOW + players.get(0).getName());
            } else {
                StringBuilder message = new StringBuilder(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " is hunting ");

                for (Player player1 : players) {
                    message.append(System.getProperty("line.separator")).append(ChatColor.YELLOW).append(player1.getName());
                }

                sender.sendMessage(message.toString());
            }
        } else if (command.getName().equalsIgnoreCase("hunterlist")) {
            if (this.hunterToIndex.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "There are no hunters to list.");
                return false;
            }

            for (UUID uuid : this.hunterToIndex.keySet()) {
                try {
                    sender.sendMessage(ChatColor.GREEN + String.format(Objects.requireNonNull(Bukkit.getPlayer(uuid)).getName() + " (%d)", this.hunterToIndex.get(uuid) + 1));
                } catch (NullPointerException exception) {
                    this.warn("Tried to add player to hunterlist for command, error invoked: %s", exception.getMessage());
                }
            }
        } else if (command.getName().equalsIgnoreCase("speedrunner")) {
            this.processSpeedrunnerCommand(sender, arguments);
        } else if (command.getName().equalsIgnoreCase("speedrunnerlist")) {
            if (this.speedrunners.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "There are no speedrunners to list.");
                return false;
            }
            for (UUID uuid : this.speedrunners) {
                try {
                    sender.sendMessage(ChatColor.GREEN + Objects.requireNonNull(Bukkit.getPlayer(uuid)).getName());
                } catch (NullPointerException exception) {
                    this.warn("Tried to add player to speedrunnerlist for command, error invoked: %s", exception.getMessage());
                }
            }
        } else if (command.getName().equalsIgnoreCase("manhuntmanhuntclear")) {
            if (!this.speedrunners.isEmpty() || !this.hunterToIndex.isEmpty()) {
                this.speedrunners.clear();
                this.hunterToIndex.clear();
                Bukkit.broadcastMessage(ChatColor.GREEN + "ManHuntManHunt has been cleared!");
            } else {
                sender.sendMessage(ChatColor.GRAY + "There is nothing to clear.");
            }
        }
        return false;
    }

    private boolean processHunterCommand(CommandSender sender, String[] arguments) {
        Player player = Bukkit.getPlayer(arguments.length > 2 ? arguments[2] : arguments[1]);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return false;
        }

        if (arguments[0].equalsIgnoreCase("add")) {
            if (!this.hunterToIndex.containsKey(player.getUniqueId()) && !this.speedrunners.contains(player.getUniqueId())) {
                int index;
                if (arguments.length > 2) {
                    try {
                        index = Integer.parseInt(arguments[1]) - 1;
                        if (index < 0) throw new IllegalArgumentException();
                        this.hunterToIndex.put(player.getUniqueId(), index);
                    } catch (Exception exception) {
                        return sendInvalid(sender);
                    }
                } else {
                    index = this.getHighestIndex() + 1;
                    this.hunterToIndex.put(player.getUniqueId(), index);
                }
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN +  String.format(" is now a hunter. (%d)", index + 1));
                player.getInventory().addItem(new ItemStack(Material.COMPASS));
            } else if (!this.speedrunners.contains(player.getUniqueId())) {
                sender.sendMessage(ChatColor.YELLOW + player.getName() +  ChatColor.RED + " is already a hunter!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.RED + " cannot be a hunter and a speedrunner!");
            }
        } else if (arguments[0].equalsIgnoreCase("remove")) {
            if (this.hunterToIndex.containsKey(player.getUniqueId())) {
                this.hunterToIndex.remove(player.getUniqueId());
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " is no longer a hunter.");
                player.getInventory().remove(new ItemStack(Material.COMPASS));
            } else {
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.RED + " is not a hunter!");
            }
        } else {
            return sendInvalid(sender);
        }
        return false;
    }

    private void processSpeedrunnerCommand(CommandSender sender, String[] arguments) {
        if (arguments.length < 2) {
            sender.sendMessage(ChatColor.RED + "Invalid usage. Use:\n" +
                    ChatColor.YELLOW + "/speedrunner add <name>\n" +
                    ChatColor.YELLOW + "/speedrunner remove <name>\n" +
                    ChatColor.YELLOW + "/speedrunner auto <name>\n" +
                    ChatColor.YELLOW + "/speedrunner clear");
            return;
        }

        Player player = Bukkit.getPlayer(arguments[1]);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "No player found.");
            return;
        }

        if (arguments[0].equalsIgnoreCase("add")) {
            if (!this.speedrunners.contains(player.getUniqueId()) && !this.hunterToIndex.containsKey(player.getUniqueId())) {
                this.speedrunners.add(player.getUniqueId());
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " is now a speedrunner.");
            } else if (!this.hunterToIndex.containsKey(player.getUniqueId())) {
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.RED + " is already a speedrunner.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.RED + " cannot be a speedrunner and hunter!");
            }
        } else if (arguments[0].equalsIgnoreCase("remove")) {
            if (this.speedrunners.contains(player.getUniqueId())) {
                this.speedrunners.remove(player.getUniqueId());
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " is no longer a speedrunner.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.RED + " is not a speedrunner.");
            }
        } else if (arguments[0].equalsIgnoreCase("auto")) {
            this.speedrunners.clear();
            this.hunterToIndex.clear();
            this.speedrunners.add(player.getUniqueId());

            int index = 0;
            for (Player player1 : player.getServer().getOnlinePlayers()) {
                if (!player1.equals(player)) {
                    this.hunterToIndex.put(player1.getUniqueId(), index);
                    index++;
                }
            }
            Bukkit.broadcastMessage(ChatColor.GREEN + "Hunter and speedrunners have automatically been assigned! Use /hunterlist and /speedrunnerlist to view.");
        } else if (arguments[0].equalsIgnoreCase("clear")) {
            if (this.speedrunners.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "There are no speedrunners to clear.");
            } else {
                this.speedrunners.clear();
                Bukkit.broadcastMessage(ChatColor.GREEN + "All speedrunners have been cleared!");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid usage. Use:" +
                    ChatColor.YELLOW + "/speedrunner add <name>\n" +
                    ChatColor.YELLOW + "/speedrunner remove <name>\n" +
                    ChatColor.YELLOW + "/speedrunner auto <name>\n" +
                    ChatColor.YELLOW + "/speerunner clear");
        }
    }

    private void warn(String format, Object... objects) {
        this.getLogger().warning("ManHuntManHunt > " + String.format(format, objects));
    }

    private int getHighestIndex() {
        int i = -1;
        for (int index : this.hunterToIndex.values()) {
            i = Math.max(i, index);
        }
        return i;
    }

    private List<Player> getPlayersOfIndex(int index) {
        List<Player> players = new ArrayList<>();

        if (index == -1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (this.speedrunners.contains(player.getUniqueId())) players.add(player);
            }
            return players;
        }

        for (UUID uuid : this.hunterToIndex.keySet()) {
            if (this.hunterToIndex.get(uuid) == index)
                try {
                    players.add(Bukkit.getPlayer(uuid));
                } catch (Exception exception) {
                    this.warn("Tried to add player from hunterToIndex map, error occurred: %s", exception.getMessage());
                }
        }
        return players;
    }

    private static boolean sendInvalid(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Invalid usage. Use:\n" +
                ChatColor.YELLOW + "/hunter add <name>\n" +
                ChatColor.YELLOW + "/hunter add <number> <name>\n" +
                ChatColor.YELLOW + "/hunter remove <name>\n" +
                ChatColor.YELLOW + "/hunter clear");
        return false;
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (this.hunterToIndex.containsKey(player.getUniqueId()) && event.getItem() != null && event.getItem().getType() == Material.COMPASS && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
            int index = this.hunterToIndex.get(player.getUniqueId());
            Player nearest = null;
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            List<Player> players = this.getPlayersOfIndex(index - 1);
            for (Player player1 : players) {
                if (onlinePlayers.contains(player1)) {
                    if (nearest == null || player1.getLocation().distance(player.getLocation()) < nearest.getLocation().distance(player.getLocation())) {
                        nearest = player1;
                    }
                }
            }
            if (nearest == null) {
                player.sendMessage(ChatColor.RED + "No players to track!");
                return;
            }
            player.setCompassTarget(nearest.getLocation());
            player.sendMessage(ChatColor.GREEN + "Compass is now pointing to " + ChatColor.YELLOW + nearest.getName() + ChatColor.GREEN + ".");
        }

    }

    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        if (this.hunterToIndex.containsKey(event.getEntity().getUniqueId())) {
            event.getDrops().removeIf(next -> next.getType() == Material.COMPASS);
            Player player = event.getEntity();
            this.queuedUUIDs.add(player.getUniqueId());
            int index = this.hunterToIndex.get(player.getUniqueId());
            this.hunterToIndex.remove(player.getUniqueId());
            boolean otherOfSameIndex = this.hunterToIndex.containsValue(index);

            if (!otherOfSameIndex) {
                Map<UUID, Integer> replacementMap = new HashMap<>();
                for (UUID uuid : this.hunterToIndex.keySet()) {
                    int theIndex = this.hunterToIndex.get(uuid);
                    if (theIndex > index) {
                        replacementMap.put(uuid, theIndex - 1);
                        Player player1 = Bukkit.getPlayer(uuid);
                        if (player1 != null) {
                            Bukkit.broadcastMessage(String.format("%s%s %sis now a hunter of index %s%d%s.",
                                    ChatColor.GREEN, player1.getName(), ChatColor.YELLOW, ChatColor.GREEN, theIndex, ChatColor.YELLOW));
                        }
                    } else {
                        replacementMap.put(uuid, index);
                    }
                }
                this.hunterToIndex = replacementMap;
            }
        } else {
            if (this.speedrunners.contains(event.getEntity().getUniqueId())) {
                this.speedrunners.remove(event.getEntity().getUniqueId());
                if (this.speedrunners.isEmpty()) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "All speedrunners have perished!");
                    Bukkit.broadcastMessage(ChatColor.RED + "Game End.");
                    this.hunterToIndex.clear();
                } else {
                    Bukkit.broadcastMessage(String.format("%sSpeedrunner %s%s%s has died! %s%d runner(s) remain!",
                            ChatColor.GREEN, ChatColor.YELLOW, event.getEntity().getName(), ChatColor.GREEN, ChatColor.RED, this.speedrunners.size()));
                    Bukkit.broadcastMessage(ChatColor.GREEN + "");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDropItemEvent(PlayerDropItemEvent event) {
        if (this.hunterToIndex.containsKey(event.getPlayer().getUniqueId()) && event.getItemDrop().getItemStack().getType() == Material.COMPASS) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerRespawnEvent(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (this.queuedUUIDs.contains(player.getUniqueId())) {
            player.getInventory().addItem(new ItemStack(Material.COMPASS));
            int newIndex = this.getHighestIndex() + 1;
            this.hunterToIndex.put(player.getUniqueId(), newIndex);
            this.queuedUUIDs.remove(player.getUniqueId());
            Bukkit.broadcastMessage(String.format("%s%s %sis now a hunter of index %s%d%s.",
                    ChatColor.GREEN, player.getName(), ChatColor.YELLOW, ChatColor.GREEN, newIndex + 1, ChatColor.YELLOW));
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        if (this.hunterToIndex.containsKey(event.getPlayer().getUniqueId())) {
            Player player = event.getPlayer();
            int index = this.hunterToIndex.get(player.getUniqueId());
            this.hunterToIndex.remove(player.getUniqueId());
            Bukkit.broadcastMessage(ChatColor.YELLOW + player.getName() + " is no longer a hunter!");
            boolean otherOfSameIndex = this.hunterToIndex.containsValue(index);

            if (!otherOfSameIndex) {
                Map<UUID, Integer> replacementMap = new HashMap<>();
                for (UUID uuid : this.hunterToIndex.keySet()) {
                    int theIndex = this.hunterToIndex.get(uuid);
                    if (theIndex > index) {
                        replacementMap.put(uuid, theIndex - 1);
                        Player player1 = Bukkit.getPlayer(uuid);
                        if (player1 != null) {
                            Bukkit.broadcastMessage(String.format("%s%s %sis now a hunter of index %s%d%s.",
                                    ChatColor.GREEN, player1.getName(), ChatColor.YELLOW, ChatColor.GREEN, theIndex, ChatColor.YELLOW));
                        }
                    } else {
                        replacementMap.put(uuid, index);
                    }
                }
                this.hunterToIndex = replacementMap;
            } else if (this.speedrunners.contains(event.getPlayer().getUniqueId())) {
                this.speedrunners.remove(event.getPlayer().getUniqueId());
                if (this.speedrunners.isEmpty()) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "All speedrunners have lost!");
                    Bukkit.broadcastMessage(ChatColor.RED + "Game End.");
                    this.hunterToIndex.clear();
                } else {
                    Bukkit.broadcastMessage(String.format("%sSpeedrunner %s%s%s has left! %s%d runner(s) remain!",
                            ChatColor.GREEN, ChatColor.YELLOW, event.getPlayer().getName(), ChatColor.GREEN, ChatColor.RED, this.speedrunners.size()));
                    Bukkit.broadcastMessage(ChatColor.GREEN + "");
                }
            }
        }
    }
}
