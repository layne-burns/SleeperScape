package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Category;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.SpotanimID;

/**
 * My character reacts to plays: an emote by tier, fireworks when I score, lightning when I'm scored on, and
 * overhead text for significant plays. Purely client-side (only I see it); nothing is sent to the server.
 * Plays arrive from the engine thread and are acted on from {@link #onGameTick()} on the client thread.
 */
class Reactor
{
	private static final int[] GOOD = {AnimationID.EMOTE_YES, AnimationID.EMOTE_CLAP, AnimationID.EMOTE_WAVE,
		AnimationID.EMOTE_BOW};
	private static final int[] VERY_GOOD = {AnimationID.EMOTE_CHEER, AnimationID.EMOTE_DANCE,
		AnimationID.EMOTE_JUMP_WITH_JOY, AnimationID.EMOTE_DANCE_SCOTTISH, AnimationID.EMOTE_DANCE_HEADBANG,
		AnimationID.EMOTE_DANCE_SPIN, AnimationID.EMOTE_FLEX};
	private static final int[] BAD = {AnimationID.EMOTE_NO, AnimationID.EMOTE_THINK, AnimationID.EMOTE_SLAP_HEAD};
	private static final int[] VERY_BAD = {AnimationID.EMOTE_CRY, AnimationID.EMOTE_ANGRY, AnimationID.EMOTE_PANIC,
		AnimationID.EMOTE_STAMPFEET};
	private static final int[] CONFLICTED = {AnimationID.EMOTE_SHRUG};

	// Key in the player's spot-anim table, clear of the slots the server uses.
	private static final int SPOTANIM_KEY = 0x51EE9;
	// Client cycles are 20 ms: about 3 s of overhead text.
	private static final int OVERHEAD_CYCLES = 150;

	private final Client client;
	private final SleeperScapeConfig config;
	private final ConcurrentLinkedQueue<PlayEvent> queue = new ConcurrentLinkedQueue<>();
	private long lastReactionAt;

	Reactor(Client client, SleeperScapeConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/** From any thread. */
	void offer(PlayEvent e)
	{
		queue.add(e);
	}

	void clear()
	{
		queue.clear();
	}

	/** On the client thread. Plays the strongest waiting play once the cooldown has passed. */
	void onGameTick()
	{
		if (queue.isEmpty())
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastReactionAt < config.reactionCooldown() * 1000L)
		{
			return;
		}
		List<PlayEvent> waiting = new ArrayList<>();
		for (PlayEvent e; (e = queue.poll()) != null; )
		{
			waiting.add(e);
		}
		PlayEvent strongest = null;
		for (PlayEvent e : waiting)
		{
			if (e.tier != null && (strongest == null || Math.abs(e.tier.value) > Math.abs(strongest.tier.value)
				|| (Math.abs(e.tier.value) == Math.abs(strongest.tier.value) && e.significant() && !strongest.significant())))
			{
				strongest = e;
			}
		}
		Player me = client.getLocalPlayer();
		if (strongest == null || me == null)
		{
			return;
		}
		lastReactionAt = now;
		// Every reaction says what caused it; significant plays get the text even with their emote tier off.
		if (config.overheadText() && (enabled(strongest.tier) || strongest.significant() || strongest.isMatchupEvent()))
		{
			me.setOverheadText(Text.overhead(strongest));
			me.setOverheadCycle(OVERHEAD_CYCLES);
		}
		react(me, strongest);
	}

	private void react(Player me, PlayEvent e)
	{
		Tier tier = e.tier;
		if (!enabled(tier))
		{
			return;
		}
		int spotanim = -1;
		if (tier == Tier.SCORED)
		{
			spotanim = e.top() == Category.MATCHUP_FINAL ? SpotanimID.LEVELUP_99_ANIM : SpotanimID.LEVELUP_ANIM;
		}
		else if (tier == Tier.SCORED_ON)
		{
			spotanim = SpotanimID.SARADOMIN_LIGHTNING;
		}
		if (spotanim >= 0)
		{
			me.createSpotAnim(SPOTANIM_KEY, spotanim, 0, 0);
		}
		boolean idle = me.getAnimation() == -1 && me.getPoseAnimation() == me.getIdlePoseAnimation();
		if (config.reactOnlyWhenIdle() && !idle)
		{
			return;
		}
		int[] pool = pool(tier);
		me.setAnimation(pool[ThreadLocalRandom.current().nextInt(pool.length)]);
		me.setAnimationFrame(0);
	}

	private boolean enabled(Tier tier)
	{
		if (tier == null)
		{
			return false;
		}
		switch (tier)
		{
			case SCORED:
				return config.reactScored();
			case VERY_GOOD:
				return config.reactVeryGood();
			case GOOD:
				return config.reactGood();
			case BAD:
				return config.reactBad();
			case VERY_BAD:
				return config.reactVeryBad();
			case SCORED_ON:
				return config.reactScoredOn();
			default:
				// A shrug: shown whenever both strong tiers are on.
				return config.reactVeryGood() && config.reactVeryBad();
		}
	}

	private static int[] pool(Tier tier)
	{
		switch (tier)
		{
			case SCORED:
			case VERY_GOOD:
				return VERY_GOOD;
			case GOOD:
				return GOOD;
			case BAD:
				return BAD;
			case VERY_BAD:
			case SCORED_ON:
				return VERY_BAD;
			default:
				return CONFLICTED;
		}
	}
}
