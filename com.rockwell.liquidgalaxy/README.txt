************************************************
 ABOUT 
************************************************
* App: Liquid Galaxy
* Project: Google CEC
* Team: Quin Kennedy

Description:
* Launches google Earth, optionally as a node in a liquid galaxy installation.

Documentation
  * Video:
  * Image(s): 

************************************************
 SETUP 
************************************************
Hardware Requirements:
  *OSX 10.7

IS:
  1. There are a number of configuration parameters that are used to configure Google Earth for use in a Liquid Galaxy cluster, and in starting up Google Earth successfully under I.S.
  	For controlling Google Earth settings:
  		config key 			default value			purpose
  		master				false					set to true for the node which will be sending out ViewSync messages
  		slaveIP				127.0.0.1 				the IP to send ViewSync messages to, can be a broadcast address
  		port				58349					The port to send/receive ViewSync messages on
  		yawOffset			0						The yaw offset in degrees
  		pitchOffset			0						the pitch offset in degrees
  		rollOffset			0						the roll offset in degrees
  		horizFov			36.5 					The angle between the left and right side of the view port
  		queryFile			/tmp/query_php.txt		The location for the Master Liquid Galaxy node to look for the query.txt file that the web interface writes to

  	For controlling startup:
  		firstrun			0						tells the Live Activity to go through its firstrun routine. This should be incremented every time an update is deployed (not every time a configuration value is updated)
  		startupDelay		0 (15 for master)		the number of seconds to wait between the Live Activity starting up, and Liquid Galaxy starting up. There is a delay for the Master so that the client nodes will have time to start up before receiving ViewSync messages
  		activateDelay		15						the number of seconds between startup and activation. Activation merely ensures that Google Earth has focus.
  		clickDelay			10						(only during firstrun) The number of seconds to wait after startup before we 'click' the OK button on any potential warning dialogs that appear
  		shutdownDelay		10						The delay between asking Google Earth to shutdown and allowing the Live Activity to shutdown. This gives Google Earth time to cleanup before the Live Activity fully tears everything down.
  		eraseEveryTime		true					This controls whether the config files and cache for Google Earth should be removed and replaced every time.

  2. All Liquid Galaxy Live Activities for a moment should be placed in the same Live Activity Group. This will ensure that the master will startup the appropriate amount of time after the clients when the Live Activity Group is started up

  3. In order to allow access to programmatically dismiss pop-ups you must go to System Preferences, Keyboard, Keyboard Shortcuts tab, and click "All controls" radio button near the bottom of the window. This is necessary to do on OSX 10.7.3, it does not seem necessary on 10.7.4, no other OSX versions have been tested.

************************************************
 RUN 
************************************************

IS:
  1. Go to the Activity Group that you have set up and click "Startup" or "Activate"
  2. Wait up to 1 minute for everything to finish starting up and configuring itself.

************************************************
 MORE INFO 
************************************************
* To-Dos
  *

* Troubleshooting
  *

* Credits
  * 

* Licensing
  * 

* Notes
  *