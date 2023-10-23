import lotus.domino.Session;

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
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;
import net.prominic.gja_v084.JavaServerAddinGenesis;

public class AgentsHelper extends JavaServerAddinGenesis {
	private String m_filePath = "agentshelper.nsf";
	private List<HashMap<String, Object>> m_events = null;
	
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

			updateCommands();
		} catch (Exception e) {
			logSevere(e);
			return false;
		}
		return true;
	}
	
	@Override
	protected void runNotesBeforeListen() {
		EventCommands eventCommands = new EventCommands("Commands", 1, true, this.m_logger);
		eventCommands.session = this.m_session;
		eventCommands.events = this.m_events;
		eventsAdd(eventCommands);
	}
	
	private void updateCommands() {
		try {
			Database database = m_session.getDatabase(null, m_filePath);
			if (database == null || !database.isOpen()) {
				logMessage("(!) LOAD FAILED - database not found: " + m_filePath);
				return;
			}

			m_events = new ArrayList<HashMap<String, Object>>();

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
					String runIf = (String) obj.get("runIf");		// optional

					HashMap<String, Object> event = new HashMap<String, Object>();
					event.put("command", command);
					event.put("interval", interval);
					event.put("runOnStart", runOnStart);
					event.put("runIf", runIf);
					event.put("lastRun", new Date());

					m_events.add(event);
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
	}

	protected void showHelp() {
		logMessage("*** Usage ***");
		logMessage("load runjava " + this.getJavaAddinName() + " <agentshelper.nsf>");
		logMessage("tell " + this.getJavaAddinName() + " <command>");
		logMessage("   quit             Unload addin");
		logMessage("   help             Show help information (or -h)");
		logMessage("   info             Show version");
		logMessage("   fire            	Fire all agents from config");
		logMessage("   update           Update config from <agentshelper.nsf>");

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
		logMessage("events       " + m_events.size());
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