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

import com.google.common.base.Strings;
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
import java.util.stream.Collectors;
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
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SpriteID;
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
import static net.runelite.api.widgets.WidgetConfig.DRAG;
import static net.runelite.api.widgets.WidgetConfig.DRAG_ON;
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
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.math.NumberUtils;

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
	private static final String CHANGE_ICON = "Set Tab Icon";
	private static final String REMOVE_TAB = "Remove Tab";
	private static final String NEW_TAB = "New Tag Tab";
	private static final String OPEN_TAG = "Open Tag";
	private static final String ICON_SEARCH = "icon_";
	private static final int TAB_BACKGROUND = SpriteID.EQUIPMENT_SLOT_TILE;
	private static final int TAB_BACKGROUND_ACTIVE = SpriteID.EQUIPMENT_SLOT_SELECTED;

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
		List<String> tags = getAllTags();

		for (String tag : tags)
		{

		}

		configManager.unsetConfiguration(CONFIG_GROUP, "tagtabs");
	}

	@Override
	public void shutDown()
	{

	}

	private void updateBounds()
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
		}
	}

	private void makeUpButton()
	{
		Widget parent = client.getWidget(WidgetID.BANK_GROUP_ID, 20);

		BufferedImage img = ImageUtil.getResourceStreamFromClass(BankTagsPlugin.class, "up-arrow2.png");

		tArrowBg = parent.createChild(-1, WidgetType.GRAPHIC);
		tArrowBg.setSpriteId(-20001);
		tArrowBg.setOriginalWidth(40);
		tArrowBg.setOriginalHeight(18);
		tArrowBg.setOriginalX(0);
		tArrowBg.setOnOpListener(ScriptID.NULL);
		tArrowBg.setHasListener(true);
		tArrowBg.setAction(1, SCROLL_UP);
		tArrowBg.revalidate();

		spriteOverrides.put(-20001, getImageSpritePixels(img));

		tArrowIcon = parent.createChild(-1, WidgetType.GRAPHIC);
//		tArrowIcon.setSpriteId(SpriteID.GE_FAST_INCREMENT_ARROW);
		tArrowIcon.setOriginalWidth(14);
		tArrowIcon.setOriginalHeight(18);
		tArrowIcon.setOriginalX(14);
		tArrowIcon.revalidate();
	}

	private void makeDownButton()
	{
		Widget parent = client.getWidget(WidgetID.BANK_GROUP_ID, 20);

		bArrowBg = parent.createChild(-1, WidgetType.GRAPHIC);
		bArrowBg.setSpriteId(-20002);
		bArrowBg.setOriginalWidth(40);
		bArrowBg.setOriginalHeight(18);
		bArrowBg.setOriginalX(bounds.x);
		bArrowBg.setOnOpListener(ScriptID.NULL);
		bArrowBg.setHasListener(true);
		bArrowBg.setAction(1, SCROLL_DOWN);
		bArrowBg.revalidate();

		BufferedImage img2 = ImageUtil.getResourceStreamFromClass(BankTagsPlugin.class, "down-arrow2.png");
		spriteOverrides.put(-20002, getImageSpritePixels(img2));

		bArrowIcon = parent.createChild(-1, WidgetType.GRAPHIC);
//		bArrowIcon.setSpriteId(SpriteID.GE_FAST_DECREMENT_ARROW);
		bArrowIcon.setOriginalWidth(14);
		bArrowIcon.setOriginalHeight(18);
		bArrowIcon.setOriginalX(bounds.x + 14);
		bArrowIcon.revalidate();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (widgetLoaded.getGroupId() == WidgetID.BANK_GROUP_ID)
		{
			idx = 0;
			log.debug("bank opened");

			isBankOpen = true;
			processIcon = false;
			iconToSet = null;
			setActiveTab(null);
			tagTabs.clear();

			BufferedImage img = ImageUtil.getResourceStreamFromClass(BankTagsPlugin.class, "new-tab.png");

			Widget parent = client.getWidget(WidgetID.BANK_GROUP_ID, 20);
			Widget btn = parent.createChild(-1, WidgetType.GRAPHIC);
			btn.setSpriteId(-20000);
			btn.setOriginalWidth(img.getWidth());
			btn.setOriginalHeight(img.getHeight());
			btn.setOriginalX(0);
			btn.setOriginalY(18);
			btn.setOnOpListener(ScriptID.NULL);
			btn.setHasListener(true);
			btn.setAction(1, NEW_TAB);
			btn.revalidate();

			spriteOverrides.put(-20000, getImageSpritePixels(img));

			updateBounds();
			makeUpButton();
			addTabs();
			makeDownButton();
			updateTabs(0);

			client.setSpriteOverrides(spriteOverrides);
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

		List<String> tags = getAllTags();
		int i = 0;
		for (String t : tags)
		{
			String item = configManager.getConfiguration(CONFIG_GROUP, ICON_SEARCH + t);
			int itemid = NumberUtils.toInt(item, ItemID.SPADE);
			TagTab tagTab = new TagTab(itemid, t);

			Widget btn = parent.createChild(-1, WidgetType.GRAPHIC);
			btn.setSpriteId(TAB_BACKGROUND);
			btn.setOriginalWidth(40);
			btn.setOriginalHeight(40);
			btn.setOriginalX(0);
			btn.setOriginalY(62 + i * 40);
			btn.setOnOpListener(ScriptID.NULL);
			btn.setHasListener(true);
			btn.setAction(1, OPEN_TAG);
			btn.setName("<col=FFA500>" + tagTab.getTag() + "</col>");
			btn.setAction(2, CHANGE_ICON);
			btn.setAction(3, REMOVE_TAB);


			btn.revalidate();
			btn.setOnOpListener();

			Widget icon = parent.createChild(-1, WidgetType.GRAPHIC);
			icon.setSpriteId(tagTab.getItemId() * -1);
			icon.setOriginalWidth(36);
			icon.setOriginalHeight(32);
			icon.setOriginalX(2);
			icon.setOriginalY(62 + i * 40 + 4);
			icon.revalidate();
			icon.setName(tagTab.getTag());
			int clickmask = icon.getClickMask();
			clickmask |= DRAG;
			clickmask |= DRAG_ON;
			icon.setClickMask(clickmask);

			spriteOverrides.put(tagTab.getItemId() * -1, getImageSpritePixels(itemManager.getImage(tagTab.getItemId())));

			tagTab.setBackground(btn);
			tagTab.setIcon(icon);

			tagTabs.add(tagTab);
		}

		client.setSpriteOverrides(spriteOverrides);
	}

	public void setActiveTab(TagTab tagTab)
	{
		if (activeTab != null)
		{
			activeTab.setSpriteId(TAB_BACKGROUND);
			activeTab.revalidate();

			activeTab = null;
		}

		if (tagTab != null)
		{
			Widget tab = tagTab.getBackground();
			tab.setSpriteId(TAB_BACKGROUND_ACTIVE);
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
				TagTab tagTab = getTabByTag(str.substring(4));

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
			Widget widget = client.getWidget(WidgetInfo.BANK_CONTAINER);
			searchStr = client.getVar(VarClientStr.SEARCH_TEXT).trim();

			if (widget == null)
			{
				isBankOpen = false;
				log.debug("bank closed");
			}

			updateTabs(0);
		}
	}

	private void updateArrows()
	{
		if (tArrowBg != null && tArrowIcon != null && bArrowBg != null && bArrowIcon != null)
		{
			boolean topHidden = !(tagTabs.size() > 0 && tagTabs.size() > maxTabs && idx != 0);
			boolean bottomHidden = !(tagTabs.size() > 0 && tagTabs.size() > maxTabs && idx != (tagTabs.size() -  maxTabs));
			tArrowIcon.setHidden(topHidden);
			tArrowBg.setHidden(topHidden);
			bArrowIcon.setHidden(bottomHidden);
			bArrowBg.setHidden(bottomHidden);

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

		if (idx >= tagTabs.size())
		{
			idx = 0;
		}

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
		else if ((tagTabs.size() - (idx + num) >= maxTabs) && (idx + num > -1))
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

	private void rerenderTabs()
	{
		tagTabs.forEach(t ->
		{
			t.getBackground().setHidden(true);
			t.getIcon().setHidden(true);
		});

		tagTabs.clear();
		addTabs();
		updateTabs(0);
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
	}

	private List<String> getAllTags()
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, "tagtabs");

		if (Strings.isNullOrEmpty(value))
		{
			return new ArrayList<>();
		}

		return Arrays.stream(value.split(",")).collect(Collectors.toList());
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
			Widget draggedOn = client.getDraggedOnWidget();

			Widget parent = client.getWidget(WidgetInfo.BANK_CONTENT_CONTAINER);

			if (draggedWidget.getItemId() > 0 && draggedOn != null)
			{
				if (draggedOn.getName().startsWith("<col=FFA500>"))
				{
					String tag = Text.removeTags(draggedOn.getName());
					int itemId = draggedWidget.getItemId();

					ItemComposition itemComposition = itemManager.getItemComposition(itemId);
					if (itemComposition.getPlaceholderTemplateId() != -1)
					{
						// if the item is a placeholder then get the item id for the normal item
						itemId = itemComposition.getPlaceholderId();
					}

					setTags(itemId, getTags(itemId) + "," + tag);
				}
			}
			else if (draggedOn != null && parent.getId() == draggedOn.getId() && parent.getId() == draggedWidget.getId())
			{
				String destinationTag = Text.removeTags(draggedOn.getName());
				String tagToMove = Text.removeTags(draggedWidget.getName());
				if (!Strings.isNullOrEmpty(destinationTag))
				{
					List<String> items = getItemsByTag("tagtabs");
					items.removeIf(s -> s.equalsIgnoreCase(tagToMove));

					int idx = items.indexOf(destinationTag);

					items.add(idx, tagToMove);
					configManager.setConfiguration(CONFIG_GROUP, "tagtabs", String.join(",", items));
					rerenderTabs();
				}
			}

			client.setDraggedOnWidget(null);
		}
		else if (isBankOpen && event.isDraggingWidget())
		{
			Widget draggedWidget = client.getDraggedWidget();
			if (draggedWidget.getItemId() > 0)
			{
				MenuEntry[] entries = client.getMenuEntries();

				if (entries.length == 4)
				{
					MenuEntry entry = entries[3];

					if (entry.getOption().equals(OPEN_TAG))
					{
						entry.setOption(TAG_SEARCH + Text.removeTags(entry.getTarget()));
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

			if (entries.length > 0)
			{
				MenuEntry entry = entries[entries.length - 1];

				if (processIcon && (entry.getOption().equals("Withdraw-1") || entry.getOption().equals("Release")))
				{
					entry.setOption("Set tag:" + iconToSet.getTag() + "</col> icon");
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

					configManager.setConfiguration(CONFIG_GROUP, ICON_SEARCH + iconToSet.getTag(), itemId + "");
				}
				else
				{
					iconToSet = null;
					processIcon = false;
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
					iconToSet = getTabByTag(Text.removeTags(event.getMenuTarget()));
					break;
				case OPEN_TAG:
					event.consume();
					Widget parent = client.getWidget(WidgetInfo.BANK_CONTENT_CONTAINER);
					Widget[] children = parent.getDynamicChildren();
					Widget clicked = children[event.getActionParam()];

					openTag(TAG_SEARCH + Text.removeTags(clicked.getName()));
					break;
				case NEW_TAB:
					event.consume();
					chatboxInputManager.openInputWindow("Tag Name", "", (tagName) ->
					{
						newTagTab(tagName);
					});
					break;
				case REMOVE_TAB:
					event.consume();
					chatboxInputManager.openInputWindow("Are you sure you want to delete tab " + Text.removeTags(event.getMenuTarget()) + "?<br>(y)es or (n)o:", "", (response) ->
					{
						if (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes"))
						{
							deleteTab(Text.removeTags(event.getMenuTarget()));
						}
					});
					break;
			}
		}
	}

	private void deleteTab(String tag)
	{
		log.debug("Removing tag tab: {}", tag);
		tagTabs.removeIf(t ->
		{
			if (t.getTag().equalsIgnoreCase(tag))
			{
				t.getBackground().setHidden(true);
				t.getIcon().setHidden(true);

				return true;
			}

			return false;
		});
		saveOrder();
		rerenderTabs();
	}

	private void newTagTab(String tagName)
	{
		if (!Strings.isNullOrEmpty(tagName) && !getAllTags().stream().anyMatch(s -> s.equalsIgnoreCase(tagName)))
		{
			List<String> items = getItemsByTag("tagtabs");
			items.add(tagName);
			configManager.setConfiguration(CONFIG_GROUP, "tagtabs", String.join(",", items));
			rerenderTabs();
		}
	}

	private void saveOrder()
	{
		String tags = tagTabs.stream().map(t -> t.getTag()).collect(Collectors.joining(","));
		configManager.setConfiguration(CONFIG_GROUP, "tagtabs", tags);
	}

	private List<String> getItemsByTag(String tag)
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, tag);

		if (Strings.isNullOrEmpty(value))
		{
			return new ArrayList<>();
		}

		return Arrays.stream(value.split(",")).collect(Collectors.toList());
	}

	private TagTab getTabByTag(String name)
	{
		log.debug("Getting tag by name: {}", name);

		Optional<TagTab> first = tagTabs.stream().filter(f -> f.getTag().equalsIgnoreCase(name)).findFirst();

		if (first.isPresent())
		{
			return first.get();
		}

		return null;
	}

	private int getWidgetId(WidgetInfo widgetInfo)
	{
		return client.getWidget(widgetInfo).getId();
	}

	private void openTag(String tag)
	{
		Widget widget = client.getWidget(WidgetInfo.CHATBOX_SEARCH);

		if (widget != null && widget.isHidden())
		{
			client.runScript(281, 1,
				getWidgetId(WidgetInfo.BANK_CONTAINER),
				getWidgetId(WidgetInfo.BANK_INNER_CONTAINER),
				getWidgetId(WidgetInfo.BANK_SETTINGS),
				getWidgetId(WidgetInfo.BANK_ITEM_CONTAINER),
				getWidgetId(WidgetInfo.BANK_SCROLLBAR),
				getWidgetId(WidgetInfo.BANK_BOTTOM_BAR),
				getWidgetId(WidgetInfo.BANK_TITLE_BAR),
				getWidgetId(WidgetInfo.BANK_ITEM_COUNT),
				getWidgetId(WidgetInfo.BANK_SEARCH_BUTTON_BACKGROUND),
				getWidgetId(WidgetInfo.BANK_TAB_BAR),
				getWidgetId(WidgetInfo.BANK_INCINERATOR),
				getWidgetId(WidgetInfo.BANK_INCINERATOR_CONFIRM),
				getWidgetId(WidgetInfo.BANK_SOMETHING));
			widget = client.getWidget(WidgetInfo.CHATBOX_SEARCH);
		}

		client.setVar(VarClientStr.SEARCH_TEXT, tag);
		widget.setText(tag);

		TagTab tagTab = getTabByTag(tag.substring(4));
		setActiveTab(tagTab);
	}
}
