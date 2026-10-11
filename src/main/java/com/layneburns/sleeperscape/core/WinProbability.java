package com.layneburns.sleeperscape.core;

/**
 * A simple live win probability: each starter's remaining points are their projection times the share of
 * their game still to play, with variance growing with the same share. The difference of two sums of
 * independent players is treated as normal.
 */
public final class WinProbability
{
	/** Variance of a player's full-game points as a share of their projection squared (SD ~ 0.55 x proj). */
	static final double VARIANCE_RATIO = 0.3;

	private double mean;
	private double variance;

	public WinProbability(double myScore, double oppScore)
	{
		this.mean = myScore - oppScore;
	}

	/** Adds a starter: projected full-game points and the share of their game left. */
	public void add(boolean mine, double projected, double fractionLeft)
	{
		double remaining = projected * fractionLeft;
		mean += mine ? remaining : -remaining;
		variance += VARIANCE_RATIO * projected * projected * fractionLeft;
	}

	public double probability()
	{
		if (variance < 1e-9)
		{
			return mean > 0.005 ? 1 : mean < -0.005 ? 0 : 0.5;
		}
		double p = phi(mean / Math.sqrt(variance));
		return Math.max(0.001, Math.min(0.999, p));
	}

	/** Standard normal CDF (Abramowitz-Stegun 7.1.26 via erf). */
	static double phi(double z)
	{
		double x = Math.abs(z) / Math.sqrt(2);
		double t = 1 / (1 + 0.3275911 * x);
		double erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592)
			* t * Math.exp(-x * x);
		return z >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf);
	}
}
