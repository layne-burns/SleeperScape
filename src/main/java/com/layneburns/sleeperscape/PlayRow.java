package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Format;
import com.layneburns.sleeperscape.core.Owner;
import com.layneburns.sleeperscape.core.PlayEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * One play in the feed, compact: time, headline (significant plays), player, and the net points for me on the
 * right; significant plays add a one-line detail. Clicking shows every league's side, the full ESPN text and
 * the stat breakdown; clicking again folds it.
 */
class PlayRow extends JPanel
{
	private static final SimpleDateFormat CLOCK = new SimpleDateFormat("h:mm");

	private final PlayEvent e;
	private final JLabel time = new JLabel();
	private final JLabel headline = new JLabel();
	private final JLabel name = new JLabel();
	private final JLabel net = new JLabel();
	private final JLabel line2 = new JLabel();
	private final JLabel details = new JLabel();
	private boolean expanded;

	PlayRow(PlayEvent e)
	{
		this.e = e;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setOpaque(true);
		setBackground(Colors.background(e, ColorScheme.DARKER_GRAY_COLOR));
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(3, 4, 3, 4)));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JPanel lead = new JPanel(new BorderLayout(4, 0));
		lead.setOpaque(false);
		lead.add(time, BorderLayout.WEST);
		lead.add(headline, BorderLayout.EAST);
		JPanel first = new JPanel(new BorderLayout(4, 0));
		first.setOpaque(false);
		first.add(lead, BorderLayout.WEST);
		first.add(name, BorderLayout.CENTER);
		first.add(net, BorderLayout.EAST);
		add(Ui.row(first));
		add(Ui.row(line2));
		add(Ui.row(details));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent ev)
			{
				expanded = !expanded;
				refresh();
			}
		});
		refresh();
	}

	PlayEvent event()
	{
		return e;
	}

	/** Re-reads the event (ESPN text can arrive after the play was shown). */
	void refresh()
	{
		boolean big = e.significant() || e.isMatchupEvent();
		time.setText(CLOCK.format(new Date(e.observedAt)) + (e.offline ? " (offline)" : ""));
		time.setForeground(Colors.MUTED);
		headline.setText(big ? e.headline() : "");
		headline.setForeground(Colors.play(e, e.tier));
		name.setText(e.isMatchupEvent() ? "" : e.name());
		name.setForeground(big ? Colors.play(e, e.tier) : Colors.NEUTRAL);
		if (e.isMatchupEvent())
		{
			net.setText("");
		}
		else
		{
			net.setText(Format.points(e.netForMe));
			net.setForeground(e.netForMe > 0.005 ? Colors.WINNING : e.netForMe < -0.005 ? Colors.OPPONENT : Colors.MUTED);
		}

		// The one-line detail for significant plays (JLabel trims it with "..." when too long).
		String detail = e.detail();
		line2.setText(detail);
		line2.setForeground(Colors.NEUTRAL);
		line2.setVisible(big && !expanded && !detail.isEmpty());

		details.setVisible(expanded);
		if (expanded)
		{
			details.setText(detailsHtml());
		}
		revalidate();
		repaint();
	}

	/** Each league's side, then the full ESPN text, then the breakdown. */
	private String detailsHtml()
	{
		StringBuilder sb = new StringBuilder("<html><body style='width:165px'>");
		for (PlayEvent.Impact i : e.visible)
		{
			String tag = "[" + i.alias + (e.isMatchupEvent() ? "" : " " + Format.points(i.delta)) + "]";
			sb.append(Ui.span(Colors.play(e, i.tier), tag)).append(' ')
				.append(Ui.span(Colors.NEUTRAL, i.owner == Owner.MINE ? "You" : i.ownerName)).append("<br>");
		}
		String detail = e.detail();
		if (!detail.isEmpty())
		{
			sb.append(Ui.span(Colors.NEUTRAL, detail)).append("<br>");
		}
		if (e.playText != null && !e.breakdown().isEmpty())
		{
			sb.append(Ui.span(Colors.MUTED, e.breakdown()));
		}
		return sb.append("</body></html>").toString();
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
