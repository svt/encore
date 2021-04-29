#!/bin/bash -e

readonly INSTALL_DIR=$(pwd)

echo "using install dir [$INSTALL_DIR]"

JAVA_LOG4J2="-DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"
JAVA_NETWORK="-Djava.net.preferIPv4Stack=true"

JAVA_OPTS="$JAVA_OPTS ${JAVA_LOG4J2} ${JAVA_NETWORK}"

CMD="java $JAVA_OPTS -jar ${INSTALL_DIR}/encore.jar"

echo "Starting boot app with command: $CMD"
exec ${CMD}




