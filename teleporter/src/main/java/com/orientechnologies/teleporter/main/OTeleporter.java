/*
 * Copyright 2015 Orient Technologies LTD (info--at--orientechnologies.com)
 * All Rights Reserved. Commercial License.
 * 
 * NOTICE:  All information contained herein is, and remains the property of
 * Orient Technologies LTD and its suppliers, if any.  The intellectual and
 * technical concepts contained herein are proprietary to
 * Orient Technologies LTD and its suppliers and may be covered by United
 * Kingdom and Foreign Patents, patents in process, and are protected by trade
 * secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Orient Technologies LTD.
 * 
 * For more information: http://www.orientechnologies.com
 */

package com.orientechnologies.teleporter.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;
import com.orientechnologies.orient.server.plugin.OServerPluginAbstract;
import com.orientechnologies.teleporter.context.OOutputStreamManager;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.factory.OStrategyFactory;
import com.orientechnologies.teleporter.strategy.OImportStrategy;
import com.orientechnologies.teleporter.ui.OProgressMonitor;

/**
 * Main Class from which the importing process starts.
 * 
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 * 
 */

public class OTeleporter extends OServerPluginAbstract {

	private static OOutputStreamManager outputManager;

	private static final OStrategyFactory FACTORY = new OStrategyFactory();

	private static final String teleport =  "OrientDB                  \n" +
			" ______________________________________________________________________________ \n" +
			" ___  __/__  ____/__  /___  ____/__  __ \\_  __ \\__  __\\__  __/__  ____/__  _ _ \\  \n" +
			" __  /  __  __/  __  / __  __/  __  /_/ /  / / /_  /_/ /_  /  __  __/  __  /_/ /\n" +
			" _  /   _  /___  _  /___  /___  _  ____// /_/ /_  _, _/_  /   _  /___  _  _, _/ \n" +
			" /_/    /_____/  /_____/_____/  /_/     \\____/ /_/ |_| /_/    /_____/  /_/ |_|  \n" +
			"\n" +
			"                                                  http://orientdb.com/teleporter";	


	public static void main(String[] args) {


		// Output Manager setting
		outputManager = new OOutputStreamManager(2);
		outputManager.info("\n\n" + teleport + "\n");


		/*
		 * Input args validation
		 */

		// Missing argument validation

		if(args.length < 10) {
			outputManager.error("Syntax error, missing argument. Use:\n ./oteleporter.sh -jdriver <jdbc-driver> -jurl <jdbc-url> -juser <username> -jpasswd <password> -ourl <orientdb-url>.");
			System.exit(0);
		}


		// Filling the argument map
		Map<String,String> arguments = new HashMap<String,String>();

		int i = 0;
		while(i<args.length) {
			arguments.put(args[i], args[i+1]);
			i += 2;
		}

		// Mandatory args validation

		if(!arguments.containsKey("-jdriver")) {
			outputManager.error("Argument -jdriver is mandatory, please try again with expected argument: -jdriver <your-db-driver-name>\n");
			System.exit(0);
		}

		if(!arguments.containsKey("-jurl")) {
			outputManager.error("Argument -jurl is mandatory, please try again with expected argument: -jurl <input-db-jdbc-URL>\n");
			System.exit(0);
		}

		if(!arguments.containsKey("-juser")) {
			outputManager.error("Argument -juser is mandatory, please try again with expected argument: -juser <your-db-username>\n");
			System.exit(0);
		}

		if(!arguments.containsKey("-jpasswd")) {
			outputManager.error("Argument -jpasswd is mandatory, please try again with expected argument: -jpasswd <your-db-password>\n");
			System.exit(0);
		}

		if(!arguments.containsKey("-ourl")) {
			outputManager.error("Argument -ourl is mandatory, please try again with expected argument: -ourl <output-orientdb-desired-URL>\n");
			System.exit(0);
		}


		// simple syntax check on command

		if(!arguments.get("-jdriver").equalsIgnoreCase("Oracle") && !arguments.get("-jdriver").equalsIgnoreCase("SQLServer") && !arguments.get("-jdriver").equalsIgnoreCase("MySQL") 
				&& !arguments.get("-jdriver").equalsIgnoreCase("PostgreSQL") && !arguments.get("-jdriver").equalsIgnoreCase("HyperSQL")) {
			outputManager.error("Not valid db-driver name. Type one of the following driver names: 'Oracle','SQLServer','MySQL','PostgreSQL','HyperSQL'\n");
			System.exit(0);
		}

		if(!arguments.get("-jurl").contains("jdbc:")) {
			outputManager.error("Not valid db-url.\n");
			System.exit(0);
		}

		if(! (arguments.get("-ourl").contains("plocal:") | arguments.get("-ourl").contains("remote:") | arguments.get("-ourl").contains("memory:")) ) {
			outputManager.error("Not valid output orient db uri.\n");
			System.exit(0);
		}

		if(arguments.get("-s") != null) {
			if(! (arguments.get("-s").equals("naive") | arguments.get("-s").equals("naive-aggregate")) ) {
				outputManager.error("Not valid strategy.\n");
				System.exit(0);
			}
		}

		if(arguments.get("-v") != null) {
			if(! (arguments.get("-v").equals("0") | arguments.get("-v").equals("1") | arguments.get("-v").equals("2") | arguments.get("-v").equals("3")) ) {
				outputManager.error("Not valid output level. Available levels:\n0 - No messages\n1 - Debug\n2 - Info\n3 - Warning \n4 - Error");
				System.exit(0);
			}
		}

		if(arguments.get("-inheritance") != null) {
			if(! (arguments.get("-inheritance").contains("hibernate:") ) ) {
				outputManager.error("Not valid inheritance argument. Syntax: -inheritance hibernate:<xml-path>");
				System.exit(0);
			}
		}

		if(arguments.get("-include") != null && arguments.get("-exclude") != null) {
			outputManager.error("It's not possible to use both 'include' and 'exclude' arguments.");
			System.exit(0);
		}


		// Mandatory arguments
		String driver = arguments.get("-jdriver");
		String jurl = arguments.get("-jurl");
		String username = arguments.get("-juser");
		String password = arguments.get("-jpasswd");
		String outDbUrl = arguments.get("-ourl");

		// Optional arguments
		String chosenStrategy = arguments.get("-s");
		String nameResolver = arguments.get("-nr");
		String outputLevel = arguments.get("-v");
		String chosenMapper = "basicDBMapper";  // Mapper argument
		String xmlPath = null;
		if(arguments.containsKey("-inheritance")) {
			String argument = arguments.get("-inheritance");

			chosenMapper = argument.substring(0, argument.indexOf(':'));
			xmlPath = argument.substring(argument.indexOf(':')+1);
		}

		List<String> includedTables = null;
		List<String> excludedTables = null;

		if(arguments.get("-include") != null) {
			String tables = arguments.get("-include");
			String[] arrayTables = tables.split(",");
			includedTables = new ArrayList<String>(Arrays.asList(arrayTables));
		}

		if(arguments.get("-exclude") != null) {
			String tables = arguments.get("-exclude");
			String[] arrayTables = tables.split(",");
			excludedTables = new ArrayList<String>(Arrays.asList(arrayTables));
		}


		OTeleporter.execute(driver, jurl, username, password, outDbUrl, chosenStrategy, chosenMapper, xmlPath, nameResolver, outputLevel, includedTables, excludedTables);
	}

