package com.rockwell.liquidgalaxy;

import interactivespaces.activity.impl.BaseActivity;
import interactivespaces.activity.ActivityFilesystem;
import interactivespaces.service.script.ScriptService;
import interactivespaces.util.io.Files;
import interactivespaces.util.data.persist.JsonSimpleMapPersister;
import java.util.Map;
import java.util.Hashtable;
import java.io.File;
//import com.dd.plist.*;//TODO: use this to edit the plist
//TODO: replace 'firstrun' check with permission check.

/**
 * The I.S. Activity for configuring, launching, 
 * and exiting a Google Earth instance in a Liquid Galaxy installation
 */
public class LiquidGalaxyRunner extends BaseActivity {
	/********************************************
	 **************** NOTES *********************
	 ********************************************
	 *	This activity uses Applescripts to manage most tasks with
	 *	startup, dismissing popups, and shutting down Google Earth.
	 *	Currently the Applescripts are all run synchronously,
	 *	so all delays in the applescripts will block the I.S. activity from
	 *	reporting as fully started up.
	 *
	 * TODO: if we notice that G.E. is not running (via a checkGERunning applescript)
	 * 		perhaps we should throw an error so I.S. knows there was an issue.
	 */

	private boolean m_bMaster = false;
	private boolean m_bFirstrun = false;
	/**
	 * Applescript snippet for checking that Google Earth is running
	 * @type {String}
	 */
	private static String checkGERunning = "tell application \"System Events\" to set IsRunning to (name of processes) contains \"Google Earth\"\n";
	/**
	 * Applescript snippet for activating Google Earth.
	 * This snippet is only used for gaining focus once Google Earth is already opened.
	 * We want to open Google Earth from the install directory initially, 
	 * so we must include the full application path the first time we activate it.
	 * @type {String}
	 */
	private static String activateGE = "tell application \"Google Earth\" to activate\n";
	/**
	 * Appliscript snippet for simulating the user pressing the return key.
	 * Used to automatically dimiss popups during startup and shutdown.
	 * @type {String}
	 */
	private static String pressReturn = "tell application \"System Events\" to keystroke return\n";


