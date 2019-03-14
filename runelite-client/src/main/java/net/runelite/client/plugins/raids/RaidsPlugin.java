/*
 * Copyright (c) 2018, Kamiel
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.raids;

import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InstanceTemplates;
import net.runelite.api.NullObjectID;
import static net.runelite.api.Perspective.SCENE_SIZE;
import net.runelite.api.Point;
import static net.runelite.api.SpriteID.TAB_QUESTS_BROWN_RAIDING_PARTY;
import net.runelite.api.Tile;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.raids.solver.Layout;
import net.runelite.client.plugins.raids.solver.LayoutSolver;
import net.runelite.client.plugins.raids.solver.RotationSolver;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;

@PluginDescriptor(
	name = "Chambers Of Xeric",
	description = "Show helpful information for the Chambers of Xeric raid",
	tags = {"combat", "raid", "overlay", "pve", "pvm", "bosses"}
)
@Slf4j
public class RaidsPlugin extends Plugin
{
	private static final int LOBBY_PLANE = 3;
	private static final String RAID_START_MESSAGE = "The raid has begun!";
	private static final String LEVEL_COMPLETE_MESSAGE = "level complete!";
	private static final String RAID_COMPLETE_MESSAGE = "Congratulations - your raid is complete!";
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("###.##");
	static final DecimalFormat POINTS_FORMAT = new DecimalFormat("#,###");
	private static final String SPLIT_REGEX = "\\s*,\\s*";
	private static final Pattern ROTATION_REGEX = Pattern.compile("\\[(.*?)]");
	private static final Pattern RAID_TIME_REGEX = Pattern.compile("Congratulations - your raid is complete! Duration: ([0-9]*[0-9]):([0-9]*[0-9])");
	private static final int OUTSIDE_RAID_REGION = 4919;
	private static boolean olmSpawned = false;
	private static boolean skip = true;
	private static boolean marked = false;

	@Inject
	private ChatMessageManager chatMessageManager;

	private RaidsSidepanel panel;
	private NavigationButton navButton;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private Client client;

	@Inject
	private RaidsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RaidsOverlay overlay;

	@Inject
	private LayoutSolver layoutSolver;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Getter
	private final ArrayList<String> roomWhitelist = new ArrayList<>();

	@Getter
	private final ArrayList<String> roomBlacklist = new ArrayList<>();

	@Getter
	private final ArrayList<String> rotationWhitelist = new ArrayList<>();

	@Getter
	private final ArrayList<String> layoutWhitelist = new ArrayList<>();

	@Getter
	private Raid raid;

	//This is a temp raid for the overlay to grab information from when the player is outside a raid.
	@Getter
	private Raid oldRaid;

	@Getter
	private boolean inRaidChambers;

	private RaidsTimer timer;
	private String previousUsername = "";
	private boolean inRaidParty;

	@Provides
	RaidsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidsConfig.class);
	}

	@Override
	public void configure(Binder binder)
	{
		binder.bind(RaidsOverlay.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(RaidsSidepanel.class);
		panel.init();

		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "instancereloadhelper.png");

		navButton = NavigationButton.builder()
			.tooltip("Instance Reload Helper")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		if (config.reloadBool())
		{
			clientToolbar.addNavigation(navButton);
		}
		overlayManager.add(overlay);
		updateLists();
		clientThread.invokeLater(() -> checkRaidPresence(true));
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		infoBoxManager.removeInfoBox(timer);
		inRaidChambers = false;
		reset();
		olmSpawned = false;
		skip = true;
		marked = false;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("raids"))
		{
			return;
		}

		if (event.getKey().equals("reload"))
		{
			SwingUtilities.invokeLater(() ->
			{
				if (config.reloadBool())
				{
					clientToolbar.addNavigation(navButton);
				}
				else
				{
					clientToolbar.removeNavigation(navButton);
				}
			});
		}

		if (event.getKey().equals("raidsTimer"))
		{
			updateInfoBoxState();
			return;
		}

		updateLists();
		clientThread.invokeLater(() -> checkRaidPresence(true));
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		String[] args = commandExecuted.getArguments();

		if ("r".equals(commandExecuted.getCommand()))
		{
			reload();
		}
	}

	public void reload()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		try
		{
			Method m = client.getClass().getClassLoader().loadClass("ay").getDeclaredMethod("fy", Integer.TYPE);
			m.setAccessible(true);
			m.invoke(null, -904767418);
			while (client.getGameState() != GameState.CONNECTION_LOST)
			{
				//probably not the best way to do this...
			}
			//TODO: Since this is mainly for raids i'd like to reload the raids scouting plugin after the dc is finished

		}
		catch (ReflectiveOperationException f)
		{
			throw new RuntimeException(f);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		boolean inParty = client.getVar(VarPlayer.IN_RAID_PARTY) != -1;
		if (inParty != inRaidParty)
		{
			if (!inParty)
			{
				oldRaid = null;
			}
			inRaidParty = inParty;
		}
		checkRaidPresence(false);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (inRaidChambers && event.getType() == ChatMessageType.CLANCHAT_INFO)
		{
			String message = Text.removeTags(event.getMessage());

			if (config.raidsTimer() && message.startsWith(RAID_START_MESSAGE))
			{
				timer = new RaidsTimer(spriteManager.getSprite(TAB_QUESTS_BROWN_RAIDING_PARTY, 0), this, Instant.now());
				infoBoxManager.addInfoBox(timer);
			}

			if (timer != null && message.contains(LEVEL_COMPLETE_MESSAGE))
			{
				timer.timeFloor();
			}

			if (message.startsWith(RAID_COMPLETE_MESSAGE))
			{
				olmSpawned = false;
				skip = true;
				marked = false;

				if (timer != null)
				{
					timer.timeOlm();
					timer.setStopped(true);
				}

				if (config.pointsMessage())
				{
					Matcher m = RAID_TIME_REGEX.matcher(message);

					int totalPoints = client.getVar(Varbits.TOTAL_POINTS);
					int personalPoints = client.getVar(Varbits.PERSONAL_POINTS);

					double percentage = personalPoints / (totalPoints / 100.0);

					String chatMessage = new ChatMessageBuilder()
						.append(ChatColorType.NORMAL)
						.append("Total points: ")
						.append(ChatColorType.HIGHLIGHT)
						.append(POINTS_FORMAT.format(totalPoints))
						.append(ChatColorType.NORMAL)
						.append(", Personal points: ")
						.append(ChatColorType.HIGHLIGHT)
						.append(POINTS_FORMAT.format(personalPoints))
						.append(ChatColorType.NORMAL)
						.append(" (")
						.append(ChatColorType.HIGHLIGHT)
						.append(DECIMAL_FORMAT.format(percentage))
						.append(ChatColorType.NORMAL)
						.append("%)")
						.build();

					chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.CLANCHAT_INFO)
						.runeLiteFormattedMessage(chatMessage)
						.build());

					if (m.find())
					{
						double raidTime = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
						double pointsPerHour = (personalPoints / raidTime) * 60 * 60;

						String pointHrMessage = new ChatMessageBuilder()
							.append(ChatColorType.NORMAL)
							.append("Points/hr: ")
							.append(ChatColorType.HIGHLIGHT)
							.append(POINTS_FORMAT.format(pointsPerHour))
							.build();

						chatMessageManager.queue(QueuedMessage.builder()
							.type(ChatMessageType.CLANCHAT_INFO)
							.runeLiteFormattedMessage(pointHrMessage)
							.build());
					}
				}
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (!config.showOlmSpawn() || event.getNpc().getId() != 7551 && event.getNpc().getId() != 7554)
		{
			return;
		}

		if (!olmSpawned)
		{
			client.setHintArrow(event.getNpc());
			olmSpawned = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!olmSpawned)
		{
			return;
		}

		if (skip || marked)
		{
			skip = false;
			return;
		}

		client.clearHintArrow();
		marked = true;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			String username = client.getUsername();
			int playerRegion = client.getLocalPlayer().getWorldLocation().getRegionID();
			if (raid != null)
			{
				if (playerRegion == OUTSIDE_RAID_REGION)
				{
					if (client.getVar(Varbits.RAID_ONGOING) != 5)
					{
						oldRaid = raid;
					}
					else
					{
						oldRaid = null;
					}
					reset();
				}
				else if (!username.equals(previousUsername))
				{
					reset();
				}
				else
				{
					checkRaidPresence(false);
				}
			}
			previousUsername = username;
		}
	}

	private void checkRaidPresence(boolean force)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (raid != null)
		{
			raid = buildRaid();
			RotationSolver.solve(raid.getCombatRooms());
			return;
		}

		boolean setting = client.getVar(Varbits.IN_RAID) == 1;

		if (force || inRaidChambers != setting)
		{
			inRaidChambers = setting;
			updateInfoBoxState();

			if (inRaidChambers)
			{
				raid = buildRaid();

				if (raid == null)
				{
					log.debug("Failed to build raid");
					return;
				}

				Layout layout = layoutSolver.findLayout(raid.toCode());

				if (layout == null)
				{
					log.debug("Could not find layout match");
					return;
				}

				raid.updateLayout(layout);
				RotationSolver.solve(raid.getCombatRooms());
				sendRaidLayoutMessage();
			}
		}
	}

	private void sendRaidLayoutMessage()
	{
		if (!config.layoutMessage() || oldRaid != null)
		{
			return;
		}

		final String layout = getRaid().getLayout().toCodeString();
		final String rooms = getRaid().toRoomString();
		final String raidData = "[" + layout + "]: " + rooms;

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CLANCHAT_INFO)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("Layout: ")
				.append(ChatColorType.NORMAL)
				.append(raidData)
				.build())
			.build());
	}

	private void updateInfoBoxState()
	{
		if (timer == null)
		{
			return;
		}

		if (inRaidChambers && config.raidsTimer())
		{
			if (!infoBoxManager.getInfoBoxes().contains(timer))
			{
				infoBoxManager.addInfoBox(timer);
			}
		}
		else
		{
			infoBoxManager.removeInfoBox(timer);
		}

		if (!inRaidChambers)
		{
			timer = null;
		}
	}

	private void updateLists()
	{
		updateList(roomWhitelist, config.whitelistedRooms());
		updateList(roomBlacklist, config.blacklistedRooms());
		updateList(rotationWhitelist, config.whitelistedRotations());
		updateList(layoutWhitelist, config.whitelistedLayouts());
	}

	private void updateList(ArrayList<String> list, String input)
	{
		list.clear();

		if (list == rotationWhitelist)
		{
			Matcher m = ROTATION_REGEX.matcher(input);
			while (m.find())
			{
				String rotation = m.group(1).toLowerCase();

				if (!list.contains(rotation))
				{
					list.add(rotation);
				}
			}
		}
		else
		{
			list.addAll(Arrays.asList(input.toLowerCase().split(SPLIT_REGEX)));
		}
	}

	int getRotationMatches(Raid raid)
	{
		String rotation = raid.getRotationString().toLowerCase();
		String[] bosses = rotation.split(SPLIT_REGEX);

		if (rotationWhitelist.contains(rotation))
		{
			return bosses.length;
		}

		for (String whitelisted : rotationWhitelist)
		{
			int matches = 0;
			String[] whitelistedBosses = whitelisted.split(SPLIT_REGEX);

			for (int i = 0; i < whitelistedBosses.length; i++)
			{
				if (i < bosses.length && whitelistedBosses[i].equals(bosses[i]))
				{
					matches++;
				}
				else
				{
					matches = 0;
					break;
				}
			}

			if (matches >= 2)
			{
				return matches;
			}
		}

		return 0;
	}

	private Point findLobbyBase()
	{
		Tile[][] tiles = client.getScene().getTiles()[LOBBY_PLANE];

		for (int x = 0; x < SCENE_SIZE; x++)
		{
			for (int y = 0; y < SCENE_SIZE; y++)
			{
				if (tiles[x][y] == null || tiles[x][y].getWallObject() == null)
				{
					continue;
				}

				if (tiles[x][y].getWallObject().getId() == NullObjectID.NULL_12231)
				{
					return tiles[x][y].getSceneLocation();
				}
			}
		}

		return null;
	}

	private Raid buildRaid()
	{
		if (raid == null)
		{
			Point gridBase = findLobbyBase();

			if (gridBase == null)
			{
				return null;
			}

			Raid raid = new Raid(new WorldPoint(client.getBaseX() + gridBase.getX(), client.getBaseY() + gridBase.getY(), 3));
			Tile[][] tiles;
			int position, x, y, offsetX;
			int startX = -2;

			for (int plane = 3; plane > 1; plane--)
			{
				tiles = client.getScene().getTiles()[plane];

				if (tiles[gridBase.getX() + RaidRoom.ROOM_MAX_SIZE][gridBase.getY()] == null)
				{
					position = 1;
				}
				else
				{
					position = 0;
				}

				for (int i = 1; i > -2; i--)
				{
					y = gridBase.getY() + (i * RaidRoom.ROOM_MAX_SIZE);

					for (int j = startX; j < 4; j++)
					{
						x = gridBase.getX() + (j * RaidRoom.ROOM_MAX_SIZE);
						offsetX = 0;

						if (x > SCENE_SIZE && position > 1 && position < 4)
						{
							position++;
						}

						if (x < 0)
						{
							offsetX = Math.abs(x) + 1; //add 1 because the tile at x=0 will always be null
						}

						if (x < SCENE_SIZE && y >= 0 && y < SCENE_SIZE)
						{
							if (tiles[x + offsetX][y] == null)
							{
								if (position == 4)
								{
									position++;
									break;
								}

								continue;
							}

							if (position == 0 && startX != j)
							{
								startX = j;
							}

							Tile base = tiles[offsetX > 0 ? 1 : x][y];
							RaidRoom room = determineRoom(base);
							int actualPos = position + Math.abs((plane - 3) * 8);
							raid.setRoom(room, actualPos);
							if (room.getType() == RaidRoom.Type.START && actualPos < 8)
							{
								raid.setBasePosition(actualPos);
							}
							position++;
						}
					}
				}
			}

			return raid;
		}
		else
		{
			for (int i = 0; i < raid.getRooms().length; i++)
			{
				RaidRoom room = raid.getRooms()[i];
				if (room == null)
				{
					continue;
				}
				if ((room.getPuzzle() != RaidRoom.Puzzle.UNKNOWN)
						&& (room.getBoss() != RaidRoom.Boss.UNKNOWN))
				{
					continue;
				}
				WorldPoint base;
				if (room.getBase() != null)
				{
					base = room.getBase();
				}
				else
				{
					int x = i % 4;
					int y = i % 8 > 3 ? 1 : 0;
					int plane = i > 7 ? 2 : 3;

					int baseX = raid.getBasePosition() % 4;
					int baseY = raid.getBasePosition() % 8 > 3 ? 1 : 0;

					x = x - baseX;
					y = y - baseY;

					x = raid.getGridBase().getX() + x * RaidRoom.ROOM_MAX_SIZE;
					y = raid.getGridBase().getY() - y * RaidRoom.ROOM_MAX_SIZE;
					base = new WorldPoint(x , y, plane);
					room.setBase(base);
					raid.setRoom(room, i);
				}

				int x = base.getX() - client.getBaseX();
				int y = base.getY() - client.getBaseY();

				if (x < (1 - RaidRoom.ROOM_MAX_SIZE) || x >= SCENE_SIZE)
				{
					continue;
				}
				else if (x < 1)
				{
					x = 1;
				}

				if (y < 1)
				{
					y = 1;
				}

				Tile tile = client.getScene().getTiles()[base.getPlane()][x][y];

				if (tile == null)
				{
					continue;
				}

				room = determineRoom(tile);

				if (room.getType() == RaidRoom.Type.EMPTY)
				{
					continue;
				}

				raid.setRoom(room, i);
			}

			return raid;
		}
	}

	private RaidRoom determineRoom(Tile base)
	{
		RaidRoom room = new RaidRoom(base.getWorldLocation(), RaidRoom.Type.EMPTY);
		int chunkData = client.getInstanceTemplateChunks()[base.getPlane()][(base.getSceneLocation().getX()) / 8][base.getSceneLocation().getY() / 8];
		InstanceTemplates template = InstanceTemplates.findMatch(chunkData);

		if (template == null)
		{
			return room;
		}

		switch (template)
		{
			case RAIDS_LOBBY:
			case RAIDS_START:
				room.setType(RaidRoom.Type.START);
				break;

			case RAIDS_END:
				room.setType(RaidRoom.Type.END);
				break;

			case RAIDS_SCAVENGERS:
			case RAIDS_SCAVENGERS2:
				room.setType(RaidRoom.Type.SCAVENGERS);
				break;

			case RAIDS_SHAMANS:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.SHAMANS);
				break;

			case RAIDS_VASA:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.VASA);
				break;

			case RAIDS_VANGUARDS:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.VANGUARDS);
				break;

			case RAIDS_ICE_DEMON:
				room.setType(RaidRoom.Type.PUZZLE);
				room.setPuzzle(RaidRoom.Puzzle.ICE_DEMON);
				break;

			case RAIDS_THIEVING:
				room.setType(RaidRoom.Type.PUZZLE);
				room.setPuzzle(RaidRoom.Puzzle.THIEVING);
				break;

			case RAIDS_FARMING:
			case RAIDS_FARMING2:
				room.setType(RaidRoom.Type.FARMING);
				break;

			case RAIDS_MUTTADILES:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.MUTTADILES);
				break;

			case RAIDS_MYSTICS:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.MYSTICS);
				break;

			case RAIDS_TEKTON:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.TEKTON);
				break;

			case RAIDS_TIGHTROPE:
				room.setType(RaidRoom.Type.PUZZLE);
				room.setPuzzle(RaidRoom.Puzzle.TIGHTROPE);
				break;

			case RAIDS_GUARDIANS:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.GUARDIANS);
				break;

			case RAIDS_CRABS:
				room.setType(RaidRoom.Type.PUZZLE);
				room.setPuzzle(RaidRoom.Puzzle.CRABS);
				break;

			case RAIDS_VESPULA:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.VESPULA);
				break;
		}

		return room;
	}

	public void reset()
	{
		raid = null;
		updateInfoBoxState();
	}
}
