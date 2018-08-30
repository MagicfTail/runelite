package net.runelite.client.plugins.banktags;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Slf4j
public class BankTagsOverlay extends Overlay
{
	private final Client client;
	private final BankTagsPlugin plugin;
	private final TooltipManager tooltipManager;

	private PanelComponent panel = new PanelComponent();

	private long last_time = System.nanoTime();

	private Rectangle bounds = new Rectangle();
	private int y = 0;
	private int x = 0;
	private BufferedImage tab;


	private int idx = 0;

	private int width = 40;
	private int height = 40;

	private final int notch = 6;

	private final Color borderColor = new Color(113, 100, 81);
	private final Color shadowColor = new Color(46, 43, 45);
//	private final GradientPaint fadeColor = new GradientPaint(0, 0,  1);

	private final int xpad = 2;

	private int activeTab = -1;

	private void drawTab(Graphics2D graphics, boolean active, boolean focused)
	{
		graphics.setColor(borderColor);

		graphics.drawLine(x + xpad, y + notch, x + xpad, y + height);
		graphics.drawLine(x + xpad, y + height, x + width, y + height);
		graphics.drawLine(x + xpad, y + notch, x + notch + xpad, y);
		graphics.drawLine(x + notch + 2, y, x + width, y);

		if (!active)
		{
			graphics.drawLine(x + width, y, x + width, y + height);
		}
	}

	private void drawBorder(Graphics2D graphics, boolean active)
	{
		graphics.setColor(shadowColor);

		graphics.drawLine(x + xpad - 1, y + notch - 1, x + xpad - 1, y + height + 1);
		graphics.drawLine(x + xpad - 1, y + height + 1, x + width + 1, y + height + 1);
		graphics.drawLine(x + xpad - 1, y + notch - 1, x + notch + xpad - 1, y - 1);
		graphics.drawLine(x + notch + 2 - 1, y - 1, x + width + 1, y - 1);

		if (!active)
		{
			graphics.drawLine(x + width + 1, y - 1, x + width + 1, y + height + 1);
		}
	}

	private void drawBackground(Graphics2D graphics, boolean focused)
	{

	}

	@Inject
	BankTagsOverlay(Client client, BankTagsPlugin plugin, TooltipManager tooltipManager)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.client = client;
		this.plugin = plugin;
		this.tooltipManager = tooltipManager;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.isBankOpen())
		{
			if (!updateBounds())
			{
				return null;
			}

			boolean processClick = click();

			long time = System.nanoTime();
			int delta_time = (int) ((time - last_time) / 1000000);
			last_time = time;

//			log.debug("delta time: {}", delta_time);

			x = bounds.x;
			y = bounds.y;

			final Point mousePos = client.getMouseCanvasPosition();


			final int bottom = y + bounds.height;

			final Rectangle upArrowBox = new Rectangle(x, y - 20, width, 20);
			final Rectangle downArrowBox = new Rectangle(x, bottom, width, 20);

			final boolean upHighlight = upArrowBox.contains(mousePos.getX(), mousePos.getY());
			final boolean downHighlight = downArrowBox.contains(mousePos.getX(), mousePos.getY());

			if (upHighlight)
			{
				drawRectangle(graphics, upArrowBox, new Color(100, 20, 200, 50));

				if (processClick)
				{
					processClick = false;
					updateIndex(-1);
				}
			}

			graphics.drawLine(x, y, x + width / 2, y - 20);
			graphics.drawLine(x + width / 2, y - 20, x + width, y);

			if (downHighlight)
			{
				drawRectangle(graphics, downArrowBox, new Color(100, 20, 200, 50));

				if (processClick)
				{
					processClick = false;
					updateIndex(1);
				}
			}

			width = plugin.getTabIcon().getWidth();
			height = plugin.getTabIcon().getHeight();

			graphics.drawLine(x, bottom, x + width / 2, bottom + 20);
			graphics.drawLine(x + width / 2, bottom + 20, x + width, bottom);

			graphics.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);

			y -= (idx * (height + 2));

			for (TagTab tagTab : plugin.getTabs())
			{

				final Rectangle itemHighlightBox = new Rectangle(x + 2, y, width, height);

				final boolean mouseInHighlightBox = itemHighlightBox.contains(mousePos.getX(), mousePos.getY());
				final boolean activeTab = plugin.getSearchStr().equalsIgnoreCase(tagTab.getName());

				if (mouseInHighlightBox)
				{
					if (processClick)
					{
						processClick = false;
						openTag(tagTab.getName());
					}
				}

				graphics.drawImage(plugin.getTabIcon(), x, y, null);

//				drawTab(graphics, activeTab, mouseInHighlightBox);
//				drawBorder(graphics, activeTab);

				final BufferedImage image = tagTab.getImage();

				graphics.drawImage(tab, x, y, null);
				graphics.drawImage(image, x + 4, y + (height - image.getHeight()) / 2, null);

				if (mouseInHighlightBox && bounds.contains(mousePos.getX(), mousePos.getY()))
				{
					tooltipManager.add(new Tooltip(tagTab.getName()));
				}

				y += height - 1;
			}
		}

		return null;
	}

	private void updateIndex(int i)
	{
		if (i == 1)
		{
			if (idx < plugin.getTabs().size() - 1)
			{
				idx++;
			}
		}
		else
		{
			if (idx > 0)
			{
				idx--;
			}
		}
	}

	private boolean click()
	{
		if (plugin.click)
		{
			plugin.click = false;
			return true;
		}

		return false;
	}

	private void drawRectangle(Graphics2D graphics, Rectangle rect, Color color)
	{
		graphics.setColor(color);
		graphics.drawRect(rect.x + 1, rect.y + 1, rect.width, rect.height);
		graphics.draw(rect);
		graphics.fill(rect);

		graphics.setColor(Color.WHITE);
	}

	private void openTag(String tag)
	{

		Widget widget = client.getWidget(162, 37);

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

			widget = client.getWidget(162, 37);
		}

		client.setVar(VarClientStr.SEARCH_TEXT, tag);
		widget.setText(tag);
	}


	private boolean updateBounds()
	{
		Widget itemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		if (itemContainer != null)
		{
			bounds.setBounds(itemContainer.getBounds());

			bounds.setSize(52, bounds.height);
			bounds.setLocation(bounds.x - 52, bounds.y + 20);

			Widget incinerator = client.getWidget(WidgetInfo.BANK_INCINERATOR);

			if (incinerator != null && !incinerator.isHidden())
			{
				bounds.setSize(52, bounds.height - 70);
			}

			bounds.setSize(52, bounds.height - 40);

			plugin.tabsBounds.setBounds(bounds.x, bounds.y - 20, bounds.width, bounds.height + 40);

			return true;
		}

		return false;
	}
}
