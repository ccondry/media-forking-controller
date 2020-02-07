#!/bin/sh
# this file is meant to be run from cron to update the local git branch with the
# associated upstream branch, then build and install into /var/www/html

# fetch changes from origin
git fetch
# get the number of changes between local and remote (current branch)
CHANGES=$(git rev-list HEAD...@{u} --count)

if [ $CHANGES = "0" ]; then
  echo "git repo is current"
else
  echo "git repo is not current. updating..."
  git pull
  if [ $? -eq 0 ]; then
    echo "running mvn package..."
    mvn package
    if [ $? -eq 0 ]; then
      echo "mvn package successful"
      echo "copying new war file to tomcat webapps"
      cp -rf target/*.war /var/lib/tomcat9/webapps/
      if [ $? -eq 0 ]; then
        echo "successfully updated media-forking-controller"
      fi
    else
      echo "mvn package failed"
    fi
  else
    echo "failed to pull repo"
    echo "trying to remove package-lock.json and try on next iteration"
    rm package-lock.json
  fi
fi
