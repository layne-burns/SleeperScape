package com.layneburns.sleeperscape;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Overhead text for one reaction, in coloured pieces, with an OSRS-style effect: routine plays plain yellow,
 * significant plays coloured, my scoring plays wave and glow, being scored on shakes in red. Never flashes.
 */
final class Speech
{
	enum Style
	{
		/** Routine play: yellow, like normal overhead chat. */
		PLAIN,
		/** Significant: coloured by how it went for me (green good, red bad, orange both). */
		SIGNIFICANT,
		/** My scoring play or a matchup win: wave and a pink/white/green glow. */
		SCORED,
		/** Scored on, or a matchup loss: a red shake (steady colour, no flashing). */
		SCORED_ON
	}

	static final class Segment
	{
		final String text;
		final Color color;

		Segment(String text, Color color)
		{
			this.text = text;
			this.color = color;
		}
	}

	final List<Segment> segments = new ArrayList<>();
	final Style style;
	final long startedAt;
	final long endsAt;

	Speech(Style style, long startedAt, long endsAt)
	{
		this.style = style;
		this.startedAt = startedAt;
		this.endsAt = endsAt;
	}

	Speech add(String text, Color color)
	{
		if (!text.isEmpty())
		{
			segments.add(new Segment(text, color));
		}
		return this;
	}

	String plain()
	{
		StringBuilder sb = new StringBuilder();
		for (Segment s : segments)
		{
			sb.append(s.text);
		}
		return sb.toString();
	}
}
