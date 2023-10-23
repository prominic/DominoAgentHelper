import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.View;
import lotus.domino.Document;
import lotus.domino.NotesException;
import net.prominic.gja_v084.JavaServerAddinGenesis;

public class AgentsHelper extends JavaServerAddinGenesis {
	private String m_filePath = "agentshelper.nsf";
	EventCommands m_event = null;
	
	public AgentsHelper(String[] args) {
		super();
		m_filePath = args[0];
	}

	public AgentsHelper() {
		super();
	}

	@Override
	protected String getJavaAddinVersion() {
		return "1.0.2";
	}
	
	@Override
	protected String getJavaAddinDate() {
		return "2023-10-23 15:05";
	}
	
	protected boolean runNotesAfterInitialize() {
		try {
			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logSevere("(!) LOAD FAILED - database not found: " + m_filePath);
				return false;
			}
			
			m_event = new EventCommands("Commands", 1, true, this.m_logger);
			m_event.session = this.m_session;
			m_event.events = getCommands();
			eventsAdd(m_event);
		} catch (Exception e) {
			logSevere(e);
			return false;
		}
		return true;
	}
	
	@Override
	protected void runNotesBeforeListen() {
		m_event.triggerOnStart();
	}
	
	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = super.resolveMessageQueueState(cmd);
		if (flag)
			return true;

		if (cmd.startsWith("update")) {
			m_event.events = getCommands();
			logMessage("update - completed");
		} else if (cmd.startsWith("trigger")) {
			m_event.triggerFireForce();
			logMessage("trigger - completed");
		} else {
			logMessage("invalid command (use -h or help to get details)");
		}

		return true;
	}
	
	private List<HashMap<String, Object>> getCommands() {
		List<HashMap<String, Object>> list = null;
		
		try {
			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logMessage("(!) LOAD FAILED - database not found: " + m_filePath);
				return null;
			}

			list = new ArrayList<HashMap<String, Object>>();

			View view = database.getView("Commands");
			Document doc = view.getFirstDocument();
			while (doc != null) {
				Document docNext = view.getNextDocument(doc);

				// start new thread for each agent
				String name = doc.getItemValueString("Name");
				String json = doc.getItemValueString("JSON");

				JSONObject obj = getJSONObject(json);
				if (obj != null) {
					String command = (String) obj.get("command");	// required
					Long interval = (Long) obj.get("interval");		// required
					boolean runOnStart = obj.containsKey("runOnStart") && (Boolean) obj.get("runOnStart");	// optional
					String runIfFormula = (String) obj.get("runIfFormula");		// optional
					String runIfDatabase = (String) obj.get("runIfDatabase");	// optional
					
					HashMap<String, Object> event = new HashMap<String, Object>();
					event.put("name", name);
					event.put("command", command);
					event.put("interval", interval);
					event.put("runOnStart", runOnStart);
					event.put("runIfFormula", runIfFormula);
					event.put("runIfDatabase", runIfDatabase);
					event.put("lastRun", new Date());

					list.add(event);
				}
				else {
					logMessage(name + ": invalid json");
				}

				recycle(doc);
				doc = docNext;
			}

			recycle(view);
			recycle(database);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return list;
	}

	protected void showHelp() {
		logMessage("*** Usage ***");
		logMessage("load runjava " + this.getJavaAddinName() + " <agentshelper.nsf>");
		logMessage("tell " + this.getJavaAddinName() + " <command>");
		logMessage("   quit             Unload addin");
		logMessage("   help             Show help information (or -h)");
		logMessage("   info             Show version");
		logMessage("   trigger          Fire all agents from " + m_filePath);
		logMessage("   update           Update config from " + m_filePath);

		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("Copyright (C) Prominic.NET, Inc. 2023" + (year > 2023 ? " - " + Integer.toString(year) : ""));
		logMessage("See https://prominic.net for more details.");
	}

	/**
	 * JSONObject
	 */
	private JSONObject getJSONObject(String json) {
		JSONParser parser = new JSONParser();
		try {
			return (JSONObject) parser.parse(json);
		} catch (ParseException e) {
			return null;
		}
	}

	/**
	 * Display run configuration
	 */
	protected void showInfoExt() {
		logMessage("config       " + m_filePath);
		logMessage("events       " + m_event.events.size());
	}

	/**
	 * Recycle Domino objects.
	 */
	private static void recycle(Base object) throws NotesException {
		if (object == null)
			return;
		object.recycle();
		object = null;
	}

}