	@Override
	public void onActivitySetup() {
		ScriptService scriptService = (ScriptService)getSpaceEnvironment().getServiceRegistry().getService(ScriptService.SERVICE_NAME);

		//Send a UDP packet to disable ViewSync message passing.
		//This will have no effect if we are not using the python ViewSync translation script
		//to route ViewSync packets
		//TODO: is there a way to do this with ipfw? When I tried using ipfw before I think it failed
		//		because of permissions issues (need sudo privelages to add ipfw filters)
		String disableTranslate = "do shell script \"echo 'disable' > /dev/udp/127.0.0.1/58390\"";
		scriptService.executeSimpleScript("AppleScript", disableTranslate);

		ActivityFilesystem fs = getActivityFilesystem();
		///////////////  Leftover from when we were using the native runner to launch G.E.
		///////////////  But that would periodically launch G.E. twice.
		///////////////  We may go back to that if the double-launching is resolved
		// //copy .app to data directory
		// //not doing that right now because ../data/ "is not local to the activity"
		// //and permission denied if I try using absolute path
		// File srcDir = fs.getInstallFile("Google Earth.app");
		// File dstDir = fs.getPermanentDataFile("Google Earth.app");
		// Files.directoryExists(dstDir);
		// Files.deleteDirectoryContents(dstDir);
		// Files.copyDirectory(srcDir, dstDir, true);
		

		//Get persistent map from the data directory.
		//We use this to track information such as the last time files were updated.
		//This is helpful for telling us when new configurations were pushed while 
		//the activity was shutdown, or if the activity was re-deployed.
		//
		//THOUGHT: instead of checking modified dates, we could just do everything every time...
		JsonSimpleMapPersister dataPersister = new JsonSimpleMapPersister(fs.getPermanentDataDirectory());
		Map<String, Object> map = dataPersister.getMap("data");
		m_bFirstrun = (map == null);
		if (m_bFirstrun){
			map = new Hashtable<String, Object>(1);
		}

		//Check the last modified dates on various files against that stored in
		//the persistent map to determine if we should update files.
		boolean needsUpdate = !map.containsKey("lastModified");
		//check if data/activity.conf, install/activity.conf, or install/drivers.ini
		// has been updated recently
		File liveActivityConf = fs.getPermanentDataFile("activity.conf");
		File activityConf = fs.getInstallFile("activity.conf");
		File driversIni = fs.getInstallFile("drivers.ini");
		long latestModifiedDate = liveActivityConf.lastModified();
		if (latestModifiedDate < activityConf.lastModified()){
			latestModifiedDate = activityConf.lastModified();
		}
		if (latestModifiedDate < driversIni.lastModified()){
			latestModifiedDate = driversIni.lastModified();
		}
		if (map.containsKey("lastModified")){
			long storedLastMod = ((Long)map.get("lastModified")).longValue();
			needsUpdate |= storedLastMod < latestModifiedDate;
			if (storedLastMod < driversIni.lastModified()){
				m_bFirstrun = true;
			}
		}


		m_bMaster = getConfiguration().getPropertyBoolean("master", false);
		getLog().info("####### needs Update is " + needsUpdate);

		//If we want to re-generate some files, do that here
		if (needsUpdate){
			//update the stored 'lastModified' date so we don't repeat this
			//next time
			map.put("lastModified", new Long(latestModifiedDate));

			//get configs
			//use template to write out drivers.ini
			File srcConfig = fs.getInstallFile("drivers.ini");
			File dstConfig = fs.getInstallFile("Google Earth.app/Contents/MacOS/drivers.ini");
			String srcContent = Files.readFile(srcConfig);

			//handle the settings that have a one-to-one match from the I.S. config to the G.E. config
			String[] settings = new String[]{"port","yawOffset","pitchOffset","rollOffset","horizFov","slaveIP","queryFile"};
			String setting;
			for(int i = 0; i < settings.length; i++){
				setting = settings[i];
				srcContent = srcContent.replace("${"+setting+"}",getConfiguration().getPropertyString(setting));
				getLog().info("ViewSync/"+setting+"="+getConfiguration().getPropertyString(setting));
			}

			//handle the settings that don't have a one-to-one match
			//Whether or not this node is a master controls both the 
			//'send' and 'receive' parameters in drivers.ini
			srcContent = srcContent.replace("${send}",m_bMaster?"true":"false");
			getLog().info("ViewSync/send = " + Boolean.toString(m_bMaster));
			srcContent = srcContent.replace("${receive}",m_bMaster?"false":"true");
			getLog().info("ViewSync/receive = " + Boolean.toString(!m_bMaster));

			//Write out the updated drivers.ini file
			Files.writeFile(dstConfig, srcContent);
		}

		//save the updated persistent data map
		dataPersister.putMap("data",map);

		//if the activity was re-deployed, or the config value 'eraseEveryTime' is set
		//then we want to update preferences files and permissions.
		if (m_bFirstrun || getConfiguration().getPropertyBoolean("eraseEveryTime", true)){
			if (m_bFirstrun){
				getLog().info("############## firstrun routine in setup");

				//Copy updated configuration files to the permenant data directory
				File copyfrom = fs.getInstallFile("GEFiles");
				File copyto = fs.getPermanentDataFile("GEFiles");
				Files.directoryExists(copyto);
				Files.deleteDirectoryContents(copyto);
				Files.copyDirectory(copyfrom, copyto, true);

				//Change permissions on the GE executable
				File exec = fs.getInstallFile("Google Earth.app/Contents/MacOS/Google Earth");
				String changePerm = "do shell script \"chmod 755 '" + exec.getAbsolutePath() + "'\"";
				getLog().info("############## changing permissions: " + changePerm);
				scriptService.executeSimpleScript("AppleScript", changePerm);
			}
			getLog().info("########## attempting to remove preferences etc.");
			//Get the username of the current user.
			//This is used to know where to copy preferences files to since we can't use 
			//'~' to reference the home directory.
			String username = System.getProperty("user.name");

			//replace App Support files, they store sidebar "Places" preferences
			File toRemove = new File("/tmp/lg/Library/Application Support/Google Earth");
			getLog().info("############ dir " + toRemove.getAbsolutePath() + " exists: " + toRemove.exists());
			Files.deleteDirectoryContents(toRemove);
			File replaceWith = fs.getPermanentDataFile("GEFiles/App Support GE");
			Files.copyDirectory(replaceWith, toRemove, true);

			//replace GoogleEarthPlus.plist, it stores preferences from the 'view' menu 
			//as well as the 'preferences' screen
			toRemove = new File("/Users/"+username+"/Library/Preferences/com.google.GoogleEarthPlus.plist");
			getLog().info("############ file " + toRemove.getAbsolutePath() + " exists: " + toRemove.exists());
			replaceWith = fs.getPermanentDataFile("GEFiles/com.google.GoogleEarthPlus.plist");
			Files.copyFile(replaceWith, toRemove);

			//replace GECommonSettings.plist, it stores more preferences from the 'view' menu
			toRemove = new File("/Users/"+username+"/Library/Preferences/com.google.GECommonSettings.plist");
			replaceWith = fs.getPermanentDataFile("GEFiles/com.google.GECommonSettings.plist");
			Files.copyFile(replaceWith, toRemove);

			//clear out the cache
			//We locate the cache in /tmp/lg/Library/Caches/ so we don't have issues
			//with home directories being different on various machines
			//(G.E. defaults to ~/Library/Caches/ but doesn't allow '~' in its plist files)
			toRemove = new File("/tmp/lg/Library/Caches/com.Google.GoogleEarthPlus");
			getLog().info("############ dir " + toRemove.getAbsolutePath() + " exists: " + toRemove.exists());
			Files.deleteDirectoryContents(toRemove);
			Files.delete(toRemove);
			getLog().info("############ dir " + toRemove.getAbsolutePath() + " exists: " + toRemove.exists());
			toRemove = new File("/tmp/lg/Library/Caches/Google Earth");
			getLog().info("############ dir " + toRemove.getAbsolutePath() + " exists: " + toRemove.exists());
			Files.deleteDirectoryContents(toRemove);
			Files.delete(toRemove);
			getLog().info("############ dir " + toRemove.getAbsolutePath() + " exists: " + toRemove.exists());

			//some google earth related files, but we probably don't need to worry about deleting them
			//I think they are for the Chrome G.E. plugin
			// toRemove = new File("~/Library/Preferences/com.google.GoogleEarthPlugin.plist");
			// toRemove = new File("~/Library/Caches/com.Google.GoogleEarthPlugin");
		}

		///////////  This was used when we tried to launch G.E. as a native app.
		///////////  When I was trying that before though, it would launch two instances sometimes
		///////////  We may re-instate the native runner if we figure out that double-launching bug.
		// //we want the master to block for 15 seconds to allow the slaves to start first
		// //before ViewSync messages start.
		// m_bMaster = getConfiguration().getPropertyBoolean("master", false);
		// if (m_bMaster){
		// 	long endTime = System.currentTimeMillis()+15000;//set for 15 seconds in the future
		// 	while (endTime>System.currentTimeMillis()){}
		// }

		//add native runner
		//addActivityComponent("runner.native");
	}

