# install java dependencies
sudo apt install tomcat9 maven openjdk-9-jdk
# add administrator to tomcat group
sudo usermod -aG tomcat administrator
# switch-user to administrator to make the group change take effect
su administrator
# go to install folder
cd /opt/dcloud
# make folder for google cloud project auth file
mkdir gcp
# copy google cloud project auth json file to server
(copy gcp.json file with SFTP to /opt/dcloud/gcp/gcp.json )
# enable gcp file to tomcat9 startup environment vars
sudo vim /etc/default/tomcat9
# add this after the last line:
GOOGLE_APPLICATION_CREDENTIALS="/opt/dcloud/gcp/gcp.json"
# clone the "dcloud" branch of the forkctrl-1.2 repository
git clone -b dcloud git@gitlab.com:ccondry/media-forking-controller.git
# go to the new project folder
cd media-forking-controller
# install into tomcat8
./install.sh
# restart tomcat8
sudo systemctl restart tomcat8
