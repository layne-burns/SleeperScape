package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.LeagueContext;
import com.layneburns.sleeperscape.core.Owner;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.SideOfBall;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel: status, delay (quick presets plus fine tuning), mute, the leagues with their on/off switch and
 * short name, and the full feed of every play (newest first). All methods run on the Swing EDT.
 */
class SleeperScapePanel extends PluginPanel
{
	private static final int[] PRESETS = {30, 60, 90, 120};
	private static final int MAX_FEED = 300;

	private final SleeperScapeConfig config;
	private final ConfigManager configManager;
	private final Settings settings;

	private final JLabel status = new JLabel();
	private final JLabel delayLabel = new JLabel();
	private final JCheckBox mute = new JCheckBox("Mute pop-ups, chat & reactions");
	private final JPanel leaguesPanel = new JPanel();
	private final JPanel feed = new JPanel();
	private final JLabel sideOfBall = new JLabel();
	private String sideOfBallKey;
	private final Map<Long, JLabel> feedLabels = new HashMap<>();
	private final SimpleDateFormat clock = new SimpleDateFormat("h:mm");

	SleeperScapePanel(SleeperScapeConfig config, ConfigManager configManager, Settings settings)
	{
		super(false);
		this.config = config;
		this.configManager = configManager;
		this.settings = settings;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);

		status.setForeground(Colors.MUTED);
		top.add(left(status));
		top.add(Box.createVerticalStrut(6));

		top.add(left(delayLabel));
		JPanel presets = new JPanel(new GridLayout(1, PRESETS.length, 3, 0));
		presets.setOpaque(false);
		for (int p : PRESETS)
		{
			presets.add(button(p + "s", () -> setDelay(p)));
		}
		top.add(left(presets));
		top.add(Box.createVerticalStrut(3));
		JPanel fine = new JPanel(new GridLayout(1, 3, 3, 0));
		fine.setOpaque(false);
		fine.add(button("-5s", () -> setDelay(config.delaySeconds() - 5)));
		fine.add(button("0s", () -> setDelay(0)));
		fine.add(button("+5s", () -> setDelay(config.delaySeconds() + 5)));
		top.add(left(fine));
		top.add(Box.createVerticalStrut(6));

		mute.setOpaque(false);
		mute.addActionListener(a -> configManager.setConfiguration(SleeperScapeConfig.GROUP, "muted", mute.isSelected()));
		top.add(left(mute));
		top.add(Box.createVerticalStrut(6));

		JLabel leaguesTitle = new JLabel("Leagues (tick to show; edit short name)");
		leaguesTitle.setForeground(Color.WHITE);
		top.add(left(leaguesTitle));
		leaguesPanel.setLayout(new BoxLayout(leaguesPanel, BoxLayout.Y_AXIS));
		leaguesPanel.setOpaque(false);
		top.add(left(leaguesPanel));
		top.add(Box.createVerticalStrut(8));

		JLabel sideTitle = new JLabel("My side of the ball (live)");
		sideTitle.setForeground(Color.WHITE);
		sideTitle.setToolTipText("Games where your starters are on the field now: offense while their team has the ball, IDP/DEF while it doesn't");
		top.add(left(sideTitle));
		top.add(left(sideOfBall));
		setSideOfBall(Collections.emptyList());
		top.add(Box.createVerticalStrut(8));

		JLabel feedTitle = new JLabel("Plays (after the delay)");
		feedTitle.setForeground(Color.WHITE);
		top.add(left(feedTitle));

		feed.setLayout(new BoxLayout(feed, BoxLayout.Y_AXIS));
		feed.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JPanel feedHolder = new JPanel(new BorderLayout());
		feedHolder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		feedHolder.add(feed, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(feedHolder);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		add(top, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);
		refreshConfig();
	}

