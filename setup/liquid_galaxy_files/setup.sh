cd /etc
sudo cp /Volumes/LAB/LAB_Quin/liquid\ galaxy\ files/hosts.squid hosts.squid
sudo mv hosts hosts.backup
sudo cp /Volumes/LAB/LAB_Quin/liquid\ galaxy\ files/hosts hosts
cd /usr/local/squid/etc/
sudo cp /Volumes/LAB/LAB_Quin/liquid\ galaxy\ files/squid.conf squid.conf
sudo cp /Volumes/LAB/LAB_Quin/liquid\ galaxy\ files/cachemgr.conf cachemgr.conf
sudo chmod 777 /usr/local/squid/var/cache
cd ../sbin
sudo ./squid -z
sudo ./squid -NCd1
