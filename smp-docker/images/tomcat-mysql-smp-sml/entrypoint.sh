#!/bin/bash

#set -e

# parameters
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-"root"}
SMP_DB_USER=${SMP_DB_USER:-"smp"}
SMP_DB_USER_PASSWORD=${SMP_DB_USER_PASSWORD:-"secret123"}
SMP_DB_SCHEMA=${SMP_DB_SCHEMA:-"smp"}

SML_DB_USER=${SML_DB_USER:-"sml"}
SML_DB_USER_PASSWORD=${SML_DB_USER_PASSWORD:-"secret123"}
SML_DB_SCHEMA=${SML_DB_SCHEMA:-"sml"}

DATA_DIR=/data
MYSQL_DATA_DIR=${DATA_DIR}/mysql
TOMCAT_DIR=${DATA_DIR}/tomcat
TOMCAT_HOME=${SMP_HOME}/apache-tomcat-$TOMCAT_VERSION/
BIND_DATA_DIR=${DATA_DIR}/bind


if [ ! -d ${DATA_DIR} ]; then
   mkdir -p ${DATA_DIR}
fi

init_tomcat() {
  # add java code coverage agent to image
  if [ -e /opt/jacoco/jacoco-agent.jar ]; then
    JAVA_OPTS="-javaagent:/opt/jacoco/jacoco-agent.jar=output=tcpserver,address=*,port=6901,includes=eu.europa.ec.edelivery.smp.* $JAVA_OPTS"
  fi

  # for debugging
  JAVA_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9999 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost"
  JAVA_OPTS="$JAVA_OPTS -Xms512m -Xmx512m -server "
  # add allow encoded slashes and disable scheme for proxy
  JAVA_OPTS="$JAVA_OPTS -Dorg.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH=true -Djdk.http.auth.tunneling.disabledSchemes="
  # add truststore for eulogin
  if [ -e /tmp/keystores/smp-eulogin-mock.p12 ]; then
      echo "add eulogin trustStore: /tmp/keystores/smp-eulogin-mock.p12"
      JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStore=/tmp/keystores/smp-eulogin-mock.p12 -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=test123"
  fi

   # add external extensions
  for extensionLibFile in /tmp/artefacts/*.jar; do
    # Check if the glob gets expanded to existing files.
    [ -e "$extensionLibFile" ] &&  mv $extensionLibFile $SMP_HOME/apache-tomcat-$TOMCAT_VERSION/smp-libs || echo "Extensions do not exist"
  done


  echo "[INFO] init tomcat JAVA_OPTS: $JAVA_OPTS"
  export  JAVA_OPTS


  echo "[INFO] init tomcat folders: $tfile"
  if [ ! -d ${TOMCAT_DIR} ]; then
    mkdir -p ${TOMCAT_DIR}
  fi

  # move tomcat log folder to data folder
  if [ ! -d ${TOMCAT_DIR}/logs ]; then
    if [ ! -d  ${TOMCAT_HOME}/logs  ]; then
      mkdir -p ${TOMCAT_DIR}/logs
    else 
      mv ${TOMCAT_HOME}/logs ${TOMCAT_DIR}/
      rm -rf ${TOMCAT_HOME}/logs 
    fi
  fi
  rm -rf ${TOMCAT_HOME}/logs 
  ln -sf ${TOMCAT_DIR}/logs ${TOMCAT_HOME}/logs

  # move tomcat conf folder to data folder
  if [ ! -d ${TOMCAT_DIR}/conf ]; then
    mv ${TOMCAT_HOME}/conf ${TOMCAT_DIR}/ 
  fi
  rm -rf ${TOMCAT_HOME}/conf 
  ln -sf ${TOMCAT_DIR}/conf ${TOMCAT_HOME}/conf

  # move smp conf folder to data folder
  if [ ! -d ${TOMCAT_DIR}/classes ]; then
    mv ${TOMCAT_HOME}/classes ${TOMCAT_DIR}/
  fi
  rm -rf ${TOMCAT_HOME}/classes
  ln -sf ${TOMCAT_DIR}/classes ${TOMCAT_HOME}/

   # sleep a little to avoid mv issues
   sleep 5s
}

init_smp_properties() {
    echo "[INFO] init smp properties:"

    { echo "# SMP init parameters"
      echo "libraries.folder=$SMP_HOME/apache-tomcat-$TOMCAT_VERSION/smp-libs"
      echo "bdmsl.integration.logical.address=${SMP_LOGICAL_ADDRESS:-http://localhost:8080/smp/}"
      echo "authentication.blueCoat.enabled=true"
      echo "bdmsl.integration.enabled=true"
      echo "bdmsl.integration.physical.address=0.0.0.0"
      echo "bdmsl.participant.multidomain.enabled=false"
      echo "bdmsl.integration.url=http://localhost:8080/edelivery-sml/"
      echo "bdmsl.integration.logical.address=${SMP_LOGICAL_ADDRESS:-http://localhost:8080/smp/}"
    } >>  "$SMP_HOME/apache-tomcat-$TOMCAT_VERSION/classes/smp.config.properties"

    addOrReplaceProperties  "$SMP_HOME/apache-tomcat-$TOMCAT_VERSION/classes/smp.config.properties" "$SMP_INIT_PROPERTIES" "$SMP_INIT_PROPERTY_DELIMITER"
}


init_mysql() {
  echo "[INFO] init database:"
  if [ ! -d "/run/mysqld" ]; then
    mkdir -p /run/mysqld
    chown -R mysql:mysql /run/mysqld
  fi

  if [ ! -d ${MYSQL_DATA_DIR} ]; then
    # sleep a little to avoid mv issues
    sleep 3s
    mv /var/lib/mysql ${DATA_DIR}
  fi
  
  rm -rf /var/lib/mysql
  ln -sf ${MYSQL_DATA_DIR} /var/lib/mysql
  chmod -R 0777 ${MYSQL_DATA_DIR}
  chown -R mysql:mysql ${MYSQL_DATA_DIR}
  echo '[INFO] start MySQL'
  sleep 5s
  service mysql start
 
  echo "[INFO] create SMP database: ${SMP_DB_SCHEMA}"
  if [ -d ${MYSQL_DATA_DIR}/${SMP_DB_SCHEMA} ]; then
    echo "[INFO] MySQL ${SMP_DB_SCHEMA} already present, skipping creation"
  else 
    echo "[INFO] MySQL ${SMP_DB_SCHEMA}  not found, creating initial DBs"

    echo 'Create smp database'
    mysql -h localhost -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$MYSQL_ROOT_PASSWORD';drop schema if exists $SMP_DB_SCHEMA;DROP USER IF EXISTS $SMP_DB_USER;  create schema $SMP_DB_SCHEMA;alter database $SMP_DB_SCHEMA charset=utf8; create user $SMP_DB_USER identified by '$SMP_DB_USER_PASSWORD';grant all on $SMP_DB_SCHEMA.* to $SMP_DB_USER;"

    if [ -f "/tmp/custom-data/mysql5innodb.sql" ]
    then
        echo "Use custom database script! "
        mysql -h localhost -u root --password=$MYSQL_ROOT_PASSWORD $SMP_DB_SCHEMA < "tmp/custom-data/mysql5innodb.ddl"
    else
          echo "Use default database ddl script!"
           mysql -h localhost -u root --password=$MYSQL_ROOT_PASSWORD $SMP_DB_SCHEMA < "/tmp/smp-setup/database-scripts/mysql5innodb.ddl"
    fi

    if [ -f "/tmp/custom-data/mysql5innodb-data.sql" ]
    then
         echo "Use custom init script! "
         mysql -h localhost -u root --password=$MYSQL_ROOT_PASSWORD $SMP_DB_SCHEMA < "/tmp/custom-data/mysql5innodb-data.sql"
     else
        echo "Use default init script!"
         mysql -h localhost -u root --password=$MYSQL_ROOT_PASSWORD $SMP_DB_SCHEMA < "/tmp/smp-setup/database-scripts/mysql5innodb-data.sql"
    fi
  fi


  echo "[INFO] create SML database: ${SML_DB_SCHEMA}"
  if [ -d ${MYSQL_DATA_DIR}/${SML_DB_SCHEMA} ]; then
    echo "[INFO] MySQL $SML_DB_SCHEMA already present, skipping creation"
  else
    echo "[INFO] MySQL ${SML_DB_SCHEMA}  not found, creating initial DBs"

    echo 'Create sml database'
        mysql -h localhost -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$MYSQL_ROOT_PASSWORD';drop schema if exists $SML_DB_SCHEMA;DROP USER IF EXISTS $SML_DB_USER;  create schema $SML_DB_SCHEMA;alter database $SML_DB_SCHEMA charset=utf8; create user $SML_DB_USER identified by '$SML_DB_USER_PASSWORD';grant all on $SML_DB_SCHEMA.* to $SML_DB_USER;"

    if [ -f "/tmp/custom-data/sml-mysql5innodb.sql" ]
    then
        echo "Use custom database script! "
        mysql -h localhost -u root --password=$MYSQL_ROOT_PASSWORD $SML_DB_SCHEMA < "/tmp/custom-data/sml-mysql5innodb.ddl"
    else
          echo "Use default database ddl script!"
           mysql -h localhost -u root --password=$MYSQL_ROOT_PASSWORD $SML_DB_SCHEMA < "/tmp/sml-setup/database-scripts/mysql5innodb.ddl"
    fi

    if [ -f "/tmp/custom-data/sml-mysql5innodb-data.sql" ]
    then
         echo "Use custom init script! "
         mysql -h localhost -u root --password=$MYSQL_ROOT_PASSWORD $SML_DB_SCHEMA < "/tmp/custom-data/sml-mysql5innodb-data.sql"
     else
        echo "Use default init script!"
         mysql -h localhost -u root --password=$MYSQL_ROOT_PASSWORD $SML_DB_SCHEMA < "/tmp/sml-setup/database-scripts/mysql5innodb-data.sql"
    fi
  fi


  sleep 5s
  # start mysql 
 
}

addOrReplaceProperties() {

  PROP_FILE=$1
  INIT_PROPERTIES=$2
  INIT_PROPERTY_DELIMITER=$3
  
  # replace domibus properties
  if [ -n "$INIT_PROPERTIES" ]; then
    echo "Parse init properties: $INIT_PROPERTIES"
    # add delimiter also to end :)
    s="$INIT_PROPERTIES$INIT_PROPERTY_DELIMITER"

    array=()
    while [[ $s ]]; do
      array+=("${s%%"$INIT_PROPERTY_DELIMITER"*}")
      s=${s#*"$INIT_PROPERTY_DELIMITER"}
    done

    # replace parameters
    IFS='='
    for property in "${array[@]}"; do
      read -r key value <<<"$property"
      # escape regex chars and remove trailing and leading spaces..
      keyRE="$(printf '%s' "${key// }" | sed 's/[[\*^$()+?{|]/\\&/g')"
      propertyRE="$(printf '%s' "${property// }" | sed 's/[[\*^$()+?{|/]/\\&/g')"

      echo "replace or add property: [$keyRE] with value [$propertyRE]"
      # replace key line and commented #key line with new property
      sed -i "s/^$keyRE=.*/$propertyRE/;s/^#$keyRE=.*/$propertyRE/" $PROP_FILE
      # test if replaced if the line not exists add in on the end
      grep -qF -- "$propertyRE" "$PROP_FILE" || echo "$propertyRE" >>"$PROP_FILE"
    done

  fi
}