	/**
	 * Called when the activity starts up.
	 * Uses Applescript to start up Google Earth from this activity's install directory.
	 * If this is the master node, it will delay startup (configuration parameter 'startupDelay')
	 * to allow the clients time to start up before receiving ViewSync messages.
	 * After startup, We use Applescript again to simulate clicking "return" to dismiss any popups,
	 * and then re-activate Google Earth to make sure it has focus.
	 */
	@Override
	public void onActivityStartup() {
		ActivityFilesystem fs = getActivityFilesystem();
		ScriptService scriptService = (ScriptService)getSpaceEnvironment().getServiceRegistry().getService(ScriptService.SERVICE_NAME);

		//The initial startup of Google Earth
		File earth = fs.getInstallFile("Google Earth.app");
		getLog().info("############### starting " + earth.getAbsolutePath());
		int delay = getConfiguration().getPropertyInteger("startupDelay",m_bMaster ? 15 : 0);
		String startEarth = "delay " + delay + "\ntell application \""+earth.getAbsolutePath()+"\" to activate";
		scriptService.executeSimpleScript("AppleScript", startEarth);

		//Wait a default of 10 seconds, then simulate clicking 'return' to dismiss any popups that might occur
		//	I have seen an instance of 3 popups needing to be dismissed, but that was when G.E. could
		//	not access the servers, so 1 popup was for the inaccessible User path, 
		//	and the other 2 were regarding error while connecting to the server.
		//	The other 2 errors are dismissed during shutdown
		delay = getConfiguration().getPropertyInteger("clickDelay", 10);
		String clickok = checkGERunning + "if IsRunning then\ndelay "+delay+"\n"+activateGE + "delay 1\n" + pressReturn + "end if";
		scriptService.executeSimpleScript("AppleScript", clickok);
		
		//Wait a default of 10 seconds, then activate G.E. a final
		//time to make sure it has focus and appears above everything else.
		delay = getConfiguration().getPropertyInteger("activateDelay",10);
		String activate = checkGERunning + "if IsRunning then\ndelay " + delay + "\n" + activateGE + "delay 2\nend if";
		scriptService.executeSimpleScript("AppleScript", activate);

		//Send a UDP packet to re-enable ViewSync message passing.
		//This will have no effect if we are not using the python ViewSync translation script
		//to route ViewSync packets
		String enableTranslate = "do shell script \"echo 'enable' > /dev/udp/127.0.0.1/58390\"";
		scriptService.executeSimpleScript("AppleScript", enableTranslate);
	}

	/**
	 * Called when the activity shuts down.
	 * This uses Applescript to dismiss any popups, then tell Google Earth to quit.
	 * There is a delay (configuration parameter 'shutdownDelay') after telling 
	 * Google Earth to quit to allow Google Earth to shutdown completely 
	 * before continuing with shutdown of the activity.
	 */
	@Override
	public void onActivityShutdown(){
		ScriptService scriptService = (ScriptService)getSpaceEnvironment().getServiceRegistry().getService(ScriptService.SERVICE_NAME);
		int delay = getConfiguration().getPropertyInteger("shutdownDelay",0);
		//We press return twice incase there are multiple popups for an error.
		//If G.E. cannot access the Google Servers, then it will display 2 popups.
		String quit = checkGERunning + "if IsRunning then\n"+ activateGE + "delay 1\n" + pressReturn + "delay 1\n" + pressReturn + "delay 1\ntell application \"Google Earth\" to quit\ndelay "+delay+"\nend if";
		scriptService.executeSimpleScript("AppleScript", quit);
	}
}
