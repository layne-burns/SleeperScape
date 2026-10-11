package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.View;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * Compact scoreboard: per enabled league, its full name (and starters left), then me and my opponent as
 * name | score | projected final | win %. Colours follow the win probability: above 50% my row is green,
 * below 50% theirs is red, at 50% (or unknown and tied) both are white. Without a win probability (no ESPN
 * data or projections yet), whoever leads on points takes the colour.
 */
class ScoreboardOverlay extends Overlay
{
	private static final int PAD = ComponentConstants.STANDARD_BORDER;
	private static final int COLUMN_GAP = 8;
	private static final int LEAGUE_GAP = 4;

	private final SleeperScapeConfig config;
	private final LiveState state;
	private final BackgroundComponent background = new BackgroundComponent();
	private final TextComponent text = new TextComponent();

	/** One drawn line: a league header (spanning) or a row of four cells. */
	private static final class Line
	{
		final String header;
		final String headerRight;
		final String[] cells;
		final Color color;
		final boolean gapBefore;

		Line(String header, String headerRight, String[] cells, Color color, boolean gapBefore)
		{
			this.header = header;
			this.headerRight = headerRight;
			this.cells = cells;
			this.color = color;
			this.gapBefore = gapBefore;
		}
	}

	@Inject
	ScoreboardOverlay(SleeperScapePlugin plugin, SleeperScapeConfig config, LiveState state)
	{
		super(plugin);
		this.config = config;
		this.state = state;
		setPosition(OverlayPosition.TOP_LEFT);
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
		List<Line> lines = new ArrayList<>();
		if (view.lines.isEmpty())
		{
			if (view.bufferingMs > 0)
			{
				lines.add(new Line("SleeperScape", "delay " + (view.bufferingMs + 999) / 1000 + "s", null, Colors.MUTED, false));
			}
			else if (status != null)
			{
				lines.add(new Line(status, "", null, Colors.MUTED, false));
			}
			else
			{
				return null;
			}
		}
		boolean showWin = config.showWinProbability();
		boolean first = true;
		for (View.Line l : view.lines)
		{
			String left = l.myLeft != null && l.oppLeft != null ? l.myLeft + "v" + l.oppLeft + " left" : "";
			lines.add(new Line(l.leagueName, left, null, Colors.MUTED, !first));
			first = false;
			Color mine = Colors.NEUTRAL;
			Color theirs = Colors.NEUTRAL;
			if (l.opp != null)
			{
				int sign;
				if (l.winProbability != null)
				{
					long pct = Math.round(l.winProbability * 100);
					sign = pct > 50 ? 1 : pct < 50 ? -1 : 0;
				}
				else
				{
					sign = l.my > l.opp + 0.005 ? 1 : l.opp > l.my + 0.005 ? -1 : 0;
				}
				mine = sign > 0 ? Colors.WINNING : Colors.NEUTRAL;
				theirs = sign < 0 ? Colors.OPPONENT : Colors.NEUTRAL;
			}
			lines.add(new Line(null, null, new String[]{l.myOwner, Format.score(l.my), projected(l.myProjected),
				showWin && l.winProbability != null ? Math.round(l.winProbability * 100) + "%" : ""}, mine, false));
			if (l.opp != null)
			{
				lines.add(new Line(null, null, new String[]{l.oppOwner, Format.score(l.opp), projected(l.oppProjected),
					showWin && l.winProbability != null ? Math.round((1 - l.winProbability) * 100) + "%" : ""}, theirs, false));
			}
		}
		if (status != null && !view.lines.isEmpty())
		{
			lines.add(new Line(status, "", null, Colors.MUTED, true));
		}
		return draw(graphics, lines);
	}

	private static String projected(Double p)
	{
		return p == null ? "" : Format.score(p);
	}

	private Dimension draw(Graphics2D graphics, List<Line> lines)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();
		int lineHeight = fm.getHeight();

		// Column widths: name left-aligned, the numbers right-aligned.
		int[] widths = new int[4];
		int headerWidth = 0;
		for (Line line : lines)
		{
			if (line.cells == null)
			{
				int w = fm.stringWidth(line.header) + (line.headerRight.isEmpty() ? 0 : COLUMN_GAP + fm.stringWidth(line.headerRight));
				headerWidth = Math.max(headerWidth, w);
				continue;
			}
			for (int c = 0; c < 4; c++)
			{
				widths[c] = Math.max(widths[c], fm.stringWidth(line.cells[c]));
			}
		}
		int tableWidth = widths[0];
		for (int c = 1; c < 4; c++)
		{
			tableWidth += widths[c] == 0 ? 0 : COLUMN_GAP + widths[c];
		}
		int innerWidth = Math.max(tableWidth, headerWidth);
		int height = PAD * 2;
		for (Line line : lines)
		{
			height += lineHeight + (line.gapBefore ? LEAGUE_GAP : 0);
		}
		int width = innerWidth + PAD * 2;

		background.setRectangle(new Rectangle(0, 0, width, height));
		background.setBackgroundColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
		background.render(graphics);

		int y = PAD;
		for (Line line : lines)
		{
			y += line.gapBefore ? LEAGUE_GAP : 0;
			int baseline = y + fm.getAscent();
			if (line.cells == null)
			{
				drawText(graphics, line.header, PAD, baseline, line.color);
				if (!line.headerRight.isEmpty())
				{
					drawText(graphics, line.headerRight, PAD + innerWidth - fm.stringWidth(line.headerRight), baseline, line.color);
				}
			}
			else
			{
				drawText(graphics, line.cells[0], PAD, baseline, line.color);
				// Numbers right-aligned in their columns, flush with the right edge.
				int right = PAD + innerWidth;
				for (int c = 3; c >= 1; c--)
				{
					if (widths[c] == 0)
					{
						continue;
					}
					String s = line.cells[c];
					// The projection is secondary: same colour, dimmed.
					Color color = c == 2 ? dim(line.color) : line.color;
					drawText(graphics, s, right - fm.stringWidth(s), baseline, color);
					right -= widths[c] + COLUMN_GAP;
				}
			}
			y += lineHeight;
		}
		return new Dimension(width, height);
	}

	private void drawText(Graphics2D graphics, String s, int x, int y, Color color)
	{
		text.setText(s);
		text.setColor(color);
		text.setPosition(x, y);
		text.render(graphics);
	}

	private static Color dim(Color c)
	{
		return new Color((int) (c.getRed() * 0.7), (int) (c.getGreen() * 0.7), (int) (c.getBlue() * 0.7));
	}
}
