package net.runelite.client.plugins.banktags;

import lombok.Data;
import net.runelite.api.widgets.Widget;

@Data
public class TagTab
{
	private int itemId;
	private String tag;

	public TagTab(int itemId, String tag)
	{
		this.itemId = itemId;
		this.tag = tag;
	}

	private Widget background;
	private Widget icon;

	public String toString()
	{
		return "TagTab{tag=" + tag + ", itemId=" + itemId + "}";
	}
}
