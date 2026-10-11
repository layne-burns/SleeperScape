package com.layneburns.sleeperscape;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ImageUtil;

/**
 * A section with a clickable header (arrow, title, summary on the right) and a body that folds away. Whether it
 * is open is remembered per key in the plugin's config ({@code panel.<key>.open}).
 */
class Collapsible extends JPanel
{
	// The same arrows RuneLite's own config sections use.
	private static final ImageIcon CLOSED_ICON;
	private static final ImageIcon OPEN_ICON;

	static
	{
		BufferedImage right = ImageUtil.loadImageResource(Collapsible.class, "/util/arrow_right.png");
		right = ImageUtil.luminanceOffset(right, -121);
		CLOSED_ICON = new ImageIcon(right);
		OPEN_ICON = new ImageIcon(ImageUtil.rotateImage(right, Math.PI / 2));
	}

	private final ConfigManager configManager;
	private final String key;
	private final JLabel arrow = new JLabel();
	private final JLabel title = new JLabel();
	private final JLabel summary = new JLabel();
	private final Ui.Stack body = new Ui.Stack();
	private boolean open;

	Collapsible(ConfigManager configManager, String key, String titleText, Color titleColor, boolean defaultOpen)
	{
		super(new BorderLayout());
		this.configManager = configManager;
		this.key = key;
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		title.setText(titleText);
		title.setForeground(titleColor);
		summary.setForeground(Colors.MUTED);
		header.add(arrow, BorderLayout.WEST);
		header.add(title, BorderLayout.CENTER);
		header.add(summary, BorderLayout.EAST);
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				setOpen(!open);
				configManager.setConfiguration(SleeperScapeConfig.GROUP, configKey(), String.valueOf(open));
			}
		});
		body.setBorder(BorderFactory.createEmptyBorder(0, 10, 2, 0));
		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);

		String saved = configManager.getConfiguration(SleeperScapeConfig.GROUP, configKey());
		setOpen(saved == null ? defaultOpen : Boolean.parseBoolean(saved));
	}

	private String configKey()
	{
		return "panel." + key + ".open";
	}

	private void setOpen(boolean open)
	{
		this.open = open;
		arrow.setIcon(open ? OPEN_ICON : CLOSED_ICON);
		body.setVisible(open);
		revalidate();
		repaint();
	}

	/** Where the section's rows go. */
	Ui.Stack body()
	{
		return body;
	}

	void setSummary(String text, Color color)
	{
		summary.setText(text);
		summary.setForeground(color);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
