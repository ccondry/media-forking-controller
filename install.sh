#!/bin/sh
echo "running mvn package..."
mvn package
if [ $? -eq 0 ]; then
  echo "mvn package successful"
  echo "copying new war file to tomcat webapps"
  cp -rf target/*.war /var/lib/tomcat9/webapps/
  if [ $? -eq 0 ]; then
    echo "successfully installed media-forking-controller"
  fi
else
  echo "mvn package failed"
fi
