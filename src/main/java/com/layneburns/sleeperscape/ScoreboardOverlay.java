package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.View;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** One block per enabled league: alias and win %, then my and my opponent's owner (team) and score. */
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
		panelComponent.setPreferredSize(new Dimension(230, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showScoreboard())
		{
			return null;
		}
		View view = state.view;
		String status = state.status;
		if (view.lines.isEmpty())
		{
			if (view.bufferingMs > 0)
			{
				panelComponent.getChildren().add(LineComponent.builder().left("SleeperScape")
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
		boolean first = true;
		for (View.Line line : view.lines)
		{
			if (!first)
			{
				panelComponent.getChildren().add(LineComponent.builder().left(" ").build());
			}
			first = false;
			String right = "";
			if (config.showWinProbability() && line.winProbability != null)
			{
				right = Math.round(line.winProbability * 100) + "%";
			}
			panelComponent.getChildren().add(TitleComponent.builder()
				.text(line.alias + (right.isEmpty() ? "" : "  " + right))
				.color(line.winProbability == null ? Colors.NEUTRAL : line.winProbability >= 0.5 ? Colors.MINE : Colors.OPPONENT)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left(name(line.myOwner, line.myTeam, line.myLeft)).leftColor(Colors.MINE)
				.right(Format.score(line.my)).rightColor(Colors.MINE)
				.build());
			if (line.opp != null)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(name(line.oppOwner, line.oppTeam, line.oppLeft)).leftColor(Colors.OPPONENT)
					.right(Format.score(line.opp)).rightColor(Colors.OPPONENT)
					.build());
			}
		}
		if (status != null)
		{
			panelComponent.getChildren().add(LineComponent.builder().left(status).leftColor(Colors.MUTED).build());
		}
		return super.render(graphics);
	}

	private static String name(String owner, String team, Integer left)
	{
		String s = team == null ? owner : owner + " (" + team + ")";
		return left == null ? s : s + " · " + left + " left";
	}
}
