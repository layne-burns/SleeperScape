package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.View;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Compact scoreboard, three small lines per enabled league: the league's short name (with win % and starters
 * left when known), then me and my opponent by username. My score is green while I lead, my opponent's red
 * while they lead; the trailing side is neutral.
 */
class ScoreboardOverlay extends OverlayPanel
{
	private final SleeperScapeConfig config;
	private final LiveState state;

	@Inject
	ScoreboardOverlay(SleeperScapePlugin plugin, SleeperScapeConfig config, LiveState state)
	{
		super(plugin);
		this.config = config;
		this.state = state;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(150, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showScoreboard())
		{
			return null;
		}
		graphics.setFont(FontManager.getRunescapeSmallFont());
		View view = state.view;
		String status = state.status;
		if (view.lines.isEmpty())
		{
			if (view.bufferingMs > 0)
			{
				panelComponent.getChildren().add(LineComponent.builder().left("SleeperScape").leftColor(Colors.MUTED)
					.right("delay " + (view.bufferingMs + 999) / 1000 + "s").rightColor(Colors.MUTED).build());
			}
			else if (status != null)
			{
				panelComponent.getChildren().add(LineComponent.builder().left(status).leftColor(Colors.MUTED).build());
			}
			else
			{
				return null;
			}
			return super.render(graphics);
		}
		for (View.Line line : view.lines)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(line.alias).leftColor(Colors.MUTED)
				.right(header(line)).rightColor(Colors.MUTED)
				.build());
			boolean iLead = line.opp != null && line.my > line.opp + 0.005;
			boolean theyLead = line.opp != null && line.opp > line.my + 0.005;
			panelComponent.getChildren().add(LineComponent.builder()
				.left(line.myOwner).leftColor(Colors.MINE)
				.right(Format.score(line.my)).rightColor(iLead ? Colors.WINNING : Colors.NEUTRAL)
				.build());
			if (line.opp != null)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(line.oppOwner).leftColor(Colors.OPPONENT)
					.right(Format.score(line.opp)).rightColor(theyLead ? Colors.OPPONENT : Colors.NEUTRAL)
					.build());
			}
		}
		if (status != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(status).leftColor(Colors.MUTED).build());
		}
		return super.render(graphics);
	}

	/** "62% · 3v2 left", either part only when known. */
	private String header(View.Line line)
	{
		StringBuilder sb = new StringBuilder();
		if (config.showWinProbability() && line.winProbability != null)
		{
			sb.append(Math.round(line.winProbability * 100)).append('%');
		}
		if (line.myLeft != null && line.oppLeft != null)
		{
			if (sb.length() > 0)
			{
				sb.append(" · ");
			}
			sb.append(line.myLeft).append('v').append(line.oppLeft).append(" left");
		}
		return sb.toString();
	}
}
