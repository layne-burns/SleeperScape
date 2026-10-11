package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.PlayEvent;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Pop-up for significant plays, one at a time, fading out at the end. When plays pile up (a lowered delay
 * releases a burst) the backlog is shown as one summary instead of a long queue.
 */
class BannerOverlay extends OverlayPanel
{
	private static final int BACKLOG = 3;
	private static final long FADE_MS = 800;

	private final SleeperScapeConfig config;
	private final ConcurrentLinkedQueue<PlayEvent> queue = new ConcurrentLinkedQueue<>();
	private List<PlayEvent> showing;
	private long shownAt;

	@Inject
	BannerOverlay(SleeperScapePlugin plugin, SleeperScapeConfig config)
	{
		super(plugin);
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		panelComponent.setPreferredSize(new Dimension(300, 0));
	}

	void push(PlayEvent e)
	{
		queue.add(e);
	}

	void clear()
	{
		queue.clear();
		showing = null;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		long now = System.currentTimeMillis();
		long duration = config.bannerSeconds() * 1000L;
		if (showing == null || now - shownAt >= duration)
		{
			showing = next();
			shownAt = now;
			if (showing == null)
			{
				return null;
			}
		}
		if (showing.size() == 1)
		{
			drawPlay(showing.get(0));
		}
		else
		{
			panelComponent.getChildren().add(TitleComponent.builder().text(showing.size() + " PLAYS").color(Colors.NEUTRAL).build());
			for (PlayEvent e : showing)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(e.top().headline + " " + e.shortName()).leftColor(Colors.tier(e.tier))
					.right(Text.impactsPlain(e)).rightColor(Colors.MUTED)
					.build());
			}
		}
		long left = duration - (now - shownAt);
		Composite old = graphics.getComposite();
		if (left < FADE_MS)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, left / (float) FADE_MS)));
		}
		try
		{
			return super.render(graphics);
		}
		finally
		{
			graphics.setComposite(old);
		}
	}

	private List<PlayEvent> next()
	{
		PlayEvent first = queue.poll();
		if (first == null)
		{
			return null;
		}
		List<PlayEvent> out = new ArrayList<>();
		out.add(first);
		if (queue.size() >= BACKLOG)
		{
			for (PlayEvent e; out.size() < 8 && (e = queue.poll()) != null; )
			{
				out.add(e);
			}
		}
		return out;
	}

	private void drawPlay(PlayEvent e)
	{
		panelComponent.getChildren().add(TitleComponent.builder().text(e.top().headline).color(Colors.tier(e.tier)).build());
		if (!e.isMatchupEvent())
		{
			String pt = e.positionTeam();
			panelComponent.getChildren().add(LineComponent.builder()
				.left(e.name()).leftColor(Colors.owner(e.visible.get(0).owner))
				.right(pt).rightColor(Colors.MUTED)
				.build());
		}
		String detail = e.detail();
		if (!detail.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder().left(detail).leftColor(Colors.NEUTRAL).build());
		}
		if (e.playText != null && !e.breakdown().isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder().left(e.breakdown()).leftColor(Colors.MUTED).build());
		}
		for (PlayEvent.Impact i : e.visible)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(i.alias).leftColor(Colors.owner(i.owner))
				.right(e.isMatchupEvent() ? "" : Format.points(i.delta))
				.rightColor(Colors.owner(i.owner))
				.build());
		}
	}
}
