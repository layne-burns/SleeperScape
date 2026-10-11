package com.layneburns.sleeperscape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(SleeperScapeConfig.GROUP)
public interface SleeperScapeConfig extends Config
{
	String GROUP = "sleeperscape";

	enum ChatMode
	{
		OFF,
		SIGNIFICANT,
		ALL
	}

	@ConfigSection(name = "Account", description = "Which Sleeper leagues to follow", position = 0)
	String accountSection = "account";

	@ConfigSection(name = "Plays & scores", description = "What is shown and when", position = 1)
	String displaySection = "display";

	@ConfigSection(name = "Reactions", description = "Your character reacts to plays (only you see it)", position = 2)
	String reactionsSection = "reactions";

	@ConfigSection(name = "Red zone", description = "Live alert when your players' team is inside the 20", position = 3)
	String redZoneSection = "redZone";

	@ConfigSection(name = "Data", description = "Where the data comes from", position = 4)
	String dataSection = "data";

	@ConfigSection(name = "Developer", description = "Recording for testing", position = 5, closedByDefault = true)
	String developerSection = "developer";

	// ---- Account ----

	@ConfigItem(
		keyName = "username",
		name = "Sleeper username",
		description = "Your Sleeper username (no password: Sleeper's data is public and read-only). Your leagues are found from it",
		section = accountSection,
		position = 0
	)
	default String username()
	{
		return "";
	}

	@ConfigItem(
		keyName = "extraLeagueIds",
		name = "Extra league IDs",
		description = "Comma-separated Sleeper league IDs to follow as well (from the league's URL)",
		section = accountSection,
		position = 1
	)
	default String extraLeagueIds()
	{
		return "";
	}

	@Range(max = 18)
	@ConfigItem(
		keyName = "weekOverride",
		name = "Week override",
		description = "0 = the current NFL week. Set a week only for testing",
		section = accountSection,
		position = 2
	)
	default int weekOverride()
	{
		return 0;
	}

	// ---- Plays & scores ----

