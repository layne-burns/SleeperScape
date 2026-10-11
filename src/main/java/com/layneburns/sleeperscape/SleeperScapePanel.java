package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.LeagueContext;
import com.layneburns.sleeperscape.core.PlayEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.SimpleDateFormat;
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
		JLabel label = new JLabel(html(e));
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

	void clearFeed()
	{
		feed.removeAll();
		feedLabels.clear();
		feed.revalidate();
		feed.repaint();
	}

	private String html(PlayEvent e)
	{
		StringBuilder sb = new StringBuilder("<html><body style='width:170px'>");
		sb.append("<span style='color:").append(Colors.hex(Colors.MUTED)).append("'>")
			.append(clock.format(new Date(e.observedAt)));
		if (e.offline)
		{
			sb.append(" · while offline");
		}
		sb.append("</span> ");
		for (PlayEvent.Impact i : e.visible)
		{
			sb.append("<span style='color:").append(Colors.hex(Colors.play(e, i.tier))).append("'>[")
				.append(Text.escapeHtml(i.alias));
			if (!e.isMatchupEvent())
			{
				sb.append(' ').append(Format.points(i.delta));
			}
			sb.append("]</span> ");
		}
		sb.append("<br>");
		if (e.significant() || e.isMatchupEvent())
		{
			sb.append("<b style='color:").append(Colors.hex(Colors.play(e, e.tier))).append("'>")
				.append(Text.escapeHtml(e.top().headline)).append("</b> ");
		}
		if (!e.isMatchupEvent())
		{
			sb.append("<span style='color:").append(Colors.hex(Colors.play(e, e.tier))).append("'>")
				.append(Text.escapeHtml(e.name())).append("</span> ");
		}
		String detail = e.detail();
		if (!detail.isEmpty())
		{
			sb.append("<br><span style='color:").append(Colors.hex(Colors.NEUTRAL)).append("'>")
				.append(Text.escapeHtml(detail)).append("</span>");
		}
		if (e.playText != null && !e.breakdown().isEmpty())
		{
			sb.append("<br><span style='color:").append(Colors.hex(Colors.MUTED)).append("'>")
				.append(Text.escapeHtml(e.breakdown())).append("</span>");
		}
		return sb.append("</body></html>").toString();
	}
}
