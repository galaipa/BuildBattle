/*
 * BuildBattle - Ultimate building competition minigame
 * Copyright (C) 2019  Plajer's Lair - maintained by Plajer and Tigerpanzer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.plajer.buildbattle.arena.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import pl.plajer.buildbattle.ConfigPreferences;
import pl.plajer.buildbattle.Main;
import pl.plajer.buildbattle.api.StatsStorage;
import pl.plajer.buildbattle.api.event.game.BBGameEndEvent;
import pl.plajer.buildbattle.api.event.game.BBGameStartEvent;
import pl.plajer.buildbattle.arena.ArenaState;
import pl.plajer.buildbattle.arena.ArenaUtils;
import pl.plajer.buildbattle.arena.managers.plots.Plot;
import pl.plajer.buildbattle.arena.options.ArenaOption;
import pl.plajer.buildbattle.handlers.ChatManager;
import pl.plajer.buildbattle.handlers.language.LanguageManager;
import pl.plajer.buildbattle.menus.themevoter.VoteMenu;
import pl.plajer.buildbattle.menus.themevoter.VotePoll;
import pl.plajer.buildbattle.user.User;
import pl.plajerlair.core.services.exception.ReportedException;
import pl.plajerlair.core.utils.InventoryUtils;
import pl.plajerlair.core.utils.MinigameUtils;

/**
 * @author Plajer
 * <p>
 * Created at 11.01.2019
 */
public class SoloArena extends BaseArena {

  private Map<Integer, List<Player>> topList = new HashMap<>();
  private String theme = "Theme";
  private boolean receivedVoteItems;
  private Queue<Player> queue = new LinkedList<>();
  private Plot votingPlot = null;
  private boolean voteTime;
  private boolean themeVoteTime = true;
  private boolean themeTimerSet = false;
  private int buildTime;
  private VoteMenu voteMenu;

  public SoloArena(String id, Main plugin) {
    super(id, plugin);
  }

  /**
   * Initiates voting poll
   */
  public void initPoll() {
    voteMenu = new VoteMenu(this);
    voteMenu.resetPoll();
  }

  public VotePoll getVotePoll() {
    return voteMenu.getVotePoll();
  }

  public VoteMenu getVoteMenu() {
    return voteMenu;
  }

  /**
   * Is voting time in game?
   *
   * @return true = voting time, false = no
   */
  public boolean isVoting() {
    return voteTime;
  }

  public void setVoting(boolean voting) {
    voteTime = voting;
  }

  public boolean isThemeVoteTime() {
    return themeVoteTime;
  }

  public void setThemeVoteTime(boolean themeVoteTime) {
    this.themeVoteTime = themeVoteTime;
  }

  public int getBuildTime() {
    return buildTime;
  }

  public Queue<Player> getQueue() {
    return queue;
  }

