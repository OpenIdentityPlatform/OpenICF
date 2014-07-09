#!/bin/bash
#
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
#
# Copyright (c) 2014 ForgeRock AS. All Rights Reserved
#
# The contents of this file are subject to the terms
# of the Common Development and Distribution License
# (the License). You may not use this file except in
# compliance with the License.
#
# You can obtain a copy of the License at
# http://forgerock.org/license/CDDLv1.0.html
# See the License for the specific language governing
# permission and limitations under the License.
#
# When distributing Covered Code, include this CDDL
# Header Notice in each file and include the License file
# at http://forgerock.org/license/CDDLv1.0.html
# If applicable, add the following below the CDDL Header,
# with the fields enclosed by brackets [] replaced by
# your own identifying information:
# "Portions Copyrighted [year] [name of copyright owner]"
#
# clean up left over pid files if necessary
cleanupPidFile() {
  if [ -f "$OPENICF_PID_FILE" ]; then
    rm -f "$OPENICF_PID_FILE"
  fi
  trap - EXIT
  exit
}

#setup classpath for Java 5. It needs the exact jar file name
setupClasspath(){

    for JAR_FILE in `ls *.jar`
    do
        CLASSPATH="$CONNECTOR_SERVER_HOME/lib/$JAR_FILE":$CLASSPATH
    done
}

setkey(){
    echo Please provide key you want to set:
    while read NEW_KEY; do
       #Sets key in the Identity Connectors Server properties file
       exec java $OPENICF_OPTS \
        -classpath "$CLASSPATH" \
        org.identityconnectors.framework.server.Main -setkey -key ${NEW_KEY} -properties "$CONNECTOR_SERVER_HOME/conf/ConnectorServer.properties"
       exit 0
    done
}

start(){
    # Keep track of this pid
    echo $$ > "$OPENICF_PID_FILE"
    exec java $OPENICF_OPTS -server \
        -classpath "$CLASSPATH" \
        org.identityconnectors.framework.server.Main -run -properties "$CONNECTOR_SERVER_HOME/conf/ConnectorServer.properties"
}

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

echo "Executing "$PRG"..."

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set CONNECTOR_SERVER_HOME if not already set
[ -z "$CONNECTOR_SERVER_HOME" ] && CONNECTOR_SERVER_HOME=`(cd "$PRGDIR/.." >/dev/null; pwd)`

# Only set OPENICF_PID_FILE if not already set
[ -z "$OPENICF_PID_FILE" ] && OPENICF_PID_FILE="$CONNECTOR_SERVER_HOME"/.openicf.pid

# Only set OPENICF_OPTS if not already set
[ -z "$OPENICF_OPTS" ] && OPENICF_OPTS="-Dlogback.configurationFile=lib/logback.xml"



if [ "$1" = "jpda" ] ; then
  if [ -z "$JPDA_TRANSPORT" ]; then
    JPDA_TRANSPORT="dt_socket"
  fi
  if [ -z "$JPDA_ADDRESS" ]; then
    JPDA_ADDRESS="5005"
  fi
  if [ -z "$JPDA_SUSPEND" ]; then
    JPDA_SUSPEND="y"
  fi
  if [ -z "$JPDA_OPTS" ]; then
    JPDA_OPTS="-agentlib:jdwp=transport=$JPDA_TRANSPORT,address=$JPDA_ADDRESS,server=y,suspend=$JPDA_SUSPEND"
  fi
  OPENICF_OPTS="$OPENICF_OPTS $JPDA_OPTS"
  shift
fi


CLASSPATH="$CONNECTOR_SERVER_HOME/lib/*:$CONNECTOR_SERVER_HOME/lib/framework/*"

# Make the script location the current directory
cd "$CONNECTOR_SERVER_HOME"

#Run java options, separated by space
#set JAVA_OPTS=-Xmx500m "-Djava.util.logging.config.file=conf/logging.properties" "-Dlogback.configurationFile=lib/logback.xml"

echo "Using CONNECTOR_SERVER_HOME:  $CONNECTOR_SERVER_HOME"
echo "Using          OPENICF_OPTS:  $OPENICF_OPTS"
echo "Using             CLASSPATH:  $CLASSPATH"


if [[ "$1" == "/run" ]]; then
       start
elif [[ "$1" == "/setkey" ]]; then
      setkey
else
     echo Usage: ConnectorServer
fi

