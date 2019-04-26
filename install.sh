#!/bin/sh
echo "running mvn package..."
mvn package
if [ $? -eq 0 ]; then
  echo "mvn package successful"
  echo "copying new war file"
  cp -rf target/*.war /var/lib/tomcat8/webapps/
else
  echo "mvn package failed"
fi
