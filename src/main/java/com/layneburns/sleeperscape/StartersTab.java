package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;

/**
 * Per enabled league (collapsible): the score and win %, then my starters (pink) and my opponent's (red) grouped
 * into Playing, Yet to play and Done (each collapsible). Built from the same delayed frame as the scoreboard.
 */
class StartersTab extends JPanel
{
	private static final SimpleDateFormat TIME = new SimpleDateFormat("h:mm a");
	private static final SimpleDateFormat DAY_TIME = new SimpleDateFormat("EEE h:mm a");

	private final ConfigManager configManager;
	private final Ui.Stack stack = new Ui.Stack();

	StartersTab(ConfigManager configManager)
	{
		super(new BorderLayout());
		this.configManager = configManager;
		setOpaque(false);
		add(Ui.scroll(stack), BorderLayout.CENTER);
		show(View.EMPTY, false);
	}

	void show(View view, boolean espn)
	{
		stack.removeAll();
		if (view.lines.isEmpty())
		{
			stack.add(Ui.row(Ui.note(view.bufferingMs > 0 ? "Waiting for the delay..." : "No matchups yet.")));
		}
		for (View.Line line : view.lines)
		{
			stack.add(league(line));
		}
		if (!espn && !view.lines.isEmpty())
		{
			stack.add(Ui.row(Ui.note("Turn on \"Use ESPN data\" to see who is playing, yet to play and done.")));
		}
		stack.revalidate();
		stack.repaint();
	}

	private Collapsible league(View.Line line)
	{
		Collapsible c = new Collapsible(configManager, "starters." + line.leagueId, line.leagueName, Color.WHITE, true);
		int sign;
		if (line.opp == null)
		{
			sign = 0;
		}
		else if (line.winProbability != null)
		{
			long pct = Math.round(line.winProbability * 100);
			sign = pct > 50 ? 1 : pct < 50 ? -1 : 0;
		}
		else
		{
			sign = line.my > line.opp + 0.005 ? 1 : line.opp > line.my + 0.005 ? -1 : 0;
		}
		String score = Format.score(line.my) + (line.opp == null ? "" : " - " + Format.score(line.opp));
		if (line.winProbability != null)
		{
			score += "  " + Math.round(line.winProbability * 100) + "%";
		}
		c.setSummary(score, sign > 0 ? Colors.WINNING : sign < 0 ? Colors.OPPONENT : Colors.NEUTRAL);

		boolean known = false;
		for (View.Starter s : line.myStarters)
		{
			known |= s.status != View.Starter.Status.UNKNOWN;
		}
		if (!known)
		{
			addRows(c.body(), line.myStarters, line.oppStarters, line.oppOwner);
			return c;
		}
		group(c, line, View.Starter.Status.PLAYING, "PLAYING", true);
		group(c, line, View.Starter.Status.YET_TO_PLAY, "YET TO PLAY", true);
		group(c, line, View.Starter.Status.DONE, "DONE", false);
		return c;
	}

	private void group(Collapsible league, View.Line line, View.Starter.Status status, String title, boolean open)
	{
		List<View.Starter> mine = of(line.myStarters, status);
		List<View.Starter> theirs = of(line.oppStarters, status);
		Collapsible g = new Collapsible(configManager, "starters." + line.leagueId + "." + status.name(), title,
			Colors.MUTED, open);
		g.setSummary(mine.size() + " v " + theirs.size(), Colors.MUTED);
		addRows(g.body(), mine, theirs, line.oppOwner);
		league.body().add(g);
	}

	private static List<View.Starter> of(List<View.Starter> starters, View.Starter.Status status)
	{
		List<View.Starter> out = new ArrayList<>();
		for (View.Starter s : starters)
		{
			if (s.status == status)
			{
				out.add(s);
			}
		}
		return out;
	}

	/** My starters, a "vs <opponent>" divider, then theirs. */
	private void addRows(Ui.Stack body, List<View.Starter> mine, List<View.Starter> theirs, String opponent)
	{
		for (View.Starter s : mine)
		{
			body.add(row(s, Colors.MINE));
		}
		if (!theirs.isEmpty())
		{
			JLabel vs = Ui.label("vs " + (opponent == null ? "opponent" : opponent), Colors.MUTED);
			vs.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(1, 0, 0, 0)));
			body.add(Ui.row(vs));
		}
		for (View.Starter s : theirs)
		{
			body.add(row(s, Colors.OPPONENT));
		}
	}

	/** "Bo Nix QB ......... Q3 7:12  14.2"; before kickoff the time and the projection instead of points. */
	private JPanel row(View.Starter s, Color nameColor)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		JLabel name = Ui.label(s.name + (s.position.isEmpty() ? "" : " " + s.position), nameColor);
		String when = s.status == View.Starter.Status.YET_TO_PLAY ? kickoff(s.kickoff) : s.statusText;
		boolean before = s.status == View.Starter.Status.YET_TO_PLAY;
		String pts = before
			? (s.projected == null ? "" : "proj " + Format.score(s.projected))
			: Format.score(s.points);
		JLabel right = Ui.label((when.isEmpty() ? "" : when + "  ") + pts, before ? Colors.MUTED : Colors.NEUTRAL);
		row.add(name, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		return Ui.row(row);
	}

	/** "4:25 PM" today, "Mon 8:15 PM" otherwise. */
	private static String kickoff(long at)
	{
		if (at <= 0)
		{
			return "";
		}
		Calendar now = Calendar.getInstance();
		Calendar then = Calendar.getInstance();
		then.setTimeInMillis(at);
		boolean today = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
			&& now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
		return (today ? TIME : DAY_TIME).format(new Date(at));
	}
}
