#!/bin/sh
#Flush existing rules
/sbin/ipfw -q flush
#run ipfw and load custom rules
/sbin/ipfw -q /etc/ipfw.conf
#enable detailed logging to syslog
/usr/sbin/sysctl -w net.inet.ip.fw.verbose=1