	/**
	 * A full-width row for the vertical box. Its height follows its content as that changes (the league list
	 * fills in after loading), so nothing is clipped to the size it had when the panel was built.
	 */
	private static JPanel left(Component c)
	{
		JPanel row = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setOpaque(false);
		row.add(c, BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFocusable(false);
		b.setMargin(new Insets(2, 2, 2, 2));
		b.addActionListener(a -> action.run());
		return b;
	}

	private void setDelay(int seconds)
	{
		int s = Math.max(0, Math.min(600, seconds));
		configManager.setConfiguration(SleeperScapeConfig.GROUP, "delaySeconds", s);
	}

	/** After any config change: delay label and mute box. */
	void refreshConfig()
	{
		delayLabel.setText("Delay: " + config.delaySeconds() + " s  (red zone is live)");
		delayLabel.setForeground(Color.WHITE);
		mute.setSelected(config.muted());
	}

	void setStatus(String text)
	{
		status.setText(text == null ? " " : "<html><body style='width:180px'>" + Text.escapeHtml(text) + "</body></html>");
	}

	void setLeagues(List<LeagueContext> leagues)
	{
		leaguesPanel.removeAll();
		for (LeagueContext l : leagues)
		{
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setOpaque(false);
			JCheckBox box = new JCheckBox(l.name(), settings.leagueEnabled(l.id()));
			box.setOpaque(false);
			box.setToolTipText(l.myRosterId < 0 ? "You don't own a team in this league" : l.name());
			box.addActionListener(a -> settings.setLeagueEnabled(l.id(), box.isSelected()));
			JTextField alias = new JTextField(settings.leagueAlias(l.id()), 4);
			alias.setToolTipText("Short name shown in chat and overlays");
			Runnable save = () -> settings.setLeagueAlias(l.id(), alias.getText());
			alias.addActionListener(a -> save.run());
			alias.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					save.run();
				}
			});
			row.add(box, BorderLayout.CENTER);
			row.add(alias, BorderLayout.EAST);
			leaguesPanel.add(left(row));
		}
		leaguesPanel.revalidate();
		revalidate();
		repaint();
	}

	void addPlay(PlayEvent e)
	{
		JLabel label = new JLabel(html(e))
		{
			@Override
			public Dimension getMaximumSize()
			{
				// Full width, so the row's tint spans the panel.
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		label.setOpaque(true);
		label.setBackground(Colors.background(e, ColorScheme.DARKER_GRAY_COLOR));
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 4, 4, 4)));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		feed.add(label, 0);
		feedLabels.put(e.id, label);
		while (feed.getComponentCount() > MAX_FEED)
		{
			feed.remove(feed.getComponentCount() - 1);
		}
		if (feedLabels.size() > MAX_FEED * 2)
		{
			feedLabels.keySet().removeIf(id -> id < e.id - MAX_FEED * 2L);
		}
		feed.revalidate();
		feed.repaint();
	}

	void updatePlay(PlayEvent e)
	{
		JLabel label = feedLabels.get(e.id);
		if (label != null)
		{
			label.setText(html(e));
		}
	}

	/** Live: redrawn only when something changed. */
	void setSideOfBall(List<SideOfBall> games)
	{
		StringBuilder key = new StringBuilder(String.valueOf(config.useEspn()));
		for (SideOfBall g : games)
		{
			key.append('#').append(g.key());
		}
		if (key.toString().equals(sideOfBallKey))
		{
			return;
		}
		sideOfBallKey = key.toString();
		StringBuilder sb = new StringBuilder("<html><body style='width:180px'>");
		if (!config.useEspn())
		{
			sb.append(span(Colors.MUTED, "Turn on \"Use ESPN data\" to see who has the ball."));
		}
		else if (games.isEmpty())
		{
			sb.append(span(Colors.MUTED, "None of your starters are on the field right now."));
		}
		boolean firstGame = true;
		for (SideOfBall g : games)
		{
			if (!firstGame)
			{
				sb.append("<br>");
			}
			firstGame = false;
			sb.append(span(Colors.MUTED, g.game + " · " + g.teamWithBall + " ball"));
			if (g.redZone)
			{
				sb.append(' ').append("<b>").append(span(Colors.OPPONENT, "RED ZONE")).append("</b>");
			}
			for (SideOfBall.Entry e : g.entries)
			{
				sb.append("<br>&nbsp;&nbsp;").append(span(Colors.MINE, e.name))
					.append(span(Colors.MUTED, (e.defense ? " on D" : "") + " (" + String.join(", ", e.leagues) + ")"));
			}
		}
		sideOfBall.setText(sb.append("</body></html>").toString());
		revalidate();
		repaint();
	}

	void clearFeed()
	{
		feed.removeAll();
		feedLabels.clear();
		feed.revalidate();
		feed.repaint();
	}

	/**
	 * Time and headline (significant plays only) and player; then each league's side ("[UND +6.3] CaptainKusch",
	 * "[KCL +0.4] You"); then the breakdown.
	 */
	private String html(PlayEvent e)
	{
		StringBuilder sb = new StringBuilder("<html><body style='width:170px'>");
		sb.append(span(Colors.MUTED, clock.format(new Date(e.observedAt))));
		if (e.offline)
		{
			sb.append(span(Colors.MUTED, " · while offline"));
		}
		sb.append(' ');
		if (e.significant() || e.isMatchupEvent())
		{
			sb.append("<b>").append(span(Colors.play(e, e.tier), e.top().headline)).append("</b> ");
		}
		if (!e.isMatchupEvent())
		{
			sb.append(span(Colors.play(e, e.tier), e.name()));
		}
		sb.append("<br>");
		boolean first = true;
		for (PlayEvent.Impact i : e.visible)
		{
			if (!first)
			{
				sb.append(span(Colors.MUTED, " · "));
			}
			first = false;
			String tag = "[" + i.alias + (e.isMatchupEvent() ? "" : " " + Format.points(i.delta)) + "]";
			sb.append(span(Colors.play(e, i.tier), tag)).append(' ')
				.append(span(Colors.NEUTRAL, i.owner == Owner.MINE ? "You" : i.ownerName));
		}
		String detail = e.detail();
		if (!detail.isEmpty())
		{
			sb.append("<br>").append(span(Colors.NEUTRAL, detail));
		}
		if (e.playText != null && !e.breakdown().isEmpty())
		{
			sb.append("<br>").append(span(Colors.MUTED, e.breakdown()));
		}
		return sb.append("</body></html>").toString();
	}

	private static String span(Color color, String text)
	{
		return "<span style='color:" + Colors.hex(color) + "'>" + Text.escapeHtml(text) + "</span>";
	}
}
