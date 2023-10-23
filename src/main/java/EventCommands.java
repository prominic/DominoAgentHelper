import java.util.Date;
import java.util.HashMap;
import java.util.List;

import lotus.domino.NotesException;
import lotus.domino.Session;
import net.prominic.gja_v084.Event;
import net.prominic.gja_v084.GLogger;

public class EventCommands extends Event {
	public Session session = null;
	public List<HashMap<String, Object>> events = null;
	
	public EventCommands(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}
		
	@Override
	public void run() {
		Date now = new Date();

		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);

			Date lastRun = (Date) event.get("lastRun");
			Long interval = (Long) event.get("interval");

			long seconds = (now.getTime()-lastRun.getTime())/1000;
			if (seconds > interval) {
				eventFire(event);
			};
		}		
	}

	private void eventFire(HashMap<String, Object> event) {
		try {
			String command = (String) event.get("command");
			session.sendConsoleCommand("", command);
			event.put("lastRun", new Date());
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}
}
