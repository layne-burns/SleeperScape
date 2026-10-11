package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.Category;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.Tier;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.SpotanimID;

/**
 * My character reacts to plays: an emote by tier, fireworks when I score, lightning when I'm scored on, and
 * overhead text naming the play and the leagues it helps or hurts. Each reaction loops its emote and graphic
 * for {@link SleeperScapeConfig#reactionSeconds()}, with the text up as long. Purely client-side (only I see
 * it); nothing is sent to the server.
 *
 * Plays arrive from the engine thread; everything else runs on the client thread from {@link #onClientTick()}.
 * A waiting play stronger than the one playing interrupts it; otherwise the strongest waiting play goes next.
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
	// Client cycles are 20 ms.
	private static final int MS_PER_CYCLE = 20;
	// Restart the graphic at least this often, in case a finished one lingers in the actor's table.
	private static final long SPOTANIM_REPEAT_MS = 3000;

	private final Client client;
	private final SleeperScapeConfig config;
	private final ConcurrentLinkedQueue<PlayEvent> queue = new ConcurrentLinkedQueue<>();

	/** The reaction playing now, or null. */
	private PlayEvent current;
	private volatile long endsAt;
	private int animation = -1;
	private int spotanim = -1;
	private long spotanimAt;

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

	/** From any thread: forget waiting plays and let the current loop end at its next check. */
	void clear()
	{
		queue.clear();
		endsAt = 0;
	}

	/** On the client thread, every client tick. */
	void onClientTick()
	{
		long now = System.currentTimeMillis();
		Player me = client.getLocalPlayer();
		if (current != null && now >= endsAt)
		{
			current = null;
			animation = -1;
			spotanim = -1;
		}
		PlayEvent waiting = strongestWaiting();
		long gap = config.reactionCooldown() * 1000L;
		boolean free = current == null && now >= endsAt + gap;
		if (waiting != null && me != null && (free || stronger(waiting, current)))
		{
			queue.clear();
			start(me, waiting, now);
		}
		else if (waiting != null)
		{
			// Keep only the strongest waiting play.
			queue.clear();
			queue.add(waiting);
		}
		if (current != null && me != null)
		{
			keepLooping(me, now);
		}
	}

	private PlayEvent strongestWaiting()
	{
		PlayEvent best = null;
		for (PlayEvent e : queue)
		{
			if (e.tier == null)
			{
				continue;
			}
			if (best == null || Math.abs(e.tier.value) > Math.abs(best.tier.value)
				|| (Math.abs(e.tier.value) == Math.abs(best.tier.value) && e.significant() && !best.significant()))
			{
				best = e;
			}
		}
		return best;
	}

	private static boolean stronger(PlayEvent a, PlayEvent b)
	{
		return b != null && Math.abs(a.tier.value) > Math.abs(b.tier.value);
	}

	private void start(Player me, PlayEvent e, long now)
	{
		long length = config.reactionSeconds() * 1000L;
		boolean react = enabled(e.tier);
		boolean text = config.overheadText() && (react || e.significant() || e.isMatchupEvent());
		if (!react && !text)
		{
			return;
		}
		current = e;
		endsAt = now + length;
		if (text)
		{
			// Every reaction says what caused it; significant plays get the text even with their emote tier off.
			me.setOverheadText(Text.overhead(e));
			me.setOverheadCycle((int) (length / MS_PER_CYCLE));
		}
		animation = -1;
		spotanim = -1;
		if (!react)
		{
			return;
		}
		if (e.tier == Tier.SCORED)
		{
			spotanim = e.top() == Category.MATCHUP_FINAL ? SpotanimID.LEVELUP_99_ANIM : SpotanimID.LEVELUP_ANIM;
		}
		else if (e.tier == Tier.SCORED_ON)
		{
			spotanim = SpotanimID.SARADOMIN_LIGHTNING;
		}
		if (spotanim >= 0)
		{
			me.createSpotAnim(SPOTANIM_KEY, spotanim, 0, 0);
			spotanimAt = now;
		}
		if (!config.reactOnlyWhenIdle() || idle(me))
		{
			int[] pool = pool(e.tier);
			animation = pool[ThreadLocalRandom.current().nextInt(pool.length)];
			me.setAnimation(animation);
			me.setAnimationFrame(0);
		}
	}

	/** Restart the emote and graphic when they finish, until the reaction's time is up. */
	private void keepLooping(Player me, long now)
	{
		if (animation >= 0)
		{
			int playing = me.getAnimation();
			if (playing == -1 && me.getPoseAnimation() == me.getIdlePoseAnimation())
			{
				me.setAnimation(animation);
				me.setAnimationFrame(0);
			}
			else if (playing != animation)
			{
				// The player started doing something (walking, skilling): stop the emote, keep the rest.
				animation = -1;
			}
		}
		if (spotanim >= 0 && (!me.hasSpotAnim(spotanim) || now - spotanimAt >= SPOTANIM_REPEAT_MS))
		{
			me.createSpotAnim(SPOTANIM_KEY, spotanim, 0, 0);
			spotanimAt = now;
		}
	}

	private static boolean idle(Player me)
	{
		return me.getAnimation() == -1 && me.getPoseAnimation() == me.getIdlePoseAnimation();
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
