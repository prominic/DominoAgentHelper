import java.util.Date;
import java.util.HashMap;
import java.util.List;

import lotus.domino.Agent;
import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;
import net.prominic.gja_v084.Event;
import net.prominic.gja_v084.GLogger;

public class EventAgents extends Event {
	public Session session = null;
	public List<HashMap<String, Object>> events = null;

	public EventAgents(String name, long seconds, boolean fireOnStart, GLogger logger) {
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
			if (seconds >= interval) {
				triggerAgent(event);	
			};
		}
	}

	public void triggerAgentOnStart() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);
			boolean runOnStart = (Boolean) event.get("runOnStart");
			if (runOnStart) {
				triggerAgent(event);
			}
		}
	}

	public void triggerFireForce() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);
			triggerAgent(event);
		}
	}

	private void triggerAgent(HashMap<String, Object> event) {
		boolean allow = true;
		Date date = new Date();

		try {
			String server = (String) event.get("server");
			String filePath = (String) event.get("filePath");
			Database database = session.getDatabase(server, filePath);

			if (database == null || !database.isOpen()) {
				allow = false;
				String err = String.format("%s !! %s not found", server, filePath);
				this.getLogger().severe(err);
				System.err.print(err);
			}

			String viewName = (String) event.get("view");
			if (!viewName.isEmpty() && allow) {
				View view = database.getView(viewName);
				if (view == null) {
					allow = false;
					String err = String.format("%s view not found in database %s", viewName, filePath);
					this.getLogger().severe(err);
					System.err.print(err);
				}
				else {
					allow = view.getAllEntries().getCount() > 0;
					view.recycle();
				}
			}

			if (allow) {
				String agentName = (String) event.get("agent");
				Agent agent = database.getAgent(agentName);
				if (agent == null) {
					allow = false;
					String err = String.format("%s agent not found in database %s", agentName, filePath);
					this.getLogger().severe(err);
					System.err.print(err);
				}
				else {
					agent.run();

					// apply delay if event was triggered
					Long delay = (Long) event.get("delay");
					long millisecondsToAdd = delay * 1000; // 100 seconds * 1000 milliseconds per second
					date = new Date(date.getTime() + millisecondsToAdd);
				}
			}

			database.recycle();
		} catch (NotesException e) {
			e.printStackTrace();
		}

		event.put("lastRun", date);
	}
}
