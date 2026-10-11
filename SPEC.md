# SleeperScape — Specification v0.1

A personal (never Plugin Hub) RuneLite plugin. `AGENTS.md` (from the RuneLite template) applies in full; if something here appears to break it, stop and ask.

## 0. Context

A personal (never Plugin Hub) RuneLite plugin that shows what happens in your Sleeper fantasy matchups while you play: every play that moves the score, the current scores, a win %, and live red-zone alerts. It is game-day only (off during the week), not a roster manager (no bench-points or lineup checks). It runs in regular play inside the combined AnkiScape jar, next to AnkiScape and Port Tasks. Goal: a first working version running before the end of today's (2026-10-11) morning slate that also **records today's raw data**, so later phases can be built and tested offline against real games.

User decisions so far:
- **Leagues:** 3, all head-to-head, no median. One redraft, two dynasty; one league is IDP.
- **Delay:** one global delay that covers everything except the red-zone alert.
- **Plays:** any point change is a play and goes to the full feed. "Significant" plays are a categorised subset (below).
- **Detail:** all three levels: points, stat line, ESPN play text.
- **Display:**
  - Significant plays get a pop-up banner, a chat line, overhead text, and avatar emotes/graphics in 6 tiers.
  - No Windows notifications, no sound.
- **Win %:** our own estimate is fine.
- **Red zone:** mine and the opponent's players. Mine in **pink**, the opponent's in **red**. Pink is the user's "top quality" colour across their plugins; red is bad.
- Keeps running at the login screen and through world hops.

## Data sources (all read-only, no auth)

| Source | Use | Cadence |
|---|---|---|
| Sleeper `v1/state/nfl` | season, week | start + hourly |
| Sleeper `v1/user/<username>` → `v1/user/<id>/leagues/nfl/<season>` | league discovery | start |
| Sleeper `v1/league/<id>` | name, **`scoring_settings` (auto-loaded, no manual entry)**, roster positions | start |
| Sleeper `v1/league/<id>/rosters`, `/users` | my roster_id (incl. co-owner), team names | start + every 10 min |
| Sleeper `v1/league/<id>/matchups/<week>` | **Level 1**: authoritative per-league `players_points`, `starters`, `points`, `matchup_id` | 10 s while games live, 5 min otherwise |
| Sleeper `v1/players/nfl` (~5 MB) | names, positions, team, `espn_id` | cached on disk, refreshed daily |
| Sleeper `api.sleeper.com/stats/nfl/<season>/<week>?season_type=` (unofficial; ~1.3 MB array). Fallbacks: the same on `api.sleeper.app`, then `v1/stats/nfl/<type>/<season>/<week>` (~270 KB, but it lagged the live scores on 2026-10-11 and left most plays without a stat line) | **Level 2**: per-player stat lines incl. IDP | 10 s while live |
| Sleeper `projections/nfl/<season>/<week>` (unofficial) | win % | every 10 min |
| ESPN `site.api.espn.com/.../nfl/scoreboard` (unofficial) | game status/clock, possession, `isRedZone` | 10 s while live |
| ESPN game summary / core plays (unofficial) | **Level 3**: play text, play yards, participants | on demand, for games with a fresh delta |

- Every endpoint marked unofficial degrades gracefully: level 1 alone must always work.
- Per AGENTS.md, ESPN use sits behind one opt-in toggle `useEspn` (default off, with the standard IP warning). It also gates red zone, game clock, players-left and win %.

## Architecture

```
DataSource (HttpSource | ReplaySource) ──► Recorder (raw responses → disk)
      │ raw JSON + observedAt
      ▼
Engine (single-thread executor)
  Snapshot timeline ─► DeltaDetector ─► Classifier (stat diff + scoring_settings + ESPN match)
                                           └► PlayEvent { nfl player, category, tier per league, impacts[] }
  Presentation clock = now − delay ──► releases events/snapshots ──► Surfaces
  RedZoneTracker (live, bypasses delay) ─────────────────────────────► RedZone overlay/chat
Surfaces: Scoreboard overlay · Banner overlay · Side panel feed · Chat · Overhead text · Avatar reactions
```

