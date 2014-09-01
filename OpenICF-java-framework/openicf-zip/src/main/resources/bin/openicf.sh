#!/bin/sh
# /etc/init.d/openicf

### BEGIN INIT INFO
# Provides:          openicf
# Short-Description: Starts the OpenICF service
# Description:       This file is used to start the daemon
#                    and should be placed in /etc/init.d
### END INIT INFO


NAME="openicf"
DESC="OpenICF Connector Server"

# The path to Jsvc
EXEC="/usr/bin/jsvc"

# The path to the folder containing OpenICF
# Only set OPENICF_HOME if not already set
[ -z "$OPENICF_HOME" ] && OPENICF_HOME=/usr/local/$NAME


# The path to the folder containing the java runtime
if [ -z "$JAVA_HOME" ] ; then          
 shopt -s nocasematch
 OSTYPE=$(uname)
 if [[ "$OSTYPE" == "linux-gnu" ]]; then
        # Linux
        JAVA_HOME="/usr/lib/jvm/default-java" 
 elif [[ "$OSTYPE" == "darwin"* ]]; then
        # Mac 
        JAVA_HOME=$(/usr/libexec/java_home)
 elif [[ "$OSTYPE" == "cygwin" ]]; then
        # Cygwin (Windows)   
        JAVA_HOME="/cygdrive/c/Program\ Files/Java/jdkversion/"
 elif [[ "$OSTYPE" == "freebsd"* ]]; then
        # FreeBSD  
        JAVA_HOME="/usr/local/openjdk6"
 else
        # Unknown.     
        echo "Could not find JAVA_HOME! Aborting." 1>&2
        exit 1  
 fi
 shopt -u nocasematch
fi

# Our classpath including OpenICF jar files and the Apache Commons Daemon library
CLASS_PATH="$OPENICF_HOME"/lib/framework/*:"$OPENICF_HOME"/lib/framework

# The fully qualified name of the class to execute
CLASS="org.identityconnectors.framework.server.Main"

# Any command line arguments to be passed to the our Java Daemon implementations init() method
ARGS="-properties conf/ConnectorServer.properties"

#The user to run the daemon as
USER=$(whoami)

# The file that will contain our process identification number (pid) for other scripts/programs that need to access it.
PID="$OPENICF_HOME/.$NAME.pid"

# System.out writes to this file...
LOG_OUT="$OPENICF_HOME/logs/$NAME.out"

# System.err writes to this file...
LOG_ERR="$OPENICF_HOME/logs/$NAME.err"
 
# Debug options
DEBUG_OPS=""    
if [ "$1" = "jpda" ] ; then
  DEBUG_OPS="-debug -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
  shift
fi

jsvc_exec()
{
    $EXEC $DEBUG_OPS -user $USER -cwd "$OPENICF_HOME" -home "$JAVA_HOME" -outfile "$LOG_OUT" -errfile "$LOG_ERR" -pidfile "$PID" $1 -cp "$CLASS_PATH" $CLASS $ARGS
}

case "$1" in
    start)
        echo "Starting the $DESC..."

        # Start the service
        jsvc_exec

        echo "The $DESC has started."
    ;;
    stop)
        echo "Stopping the $DESC..."

        # Stop the service
        jsvc_exec "-stop"

        echo "The $DESC has stopped."
    ;;
    restart)
        if [ -f "$PID" ]; then

            echo "Restarting the $DESC..."

            # Stop the service
            jsvc_exec "-stop"

            # Start the service
            jsvc_exec

            echo "The $DESC has restarted."
        else
            echo "Daemon not running, no action taken"
            exit 1
        fi
            ;;
    *)
    echo "Usage: /etc/init.d/$NAME {start|stop|restart}" >&2
    exit 3
    ;;
esac