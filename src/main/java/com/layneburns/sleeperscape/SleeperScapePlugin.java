package com.layneburns.sleeperscape;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.layneburns.sleeperscape.core.Engine;
import com.layneburns.sleeperscape.core.LeagueContext;
import com.layneburns.sleeperscape.core.PlayEvent;
import com.layneburns.sleeperscape.core.Poller;
import com.layneburns.sleeperscape.core.RedZoneAlert;
import com.layneburns.sleeperscape.core.SideOfBall;
import com.layneburns.sleeperscape.core.View;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "SleeperScape",
	description = "Shows your Sleeper fantasy football plays, scores and red-zone alerts in game",
	tags = {"sleeper", "fantasy", "football", "nfl"},
	internalName = "sleeperscape"
)
public class SleeperScapePlugin extends Plugin
{
	// Changing any of these means rediscovering leagues.
	private static final Set<String> RESTART_KEYS = Set.of("username", "extraLeagueIds", "weekOverride", "useEspn");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SleeperScapeConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private LiveState state;

	@Inject
	private ScoreboardOverlay scoreboardOverlay;

	@Inject
	private BannerOverlay bannerOverlay;

	@Inject
	private RedZoneOverlay redZoneOverlay;

	@Inject
	private OverheadOverlay overheadOverlay;

	@Inject
	private Reactor reactor;

	private Settings settings;
	private SleeperScapePanel panel;
	private NavigationButton navButton;
	private Recorder recorder;
	private ScheduledExecutorService exec;
	private Poller poller;
	/** The frame the side panel's Starters tab last showed; -1 forces a redraw. */
	private volatile long shownFrameAt = -1;

	@Override
	protected void startUp() throws Exception
	{
		settings = new Settings(config, configManager);
		recorder = new Recorder(gson, () -> getPluginDirectory().join("recordings"), config::record);
		exec = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread t = new Thread(r, "sleeperscape");
			t.setDaemon(true);
			return t;
		});

		panel = new SleeperScapePanel(config, configManager, settings);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("SleeperScape")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(scoreboardOverlay);
		overlayManager.add(bannerOverlay);
		overlayManager.add(redZoneOverlay);
		overlayManager.add(overheadOverlay);

		startPolling();
		log.debug("SleeperScape started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		stopPolling();
		exec.shutdownNow();
		overlayManager.remove(scoreboardOverlay);
		overlayManager.remove(bannerOverlay);
		overlayManager.remove(redZoneOverlay);
		overlayManager.remove(overheadOverlay);
		clientToolbar.removeNavigation(navButton);
		reactor.clear();
		bannerOverlay.clear();
		recorder.close();
		state.view = View.EMPTY;
		state.status = null;
		state.redZones = List.of();
		log.debug("SleeperScape stopped");
	}

	@Provides
	SleeperScapeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SleeperScapeConfig.class);
	}

	private void startPolling()
	{
		Engine engine = new Engine(settings, new EngineEvents());
		poller = new Poller(new HttpFetcher(okHttpClient, recorder), gson, exec, engine, settings, new PlayersCache(),
			new PollerEvents());
		poller.start();
	}

	private void stopPolling()
	{
		if (poller != null)
		{
			poller.stop();
			poller = null;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SleeperScapeConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		SwingUtilities.invokeLater(panel::refreshConfig);
		if (event.getKey().startsWith("league."))
		{
			// A league switched on/off or renamed: redraw the starters with the next view.
			shownFrameAt = -1;
		}
		if (RESTART_KEYS.contains(event.getKey()))
		{
			// Rediscover leagues on the engine thread, after anything already queued there.
			exec.execute(() ->
			{
				stopPolling();
				state.view = View.EMPTY;
				state.redZones = List.of();
				SwingUtilities.invokeLater(() ->
				{
					panel.clearFeed();
					panel.setSideOfBall(List.of());
				});
				startPolling();
			});
		}
		if ("muted".equals(event.getKey()) && config.muted())
		{
			reactor.clear();
			bannerOverlay.clear();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		reactor.onClientTick();
	}

	private void chat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	/** Engine callbacks, on the engine thread. */
	private class EngineEvents implements Engine.Listener
	{
		@Override
		public void released(PlayEvent e)
		{
			SwingUtilities.invokeLater(() -> panel.addPlay(e));
			if (e.offline || config.muted())
			{
				return;
			}
			boolean big = e.significant() || e.isMatchupEvent();
			if (big && config.showBanner())
			{
				bannerOverlay.push(e);
			}
			SleeperScapeConfig.ChatMode mode = config.chatMode();
			if (mode == SleeperScapeConfig.ChatMode.ALL || (mode == SleeperScapeConfig.ChatMode.SIGNIFICANT && big))
			{
				String line = Text.chat(e);
				clientThread.invoke(() -> chat(line));
			}
			reactor.offer(e);
		}

		@Override
		public void updated(PlayEvent e)
		{
			SwingUtilities.invokeLater(() -> panel.updatePlay(e));
		}

		@Override
		public void sideOfBall(List<SideOfBall> games)
		{
			SwingUtilities.invokeLater(() -> panel.setSideOfBall(games));
		}

		@Override
		public void redZone(List<RedZoneAlert> active, List<RedZoneAlert> entered)
		{
			state.redZones = active;
			if (config.redZoneChat() && !config.muted())
			{
				for (RedZoneAlert a : entered)
				{
					String line = Text.redZoneChat(a);
					log.debug("red zone chat: {}", a.key);
					clientThread.invoke(() -> chat(line));
				}
			}
		}
	}

	/** Poller callbacks, on the engine thread. */
	private class PollerEvents implements Poller.Status
	{
		@Override
		public void status(String text)
		{
			state.status = text;
			SwingUtilities.invokeLater(() -> panel.setStatus(text));
		}

		@Override
		public void leaguesLoaded(List<LeagueContext> leagues)
		{
			Map<String, String> names = new HashMap<>();
			for (LeagueContext l : leagues)
			{
				names.put(l.id(), l.name());
			}
			settings.setLeagueNames(names);
			SwingUtilities.invokeLater(() -> panel.setLeagues(leagues));
		}

		@Override
		public void view(View view)
		{
			state.view = view;
			// The view is rebuilt every tick; the side panel only redraws when a new frame is shown.
			if (view.frameAt != shownFrameAt)
			{
				shownFrameAt = view.frameAt;
				SwingUtilities.invokeLater(() -> panel.setView(view));
			}
		}
	}

	/** The day-old Sleeper player list, in the plugin's data folder. */
	private class PlayersCache implements Poller.PlayersCache
	{
		@Override
		public String read(long maxAgeMs)
		{
			try
			{
				Filepath f = getPluginDirectory().join("players.json");
				if (!f.isFile() || System.currentTimeMillis() - f.getLastModifiedTime().toMillis() > maxAgeMs)
				{
					return null;
				}
				try (BufferedReader r = f.openBufferedReader())
				{
					StringBuilder sb = new StringBuilder();
					char[] buf = new char[1 << 16];
					for (int n; (n = r.read(buf)) > 0; )
					{
						sb.append(buf, 0, n);
					}
					return sb.toString();
				}
			}
			catch (IOException e)
			{
				log.debug("player cache unreadable", e);
				return null;
			}
		}

		@Override
		public void write(String json)
		{
			try
			{
				Filepath dir = getPluginDirectory();
				Filepath tmp = dir.join("players.json.tmp");
				tmp.write(json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
				tmp.moveTo(dir.join("players.json"), StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException e)
			{
				log.debug("player cache not written", e);
			}
		}
	}
}