- **Packages:**
  - `com.layneburns.sleeperscape.core`: pure Java plus Gson, no RuneLite imports. Unit-testable from recorded fixtures.
  - `com.layneburns.sleeperscape`: plugin, config, overlays, panel, avatar.
- **Threads:** HTTP goes through the injected `OkHttpClient` (`enqueue`). The engine runs on its own single-thread executor. Client mutations (chat, overhead text, animations) go through `clientThread.invoke`; the panel updates on the EDT. No `Thread.sleep`; scheduled futures are cancelled on `shutDown`.
- **Files:** `internalName = "sleeperscape"`, all I/O via `getPluginDirectory().join(...)` / `Filepath`, the same pattern as `ankiscape/src/main/java/com/layneburns/ankiscape/RenderStore.java`. Holds the players cache and `recordings/`.

## Core behaviour

1. **Identity:** config `username` → leagues for the current season. Manual `extraLeagueIds` for leagues not tied to the username. Per-league settings live in the side panel and are stored as dynamic config keys (`league.<id>.enabled`, `.alias`):
   - on/off
   - short alias, e.g. `RD`, `DYN1`, `DYN2`
2. **Baseline:** the first successful poll after start, or after a gap of more than 2 min, sets the baseline silently. Deltas found across a gap go to the feed only, tagged "while offline", with no banner or reactions.
3. **Deltas:**
   - One NFL stat change becomes one `PlayEvent`, with per-league `impacts[]`: league, owner (me / opponent / other), starter flag, and points delta from Level 1.
   - The same player can be mine in one league and the opponent's in another; that is still one event that lists each league.
   - DEF and IDP are handled like any other player.
4. **Filters:**
   - `startersOnly` (default on).
   - `includeOpponent` (default on).
   - Impacts owned by "other" teams are always dropped.
   - An event is shown if any of its impacts passes the filters.
5. **Delay:**
   - Every poll result is stamped `observedAt`. Surfaces only see data with `observedAt ≤ now − delaySeconds`: scoreboard, feed, banner, chat, reactions, win % and the game clock it uses.
   - The red zone is the one exception.
   - Raising the delay freezes the display until it catches up. Lowering it releases a burst, which the banner coalesces into one summary.
   - A gain reversed inside the delay window (e.g. a TD overturned on review) cancels out and is never shown.
