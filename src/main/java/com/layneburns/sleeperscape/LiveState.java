package com.layneburns.sleeperscape;

import com.layneburns.sleeperscape.core.RedZoneAlert;
import com.layneburns.sleeperscape.core.View;
import java.util.Collections;
import java.util.List;
import javax.inject.Singleton;

/** What the overlays draw, written by the engine thread and read each frame. */
@Singleton
class LiveState
{
	volatile View view = View.EMPTY;
	volatile String status;
	volatile List<RedZoneAlert> redZones = Collections.emptyList();
}
