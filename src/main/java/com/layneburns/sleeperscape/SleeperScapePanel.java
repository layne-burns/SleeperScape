package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.LeagueContext;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.SideOfBall;
import com.layneburns.sleeperscape.core.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Side panel: a status strip (delay, mute, any error), then tabs that each get the full height: Plays (the feed),
 * Starters (who is playing, yet to play, done), Field (my side of the ball, live) and Setup (delay, leagues).
 * The last tab used is remembered. All methods run on the Swing EDT.
 */
class SleeperScapePanel extends PluginPanel
{
	private static final String TAB_KEY = "panel.tab";

	private final SleeperScapeConfig config;
	private final ConfigManager configManager;

	private final JLabel delay = new JLabel();
	private final JCheckBox mute = new JCheckBox("Mute");
	private final JLabel status = new JLabel();
	private final PlaysTab plays;
	private final StartersTab starters;
	private final FieldTab field = new FieldTab();
	private final SetupTab setup;
	private final MaterialTab fieldTab;
	private String sideOfBallKey;

	SleeperScapePanel(SleeperScapeConfig config, ConfigManager configManager, Settings settings)
	{
		super(false);
		this.config = config;
		this.configManager = configManager;
		this.plays = new PlaysTab(configManager);
		this.starters = new StartersTab(configManager);
		this.setup = new SetupTab(config, configManager, settings);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		// Status strip: delay and mute always in reach; errors appear under them.
		JPanel strip = new JPanel(new BorderLayout(6, 0));
		strip.setOpaque(false);
		delay.setForeground(Color.WHITE);
		mute.setOpaque(false);
		mute.setFocusable(false);
		mute.setToolTipText("Pause pop-ups, chat, overhead text and reactions (the feed keeps going)");
		mute.addActionListener(a -> configManager.setConfiguration(SleeperScapeConfig.GROUP, "muted", mute.isSelected()));
		strip.add(delay, BorderLayout.CENTER);
		strip.add(mute, BorderLayout.EAST);
		status.setForeground(Colors.MUTED);

		JPanel display = new JPanel();
		display.setOpaque(false);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
		List<MaterialTab> all = new ArrayList<>();
		all.add(tab(tabs, "Plays", plays));
		all.add(tab(tabs, "Starters", starters));
		fieldTab = tab(tabs, "Field", field);
		all.add(fieldTab);
		all.add(tab(tabs, "Setup", setup));

		JPanel top = new Ui.Stack();
		top.add(Ui.row(strip));
		top.add(Ui.row(status));
		top.add(Ui.row(tabs));
		add(top, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		String saved = configManager.getConfiguration(SleeperScapeConfig.GROUP, TAB_KEY);
		MaterialTab first = all.get(0);
		for (MaterialTab t : all)
		{
			if (saved != null && t.getText().startsWith(saved))
			{
				first = t;
			}
		}
		tabs.select(first);
		setSideOfBall(Collections.emptyList());
		setStatus(null);
		refreshConfig();
	}

	private MaterialTab tab(MaterialTabGroup group, String name, JPanel content)
	{
		MaterialTab tab = new MaterialTab(name, group, content);
		tab.setOnSelectEvent(() ->
		{
			configManager.setConfiguration(SleeperScapeConfig.GROUP, TAB_KEY, name);
			return true;
		});
		group.addTab(tab);
		return tab;
	}

	/** After any config change: delay, mute, and views that depend on settings. */
	void refreshConfig()
	{
		delay.setText("Delay " + config.delaySeconds() + "s");
		mute.setSelected(config.muted());
	}

	void setStatus(String text)
	{
		status.setText(text == null ? "" : "<html><body style='width:190px'>" + Text.escapeHtml(text) + "</body></html>");
		status.setVisible(text != null);
	}

	void setLeagues(List<LeagueContext> leagues)
	{
		setup.setLeagues(leagues);
	}

	void addPlay(PlayEvent e)
	{
		plays.add(e);
	}

	void updatePlay(PlayEvent e)
	{
		plays.update(e);
	}

	void clearFeed()
	{
		plays.clear();
		starters.show(View.EMPTY, config.useEspn());
	}

	/** Starters, from the delayed scoreboard view (called when a new frame is shown). */
	void setView(View view)
	{
		starters.show(view, config.useEspn());
	}

	/** Live; rebuilt only when something changed. The Field tab shows how many games. */
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
		fieldTab.setText(games.isEmpty() ? "Field" : "Field " + games.size());
		field.show(games, config.useEspn());
	}
}
