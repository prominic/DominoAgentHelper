import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import lotus.domino.Database;
import lotus.domino.Document;
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
		triggerFire();
	}

	public void triggerOnStart() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);
			boolean runOnStart = (Boolean) event.get("runOnStart");
			if (runOnStart) {
				triggerEvent(event);
			}
		}
	}

	public void triggerFireForce() {
		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);
			triggerEvent(event);
		}
	}
	
	private void triggerFire() {
		Date now = new Date();

		for (int i = 0; i < events.size(); i++) {
			HashMap<String, Object> event = events.get(i);

			Date lastRun = (Date) event.get("lastRun");
			Long interval = (Long) event.get("interval");

			long seconds = (now.getTime()-lastRun.getTime())/1000;
			if (seconds > interval) {
				System.out.println(event.get("name"));
				boolean allow = true;
				String runIfFormula = (String) event.get("runIfFormula");
				String runIfDatabase = (String) event.get("runIfDatabase");
				if (!runIfFormula.isEmpty() && !runIfDatabase.isEmpty()) {
					try {
						Database database = session.getDatabase(null, runIfDatabase);
						Document doc = database.createDocument();
						@SuppressWarnings("unchecked")
						Vector<Double> res = session.evaluate(runIfFormula, doc);
						allow = res.get(0) == 1;
						doc.recycle();
						database.recycle();
					} catch (NotesException e) {
						this.getLogger().severe(e);
					}
				}

				if (allow) {
					triggerEvent(event);	
				}
				else {
					event.put("lastRun", new Date());	
				}
			};
		}
	}

	private void triggerEvent(HashMap<String, Object> event) {
		try {
			String command = (String) event.get("command");
			session.sendConsoleCommand("", command);
			event.put("lastRun", new Date());
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}
}
