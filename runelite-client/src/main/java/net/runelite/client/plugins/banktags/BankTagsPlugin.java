/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
package net.runelite.client.plugins.banktags;

import com.google.common.eventbus.Subscribe;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IntegerNode;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import static net.runelite.api.MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SpritePixels;
import net.runelite.api.VarClientStr;
import net.runelite.api.WidgetType;
import net.runelite.api.events.DraggingWidgetChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ChatboxInputManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@PluginDescriptor(
	name = "Bank Tags",
	description = "Enable tagging of bank items and searching of bank tags",
	tags = {"searching", "tagging"}
)
@Slf4j
public class BankTagsPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "banktags";

	private static final String ITEM_KEY_PREFIX = "item_";

	private static final String SEARCH_BANK_INPUT_TEXT =
		"Show items whose names or tags contain the following text:<br>" +
			"(To show only tagged items, start your search with 'tag:')";

	private static final String SEARCH_BANK_INPUT_TEXT_FOUND =
		"Show items whose names or tags contain the following text: (%d found)<br>" +
			"(To show only tagged items, start your search with 'tag:')";

	private static final String TAG_SEARCH = "tag:";

	private static final String EDIT_TAGS_MENU_OPTION = "Edit-tags";

	private static final int EDIT_TAGS_MENU_INDEX = 8;

	private static final String SCROLL_UP = "Scroll Up";
	private static final String SCROLL_DOWN = "Scroll Down";
	private static final String CHANGE_ICON = "Set Icon";

	private boolean processIcon = false;
	private TagTab iconToSet;

	public Rectangle tabsBounds = new Rectangle();

	@Setter
	private TagTab focusedTab = null;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ItemManager itemManager;

	@Getter
	private boolean isBankOpen = false;

	@Getter
	private List<TagTab> tabs = new ArrayList<>();

	@Getter
	private String searchStr = "";

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatboxInputManager chatboxInputManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;


	@Inject
	private ClientThread clientThread;

	@Inject
	private TooltipManager tooltipManager;

	private Rectangle bounds = new Rectangle();

	private int idx = 0;

	private List<TagTab> tagTabs = new ArrayList<>();

	private Widget tArrowBg;
	private Widget tArrowIcon;
	private Widget bArrowBg;
	private Widget bArrowIcon;
	private boolean dragging = false;
	int maxTabs = 0;

	private Widget activeTab = null;

	private Map<Integer, SpritePixels> spriteOverrides = new HashMap<>();


	@Override
	public void startUp()
	{
	}

	@Override
	public void shutDown()
	{

	}

	private boolean updateBounds()
	{
		Widget itemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		if (itemContainer != null)
		{
			bounds.setSize(41, itemContainer.getHeight());
			bounds.setLocation(0, itemContainer.getRelativeY());

			Widget incinerator = client.getWidget(WidgetInfo.BANK_INCINERATOR);

			if (incinerator != null && !incinerator.isHidden())
			{
				bounds.setSize(41, itemContainer.getHeight() - incinerator.getHeight());
			}

			return true;
		}

		return false;
	}

	private void makeUpButton()
	{
		Widget parent = client.getWidget(WidgetID.BANK_GROUP_ID, 20);

		tArrowBg = parent.createChild(-1, WidgetType.GRAPHIC);
		tArrowBg.setSpriteId(1110);
		tArrowBg.setOriginalWidth(40);
		tArrowBg.setOriginalHeight(20);
		tArrowBg.setOriginalX(0);
		tArrowBg.setOnOpListener(ScriptID.NULL);
		tArrowBg.setHasListener(true);
		tArrowBg.setAction(1, SCROLL_UP);
		tArrowBg.revalidate();

		tArrowIcon = parent.createChild(-1, WidgetType.GRAPHIC);
		tArrowIcon.setSpriteId(1115);
		tArrowIcon.setOriginalWidth(14);
		tArrowIcon.setOriginalHeight(18);
		tArrowIcon.setOriginalX(14);
		tArrowIcon.revalidate();
	}

	private void makeDownButton()
	{
		Widget parent = client.getWidget(WidgetID.BANK_GROUP_ID, 20);

		bArrowBg = parent.createChild(-1, WidgetType.GRAPHIC);
		bArrowBg.setSpriteId(1110);
		bArrowBg.setOriginalWidth(40);
		bArrowBg.setOriginalHeight(20);
		bArrowBg.setOriginalX(bounds.x);
		bArrowBg.setOnOpListener(ScriptID.NULL);
		bArrowBg.setHasListener(true);
		bArrowBg.setAction(1, SCROLL_DOWN);
		bArrowBg.revalidate();

		bArrowIcon = parent.createChild(-1, WidgetType.GRAPHIC);
		bArrowIcon.setSpriteId(1114);
		bArrowIcon.setOriginalWidth(14);
		bArrowIcon.setOriginalHeight(18);
		bArrowIcon.setOriginalX(bounds.x + 14);
		bArrowIcon.revalidate();
	}

	@Subscribe
	public void widgetThing(WidgetLoaded widgetLoaded)
	{
		if (widgetLoaded.getGroupId() == WidgetID.BANK_GROUP_ID)
		{
			idx = 0;
			log.debug("bank opened");
			isBankOpen = true;
			processIcon = false;
			iconToSet = null;
			dragging = false;
			setActiveTab(null);

			tagTabs.clear();

			updateBounds();

			makeUpButton();

			addTabs();

			makeDownButton();
		}
	}

	private SpritePixels getImageSpritePixels(BufferedImage image)
	{
		int[] pixels = new int[image.getWidth() * image.getHeight()];

		try
		{
			new PixelGrabber(image, 0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth())
				.grabPixels();
		}
		catch (InterruptedException ex)
		{
			log.debug("PixelGrabber was interrupted: ", ex);
		}

		return client.createSpritePixels(pixels, image.getWidth(), image.getHeight());
	}

	private void addTabs()
	{
		Widget parent = client.getWidget(WidgetID.BANK_GROUP_ID, 20);

		Set<String> tags = getAllTags();
		int i = 0;
		for (String t : tags)
		{
//			String itemid = configManager.getConfiguration(CONFIG_GROUP, TAG_SEARCH + t + "_" + "")
			TagTab tagTab = new TagTab(ItemID.SPADE, "tag:" + t);

			Widget btn = parent.createChild(-1, WidgetType.GRAPHIC);
			btn.setSpriteId(170);
			btn.setOriginalWidth(40);
			btn.setOriginalHeight(40);
			btn.setOriginalX(0);
			btn.setOriginalY(62 + i * 40);
			btn.setOnOpListener(ScriptID.NULL);
			btn.setHasListener(true);
			btn.setAction(1, "Open");
			btn.setName(tagTab.getTag());
			btn.setAction(2, CHANGE_ICON);
			btn.revalidate();

			Widget icon = parent.createChild(-1, WidgetType.GRAPHIC);
			icon.setSpriteId(tagTab.getItemId() * -1);
			icon.setOriginalWidth(36);
			icon.setOriginalHeight(32);
			icon.setOriginalX(2);
			icon.setOriginalY(62 + i * 40 + 4);
			icon.revalidate();

			spriteOverrides.put(tagTab.getItemId() * -1, getImageSpritePixels(itemManager.getImage(tagTab.getItemId())));

			tagTab.setBackground(btn);
			tagTab.setIcon(icon);

			tagTabs.add(tagTab);
		}

		client.setSpriteOverrides(spriteOverrides);
		updateTabs(0);
	}

	public void setActiveTab(TagTab tagTab)
	{
		if (activeTab != null)
		{
			activeTab.setSpriteId(170);
			activeTab.revalidate();

			activeTab = null;
		}

		if (tagTab != null)
		{
			Widget tab = tagTab.getBackground();
			tab.setSpriteId(179);
			tab.revalidate();

			activeTab = tab;
		}
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged clientStrChanged)
	{
		if (clientStrChanged.getIndex() == VarClientStr.SEARCH_TEXT.getIndex())
		{
			String str = client.getVar(VarClientStr.SEARCH_TEXT).trim();

			if (str.startsWith(TAG_SEARCH))
			{
				TagTab tagTab = getTabByTag(str);

				setActiveTab(tagTab);
			}
			else
			{
				setActiveTab(null);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (isBankOpen)
		{
			Widget widget = client.getWidget(WidgetID.BANK_GROUP_ID, 11);

			searchStr = client.getVar(VarClientStr.SEARCH_TEXT).trim();

			if (widget == null)
			{
				isBankOpen = false;
				log.debug("bank closed");
				idx = 0;
			}

			updateTabs(0);
		}
	}

	private void updateArrows()
	{
		if (tArrowBg != null && tArrowIcon != null && bArrowBg != null && bArrowIcon != null)
		{
			tArrowBg.setOriginalY(bounds.y);
			tArrowBg.revalidate();

			tArrowIcon.setOriginalY(bounds.y + 2);
			tArrowIcon.revalidate();

			bArrowBg.setOriginalY(bounds.y + maxTabs * 40 + 20);
			bArrowBg.revalidate();

			bArrowIcon.setOriginalY(bounds.y + maxTabs * 40 + 22);
			bArrowIcon.revalidate();
		}
	}

	private void updateTabs(int num)
	{
		updateBounds();

		maxTabs = (bounds.height - 40) / 40;

		if (maxTabs >= tagTabs.size())
		{
			idx = 0;
			int y = bounds.y + 20;
			int height = 40;
			for (TagTab tg : tagTabs)
			{
				updateWidget(tg.getBackground(), y);
				updateWidget(tg.getIcon(), y + 4);

				y += height;
			}
		}
		else if ((tagTabs.size() - (idx + num) > maxTabs) && (idx + num > -1))
		{
			idx += num;
			int y = 62;
			int height = 40;
			y -= (idx * height);
			for (TagTab tg : tagTabs)
			{
				updateWidget(tg.getBackground(), y);
				updateWidget(tg.getIcon(), y + 4);

				y += height;
			}
		}

		updateArrows();
	}

	private void updateWidget(Widget t, int y)
	{
		t.setOriginalY(y);
		t.setRelativeY(y);
		if (y < 62 || y > bounds.y + bounds.height - 62)
		{
			t.setHidden(true);
		}
		else
		{
			t.setHidden(false);
		}
		t.revalidate();
	}

	private String getTags(int itemId)
	{
		String config = configManager.getConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
		if (config == null)
		{
			return "";
		}
		return config;
	}

	private void setTags(int itemId, String tags)
	{
		if (tags == null || tags.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
		}
		else
		{
			configManager.setConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId, tags);
		}

//		updateTabs();
	}

	private Set<String> getAllTags()
	{
		Set<String> set = new TreeSet<>();


		List<String> values = configManager.getConfigurationKeys(CONFIG_GROUP + "." + ITEM_KEY_PREFIX);
		values.forEach(s ->
		{
			String[] split = configManager.getConfiguration(s).split(",");
			set.addAll(Arrays.asList(split));
		});

		return set;
	}

	private int getTagCount(int itemId)
	{
		String tags = getTags(itemId);
		if (tags.length() > 0)
		{
			return tags.split(",").length;
		}
		return 0;
	}

	@Subscribe
	public void onScriptEvent(ScriptCallbackEvent event)
	{
		String eventName = event.getEventName();


		int[] intStack = client.getIntStack();
		String[] stringStack = client.getStringStack();
		int intStackSize = client.getIntStackSize();
		int stringStackSize = client.getStringStackSize();

		switch (eventName)
		{
			case "bankTagsActive":
				// tell the script the bank tag plugin is active
				intStack[intStackSize - 1] = 1;
				break;
			case "setSearchBankInputText":

				stringStack[stringStackSize - 1] = SEARCH_BANK_INPUT_TEXT;

				break;
			case "setSearchBankInputTextFound":
			{
				int matches = intStack[intStackSize - 1];
				stringStack[stringStackSize - 1] = String.format(SEARCH_BANK_INPUT_TEXT_FOUND, matches);
				break;
			}
			case "setBankItemMenu":
			{
				// set menu action index so the edit tags option will not be overridden
				intStack[intStackSize - 3] = EDIT_TAGS_MENU_INDEX;

				int itemId = intStack[intStackSize - 2];
				int tagCount = getTagCount(itemId);
				if (tagCount > 0)
				{
					stringStack[stringStackSize - 1] += " (" + tagCount + ")";
				}

				int index = intStack[intStackSize - 1];
				long key = (long) index + ((long) WidgetInfo.BANK_ITEM_CONTAINER.getId() << 32);
				IntegerNode flagNode = (IntegerNode) client.getWidgetFlags().get(key);
				if (flagNode != null && flagNode.getValue() != 0)
				{
					flagNode.setValue(flagNode.getValue() | WidgetConfig.SHOW_MENU_OPTION_NINE);
				}
				break;
			}
			case "bankSearchFilter":
				int itemId = intStack[intStackSize - 1];
				String itemName = stringStack[stringStackSize - 2];
				String searchInput = stringStack[stringStackSize - 1];

				ItemComposition itemComposition = itemManager.getItemComposition(itemId);
				if (itemComposition.getPlaceholderTemplateId() != -1)
				{
					// if the item is a placeholder then get the item id for the normal item
					itemId = itemComposition.getPlaceholderId();
				}

				String tagsConfig = configManager.getConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
				if (tagsConfig == null || tagsConfig.length() == 0)
				{
					intStack[intStackSize - 2] = itemName.contains(searchInput) ? 1 : 0;
					return;
				}

				boolean tagSearch = searchInput.startsWith(TAG_SEARCH);
				String search;
				if (tagSearch)
				{
					search = searchInput.substring(TAG_SEARCH.length()).trim();
				}
				else
				{
					search = searchInput;
				}

				List<String> tags = Arrays.asList(tagsConfig.toLowerCase().split(","));

				if (tags.stream().anyMatch(tag -> tag.contains(search.toLowerCase())))
				{
					// return true
					intStack[intStackSize - 2] = 1;
				}
				else if (!tagSearch)
				{
					intStack[intStackSize - 2] = itemName.contains(search) ? 1 : 0;
				}
				break;
		}
	}

	@Subscribe
	public void draggedWidget(DraggingWidgetChanged event)
	{
		// is dragging widget and mouse button released
		if (isBankOpen && event.isDraggingWidget() && client.getMouseCurrentButton() == 0)
		{
			Widget draggedWidget = client.getDraggedWidget();
			if (draggedWidget.getItemId() > 0)
			{
				MenuEntry[] entries = client.getMenuEntries();

				if (entries.length == 3)
				{
					MenuEntry entry = entries[2];

					if (entry.getOption().equals("Open"))
					{
						String tag = entry.getTarget();
						int itemId = draggedWidget.getItemId();
						setTags(itemId, getTags(itemId) + "," + tag);
					}
				}
			}
		}
		else if (isBankOpen && event.isDraggingWidget())
		{
			Widget draggedWidget = client.getDraggedWidget();
			if (draggedWidget.getItemId() > 0)
			{
				MenuEntry[] entries = client.getMenuEntries();

				if (entries.length == 3)
				{
					MenuEntry entry = entries[2];

					if (entry.getOption().equals("Open"))
					{
						entry.setOption(entry.getTarget());
						entry.setTarget(draggedWidget.getName());
						client.setMenuEntries(entries);
					}
				}
			}
		}
	}

	@Subscribe
	public void onMenuOptionAdded(MenuEntryAdded menuEntryAdded)
	{
		if (isBankOpen)
		{
			MenuEntry[] entries = client.getMenuEntries();

			if (entries.length == 10)
			{
				MenuEntry entry = entries[9];

				if (processIcon)
				{
					entry.setOption("Set " + iconToSet.getTag() + " icon");
					client.setMenuEntries(entries);
				}
			}
		}
	}


	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getWidgetId() == WidgetInfo.BANK_ITEM_CONTAINER.getId()
			&& event.getMenuAction() == MenuAction.EXAMINE_ITEM_BANK_EQ
			&& event.getId() == EDIT_TAGS_MENU_INDEX
			&& event.getMenuOption().startsWith(EDIT_TAGS_MENU_OPTION))
		{
			event.consume();
			int inventoryIndex = event.getActionParam();
			ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
			if (bankContainer == null)
			{
				return;
			}
			Item[] items = bankContainer.getItems();
			if (inventoryIndex < 0 || inventoryIndex >= items.length)
			{
				return;
			}
			Item item = bankContainer.getItems()[inventoryIndex];
			if (item == null)
			{
				return;
			}
			ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
			int itemId;
			if (itemComposition.getPlaceholderTemplateId() != -1)
			{
				// if the item is a placeholder then get the item id for the normal item
				itemId = itemComposition.getPlaceholderId();
			}
			else
			{
				itemId = item.getId();
			}

			String itemName = itemComposition.getName();

			String initialValue = getTags(itemId);

			chatboxInputManager.openInputWindow(itemName + " tags:", initialValue, (newTags) ->
			{
				if (newTags == null)
				{
					return;
				}
				setTags(itemId, newTags);
				Widget bankContainerWidget = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
				if (bankContainerWidget == null)
				{
					return;
				}
				Widget[] bankItemWidgets = bankContainerWidget.getDynamicChildren();
				if (bankItemWidgets == null || inventoryIndex >= bankItemWidgets.length)
				{
					return;
				}
				Widget bankItemWidget = bankItemWidgets[inventoryIndex];
				String[] actions = bankItemWidget.getActions();
				if (actions == null || EDIT_TAGS_MENU_INDEX - 1 >= actions.length
					|| itemId != bankItemWidget.getItemId())
				{
					return;
				}
				int tagCount = getTagCount(itemId);
				actions[EDIT_TAGS_MENU_INDEX - 1] = EDIT_TAGS_MENU_OPTION;
				if (tagCount > 0)
				{
					actions[EDIT_TAGS_MENU_INDEX - 1] += " (" + tagCount + ")";
				}
			});
		}
		else if (isBankOpen)
		{
			if (processIcon)
			{
				if (event.getMenuOption().startsWith("Set tag:"))
				{
					event.consume();
					processIcon = false;

					int inventoryIndex = event.getActionParam();
					ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
					if (bankContainer == null)
					{
						return;
					}
					Item[] items = bankContainer.getItems();
					if (inventoryIndex < 0 || inventoryIndex >= items.length)
					{
						return;
					}
					Item item = bankContainer.getItems()[inventoryIndex];
					if (item == null)
					{
						return;
					}
					ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
					int itemId;
					if (itemComposition.getPlaceholderTemplateId() != -1)
					{
						// if the item is a placeholder then get the item id for the normal item
						itemId = itemComposition.getPlaceholderId();
					}
					else
					{
						itemId = item.getId();
					}

					iconToSet.setItemId(itemId);
					Widget icon = iconToSet.getIcon();
					icon.setSpriteId(itemId * -1);
					spriteOverrides.put(itemId * -1, getImageSpritePixels(itemManager.getImage(itemId)));
					client.setSpriteOverrides(spriteOverrides);
				}
				else
				{
					event.consume();
				}
			}

			switch (event.getMenuOption())
			{
				case SCROLL_UP:
					event.consume();
					updateTabs(-1);
					client.playSoundEffect(SoundEffectID.UI_BOOP);
					break;
				case SCROLL_DOWN:
					event.consume();
					updateTabs(1);
					client.playSoundEffect(SoundEffectID.UI_BOOP);
					break;
				case CHANGE_ICON:
					event.consume();
					processIcon = true;
					iconToSet = getTabByTag(event.getMenuTarget());
					break;
				case "Open":
					event.consume();
					openTag(event.getMenuTarget());
					break;
				case "Withdraw-1":

					if (processIcon)
					{


					}
					break;
			}
		}
	}

	private TagTab getTabByTag(String name)
	{
		log.debug("Getting tag by name: {}", name);

		Optional<TagTab> first = tagTabs.stream().filter(f -> f.getTag().equals(name)).findFirst();

		if (first.isPresent())
		{
			return first.get();
		}

		return null;
	}

	private void openTag(String tag)
	{
		Widget widget = client.getWidget(162, 38);

		if (widget != null && widget.isHidden())
		{
			client.runScript(281, 1,
				786444,
				786445,
				786451,
				786455,
				786456,
				786457,
				786447,
				786448,
				786471,
				786453,
				786477,
				786478,
				786487);
			widget = client.getWidget(162, 38);
		}

		client.setVar(VarClientStr.SEARCH_TEXT, tag);
		widget.setText(tag);

		TagTab tagTab = getTabByTag(tag);
		setActiveTab(tagTab);

	}
}
