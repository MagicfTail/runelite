package net.runelite.client.plugins.banktags;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.runelite.client.game.AsyncBufferedImage;

@AllArgsConstructor
@Data
public class Tag
{
	private String name;
	private AsyncBufferedImage image;
}
