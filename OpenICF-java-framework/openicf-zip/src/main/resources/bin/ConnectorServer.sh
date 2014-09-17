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
    for JAR_FILE in `ls "$CONNECTOR_SERVER_HOME/lib/framework/"*.jar | tr '\n' '\0' | xargs -0 -n 1 basename`
    do
        CLASSPATH=$CLASSPATH:"$CONNECTOR_SERVER_HOME/lib/framework/$JAR_FILE"
    done 
	CLASSPATH=$CLASSPATH:"$CONNECTOR_SERVER_HOME/lib/framework/"
}

main_exec(){
    exec java $OPENICF_OPTS -classpath "$CLASSPATH" \
     org.identityconnectors.framework.server.Main $1 -properties "$CONNECTOR_SERVER_HOME/conf/ConnectorServer.properties"
}

service_exec(){
    # Keep track of this pid
    echo $$ > "$OPENICF_PID_FILE"
    echo "OPENICF_OPTS:   "$OPENICF_OPTS
    echo "CLASSPATH:      "$CLASSPATH
    exec java $OPENICF_OPTS -server -classpath "$CLASSPATH" \
        org.identityconnectors.framework.server.Main $1 -properties "$CONNECTOR_SERVER_HOME/conf/ConnectorServer.properties"
}

usage(){
    echo "Usage: ConnectorServer <command> [option], where command is one of the following:"
    echo "   /run [\"-J<java option>\"] - Runs the server from the console."
    echo "   /setKey [<key>] - Sets the connector server key."
    echo "   /setDefaults - Sets the default ConnectorServer.properties."
    echo
    echo "example:"
    echo "    ConnectorServer.sh /run \"-J-Djavax.net.ssl.keyStore=mykeystore.jks\" \"-J-Djavax.net.ssl.keyStorePassword=changeit\""
    echo "       - this will run connector server with SSL"
    echo
    echo "    ConnectorServer.sh jpda /run"
    echo "       - this will run connector server in debug mode"
}

# Set Connector Server Home
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

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set CONNECTOR_SERVER_HOME if not already set
[ -z "$CONNECTOR_SERVER_HOME" ] && CONNECTOR_SERVER_HOME=`(cd "$PRGDIR/.." >/dev/null; pwd)`

if [ ! -f "$CONNECTOR_SERVER_HOME/bin/ConnectorServer.sh" ]; then
    echo Invalid CONNECTOR_SERVER_HOME environment variable
    echo Please set it to correct Connector Server Home
    exit 1
fi

# Make the script location the current directory
cd "$CONNECTOR_SERVER_HOME"

# Only set OPENICF_PID_FILE if not already set
[ -z "$OPENICF_PID_FILE" ] && OPENICF_PID_FILE="$CONNECTOR_SERVER_HOME"/.openicf.pid

# Only set OPENICF_OPTS if not already set
[ -z "$OPENICF_OPTS" ] && OPENICF_OPTS="-Xmx512m"

for P in "$@"
do
  if [[ "${P:0:2}" == "-J" ]]; then
    OPENICF_OPTS="$OPENICF_OPTS ${P##-J}"
  fi    
done

# Check Java availability
if type -p 'java' >/dev/null; then
    JAVA=java
elif [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]];  then    
    JAVA="$JAVA_HOME/bin/java"
else
    echo JAVA_HOME not available, Java is needed to run the Connector Server
    echo Please install Java and set the JAVA_HOME accordingly
    exit 1
fi

java_version=$("$JAVA" -version 2>&1 | awk -F '"' '/version/ {print $2}')
if [[ "$java_version" > "1.5" ]]; then
    CLASSPATH="$CONNECTOR_SERVER_HOME/lib/framework/*:$CONNECTOR_SERVER_HOME/lib/framework/"
else         
    echo "Running on Java 1.5"
    setupClasspath
fi

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

shopt -s nocasematch
if [[ "$1" == "/run" ]]; then
      service_exec -run
elif [[ "$1" == "/start" ]]; then
      service_exec -service
elif [[ "$1" == "/setDefaults" ]]; then
      main_exec -setDefaults
elif [[ "$1" == "/setKey" ]]; then
    if [[ -n "$2" ]]; then
        exec java $OPENICF_OPTS -classpath "$CLASSPATH" \
          org.identityconnectors.framework.server.Main -setKey -key $2 -properties "$CONNECTOR_SERVER_HOME/conf/ConnectorServer.properties"      
    else
        exec java $OPENICF_OPTS -classpath "$CLASSPATH" \
          org.identityconnectors.framework.server.Main -setKey -properties "$CONNECTOR_SERVER_HOME/conf/ConnectorServer.properties"   
    fi      
else
      usage
fi
shopt -u nocasematch
