package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.SideOfBall;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Live: one card per game where my starters are on the field (offense with the ball, IDP/DEF without). Header:
 * the matchup and who has the ball, or RED ZONE with a red edge. Then a row per player with their leagues.
 */
class FieldTab extends JPanel
{
	private final Ui.Stack stack = new Ui.Stack();

	FieldTab()
	{
		super(new BorderLayout());
		setOpaque(false);
		add(Ui.scroll(stack), BorderLayout.CENTER);
	}

	void show(List<SideOfBall> games, boolean espn)
	{
		stack.removeAll();
		JLabel note = Ui.note("Live (not delayed): games where your starters are on the field now.");
		note.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));
		stack.add(Ui.row(note));
		if (!espn || games.isEmpty())
		{
			stack.add(Ui.row(Ui.note(!espn
				? "Turn on \"Use ESPN data\" to see who has the ball."
				: "None of your starters are on the field.")));
		}
		boolean first = true;
		for (SideOfBall g : games)
		{
			if (!first)
			{
				stack.add(Box.createVerticalStrut(4));
			}
			first = false;
			stack.add(card(g));
		}
		stack.revalidate();
		stack.repaint();
	}

	private static JPanel card(SideOfBall g)
	{
		JPanel card = new JPanel(new BorderLayout(0, 2))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, g.redZone ? Colors.OPPONENT : ColorScheme.DARKER_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 5, 4, 5)));

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		header.add(Ui.label(g.game, Color.WHITE), BorderLayout.WEST);
		header.add(Ui.label(g.redZone ? g.teamWithBall + " · RED ZONE" : g.teamWithBall + " ball",
			g.redZone ? Colors.OPPONENT : Colors.MUTED), BorderLayout.EAST);
		card.add(header, BorderLayout.NORTH);

		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 1));
		rows.setOpaque(false);
		for (SideOfBall.Entry e : g.entries)
		{
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.add(Ui.label(e.defense ? "DEF  " + e.name : e.name, Colors.MINE), BorderLayout.CENTER);
			row.add(Ui.label(String.join(", ", e.leagues), Colors.MUTED), BorderLayout.EAST);
			rows.add(row);
		}
		card.add(rows, BorderLayout.CENTER);
		return card;
	}
}
