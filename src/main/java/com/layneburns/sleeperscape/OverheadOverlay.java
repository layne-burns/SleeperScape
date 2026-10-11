package com.layneburns.sleeperscape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the reaction's text over my character, in the bold game font with the game's black shadow, and the
 * chat-style effects the client doesn't let plugins set on real overhead text: wave and glow for my scores,
 * a red shake for being scored on. No flashing: nothing here blinks on and off. Only I see it.
 */
class OverheadOverlay extends Overlay
{
	// Above the head, where overhead chat sits.
	private static final int HEIGHT_OFFSET = 40;
	private static final Color[] GLOW = {Colors.MINE, Color.WHITE, Colors.TEXT_GREEN, Color.WHITE};
	private static final long GLOW_PERIOD_MS = 1600;

	private final Client client;
	private final Reactor reactor;

	@Inject
	OverheadOverlay(SleeperScapePlugin plugin, Client client, Reactor reactor)
	{
		super(plugin);
		this.client = client;
		this.reactor = reactor;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Speech speech = reactor.speech();
		Player me = client.getLocalPlayer();
		long now = System.currentTimeMillis();
		if (speech == null || me == null || now >= speech.endsAt)
		{
			return null;
		}
		graphics.setFont(FontManager.getRunescapeBoldFont());
		String plain = speech.plain();
		Point at = me.getCanvasTextLocation(graphics, plain, me.getLogicalHeight() + HEIGHT_OFFSET);
		if (at == null)
		{
			return null;
		}
		FontMetrics fm = graphics.getFontMetrics();
		long t = now - speech.startedAt;
		long length = Math.max(1, speech.endsAt - speech.startedAt);
		int x = at.getX();
		int i = 0;
		for (Speech.Segment seg : speech.segments)
		{
			for (int k = 0; k < seg.text.length(); k++, i++)
			{
				String ch = String.valueOf(seg.text.charAt(k));
				int dx = 0;
				int dy = 0;
				Color color = seg.color;
				switch (speech.style)
				{
					case SCORED:
						// wave2: the letters ripple up and down and sway side to side; the colours glow.
						dy = (int) Math.round(Math.sin(t / 110.0 + i / 2.0) * 3);
						dx = (int) Math.round(Math.sin(t / 160.0 + i / 3.0) * 1.5);
						color = glow(t, i);
						break;
					case SCORED_ON:
						// shake, fading as the reaction runs, in steady red.
						double amp = 3.5 * Math.max(0.3, 1 - (double) t / length);
						dy = (int) Math.round(Math.sin(t / 22.0 + i * 1.7) * amp);
						color = Colors.TEXT_RED;
						break;
					default:
						break;
				}
				graphics.setColor(Color.BLACK);
				graphics.drawString(ch, x + dx + 1, at.getY() + dy + 1);
				graphics.setColor(color);
				graphics.drawString(ch, x + dx, at.getY() + dy);
				x += fm.stringWidth(ch);
			}
		}
		return null;
	}

	/** Smoothly cycles pink, white, green, white, offset along the text so it shimmers. */
	private static Color glow(long t, int index)
	{
		double pos = ((double) (t % GLOW_PERIOD_MS) / GLOW_PERIOD_MS * GLOW.length + index * 0.15) % GLOW.length;
		int a = (int) pos;
		double f = pos - a;
		Color c1 = GLOW[a];
		Color c2 = GLOW[(a + 1) % GLOW.length];
		return new Color(
			(int) (c1.getRed() + (c2.getRed() - c1.getRed()) * f),
			(int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * f),
			(int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * f));
	}
}
