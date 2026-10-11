package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.RedZoneAlert;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Live (never delayed): who of mine (pink) and my opponent's (red) is on a team inside the 20. */
class RedZoneOverlay extends OverlayPanel
{
	private final SleeperScapeConfig config;
	private final LiveState state;

	@Inject
	RedZoneOverlay(SleeperScapePlugin plugin, SleeperScapeConfig config, LiveState state)
	{
		super(plugin);
		this.config = config;
		this.state = state;
		setPosition(OverlayPosition.TOP_RIGHT);
		panelComponent.setPreferredSize(new Dimension(200, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<RedZoneAlert> alerts = state.redZones;
		if (!config.redZoneOverlay() || alerts.isEmpty())
		{
			return null;
		}
		for (RedZoneAlert a : alerts)
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("RED ZONE · " + a.team)
				.color(Colors.OPPONENT).build());
			for (RedZoneAlert.Entry e : a.entries)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(e.name).leftColor(e.mine ? Colors.MINE : Colors.OPPONENT)
					.right(String.join(", ", e.leagues)).rightColor(Colors.MUTED)
					.build());
			}
		}
		return super.render(graphics);
	}
}