	/**
	 * Executes the import of the source DB in a OrientDB Graph through different parameters.
	 * 
	 * @param driver the driver name of the DBMS from which you want to execute the import
	 * @param jurl an absolute URL giving the location of the source DB to import
	 * @param username to access to the source DB
	 * @param password to access to the source DB
	 * @param chosenStrategy the execution approach adopted during the importing of data
	 * @param outDbUrl an absolute URI for the destination Orient Graph DB
	 * @param nameResolver the name of the resolver which transforms the names of all the elements of the source DB
	 *                     according to a specific convention (if null Java convention is adopted)
	 * @param outputLevel the level of the logging messages that will be printed on the OutputStream during the execution
	 * @param excludedTables 
	 * @param includedTables 
	 */


	public static void execute(String driver, String jurl, String username, String password, String outDbUrl, String chosenStrategy, String chosenMapper, String xmlPath, String nameResolver,
			String outputLevel, List<String> includedTables, List<String> excludedTables) {


		// OutputStream setting

		if(outputLevel != null)
			outputManager.setLevel(Integer.parseInt(outputLevel));

		// Context and Progress Monitor initialization
		final OTeleporterContext context = new OTeleporterContext();
		context.setOutputManager(outputManager);
		OProgressMonitor progressMonitor = new OProgressMonitor(context);
		progressMonitor.initialize();

		// JDBC Driver configuration and driver class name fetching
//		String driverClassName = ODriverConfigurator.checkConfiguration(driver, context);
		String driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
		context.setDriverDependencyPath("/home/gabriele/orientdb-community-2.1.0/lib/sqljdbc4.jar");

		OImportStrategy strategy = FACTORY.buildStrategy(chosenStrategy, context);
		
		if(strategy == null)
			System.exit(0);

		// Timer for statistics notifying
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				context.getStatistics().notifyListeners();        
			}
		}, 0, 1000);

		// the last argument represents the nameResolver
		strategy.executeStrategy(driverClassName, jurl, username, password, outDbUrl, chosenMapper, xmlPath, nameResolver, includedTables, excludedTables, context);

		timer.cancel();

	}


	@Override
	public String getName() {

		return "teleporter";
	}

	@Override
	public void startup() {
		super.startup();
	}

	@Override
	public void config(OServer oServer, OServerParameterConfiguration[] iParams) {

	}

	@Override
	public void shutdown() {
		super.shutdown();
	}
}