  @Deprecated //split teams into separate class
  @Override
  public void run() {
    try {
      //idle task
      if (getPlayers().size() == 0 && getArenaState() == ArenaState.WAITING_FOR_PLAYERS) {
        return;
      }
      getScoreboardManager().updateScoreboard();
      if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
        updateBossBar();
      }
      switch (getArenaState()) {
        case WAITING_FOR_PLAYERS:
          if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
            getPlugin().getServer().setWhitelist(false);
          }
          getPlotManager().resetPlotsGradually();
          if (getPlayers().size() < getMinimumPlayers()) {
            if (getTimer() <= 0) {
              setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.LOBBY, this));
              ChatManager.broadcast(this, ChatManager.colorMessage("In-Game.Messages.Lobby-Messages.Waiting-For-Players").replace("%MINPLAYERS%", String.valueOf(getMinimumPlayers())));
              return;
            }
          } else {
            ChatManager.broadcast(this, ChatManager.colorMessage("In-Game.Messages.Lobby-Messages.Enough-Players-To-Start"));
            setArenaState(ArenaState.STARTING);
            Bukkit.getPluginManager().callEvent(new BBGameStartEvent(this));
            setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.LOBBY, this));
            ArenaUtils.showPlayers(this);
          }
          setTimer(getTimer() - 1);
          break;
        case STARTING:
          for (Player player : getPlayers()) {
            player.setExp((float) (getTimer() / getPlugin().getConfig().getDouble("Lobby-Starting-Time", 60)));
            player.setLevel(getTimer());
          }
          if (getPlayers().size() < getMinimumPlayers() && !isForceStart()) {
            ChatManager.broadcast(this, ChatManager.colorMessage("In-Game.Messages.Lobby-Messages.Waiting-For-Players").replace("%MINPLAYERS%", String.valueOf(getMinimumPlayers())));
            setArenaState(ArenaState.WAITING_FOR_PLAYERS);
            Bukkit.getPluginManager().callEvent(new BBGameStartEvent(this));
            setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.LOBBY, this));
            for (Player player : getPlayers()) {
              player.setExp(1);
              player.setLevel(0);
            }
            break;
          }
          if (getTimer() == 0 || isForceStart()) {
            if (!getPlotManager().isPlotsCleared()) {
              getPlotManager().resetQueuedPlots();
            }
            setArenaState(ArenaState.IN_GAME);
            getPlotManager().distributePlots();
            getPlotManager().teleportToPlots();
            setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.THEME_VOTE, this));
            for (Player player : getPlayers()) {
              player.getInventory().clear();
              player.setGameMode(GameMode.CREATIVE);
              player.setAllowFlight(true);
              player.setFlying(true);
              ArenaUtils.hidePlayersOutsideTheGame(this, player);
              player.getInventory().setItem(8, getPlugin().getOptionsRegistry().getMenuItem());
              //to prevent Multiverse chaning gamemode bug
              Bukkit.getScheduler().runTaskLater(getPlugin(), () -> player.setGameMode(GameMode.CREATIVE), 20);
            }
          }
          if (isForceStart()) {
            setForceStart(false);
          }
          setTimer(getTimer() - 1);
          break;
        case IN_GAME:
          if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
            if (getMaximumPlayers() <= getPlayers().size()) {
              getPlugin().getServer().setWhitelist(true);
            } else {
              getPlugin().getServer().setWhitelist(false);
            }
          }
          if (isThemeVoteTime()) {
            if (!themeTimerSet) {
              setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.THEME_VOTE, this));
              themeTimerSet = true;
              for (Player p : getPlayers()) {
                p.openInventory(voteMenu.getInventory());
              }
            }
            for (Player p : getPlayers()) {
              voteMenu.updateInventory(p);
            }
            if (getTimer() == 0) {
              setThemeVoteTime(false);
              String votedTheme = voteMenu.getVotePoll().getVotedTheme();
              setTheme(votedTheme);
              setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.BUILD, this));
              String message = ChatManager.colorMessage("In-Game.Messages.Lobby-Messages.Game-Started");
              for (Player p : getPlayers()) {
                p.closeInventory();
                p.teleport(getPlotManager().getPlot(p).getTeleportLocation());
                p.sendMessage(ChatManager.getPrefix() + message);
              }
              break;
            } else {
              setTimer(getTimer() - 1);
              break;
            }
          }
          if (getPlayers().size() <= 2) {
            if ((getPlayers().size() == 1 && getArenaType() == ArenaType.SOLO) || (getPlayers().size() == 2 && getArenaType() == ArenaType.TEAM
                && getPlotManager().getPlot(getPlayers().get(0)).getOwners().contains(getPlayers().get(1)))) {
              String message = ChatManager.colorMessage("In-Game.Messages.Game-End-Messages.Only-You-Playing");
              ChatManager.broadcast(this, message);
              setArenaState(ArenaState.ENDING);
              Bukkit.getPluginManager().callEvent(new BBGameEndEvent(this));
              setTimer(10);
            }
          }
          if ((getTimer() == (4 * 60) || getTimer() == (3 * 60) || getTimer() == 5 * 60 || getTimer() == 30 || getTimer() == 2 * 60 || getTimer() == 60 || getTimer() == 15) && !this.isVoting()) {
            String message = ChatManager.colorMessage("In-Game.Messages.Time-Left-To-Build").replace("%FORMATTEDTIME%", MinigameUtils.formatIntoMMSS(getTimer()));
            String subtitle = ChatManager.colorMessage("In-Game.Messages.Time-Left-Subtitle").replace("%FORMATTEDTIME%", String.valueOf(getTimer()));
            for (Player p : getPlayers()) {
              p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
              p.sendMessage(ChatManager.getPrefix() + message);
              p.sendTitle(null, subtitle, 5, 30, 5);
            }
          }
          if (getTimer() != 0 && !receivedVoteItems) {
            if (getOption(ArenaOption.IN_PLOT_CHECKER) == 1) {
              setOptionValue(ArenaOption.IN_PLOT_CHECKER, 0);
              for (Player player : getPlayers()) {
                User user = getPlugin().getUserManager().getUser(player.getUniqueId());
                Plot buildPlot = user.getCurrentPlot();
                if (buildPlot != null) {
                  if (!buildPlot.getCuboid().isInWithMarge(player.getLocation(), 5)) {
                    player.teleport(buildPlot.getTeleportLocation());
                    player.sendMessage(ChatManager.getPrefix() + ChatManager.colorMessage("In-Game.Messages.Cant-Fly-Outside-Plot"));
                  }
                }
              }
            }
            addOptionValue(ArenaOption.IN_PLOT_CHECKER, 1);
          } else if (getTimer() == 0 && !receivedVoteItems) {
            queue.addAll(getPlayers());
            for (Player player : getPlayers()) {
              player.getInventory().clear();
              getPlugin().getVoteItems().giveVoteItems(player);
            }
            receivedVoteItems = true;
          }
          if (getTimer() == 0 && receivedVoteItems) {
            setVoting(true);
            if (!queue.isEmpty()) {
              if (getVotingPlot() != null) {
                for (Player player : getPlayers()) {
                  getVotingPlot().setPoints(getVotingPlot().getPoints() + getPlugin().getUserManager().getUser(player.getUniqueId()).getStat(StatsStorage.StatisticType.LOCAL_POINTS));
                  getPlugin().getUserManager().getUser(player.getUniqueId()).setStat(StatsStorage.StatisticType.LOCAL_POINTS, 0);
                }
              }
              if (getArenaType() == ArenaType.TEAM) {
                for (Plot p : getPlotManager().getPlots()) {
                  if (p.getOwners() != null && p.getOwners().size() == 2) {
                    //removing second owner to not vote for same plot twice
                    queue.remove(p.getOwners().get(1));
                  }
                }
              }
              voteRoutine();
            } else {
              if (getVotingPlot() != null) {
                for (Player player : getPlayers()) {
                  getVotingPlot().setPoints(getVotingPlot().getPoints() + getPlugin().getUserManager().getUser(player.getUniqueId()).getStat(StatsStorage.StatisticType.LOCAL_POINTS));
                  getPlugin().getUserManager().getUser(player.getUniqueId()).setStat(StatsStorage.StatisticType.LOCAL_POINTS, 0);
                }
              }
              calculateResults();
              Plot winnerPlot = getPlotManager().getPlot(topList.get(1).get(0));
              announceResults();

              for (Player player : getPlayers()) {
                player.teleport(winnerPlot.getTeleportLocation());
                String winner = ChatManager.colorMessage("In-Game.Messages.Voting-Messages.Winner-Title");
                if (getArenaType() == ArenaType.TEAM) {
                  if (winnerPlot.getOwners().size() == 1) {
                    winner = winner.replace("%player%", topList.get(1).get(0).getName());
                  } else {
                    winner = winner.replace("%player%", topList.get(1).get(0).getName() + " & " + topList.get(1).get(1).getName());
                  }
                } else {
                  winner = winner.replace("%player%", topList.get(1).get(0).getName());
                }
                player.sendTitle(winner, null, 5, 35, 5);
              }
              this.setArenaState(ArenaState.ENDING);
              Bukkit.getPluginManager().callEvent(new BBGameEndEvent(this));
              setTimer(10);
            }
          }
          setTimer(getTimer() - 1);
          break;
        case ENDING:
          if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
            getPlugin().getServer().setWhitelist(false);
          }
          setVoting(false);
          themeTimerSet = false;
          for (Player player : getPlayers()) {
            MinigameUtils.spawnRandomFirework(player.getLocation());
            ArenaUtils.showPlayers(this);
          }
          if (getTimer() <= 0) {
            teleportAllToEndLocation();
            for (Player player : getPlayers()) {
              if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
                getGameBar().removePlayer(player);
              }
              player.getInventory().clear();
              getPlugin().getUserManager().getUser(player.getUniqueId()).removeScoreboard();
              player.setGameMode(GameMode.SURVIVAL);
              player.setFlying(false);
              player.setAllowFlight(false);
              player.getInventory().setArmorContents(null);
              player.sendMessage(ChatManager.getPrefix() + ChatManager.colorMessage("Commands.Teleported-To-The-Lobby"));
              getPlugin().getUserManager().getUser(player.getUniqueId()).addStat(StatsStorage.StatisticType.GAMES_PLAYED, 1);
              if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
                InventoryUtils.loadInventory(getPlugin(), player);
              }
              //plot might be already deleted by team mate in TEAM game mode
              if (getPlotManager().getPlot(player) != null) {
                getPlotManager().getPlot(player).fullyResetPlot();
              }
            }
            giveRewards();
            clearPlayers();
            setArenaState(ArenaState.RESTARTING);
            if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
              for (Player player : getPlugin().getServer().getOnlinePlayers()) {
                this.addPlayer(player);
              }
            }
          }
          setTimer(getTimer() - 1);
          break;
        case RESTARTING:
          setTimer(14);
          setVoting(false);
          receivedVoteItems = false;
          if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED) && getPlugin().getConfig().getBoolean("Bungee-Shutdown-On-End", false)) {
            getPlugin().getServer().shutdown();
          }

          setOptionValue(ArenaOption.IN_PLOT_CHECKER, 0);
          setArenaState(ArenaState.WAITING_FOR_PLAYERS);
          topList.clear();
          themeTimerSet = false;
          setThemeVoteTime(true);
          voteMenu.resetPoll();
      }
    } catch (Exception ex) {
      new ReportedException(getPlugin(), ex);
    }
  }

  /**
   * Get current arena theme
   *
   * @return arena theme String
   */
  public String getTheme() {
    return theme;
  }

  public void setTheme(String theme) {
    this.theme = theme;
  }

  @Override
  public void updateBossBar() {
    switch (getArenaState()) {
      case WAITING_FOR_PLAYERS:
        getGameBar().setTitle(ChatManager.colorMessage("Bossbar.Waiting-For-Players"));
        break;
      case STARTING:
        getGameBar().setTitle(ChatManager.colorMessage("Bossbar.Starting-In").replace("%time%", String.valueOf(getTimer())));
        break;
      case IN_GAME:
        if (!isVoting()) {
          getGameBar().setTitle(ChatManager.colorMessage("Bossbar.Time-Left").replace("%time%", String.valueOf(getTimer())));
        } else {
          getGameBar().setTitle(ChatManager.colorMessage("Bossbar.Vote-Time-Left").replace("%time%", String.valueOf(getTimer())));
        }
        break;
    }
  }

  private void voteRoutine() {
    if (!queue.isEmpty()) {
      setTimer(getPlugin().getConfigPreferences().getTimer(ConfigPreferences.TimerType.PLOT_VOTE, this));
      Player player = queue.poll();
      while (getPlotManager().getPlot(player) == null && !queue.isEmpty()) {
        System.out.print("A PLAYER HAS NO PLOT!");
        player = queue.poll();
      }
      if (queue.isEmpty() && getPlotManager().getPlot(player) == null) {
        setVotingPlot(null);
      } else {
        // getPlotManager().teleportAllToPlot(plotManager.getPlot(player.getUniqueId()));
        setVotingPlot(getPlotManager().getPlot(player));
        String message = ChatManager.colorMessage("In-Game.Messages.Voting-Messages.Voting-For-Player-Plot").replace("%PLAYER%", player.getName());
        for (Player p : getPlayers()) {
          p.teleport(getVotingPlot().getTeleportLocation());
          p.setPlayerWeather(getVotingPlot().getWeatherType());
          p.setPlayerTime(Plot.Time.format(getVotingPlot().getTime(), p.getWorld().getTime()), false);
          String owner = ChatManager.colorMessage("In-Game.Messages.Voting-Messages.Plot-Owner-Title");
          if (getArenaType() == BaseArena.ArenaType.TEAM) {
            if (getVotingPlot().getOwners().size() == 1) {
              owner = owner.replace("%player%", player.getName());
            } else {
              owner = owner.replace("%player%", getVotingPlot().getOwners().get(0).getName() + " & " + getVotingPlot().getOwners().get(1).getName());
            }
          } else {
            owner = owner.replace("%player%", player.getName());
          }
          p.sendTitle(owner, null, 5, 40, 5);
          p.sendMessage(ChatManager.getPrefix() + message);
        }
      }
    }

  }

  /**
   * Get plot where players are voting currently
   *
   * @return Plot object where players are voting
   */
  public Plot getVotingPlot() {
    return votingPlot;
  }

  private void setVotingPlot(Plot buildPlot) {
    votingPlot = buildPlot;
  }

  private void announceResults() {
    List<String> messages = LanguageManager.getLanguageList("In-Game.Messages.Voting-Messages.Summary");
    List<String> formattedSummary = new ArrayList<>();
    for (String summary : messages) {
      String message = summary;
      message = ChatManager.colorRawMessage(message);
      for (int i = 1; i < 4; i++) {
        String access = "One";
        switch (i) {
          case 1:
            access = "One";
            break;
          case 2:
            access = "Two";
            break;
          case 3:
            access = "Three";
            break;
        }
        if (message.contains("%place_" + access.toLowerCase() + "%")) {
          if (topList.containsKey(i) && topList.get(i) != null && !topList.get(i).isEmpty()) {
            message = StringUtils.replace(message, "%place_" + access.toLowerCase() + "%", ChatManager.colorMessage("In-Game.Messages.Voting-Messages.Place-" + access)
                .replace("%player%", formatWinners(topList.get(i)))
                .replace("%number%", String.valueOf(getPlotManager().getPlot(topList.get(i).get(0)).getPoints())));
          } else {
            message = StringUtils.replace(message, "%place_" + access.toLowerCase() + "%", ChatManager.colorMessage("In-Game.Messages.Voting-Messages.Place-" + access)
                .replace("%player%", "None")
                .replace("%number%", "none"));
          }
        }
      }
      formattedSummary.add(message);
    }
    getPlayers().forEach((player) -> formattedSummary.forEach((msg) -> MinigameUtils.sendCenteredMessage(player, msg)));
    for (Integer rang : topList.keySet()) {
      if (topList.get(rang) != null) {
        for (Player p : topList.get(rang)) {
          if (rang > 3) {
            p.sendMessage(ChatManager.colorMessage("In-Game.Messages.Voting-Messages.Summary-Other-Place").replace("%number%", String.valueOf(rang)));
          }
          User user = getPlugin().getUserManager().getUser(p.getUniqueId());
          if (rang != 1) {
            user.addStat(StatsStorage.StatisticType.LOSES, 1);
            continue;
          }
          Plot plot = getPlotManager().getPlot(p);
          user.addStat(StatsStorage.StatisticType.WINS, 1);
          if (plot.getPoints() > user.getStat(StatsStorage.StatisticType.HIGHEST_WIN)) {
            user.setStat(StatsStorage.StatisticType.HIGHEST_WIN, plot.getPoints());
          }
        }
      }
    }
  }

  private String formatWinners(final List<Player> winners) {
    List<Player> players = new ArrayList<>(winners);
    StringBuilder builder = new StringBuilder(players.get(0).getName());
    if (players.size() == 1) {
      return builder.toString();
    } else {
      players.remove(0);
      for (Player p : players) {
        builder.append(" & ").append(p.getName());
      }
      return builder.toString();
    }
  }

  private void calculateResults() {
    for (int b = 1; b <= getPlayers().size(); b++) {
      topList.put(b, new ArrayList<>());
    }
    for (Plot buildPlot : getPlotManager().getPlots()) {
      long i = buildPlot.getPoints();
      for (int rang : topList.keySet()) {
        if (topList.get(rang) == null || topList.get(rang).isEmpty() || topList.get(rang).get(0) == null || getPlotManager().getPlot(topList.get(rang).get(0)) == null) {
          topList.put(rang, buildPlot.getOwners());
          break;
        }
        if (i > getPlotManager().getPlot(topList.get(rang).get(0)).getPoints()) {
          moveScore(rang, buildPlot.getOwners());
          break;
        }
        if (i == getPlotManager().getPlot(topList.get(rang).get(0)).getPoints()) {
          List<Player> winners = topList.get(rang);
          winners.addAll(buildPlot.getOwners());
          topList.put(rang, winners);
          break;
        }
      }
    }
  }

  private void moveScore(int pos, List<Player> owners) {
    List<Player> after = topList.get(pos);
    topList.put(pos, owners);
    if (!(pos > getPlayers().size()) && after != null) {
      moveScore(pos + 1, after);
    }
  }

  public void giveRewards() {
    if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.WIN_COMMANDS_ENABLED)) {
      if (topList.get(1) != null) {
        for (String string : getPlugin().getConfigPreferences().getWinCommands(ConfigPreferences.Position.FIRST)) {
          for (Player p : topList.get(1)) {
            getPlugin().getServer().dispatchCommand(getPlugin().getServer().getConsoleSender(), string.replace("%PLAYER%", p.getName()));
          }
        }
      }
    }
    if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.SECOND_PLACE_COMMANDS_ENABLED)) {
      if (topList.get(2) != null) {
        for (String string : getPlugin().getConfigPreferences().getWinCommands(ConfigPreferences.Position.SECOND)) {
          for (Player p : topList.get(2)) {
            getPlugin().getServer().dispatchCommand(getPlugin().getServer().getConsoleSender(), string.replace("%PLAYER%", p.getName()));
          }
        }
      }
    }
    if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.THIRD_PLACE_COMMANDS_ENABLED)) {
      if (topList.get(3) != null) {
        for (String string : getPlugin().getConfigPreferences().getWinCommands(ConfigPreferences.Position.THIRD)) {
          for (Player p : topList.get(3)) {
            getPlugin().getServer().dispatchCommand(getPlugin().getServer().getConsoleSender(), string.replace("%PLAYER%", p.getName()));
          }
        }
      }
    }
    if (getPlugin().getConfigPreferences().getOption(ConfigPreferences.Option.END_GAME_COMMANDS_ENABLED)) {
      for (String string : getPlugin().getConfigPreferences().getEndGameCommands()) {
        for (Player player : getPlayers()) {
          getPlugin().getServer().dispatchCommand(getPlugin().getServer().getConsoleSender(), string.replace("%PLAYER%", player.getName()).replace("%RANG%", Integer.toString(getRang(player))));
        }
      }
    }
  }

  public Integer getRang(Player player) {
    for (int i : topList.keySet()) {
      if (topList.get(i).contains(player)) {
        return i;
      }
    }
    return 0;
  }

}