init_bind() {

  # move configuration if it does not exist
  if [ ! -d ${BIND_DATA_DIR} ]; then
    mv /etc/bind ${BIND_DATA_DIR}
    ## add custom configuration
    cp /opt/smlconf/bind/*.* ${BIND_DATA_DIR}/
  fi
  rm -rf /etc/bind
  ln -sf ${BIND_DATA_DIR} /etc/bind
  chmod -R 0775 ${BIND_DATA_DIR}
  chown -R ${BIND_USER}:${BIND_USER} ${BIND_DATA_DIR}

    # init data
    if [ -f "/tmp/custom-data/db.test.edelivery.local" ]
    then
        echo "Use custom zone file! "
        rm -rf /etc/bind/db.test.edelivery.local
        cp /tmp/custom-data/db.test.edelivery.local /etc/bind/
    fi

}

init_smp_properties
init_bind
init_mysql
init_tomcat


echo "Starting named..."
$(which named) -u ${BIND_USER} &> $BIND_DATA_DIR/bind-console.out &



echo '[INFO] start running SMP'
chmod u+x $SMP_HOME/apache-tomcat-$TOMCAT_VERSION/bin/*.sh
cd $SMP_HOME/apache-tomcat-$TOMCAT_VERSION/
# run from this folder in order to be smp log in logs folder
exec ./bin/catalina.sh jpda run




