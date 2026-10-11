package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Owner;
import com.layneburns.sleeperscape.core.PlayEvent;
import java.awt.BorderLayout;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/** The feed: a filter (All, Big, Mine; remembered) over compact rows, newest first. */
class PlaysTab extends JPanel
{
	enum Filter
	{
		ALL("All"),
		BIG("Big"),
		MINE("Mine");

		final String label;

		Filter(String label)
		{
			this.label = label;
		}
	}

	static final String FILTER_KEY = "panel.feedFilter";
	private static final int MAX_FEED = 300;

	private final ConfigManager configManager;
	private final ArrayDeque<PlayEvent> events = new ArrayDeque<>();
	private final Map<Long, PlayRow> rows = new HashMap<>();
	private final Ui.Stack list = new Ui.Stack();
	private Filter filter = Filter.ALL;

	PlaysTab(ConfigManager configManager)
	{
		super(new BorderLayout());
		this.configManager = configManager;
		setOpaque(false);

		String saved = configManager.getConfiguration(SleeperScapeConfig.GROUP, FILTER_KEY);
		for (Filter f : Filter.values())
		{
			if (f.name().equals(saved))
			{
				filter = f;
			}
		}
		MaterialTabGroup filters = new MaterialTabGroup();
		filters.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
		MaterialTab selected = null;
		for (Filter f : Filter.values())
		{
			MaterialTab tab = new MaterialTab(f.label, filters, null);
			tab.setOnSelectEvent(() ->
			{
				setFilter(f);
				return true;
			});
			filters.addTab(tab);
			if (f == filter)
			{
				selected = tab;
			}
		}
		filters.select(selected);

		// The list itself is the scroll view: it keeps to the panel's width, so long text is trimmed, not pushed
		// off the right edge (where the net points sit).
		list.setOpaque(true);
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		add(filters, BorderLayout.NORTH);
		add(Ui.scroll(list), BorderLayout.CENTER);
	}

	private void setFilter(Filter f)
	{
		if (f == filter && !rows.isEmpty())
		{
			return;
		}
		filter = f;
		configManager.setConfiguration(SleeperScapeConfig.GROUP, FILTER_KEY, f.name());
		rebuild();
	}

	private boolean shows(PlayEvent e)
	{
		switch (filter)
		{
			case BIG:
				return e.significant() || e.isMatchupEvent();
			case MINE:
				return e.isMatchupEvent() || e.visible.stream().anyMatch(i -> i.owner == Owner.MINE);
			default:
				return true;
		}
	}

	void add(PlayEvent e)
	{
		events.addFirst(e);
		while (events.size() > MAX_FEED)
		{
			PlayEvent old = events.removeLast();
			PlayRow row = rows.remove(old.id);
			if (row != null)
			{
				list.remove(row);
			}
		}
		if (shows(e))
		{
			PlayRow row = new PlayRow(e);
			rows.put(e.id, row);
			list.add(row, 0);
		}
		list.revalidate();
		list.repaint();
	}

	void update(PlayEvent e)
	{
		PlayRow row = rows.get(e.id);
		if (row != null)
		{
			row.refresh();
		}
	}

	void clear()
	{
		events.clear();
		rebuild();
	}

	private void rebuild()
	{
		list.removeAll();
		rows.clear();
		for (Iterator<PlayEvent> it = events.iterator(); it.hasNext(); )
		{
			PlayEvent e = it.next();
			if (shows(e))
			{
				PlayRow row = new PlayRow(e);
				rows.put(e.id, row);
				list.add(row);
			}
		}
		list.revalidate();
		list.repaint();
	}
}
