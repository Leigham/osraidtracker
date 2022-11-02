package com.raidtracker;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.ui.RaidTrackerPanel;
import com.raidtracker.utils.RaidState;
import com.raidtracker.utils.RaidStateTracker;
import com.raidtracker.utils.Utils;
import com.raidtracker.utils.raidUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
@Slf4j
@PluginDescriptor(
	name = "Raid Data Tracker"
)

public class RaidTrackerPlugin extends Plugin
{
	private static final String LEVEL_COMPLETE_MESSAGE = "complete! Duration:";
	private static final String RAID_COMPLETE_MESSAGE = "Congratulations - your raid is complete!";
	private static final String DUST_RECIPIENTS = "Dust recipients: ";
	private static final String TWISTED_KIT_RECIPIENTS = "Twisted Kit recipients: ";

	private static final int REGION_LOBBY = 13454;
	private static final int WIDGET_PARENT_ID = 481;
	private static final int WIDGET_CHILD_ID = 40;

	private RaidState currentState = new RaidState(false, -1);

	private EventBus eventBus;
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private RaidTrackerConfig config;

	@Inject
	private ConfigManager configManager;
	@Inject
	private ItemManager itemManager;

	@Inject
	private RaidTracker raidTracker;

	@Inject RaidStateTracker tracker;

	private static final WorldPoint TEMP_LOCATION = new WorldPoint(3360, 5152, 2);

	@Setter
	private RaidTrackerPanel panel;
	private NavigationButton navButton;

	@Setter
	private FileReadWriter fw = new FileReadWriter();
	private boolean writerStarted = false;
	public String RTName = "";

	private boolean isInRaid;
	private RaidTrackerPlugin RaidTrackerPlugin;

