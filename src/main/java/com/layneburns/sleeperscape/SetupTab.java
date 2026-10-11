package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.LeagueContext;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;

/**
 * Delay (presets plus fine tuning) and the leagues: on/off with the full name, and a full-width short name
 * (used in chat, the feed tags and the overhead text). Everything else lives in the plugin's config.
 */
class SetupTab extends JPanel
{
	private static final int[] PRESETS = {30, 60, 90, 120};

	private final SleeperScapeConfig config;
	private final ConfigManager configManager;
	private final Settings settings;
	private final Ui.Stack stack = new Ui.Stack();
	private final Ui.Stack leagues = new Ui.Stack();

	SetupTab(SleeperScapeConfig config, ConfigManager configManager, Settings settings)
	{
		super(new BorderLayout());
		this.config = config;
		this.configManager = configManager;
		this.settings = settings;
		setOpaque(false);

		stack.add(Ui.row(Ui.label("Delay (everything but the red zone)", Color.WHITE)));
		JPanel presets = new JPanel(new GridLayout(1, PRESETS.length, 3, 0));
		presets.setOpaque(false);
		for (int p : PRESETS)
		{
			presets.add(Ui.button(p + "s", () -> setDelay(p)));
		}
		stack.add(Ui.row(presets));
		stack.add(Box.createVerticalStrut(3));
		JPanel fine = new JPanel(new GridLayout(1, 3, 3, 0));
		fine.setOpaque(false);
		fine.add(Ui.button("-5s", () -> setDelay(config.delaySeconds() - 5)));
		fine.add(Ui.button("0s", () -> setDelay(0)));
		fine.add(Ui.button("+5s", () -> setDelay(config.delaySeconds() + 5)));
		stack.add(Ui.row(fine));
		stack.add(Box.createVerticalStrut(8));

		stack.add(Ui.row(Ui.label("Leagues", Color.WHITE)));
		stack.add(leagues);
		add(Ui.scroll(stack), BorderLayout.CENTER);
	}

	void setDelay(int seconds)
	{
		configManager.setConfiguration(SleeperScapeConfig.GROUP, "delaySeconds", Math.max(0, Math.min(600, seconds)));
	}

	/** One card per league: tick and full name, then the short name across the full width. */
	void setLeagues(List<LeagueContext> list)
	{
		leagues.removeAll();
		for (LeagueContext l : list)
		{
			Ui.Stack card = new Ui.Stack();
			card.setOpaque(true);
			card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			card.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
			JCheckBox box = new JCheckBox(l.name(), settings.leagueEnabled(l.id()));
			box.setOpaque(false);
			box.setToolTipText(l.myRosterId < 0 ? "You don't own a team in this league" : "Show this league");
			box.addActionListener(a -> settings.setLeagueEnabled(l.id(), box.isSelected()));
			card.add(Ui.row(box));

			JPanel aliasRow = new JPanel(new BorderLayout(6, 0));
			aliasRow.setOpaque(false);
			aliasRow.add(Ui.label("Short name", Colors.MUTED), BorderLayout.WEST);
			JTextField alias = new JTextField(settings.leagueAlias(l.id()));
			alias.setToolTipText("Shown in chat, the feed tags and the overhead text");
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
			aliasRow.add(alias, BorderLayout.CENTER);
			card.add(Ui.row(aliasRow));
			leagues.add(card);
			leagues.add(Box.createVerticalStrut(4));
		}
		leagues.revalidate();
		leagues.repaint();
	}
}
