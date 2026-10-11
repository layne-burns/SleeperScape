package com.layneburns.sleeperscape;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;

/** Small Swing helpers shared by the side panel's tabs. All on the EDT. */
final class Ui
{
	private Ui()
	{
	}

	/** A vertical stack that, inside a scroll pane, keeps to the viewport's width (so text wraps, never scrolls sideways). */
	static final class Stack extends JPanel implements Scrollable
	{
		Stack()
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
			setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		@Override
		public Dimension getMaximumSize()
		{
			// Nested in another stack: full width, only as tall as its rows.
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return Math.max(16, visible.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/** A full-width row whose height follows its content (so nothing is clipped to its first size). */
	static JPanel row(Component c)
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

	static JScrollPane scroll(JComponent view)
	{
		JScrollPane scroll = new JScrollPane(view);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFocusable(false);
		b.setMargin(new Insets(2, 2, 2, 2));
		b.addActionListener(a -> action.run());
		return b;
	}

	static JLabel label(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setForeground(color);
		return l;
	}

	/** Muted text that wraps at the panel's width (an explicit width, so its height accounts for the wrapping). */
	static JLabel note(String text)
	{
		JLabel l = new JLabel("<html><body style='width:185px'>" + Text.escapeHtml(text) + "</body></html>");
		l.setForeground(Colors.MUTED);
		return l;
	}

	static String span(Color color, String text)
	{
		return "<span style='color:" + Colors.hex(color) + "'>" + Text.escapeHtml(text) + "</span>";
	}
}