	@Range(max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "delaySeconds",
		name = "Delay",
		description = "Hold every play and score this long, to match your TV. The side panel has quick buttons. The red-zone alert is never delayed",
		section = displaySection,
		position = 0
	)
	default int delaySeconds()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "startersOnly",
		name = "Starters only",
		description = "Ignore points scored by bench players",
		section = displaySection,
		position = 1
	)
	default boolean startersOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeOpponent",
		name = "Include opponent",
		description = "Also show your opponent's plays (in red)",
		section = displaySection,
		position = 2
	)
	default boolean includeOpponent()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showScoreboard",
		name = "Scoreboard overlay",
		description = "Scores for each enabled league",
		section = displaySection,
		position = 3
	)
	default boolean showScoreboard()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWinProbability",
		name = "Win probability",
		description = "Estimated from projections and game time left (needs ESPN data)",
		section = displaySection,
		position = 4
	)
	default boolean showWinProbability()
	{
		return true;
	}

	@Range(min = 2, max = 30)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "bannerSeconds",
		name = "Banner time",
		description = "How long the pop-up banner for a significant play stays up",
		section = displaySection,
		position = 5
	)
	default int bannerSeconds()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "showBanner",
		name = "Play banner",
		description = "Pop-up banner over the game for significant plays",
		section = displaySection,
		position = 6
	)
	default boolean showBanner()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatMode",
		name = "Chat messages",
		description = "Which plays also go to the chat box. Every play is always in the side panel",
		section = displaySection,
		position = 7
	)
	default ChatMode chatMode()
	{
		return ChatMode.SIGNIFICANT;
	}

	@ConfigItem(
		keyName = "overheadText",
		name = "Overhead text",
		description = "Text over your character with each reaction: the play and the leagues it helps or hurts",
		section = displaySection,
		position = 8
	)
	default boolean overheadText()
	{
		return true;
	}

	@Range(min = 5, max = 99)
	@ConfigItem(
		keyName = "bigPlayYards",
		name = "Big play yards",
		description = "A single run, catch or completion at least this long is a big play",
		section = displaySection,
		position = 9
	)
	default int bigPlayYards()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "muted",
		name = "Muted",
		description = "Pauses banners, chat, overhead text and reactions (the side panel keeps listing plays)",
		section = displaySection,
		position = 10,
		hidden = true
	)
	default boolean muted()
	{
		return false;
	}

	// ---- Reactions ----

	@ConfigItem(keyName = "reactScored", name = "Scored (fireworks)", description = "Your touchdowns, field goals, 2-pt conversions: fireworks and a big celebration", section = reactionsSection, position = 0)
	default boolean reactScored()
	{
		return true;
	}

	@ConfigItem(keyName = "reactVeryGood", name = "Very good", description = "Big plays, takeaways, sacks, milestones, taking the lead", section = reactionsSection, position = 1)
	default boolean reactVeryGood()
	{
		return true;
	}

	@ConfigItem(keyName = "reactGood", name = "Good", description = "Any other points for you (or lost by your opponent)", section = reactionsSection, position = 2)
	default boolean reactGood()
	{
		return true;
	}

	@ConfigItem(keyName = "reactBad", name = "Bad", description = "Small losses: a sack taken, a fumble kept, an opponent's small gain", section = reactionsSection, position = 3)
	default boolean reactBad()
	{
		return true;
	}

	@ConfigItem(keyName = "reactVeryBad", name = "Very bad", description = "Your turnovers, your opponent's big plays, losing the lead", section = reactionsSection, position = 4)
	default boolean reactVeryBad()
	{
		return true;
	}

	@ConfigItem(keyName = "reactScoredOn", name = "Scored on (lightning)", description = "Your opponent scores: lightning strikes you", section = reactionsSection, position = 5)
	default boolean reactScoredOn()
	{
		return true;
	}

	@ConfigItem(
		keyName = "reactOnlyWhenIdle",
		name = "Only when idle",
		description = "Don't play an emote over a skilling or walking animation (fireworks and lightning still show)",
		section = reactionsSection,
		position = 6
	)
	default boolean reactOnlyWhenIdle()
	{
		return true;
	}

	@Range(min = 1, max = 60)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "reactionCooldown",
		name = "Cooldown",
		description = "At most one reaction this often; the strongest waiting play wins",
		section = reactionsSection,
		position = 7
	)
	default int reactionCooldown()
	{
		return 3;
	}

	// ---- Red zone ----

	@ConfigItem(
		keyName = "redZoneOverlay",
		name = "Red-zone overlay",
		description = "Shows which of your (pink) and your opponent's (red) starters are in the red zone. Needs ESPN data",
		section = redZoneSection,
		position = 0
	)
	default boolean redZoneOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "redZoneChat",
		name = "Red-zone chat",
		description = "A chat line when a team enters the red zone",
		section = redZoneSection,
		position = 1
	)
	default boolean redZoneChat()
	{
		return true;
	}

	// ---- Data ----

	@ConfigItem(
		keyName = "useEspn",
		name = "Use ESPN data",
		description = "Game clock, red zone, play descriptions and win probability come from ESPN's public scoreboard",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = dataSection,
		position = 0
	)
	default boolean useEspn()
	{
		return false;
	}

	@Range(min = 5, max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "pollSeconds",
		name = "Poll every",
		description = "How often scores are fetched while games are on",
		section = dataSection,
		position = 1
	)
	default int pollSeconds()
	{
		return 10;
	}

	// ---- Developer ----

	@ConfigItem(
		keyName = "record",
		name = "Record responses",
		description = "Save every response (gzip JSON lines) to .runelite/plugin-data/sleeperscape/recordings for testing",
		section = developerSection,
		position = 0
	)
	default boolean record()
	{
		return true;
	}
}