6. **Stat attribution:** the stat feed and the scores don't always land in the same poll. A play's breakdown
   uses the earliest stat snapshot (since that player's last released play) whose change is worth exactly
   the play's points in the primary league. If none does yet, the play waits up to 15 s from when it was seen
   (`DETAIL_GRACE_MS`, even with a shorter delay), then shows the closest breakdown with an `other ±x` line.
7. **Corrections:** a point change with no matching stat change is a `CORRECTION`. A reversed TD that was already shown becomes "TD overturned", which is significant.

## Play taxonomy (Level 2 stat keys, verified against recorded data in Phase 2)

Each category has a sign and a significance. The tier is from **my** point of view: an opponent-owned event is mirrored (opponent's TD → SCORED_ON; opponent QB's INT → VERY_GOOD for me).

| Group | Category (stat keys) | Significant | Tier when mine |
|---|---|---|---|
| Offense | Pass/rush/rec TD (`pass_td`,`rush_td`,`rec_td`) | ✔ | SCORED |
| | 2-pt conversion (`*_2pt`) | ✔ | SCORED |
| | Return TD (`kr_td`,`pr_td`,`st_td`, fumble-recovery TD) | ✔ | SCORED |
| | Big play ≥ `bigPlayYards` (default 20) rush/rec/pass: a single carry/catch/completion that long, a distance bucket (`rec_20_29`…), or a new longest gain (`rush_lng`/`rec_lng`/`pass_lng` grew to ≥ the threshold, which also works when two plays merge into one poll) | ✔ | VERY_GOOD |
| | Milestone bonus: a `bonus_<stat>_<n>[p]` flag (e.g. `bonus_rush_rec_yd_100`, `bonus_tkl_10p`, `bonus_pass_cmp_25`) **that the league scores**, headlined by name (`100 SCRIMMAGE YD`, `10+ TACKLES`). `bonus_fd_*` / `bonus_rec_*` are per-play counters, never milestones | ✔ | VERY_GOOD |
| | INT thrown (`pass_int`), pick-six (`pass_int_td`), fumble lost (`fum_lost`) | ✔ | VERY_BAD |
| | Fumble not lost (`fum`), sacked (`pass_sack`), incompletion penalty | – | BAD |
| | Yards, receptions, carries, first downs, completions | – | GOOD |
| Kicker | FG made (`fgm_*` by distance) | ✔ | SCORED |
| | FG missed (`fgmiss_*`) | ✔ | BAD |
| | XP made / missed (`xpm` / `xpmiss`) | – | GOOD / BAD |
| Team DEF | DEF/ST TD (`def_td`,`def_st_td`, return TDs) | ✔ | SCORED |
| | INT, fumble recovery, safety, blocked kick, sack (`int`,`fum_rec`,`safe`,`blk_kick`,`sack`) | ✔ | VERY_GOOD |
| | Points-allowed tier change (`pts_allow_*`) | ✔ | BAD |
| | Yards-allowed tier change | – | BAD/GOOD |
| IDP | Defensive TD (`idp_def_td`, INT/fumble return TD) | ✔ | SCORED |
| | Sack, INT, forced fumble, fumble recovery, safety, blocked kick (`idp_sack`,`idp_int`,`idp_ff`,`idp_fum_rec`,`idp_safe`,`idp_blk_kick`) | ✔ | VERY_GOOD |
| | Tackle solo/assist, TFL, QB hit, pass defended (`idp_tkl_*`,`idp_qb_hit`,`idp_pass_def`) | – | GOOD |
| Special teams | Takeaway on a kick or punt (`def_st_ff`,`def_st_fum_rec` for team DEF; `st_ff`,`st_fum_rec` for players) | ✔ | VERY_GOOD |
| Matchup | Lead change (I take the lead / lose it) | ✔ | VERY_GOOD / VERY_BAD |
| | Projected lead change (the side projected to win flips; needs 52 %/48 % to flip back, so a coin-flip matchup doesn't spam) | ✔ | VERY_GOOD / VERY_BAD |
| | Matchup final (win / loss) | ✔ | SCORED / SCORED_ON |
| Other | Correction (no stat explanation); "TD overturned" | TD only | by sign |
| | **BIG SWING**: ±4 points or more with no stat line to explain it (the stat feed lagging), so a likely TD is never shown as routine | ✔ | VERY_GOOD / VERY_BAD |

- A merged poll (several stats in one delta) keeps **every** category. Its components are listed in descending order of points, e.g. `Hurts: rush TD +6.0 · 12 rush yd +1.2`. The event is significant if any component is, and it takes the tier of its strongest component. Stats the league doesn't score add no points, so they are left out of the list.
- **Text:**
  - Level 3: ESPN text, e.g. `Hurts 12-yd TD run`.
  - Level 2: stat summary, e.g. `Hurts +1 rush TD, +12 rush yd`.
  - Level 1: `Hurts +7.2`.
- **Waiting for detail:** an event waits for Level 3 text up to its release time (observedAt + delay). Without it, it is released with Level 2 text, and the panel entry is updated if the text arrives later.

## Surfaces

**Colours:** mine = pink (`#FF69B4`-ish), opponent = red. Points are shown signed.

- **Side panel**:
  - League list: toggle and alias.
  - Delay: preset buttons **30 s / 60 s / 90 s / 120 s** that snap to that value, plus ±5 s buttons to fine-tune.
  - Mute toggle, which pauses banner, chat, overhead text and reactions; the feed continues.
  - **My side of the ball** (live, like the red zone; side panel only), **below the feed** (the feed matters
    more) and capped at ~220 px, scrolling beyond: one card per live game where my starters are on the field
    (offense while their team has the ball, IDP/DEF while it doesn't). Card header: the matchup left, `PHI ball`
    right (or `PHI · RED ZONE` in red, with a red left edge on the card). Then one row per player: name (pink,
    `DEF` prefix for defenders) left, leagues right. The last team seen with the ball is kept between snaps
    (ESPN omits it); cleared at halftime.
  - Full feed of *every* delta (filters applied), newest first. Each entry: time, headline (significant only) and player; then each league's side as `[ALIAS ±points] Owner` (`You` for mine, else the opponent's Sleeper name); then the breakdown.
  - Row background, for every play: **green** when it only helps me, **red** when it only hurts me, **orange** when it does both (my player in one league, my opponent's in another). Significant plays get a stronger tint.
  - Feed colours (also chat and banner): routine plays neutral; significant plays **green when good for me, red when bad for me** (so my opponent's QB throwing a pick is green), and **pink for my scoring plays** (TD, 2-pt, FG). Each `[league ±pts]` tag is coloured by how the play went for me in that league; the headline and name by how it went overall.
  - Mixed plays (the same player is mine in one league and my opponent's in another): the stronger side wins; when both are equally strong, my net fantasy points across those leagues decide (`Tier.combine`), and an exact wash is neutral (and a shrug).
- **Scoreboard overlay** (delayed), compact small-font table, per enabled league:
  - Header: the league's **full name** (muted), starters left (`3v2 left`) on the right when known.
  - Then me and my opponent, by Sleeper username: `name | score | projected final | win %` (projection
    dimmed; projected final = score + what starters still project to add; both it and win % need ESPN and
    projections).
  - Colours follow the **win probability**: above 50 % my name and score are green (theirs white); below 50 %
    their name and score are red (mine white); exactly 50 % both white. Without a win probability, whoever
    leads on points takes the colour.
  - Usernames only, no team names (changed 2026-10-11: the team-name version was too large).
- **Banner overlay** (significant plays): fades after `bannerSeconds` (5). Shows the play and each league's impact. Coalesces bursts.
- **Chat**: game message for significant plays (`chatMode`: off / significant / all). Format: `[RD] Hurts 12-yd TD run +7.2`.
  - Red zone (live, once per trip, `redZoneChat`): `[RED ZONE] CIN inside the 20 (CIN @ MIA): Chase Brown (UND, KCL)`, mine pink, my opponent's red.
- **Overhead text** with every reaction (and every significant play) over the local player, for the reaction's length (6 s): the play, then the leagues it helps and hurts me in, e.g. `TOUCHDOWN Hurts! Helps RD, hurts DYN1`, `INTERCEPTION Winston... Helps UND, hurts KCL`, `Kelce 8 rec yd... Hurts DYN1`, `Took the lead in RD!`.
  - Drawn by an overlay (`OverheadOverlay`) in the bold game font with a black shadow, because RuneLite
    exposes no way to set real overhead text's colour or effect (and reflection is forbidden).
  - Chat-effect rules: routine plays **yellow**; significant plays coloured (**green** good, **red** bad,
    **orange** both), with "Helps …" green and "hurts …" red; my scoring plays and a matchup win **wave2 +
    glow** (pink/white/green, a smooth shimmer); scored on or a matchup loss **shake in steady red**.
  - **No flashing anywhere in the plugin** (user rule, 2026-10-11): nothing blinks on and off as a
    notification.
- **Avatar reactions**, by tier:

  | Tier | Reaction |
  |---|---|
  | GOOD | mild positive emote: yes, thumbs-up, clap |
  | VERY_GOOD | excited emote: cheer, dance, jig, jump-for-joy, headbang |
  | SCORED | VERY_GOOD emote + level-up fireworks spot-anim |
  | BAD | mild negative emote: no, slap-head |
  | VERY_BAD | strong negative emote: cry, angry, panic, stomp |
  | SCORED_ON | VERY_BAD emote + storm/rain-cloud spot-anim |

  - **Implementation:** client-side only (`setAnimation` / `createSpotAnim` on the local player, nothing sent to the server). A random pick from each tier's pool. IDs are taken from `gameval` `AnimationID` / `SpotanimID`; exact names and the cloud graphic are chosen in Phase 3.
  - **Config:** `reactionTiers` (which tiers react; default all) and `reactOnlyWhenIdle` (default on).
  - **Looping:** each reaction lasts `reactionSeconds` (default **6 s**): the emote restarts whenever it finishes and the fireworks/lightning are re-created, until the time is up; the overhead text stays up just as long. If I start walking or skilling, the emote stops (the graphic and text carry on).
  - **Queueing:** one reaction at a time. A waiting play stronger than the one playing interrupts it at once; otherwise the strongest waiting play starts after the current one ends plus `reactionCooldown` (default 1 s). Equal strong good and bad → shrug.
  - Scored-on graphic: `SpotanimID.SARADOMIN_LIGHTNING` (no rain-cloud spot-anim suited it). Fireworks: `LEVELUP_ANIM`, `LEVELUP_99_ANIM` for a matchup win.
- **Red-zone overlay** (live, no delay, ESPN):
  - While a team with one of my or my opponent's starters (QB/RB/WR/TE/K) has the ball inside the 20, show a box such as `RED ZONE · PHI — Hurts, Brown [RD, DYN1]`. Mine in pink, the opponent's in red.
  - Players and team only: no down, distance, drive or how they got there.
  - It clears when possession changes or the ball leaves the red zone.
  - ESPN quirks (seen live 2026-10-11): `situation` exists only while a game is in progress; `possession`
    (an ESPN team id) is sent only while a snap is pending, and is missing at halftime, at dead balls and right
    after a score, while `isRedZone` keeps its last value. So a trip **starts** only with `isRedZone` and
    `possession` both present. Without possession the alert is held (no re-announce) for up to 90 s, but it
    clears at once on halftime or when the game's score changes. A team's trip is announced in chat once;
    the same team isn't announced again within 3 min.
  - One chat line on entry (toggle).

## Win % (Phase 5)

For each starter:
- remaining_i = projected points (Sleeper projections scored with the league's `scoring_settings`) × fraction of their game left (from the ESPN clock; 1 before kickoff, 0 when final).
- Mean μ = (my score + Σ my remaining) − (opponent's score + Σ opponent's remaining).
- Variance σ² = Σ v · proj_i² · frac_i, with v ≈ 0.3 as a tunable constant.
- Win % = Φ(μ/σ).

Computed from the delayed snapshot. It won't match the Sleeper app's number.

## Config (RuneLite config panel; per-league settings in the side panel)

| Section | Settings |
|---|---|
| Account | `username`, `extraLeagueIds` |
| Display | `delaySeconds` (0), `startersOnly`, `includeOpponent`, `chatMode`, `bannerSeconds`, `overheadText`, `reactionTiers`, `reactOnlyWhenIdle`, `bigPlayYards` (20), `showScoreboard`, `showWinPct` |
| Red zone | `redZoneOverlay`, `redZoneChat` |
| Data | `useEspn` (opt-in, warning), `pollSeconds` (10) |
| Developer (closed) | `record` (default **on** for now), `replayFile`, `replaySpeed` |

## Build & repos

- **SleeperScape repo** (`/home/user/SleeperScape`, branch `claude/sweet-ride-v0q9ki`):
  - Gradle project mirroring `ankiscape/build.gradle`: Java 11, `latest.release` RuneLite, Lombok, JUnit 4, `run` task.
  - `group = 'com.layneburns'`, `rootProject.name = 'sleeperscape'`.
  - `SleeperScapePluginTest` loads only SleeperScape, so `./gradlew run` gives a fresh dev client. It uses the main RuneLite profile (with its Plugin Hub plugins); `gradlew run -Pprofile=sleeperscape-dev` uses a separate profile, created on first use.
  - `.gitattributes` keeps `gradlew` LF and `*.bat` CRLF, so both work after a Windows checkout.
  - Also add `SPEC.md` (this plan, AnkiScape-style), `AGENTS.md` (copied from ankiscape), `CLAUDE.md` (`@AGENTS.md @SPEC.md`) and a BSD-2 `LICENSE`.
- **ankiscape integration** (separate commit in `layne-burns/ankiscape`):
  - `settings.gradle` adds `includeBuild('../SleeperScape')`.
  - `build.gradle` adds `testImplementation 'com.layneburns:sleeperscape'`.
  - `AnkiScapePluginTest` adds `SleeperScapePlugin.class` to `loadBuiltin`.
  - `scripts/jarinfo.ps1` gets a third fingerprint (`sleeperScape`) in `Get-SourceFingerprints` and the stamp check.
- **This container** can't reach `repo.runelite.net`, `api.sleeper.app`/`api.sleeper.com` or ESPN, so I can't compile the plugin or hit live data here. The user should add those hosts (plus `site.api.espn.com`, `sports.core.api.espn.com`) under Network access in the environment settings. Otherwise each build is verified only on the user's Windows machine.

## Phases (each ends with the user confirming in the client, per AGENTS.md)

| Phase | When | What |
|---|---|---|
| 0 | now | User allowlists the hosts above (optional but strongly recommended). |
| 1 — MVP | today, ASAP | Scaffold, Sleeper discovery, matchups polling, Level 1 deltas, filters, delay timeline, scoreboard overlay, side panel (leagues + feed), chat line. **The recorder captures a superset**: matchups, stats, projections, ESPN scoreboard + summaries, so today's games feed Phases 2–5. Then the ankiscape integration commit. |
| 2 | | Stat engine (scoring_settings × stat diffs), full taxonomy incl. IDP, significance, banner, overhead text; fixture tests from today's recordings. |
| 3 | | Avatar reactions: emote/spot-anim pools picked from gameval, throttling. |
| 4 | | ESPN: live red zone, game clock, players-left, Level 3 play text matching via `espn_id` (participants first, name fallback). |
| 5 | | Win %. |
| 6 | | Replay mode + regression tests over recorded Sundays. |

## Verification

- **Unit tests** (`./gradlew test`) on `core` with recorded fixtures:
  - delta detection across multiple leagues
  - baseline and gap handling
  - delay release ordering and reversal cancellation
  - classifier categories for each table row (offense, K, DEF, IDP), perspective mirroring
  - win % edge cases (0 and 1 when final)
- **In game** (user, via `./gradlew run`, then the AnkiScape shortcut after integration):
  - leagues discovered and the toggles persist
  - scores match the Sleeper app (after the delay)
  - a TD gives banner + chat + overhead + fireworks
  - an opponent TD gives the red/cloud reaction
  - red zone appears instantly while plays are still delayed
  - everything survives a world hop and the login screen
  - recordings appear in `.runelite/plugin-data/sleeperscape/recordings/`

## First live use (2026-10-11)

- Local build against RuneLite 1.13.1 compiled with no fixes; core tests pass.
- Confirmed live: `v1/state/nfl`; all three stats URLs; projections (`api.sleeper.com`, 5.7 MB array); the ESPN scoreboard.
- `*_lng` stat keys exist (e.g. `fgm_lng`).

- Scores in messages are joined with ` - `: the game font has no en dash (`85.0–84.6` showed as `85.084.6`).
- Icon: hand-placed 16×16 pixel-art pink football (pointed tips, four stitches), since sidebar icons are drawn at 16 px.

- Stats freshness (measured live): `api.sleeper.com/stats` (A) leads; `v1/stats` (B) lags 0–40 s (median 20 s), changes in ~30 s batches and skips intermediate versions. A is primary. One play can land as two A updates ~4 s apart (e.g. `rec_5_9` first, then `rec`/`rec_yd`); the explaining-snapshot rule handles that.

## Open items (resolved during phases)

- Exact Sleeper stat key names and whether `*_lng` exists; stats payload size; ESPN play participant IDs.
- gameval names for the emote pools, fireworks and a storm/rain-cloud spot-anim.
- Win % variance constant v; tune against recorded Sundays.