	String getProfileKey(ConfigManager configManager)
	{
		return configManager.getRSProfileKey();
	}
	@Provides
	RaidTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidTrackerConfig.class);
	}

	@Override
	public void startUp() {
		tracker.onPluginStart();
		panel = new RaidTrackerPanel(itemManager, fw, config, clientThread);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel-icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Raid Data Tracker")
				.priority(6)
				.icon(icon)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);
	}
	@Override
	protected void shutDown() {
		raidTracker.setInRaidCox(false);
		clientToolbar.removeNavigation(navButton);
		tracker.onPluginStop();
		reset();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (writerStarted) return;

		boolean tempInRaid = tracker.isInRaid();

		if (tempInRaid ^ raidTracker.inRaid)
		{
			if (tempInRaid && raidTracker.isLoggedIn())
			{
				checkRaidPresence();
			} else if (raidTracker.isRaidComplete() && !raidTracker.isChestOpened())
			{
				fw.writeToFile(raidTracker);

				writerStarted = true;

				SwingUtilities.invokeLater(() -> {
					panel.addDrop(raidTracker);
					reset();
				});
			};
		};

	};

	@Subscribe
	public  void onGameTick(GameTick e)
	{
		//System.out.println(tracker.getCurrentState());
	};
	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (client.getLocalPlayer().getName() != null && RTName  == "")
		{
			RTName = client.getLocalPlayer().getName().replace("\u00a0"," ");
			fw.updateUsername(getProfileKey(configManager));
			SwingUtilities.invokeLater(() -> panel.loadRTList());
		};
	}


	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{

		if (event.getGameState() == GameState.LOGGING_IN) {};
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			// skip event while the game decides if the player belongs in a raid or not
			if (client.getLocalPlayer() == null
					|| client.getLocalPlayer().getWorldLocation().equals(TEMP_LOCATION))
			{
				//noinspection UnnecessaryReturnStatement
				return;
			}
		}
		else if (client.getGameState() == GameState.LOGIN_SCREEN
				|| client.getGameState() == GameState.CONNECTION_LOST)
		{
			raidTracker.setLoggedIn(false);
		}
		else if (client.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		checkChatMessage(event, raidTracker);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		//if (raidTracker.isChestOpened() || !raidTracker.isRaidComplete()) return;
		raidTracker.setLoggedIn(true);
		//if (writerStarted) return;

		switch (event.getGroupId()) {
			case (WidgetID.TOA_REWARD_GROUP_ID) :
				raidTracker.setChestOpened(true);

				System.out.println("Opening Toa Chest.");

				ItemContainer toaChestContainer = client.getItemContainer(InventoryID.TOA_REWARD_CHEST);

				if (toaChestContainer == null) return;


				raidTracker.setLootList((lootListFactory(toaChestContainer.getItems())));
				raidTracker.inRaidType = 2;
				System.out.println(raidTracker);
				fw.writeToFile(raidTracker);

				writerStarted = true;
				System.out.println(panel);
				SwingUtilities.invokeLater(() -> {
					panel.addDrop(raidTracker);
					reset();
				});
				break;
			case (WidgetID.CHAMBERS_OF_XERIC_REWARD_GROUP_ID):


				raidTracker.setChestOpened(true);

				ItemContainer rewardItemContainer = client.getItemContainer(InventoryID.CHAMBERS_OF_XERIC_CHEST);

				if (rewardItemContainer == null) {
					return;
				}


				raidTracker.setLootList(lootListFactory(rewardItemContainer.getItems()));
				raidTracker.inRaidType = 0;
				fw.writeToFile(raidTracker);

				writerStarted = true;

				SwingUtilities.invokeLater(() -> {
					panel.addDrop(raidTracker);
					reset();
				});

				break;

			case (WidgetID.THEATRE_OF_BLOOD_GROUP_ID):


				raidTracker.setChestOpened(true);

				rewardItemContainer = client.getItemContainer(InventoryID.THEATRE_OF_BLOOD_CHEST);

				if (rewardItemContainer == null) {
					return;
				}

				raidTracker.setLootList(lootListFactory(rewardItemContainer.getItems()));
				raidTracker.inRaidType = 1;
				fw.writeToFile(raidTracker);

				writerStarted = true;

				SwingUtilities.invokeLater(() -> {
					panel.addDrop(raidTracker);
					reset();
				});

				break;
			//459 is the mvp screen of TOB
			case (459):
				AtomicReference<String> mvp = new AtomicReference<>("");
				AtomicReference<String>[] tobPlayers;
				AtomicInteger[] tobDeaths = {new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger()};

				String[] players;

				clientThread.invokeLater(() -> {

					raidTracker.tobPlayers = new String[]{
							getWidgetText(client.getWidget(459, 22)),
							getWidgetText(client.getWidget(459, 24)),
							getWidgetText(client.getWidget(459, 26)),
							getWidgetText(client.getWidget(459, 28)),
							getWidgetText(client.getWidget(459, 30))
					};

					raidTracker.tobDeaths = new int[]{
							getWidgetNumber(client.getWidget(459, 23)),
							getWidgetNumber(client.getWidget(459, 25)),
							getWidgetNumber(client.getWidget(459, 27)),
							getWidgetNumber(client.getWidget(459, 29)),
							getWidgetNumber(client.getWidget(459, 31))
					};

					raidTracker.setMvp(getWidgetText(client.getWidget(459, 14)));

					if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
						raidTracker.setMvpInOwnName(getWidgetText(client.getWidget(459, 14)).equalsIgnoreCase(client.getLocalPlayer().getName()));
					};
				});

				break;

		}
	}

	private String getWidgetText(Widget widget) {
		if (widget == null) {
			return "";
		}
		else if (widget.getText().equals("-")) {
			return "";
		}
		return widget.getText();
	}

	private int getWidgetNumber(Widget widget) {
		if (widget == null) {
			return 0;
		}
		else if (widget.getText().equals("-")) {
			return 0;
		}
		return Integer.parseInt(widget.getText());
	}

	public void checkChatMessage(ChatMessage event, RaidTracker raidTracker)
	{
		raidTracker.setLoggedIn(true);
		String playerName = "";
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
			playerName = client.getLocalPlayer().getName();
		}
		if (tracker.isInRaid() && (event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION || event.getType() == ChatMessageType.GAMEMESSAGE))
		{
			//unescape java to avoid unicode
			String message = unescapeJavaString(Text.removeTags(event.getMessage()));

			String roomCompleteMessages[] =
			{
					LEVEL_COMPLETE_MESSAGE, // Chambers
					"wave '", // Tob
					"Challenge complete:" // Toa
			};

			if (Utils.containsCaseInsensitive(Arrays.asList(roomCompleteMessages), message))
			{
				System.out.println("gettings times");
				raidUtils.parseRaidTime(message, raidTracker, tracker);
			};


			if (raidTracker.isRaidComplete() && message.contains("Team size:")) {
				raidTracker.setRaidTime(stringTimeToSeconds(message.split("Duration: ")[1].split(" ")[0]));
			}

			//works for tob
			if (message.contains("count is:")) {
				raidTracker.setChallengeMode(message.contains("Chambers of Xeric Challenge Mode"));
				raidTracker.setCompletionCount(parseInt(message.split("count is:")[1].trim().replace(".", "")));
				System.out.println(tracker.getCurrentState().getRaidType());

				if (tracker.getCurrentState().getRaidType() == 0)
				{
					raidTracker.setTotalPoints(client.getVarbitValue(Varbits.TOTAL_POINTS));
					raidTracker.setPersonalPoints(client.getVarbitValue(Varbits.PERSONAL_POINTS));
					raidTracker.setPercentage(raidTracker.getPersonalPoints() / (raidTracker.getTotalPoints() / 100.0));
					raidTracker.setTeamSize(client.getVarbitValue(Varbits.RAID_PARTY_SIZE));
					raidTracker.setRaidComplete(true);
					raidTracker.setDate(System.currentTimeMillis());
				};

				if (tracker.getCurrentState().getRaidType() == 2)
				{
					int playerVarbits[] = {
							Varbits.TOA_MEMBER_0_HEALTH, Varbits.TOA_MEMBER_1_HEALTH, Varbits.TOA_MEMBER_2_HEALTH,
							Varbits.TOA_MEMBER_3_HEALTH, Varbits.TOA_MEMBER_4_HEALTH, Varbits.TOA_MEMBER_5_HEALTH,
							Varbits.TOA_MEMBER_6_HEALTH, Varbits.TOA_MEMBER_7_HEALTH

					};

					raidTracker.setTeamSize(Arrays.stream(playerVarbits).filter(vb -> (client.getVarbitValue(vb) > 0)).toArray().length);
					raidTracker.setRaidComplete(true);
					raidTracker.setDate(System.currentTimeMillis());
					raidTracker.setInvocation(client.getVarbitValue(Varbits.TOA_RAID_LEVEL));
				};


				if (tracker.getCurrentState().getRaidType() == 1) {
					int teamSize = 0;

					for (int i = 6442; i  < 6447; i++) {
						if (client.getVarbitValue(i) != 0) {
							teamSize++;
						}
					}
					raidTracker.setTeamSize(teamSize);
					raidTracker.setRaidComplete(true);
				}
			}

			//only special loot contain the "-" (except for the raid complete message)
			if (raidTracker.isRaidComplete() && message.contains("-") && !message.startsWith(RAID_COMPLETE_MESSAGE)) {
				//in case of multiple purples, a new purple is stored on a new line in the file, so a new raidtracker object will be used and written to the file
				if (!raidTracker.getSpecialLootReceiver().isEmpty()) {
					RaidTracker altRT = copyData();

					altRT.setSpecialLootReceiver(message.split(" - ")[0]);
					altRT.setSpecialLoot(message.split(" - ")[1]);

					altRT.setSpecialLootInOwnName(altRT.getSpecialLootReceiver().toLowerCase().trim().equals(playerName.toLowerCase().trim()));


					altRT.setSpecialLootValue(itemManager.search(raidTracker.getSpecialLoot()).get(0).getPrice());

					setSplits(altRT);

					fw.writeToFile(altRT);

					SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
				}
				else {
					raidTracker.setSpecialLootReceiver(message.split(" - ")[0]);
					raidTracker.setSpecialLoot(message.split(" - ")[1]);

					raidTracker.setSpecialLootValue(itemManager.search(raidTracker.getSpecialLoot()).get(0).getPrice());

					raidTracker.setSpecialLootInOwnName(raidTracker.getSpecialLootReceiver().toLowerCase().trim().equals(playerName.toLowerCase().trim()));


					setSplits(raidTracker);
				}
			}

			//for tob it works a bit different, not possible to get duplicates. - not tested in game yet.
			if (raidTracker.isRaidComplete() && message.toLowerCase().contains("found something special") && !message.toLowerCase().contains("lil' zik")) {
				raidTracker.setSpecialLootReceiver(message.split(" found something special: ")[0]);
				raidTracker.setSpecialLoot(message.split(" found something special: ")[1]);

				raidTracker.setSpecialLootValue(itemManager.search(raidTracker.getSpecialLoot()).get(0).getPrice());

				raidTracker.setSpecialLootInOwnName(raidTracker.getSpecialLootReceiver().toLowerCase().trim().equals(playerName.toLowerCase().trim()));
				raidTracker.inRaidType = 1;
				fw.writeToFile(raidTracker);
			}

			if (raidTracker.isRaidComplete() && message.startsWith(TWISTED_KIT_RECIPIENTS)) {
				String[] recipients = message.split(TWISTED_KIT_RECIPIENTS)[1].split(",");

				for (String recip : recipients) {
					if (raidTracker.getKitReceiver().isEmpty()) {
						raidTracker.setKitReceiver(recip.trim());
					}
					else {
						RaidTracker altRT = copyData();
						altRT.setKitReceiver(recip.trim());

						fw.writeToFile(altRT);

						SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
					}
				}
			}

			if (raidTracker.isRaidComplete() && message.startsWith(DUST_RECIPIENTS)) {
				String[] recipients = message.split(DUST_RECIPIENTS)[1].split(",");

				for (String recip : recipients) {
					if (raidTracker.getDustReceiver().isEmpty()) {
						raidTracker.setDustReceiver(recip.trim());
					}
					else {
						RaidTracker altRT = copyData();
						altRT.setDustReceiver(recip.trim());

						fw.writeToFile(altRT);

						SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
					}
				}
			}

			if (raidTracker.isRaidComplete() && (message.toLowerCase().contains("olmlet") || message.toLowerCase().contains("lil' zik")) || message.toLowerCase().contains("would have been followed")) {
				boolean inOwnName = false;
				boolean duplicate = message.toLowerCase().contains("would have been followed");

				if (playerName.equals(message.split(" ")[0]) || duplicate)	{
					inOwnName = true;
				}

				if (!raidTracker.getPetReceiver().isEmpty()) {
					RaidTracker altRT = copyData();

					if (duplicate) {
						altRT.setPetReceiver(playerName);
					}
					else {
						altRT.setPetReceiver(message.split(" ")[0]);
					}

					altRT.setPetInMyName(inOwnName);

					fw.writeToFile(altRT);

					SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
				}
				else {
					if (duplicate) {
						raidTracker.setPetReceiver(playerName);
					}
					else {
						raidTracker.setPetReceiver(message.split(" ")[0]);
					}
					raidTracker.setPetInMyName(inOwnName);
				}
			}
		}
	}

	public void setSplits(RaidTracker raidTracker)
	{

		int lootSplit = raidTracker.getSpecialLootValue() / raidTracker.getTeamSize();

		int cutoff = config.FFACutoff();

		//
		if (raidTracker.getSpecialLoot().length() > 0) {
			if (config.defaultFFA() || lootSplit < cutoff) {
				raidTracker.setFreeForAll(true);
				if (raidTracker.isSpecialLootInOwnName()) {
					raidTracker.setLootSplitReceived(raidTracker.getSpecialLootValue());
				}
			} else if (raidTracker.isSpecialLootInOwnName()) {
				raidTracker.setLootSplitPaid(raidTracker.getSpecialLootValue() - lootSplit);
				raidTracker.setLootSplitReceived(lootSplit);
			} else {
				raidTracker.setLootSplitReceived(lootSplit);
			}
		}
	}

	public ArrayList<RaidTrackerItem> lootListFactory(Item[] items)
	{
		ArrayList<RaidTrackerItem> lootList = new ArrayList<>();
		Arrays.stream(items)
				.filter(item -> item.getId() > -1)
				.forEach(item -> {
					ItemComposition comp = itemManager.getItemComposition(item.getId());
					lootList.add(new RaidTrackerItem(comp.getName(), comp.getId(), item.getQuantity(),comp.getPrice() * item.getQuantity()));
				});
		return lootList;
	}

	private void checkRaidPresence()
	{
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		Widget toaWidget = client.getWidget(481, 40);
		raidTracker.setInRaidCox(client.getVarbitValue(Varbits.IN_RAID) == 1);
		raidTracker.setInRaidTob(client.getVarbitValue(Varbits.THEATRE_OF_BLOOD) > 1);
		raidTracker.setInRaidToa((client.getVarbitValue(14345) == 1) && (toaWidget != null && !toaWidget.isHidden()));
	}



	public static int stringTimeToSeconds(String s)
	{
		String[] split = s.split(":");
		return split.length == 3 ? parseInt(split[0]) * 3600 + parseInt(split[1]) * 60 + Math.round(parseFloat(split[2])) : parseInt(split[0]) * 60 + Math.round(parseFloat(split[1]));
	}

	public RaidTracker copyData() {
		RaidTracker RT = new RaidTracker();

		RT.setDate(raidTracker.getDate());
		RT.setTeamSize(raidTracker.getTeamSize());
		RT.setChallengeMode(raidTracker.isChallengeMode());
		RT.setInRaidTob(raidTracker.isInRaidTob());
		RT.setCompletionCount(raidTracker.getCompletionCount());
		RT.setKillCountID(raidTracker.getKillCountID());

		return RT;
	}

	private void reset()
	{
		raidTracker = new RaidTracker();
		writerStarted = false;
	}

	//from stackoverflow
	public String unescapeJavaString(String st) {

		if (st == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder(st.length());

		for (int i = 0; i < st.length(); i++) {
			char ch = st.charAt(i);
			if (ch == '\\') {
				char nextChar = (i == st.length() - 1) ? '\\' : st
						.charAt(i + 1);
				// Octal escape?
				if (nextChar >= '0' && nextChar <= '7') {
					String code = "" + nextChar;
					i++;
					if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
							&& st.charAt(i + 1) <= '7') {
						code += st.charAt(i + 1);
						i++;
						if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
								&& st.charAt(i + 1) <= '7') {
							code += st.charAt(i + 1);
							i++;
						}
					}
					sb.append((char) Integer.parseInt(code, 8));
					continue;
				}
				switch (nextChar) {
					case '\\':
						ch = '\\';
						break;
					case 'b':
						ch = '\b';
						break;
					case 'f':
						ch = '\f';
						break;
					case 'n':
						ch = '\n';
						break;
					case 'r':
						ch = '\r';
						break;
					case 't':
						ch = '\t';
						break;
					case '\"':
						ch = '\"';
						break;
					case '\'':
						ch = '\'';
						break;
					// Hex Unicode: u????
					case 'u':
						if (i >= st.length() - 5) {
							ch = 'u';
							break;
						}
						int code = Integer.parseInt(
								"" + st.charAt(i + 2) + st.charAt(i + 3)
										+ st.charAt(i + 4) + st.charAt(i + 5), 16);
						sb.append(Character.toChars(code));
						i += 5;
						continue;
				}
				i++;
			}
			sb.append(ch);
		}
		return sb.toString();
	}
}
