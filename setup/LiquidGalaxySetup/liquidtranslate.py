import socket, signal, math, time, string, threading
##############################################################################
# created by Quin Kennedy on April 19th 2012
#
# this accepts Google Earth viewsync messages and converts 
# them from earth coordinates to the current sky coordinates
#
# you can use the following command to test via terminal:
# echo [viewsync_packet] > /dev/udp/127.0.0.1/[port]
# where [viewsync_packet] is any viewsync packet such as (for new york)...
# "5090,40.7516667,-73.9941669,523818.98127554677194,-0.28372808067457,0.00000000000000,0.00000000000000,63454496156,63454496156,"
# and [port] matches whatever port is configured for UDP_PORT_RECEIVE
#
#check against http://www.fourmilab.ch/yoursky/help/controls.html#Site
#or more simply http://www.fourmilab.ch/cgi-bin/Yoursky 
#  (you may want to enable "Ecliptic and equator" and disable everything else)
#via http://www.fourmilab.ch/yoursky/
##############################################################################

#the ip(optional) and port to recieve viewsync messages from the master
#instance of google earth
UDP_IP_RECEIVE=""#set if you want to limit who to receive from
UDP_PORT_RECEIVE=58360

#the ip and port of the slave instance of google earth that 
#will be displaying SKY
UDP_IP_SEND="127.0.0.1"
UDP_PORT_SEND=58349

DEBUG = True
PERIOD = 5#number of seconds between earth rotation updates

lastMsg = []
lastTime = 0
currTime = 0
longitudeChange = 0
currIndex = 1

#assuming 1970 as start point
#subtract .25 since leap years cover forward dates but 2011 hasn't finished yet, so we need to remove the .25 that was included for it by 2008
#update: I have no idea why we subtract .25, we just need to end up at noon March 20th -- will check on actual noon March 20th 2012
#update: now that I am explicitly calculating leap years, it seems to be working better, but I should explicitly calculate leap years everywhere?
#1332244800 << the actual time for March 20th, 2012, 12:00pm
#to compute solstice for any? year
#(((((2010-1970)*365+numLeapYearsSince1970(2010))+31+28+19)*24)+12)*60*60
SOLSTICE = 1332244800

sockSend = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
sockSend.sendto("test", (UDP_IP_SEND, UDP_PORT_RECEIVE))

sockRcv = socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
sockRcv.bind((UDP_IP_RECEIVE, UDP_PORT_RECEIVE))


def longitudeToCurSky(longitude):
	if DEBUG: print currTime, ", ", longitudeChange
	#take the input longitude, and offset by the orbit around the sun and the rotation
	result = (longitude+longitudeChange)#%360.0;
	#use subtraction instead of mod since mod is integer arithmetic
	while (result > 360):
		result -= 360
	#convert to right ascention
	#result = result*24/360
	#convert to (-180,+180] span
	#if (result > 180):
	#	result = -360+result;
	return result

def translate(value, leftMin, leftMax, rightMin, rightMax):
    # Figure out how 'wide' each range is
    leftSpan = leftMax - leftMin
    rightSpan = rightMax - rightMin

    # Convert the left range into a 0-1 range (float)
    valueScaled = float(value - leftMin) / float(leftSpan)

    # Convert the 0-1 range into a value in the right range.
    return rightMin + (valueScaled * rightSpan)

def editView(inMsg):
	#counter, lat, lon, alt, heading, tilt, roll, time start, time end, planet_name ("sky", "mars", "moon", empty "" is Earth)
	#change to view sky
	inMsg[9] = "sky"
	#figure out an ideal zoom 'altitude' or how to map it intuitively
	EarthAltMax = 7000
	EarthAltMin = 100
	SkyAltMax=6466780
	SkyAltMin=255730
	#inMsg[3] = "100000"
	inMsg[3] = str(SkyAltMax)# str(max(translate(max(float(inMsg[3]),SkyAltMax), SkyAltMax, SkyAltMax + (SkyAltMax - SkyAltMin), SkyAltMax, SkyAltMin), SkyAltMin))
	if DEBUG: print "alt: " + inMsg[3]
	#when the view goes above/below verticle, we don't want the sky to snap around
	#the roll is always ~180 or ~-180 or ~0 when using the 3d mouse
	if float(inMsg[6]) > 100 or float(inMsg[6]) < -100:
		inMsg[6] = "0"
		inMsg[4] = str(float(inMsg[4])+180.0)
	return inMsg


def sendMsg():
	global currIndex
	newMsg = []
	newMsg.extend(lastMsg)
	#counter, lat, lon, alt, heading, tilt, roll, time start, time end, planet_name ("sky", "mars", "moon", empty "" is Earth)
	newMsg[2] = str(longitudeToCurSky(float(newMsg[2])))
	if float(newMsg[2]) > 180:
		newMsg[2] = str(float(newMsg[2]) - 360)
	#increment the first entry so the receiver will pay attention to us
	currIndex = max(int(currIndex)+1, int(newMsg[0]))
	newMsg[0] = str(currIndex)
	
	sockSend.sendto(string.join(newMsg, ","),(UDP_IP_SEND, UDP_PORT_SEND))
	if DEBUG: print "sent: ", string.join(newMsg, ",")

def update():
	if DEBUG: print "updating"
	global currTime, longitudeChange
	#get the current time, I should probably get this as a seperate process once a second 
	#or so in order to save time since UDP packets will be arriving maaaaany times per second. 
	#I will also want to re-calculate and simulate a UDP packet every so often to move the sky 
	#as the earth turns, maybe that can be once a second too.
	currTime = time.time()
	#get the days since the solstice
	currDay = (currTime - SOLSTICE)/60/60/24
	#get the seconds since noon GMT
	currSec = (currTime - SOLSTICE)%(60*60*24)
	#get the number of degrees around the sun the earth has orbited since the solstice
	currOrbit = 360/365.25*currDay
	#get the number of degrees the earth has rotated since noon GMT
	currRotation = 360.0/(24*60*60)*currSec
	longitudeChange = currOrbit + currRotation
	if DEBUG: print currTime, ", ", currDay, ", ", currSec,", ",currOrbit,", ",currRotation,", ",longitudeChange
	#if it has been more than half a second since the last message was received, 
	#send an update to keep the stars moving
	if (currTime - .5 >= lastTime and lastMsg and len(lastMsg) == 10):
		sendMsg()

def triggerUpdate(signum, frame):
	update()


class ViewSyncHandler(threading.Thread):
	def run(self):
		global lastTime, lastMsg
		while True:
			data, addr = sockRcv.recvfrom(1024)
			lastTime = time.time()
			if (DEBUG): print "received message: ", data
			currMsg = string.split(data, ",")
			if (len(currMsg) == 10):
				lastMsg = editView(currMsg)
				sendMsg()

update()
signal.signal(signal.SIGALRM, triggerUpdate)
signal.setitimer(signal.ITIMER_REAL, PERIOD, PERIOD)
handler = ViewSyncHandler()
handler.daemon = True
handler.start()

while True:
	pass