#!/bin/bash
# WARNING:
# *** do NOT use TABS for indentation, use SPACES
# *** TABS will cause errors in some linux distributions

# SCRIPT CONFIGURATION:
script_name=mcctl
intel_conf_dir=/etc/intel/cloudsecurity
package_name=management-console
package_dir=/opt/intel/cloudsecurity/${package_name}
package_config_filename=${intel_conf_dir}/${package_name}.properties
package_env_filename=${package_dir}/${package_name}.env
package_install_filename=${package_dir}/${package_name}.install
package_keystore_users_dir=/var/opt/intel/${package_name}/users
#mysql_required_version=5.0
#mysql_setup_log=/var/log/intel.${package_name}.install.log
#mysql_script_dir=${package_dir}/database
#glassfish_required_version=4.0
webservice_application_name=ManagementConsole
#java_required_version=1.7.0_51

# FUNCTION LIBRARY, VERSION INFORMATION, and LOCAL CONFIGURATION
if [ -f "${package_dir}/functions" ]; then . "${package_dir}/functions"; else echo "Missing file: ${package_dir}/functions"; exit 1; fi
if [ -f "${package_dir}/version" ]; then . "${package_dir}/version"; else echo_warning "Missing file: ${package_dir}/version"; fi
shell_include_files "${package_env_filename}" "${package_install_filename}"
load_conf 2>&1 >/dev/null
load_defaults 2>&1 >/dev/null
#if [ -f /root/mtwilson.env ]; then  . /root/mtwilson.env; fi

configure_keystore_dir() {
  # setup mtwilson.mc.keystore.dir
  local configured_keystore_dir="$MC_KEYSTORE_DIR"   #`read_property_from_file mtwilson.mc.keystore.dir ${package_config_filename}`
  package_keystore_users_dir=${configured_keystore_dir:-"${package_keystore_users_dir}"}
  if [[ "$configured_keystore_dir" != "$package_keystore_users_dir" ]]; then
    update_property_in_file mtwilson.mc.keystore.dir "${package_config_filename}" "${package_keystore_users_dir}"
  fi
  mkdir -p ${package_keystore_users_dir}
}

#configure_api_baseurl() {
#  # setup mtwilson.api.baseurl
#  local input_api_baseurl
#  if [ -n "${MTWILSON_API_BASEURL}" ]; then
#    mtwilson_api_baseurl="${MTWILSON_API_BASEURL}"
#  elif [ -n "${MTWILSON_SERVER}" ]; then
#    mtwilson_api_baseurl="https://${MTWILSON_SERVER}:8181"
#  else
#    local configured_api_baseurl=`read_property_from_file mtwilson.api.baseurl ${package_config_filename}`
#    prompt_with_default input_api_baseurl "Mt Wilson Server (IP, Hostname, or URL):" "${configured_server_url}"
#    if [[ "$input_api_baseurl" == "http*" ]]; then
#      mtwilson_api_baseurl="$input_api_baseurl"
#    else
#      mtwilson_api_baseurl="https://${input_api_baseurl}:8181"
#    fi
#  fi
#  update_property_in_file mtwilson.api.baseurl "${package_config_filename}" "${mtwilson_api_baseurl}"
#}

bootstrap_first_user() {
  echo "Configuring Management Console administrator username and password..."

#  local configured_keystore_dir=`read_property_from_file mtwilson.mc.keystore.dir ${package_config_filename}`
#  package_keystore_users_dir=${package_keystore_users_dir:-"${configured_keystore_dir}"}
#  local configured_api_baseurl=`read_property_from_file mtwilson.api.baseurl ${package_config_filename}`
#  local mtwilson_api_baseurl=${MTWILSON_API_BASEURL:-"${configured_api_baseurl}"}

  # run the bootstrap command
  mtwilson=`which mtwilson 2>/dev/null`
  if [ -z "$mtwilson" ]; then
    echo_failure "Missing mtwilson command line tool"
    return 1
  fi
  local datestr=`date +%Y-%m-%d.%H%M`

  # bootstrap administrator user with all privileges
  prompt_with_default MC_FIRST_USERNAME "Username:" "admin"
  prompt_with_default_password MC_FIRST_PASSWORD
  export MC_FIRST_USERNAME
  export MC_FIRST_PASSWORD
  mtwilson setup V2 create-admin-user

  # XXX as implemented, the BootstrapUser command only works when Management Console is on same server as Management Service... TODO need to add ssh for remote access, or ask for adtabase connection details and just go and approve it oursevels (well, from the java code)
#  export MC_FIRST_USERNAME MC_FIRST_PASSWORD
  #$mtwilson setup BootstrapUser --keystore.users.dir="${package_keystore_users_dir}" --mtwilson.api.baseurl="${MTWILSON_API_BASEURL}" "${MC_FIRST_USERNAME}" env:MC_FIRST_PASSWORD
  ####$mtwilson setup BootstrapUser ${package_keystore_users_dir} ${MTWILSON_API_BASEURL} "${MC_FIRST_USERNAME}" env:MC_FIRST_PASSWORD
  ####msctl approve-user ${package_keystore_users_dir} "${MC_FIRST_USERNAME}" "${MC_FIRST_PASSWORD}"
}

bootstrap_first_user__Not_Used() {
    echo "We are going to create the first login for the Management Console."
    echo "You will be prompted to provide a username and password."
    echo "After this is done, you can login to Management Console with this user to approve all other registration requests."

  local configured_keystore_dir="$MC_KEYSTORE_DIR"   #`read_property_from_file mtwilson.mc.keystore.dir ${package_config_filename}`
  package_keystore_users_dir=${configured_keystore_dir:-"${package_keystore_users_dir}"}
  if [[ "$configured_keystore_dir" != "$package_keystore_users_dir" ]]; then
    update_property_in_file mtwilson.mc.keystore.dir "${package_config_filename}" "${package_keystore_users_dir}"
  fi
  mkdir -p ${package_keystore_users_dir}
  mtwilson=`which mtwilson`
  if [ -z "$mtwilson" ]; then
    echo_failure "Missing mtwilson command line tool"
    return 1
  fi
  local datestr=`date +%Y-%m-%d.%H%M`
  mtwilson api CreateUser ${package_keystore_users_dir} | tee /tmp/${datestr}
  # the $(...) surrounding the backticks is to remove leading and trailing spaces
  keystoreFilename=$(echo `cat /tmp/${datestr} | grep "^Created keystore" | awk -F ':' '{ print $2 }'`)
  echo "Keystore filename: '${keystoreFilename}'"
  local default_server_url="$CONFIGURED_API_BASEURL"   #`read_property_from_file mtwilson.api.baseurl ${package_config_filename}`
  local server_url_in
  echo -n "Server URL [${default_server_url}]: "
  IFS= read server_url_in
  server_url_in=${server_url_in:-"${default_server_url}"}
  if [[ "$server_url_in" != "$default_server_url" ]]; then
    local should_save
    prompt_yes_no should_save "Save in ${package_config_filename}?"
    if [[ "$should_save" == "yes" ]]; then
      update_property_in_file mtwilson.api.baseurl "${package_config_filename}" "${server_url_in}"
    fi
    echo
  fi
  echo "Registering with Mt Wilson..."
  mtwilson api RegisterUser "${package_keystore_users_dir}/${keystoreFilename}" "${server_url_in}" Security

  # XXX assume management service is on the same machine
  #mtwilson remote ShowTrustHosts "${server_url_in}" | tee /tmp/${datestr}
  #local previousTrustedHosts=`cat /tmp/${datestr} | head -n 1`
  previousTrustedHosts=`msctl show mtwilson.api.trust`

 # echo "Remote mtwilson.api.trust: ${previousTrustedHosts}"
  echo "Adding localhost to mtwilson.api.trust to facilitate self-approval..."

  #mtwilson remote AddLocalHostTrust ${server_url_in}
  #mtwilson remote ShowTrustHosts "${server_url_in}" | tee /tmp/${datestr}
  #local currentTrustedHosts=`cat /tmp/${datestr} | head -n 1`
  #echo "Remote mtwilson.api.trust: ${currentTrustedHosts}"
  local currentTrustedHosts=$(hosts=(${previousTrustedHosts} 127.0.0.1); IFS=,; echo "${hosts[*]}")
  msctl edit mtwilson.api.trust "${currentTrustedHosts}"
  msctl restart

  echo "Self-approving with Mt Wilson..."
  mtwilson api ApproveUser "${package_keystore_users_dir}/${keystoreFilename}" "${server_url_in}" Security
  echo "Restoring previous mtwilson.api.trust..."
  #mtwilson remote SetTrustHosts "${server_url_in}" "${previousTrustedHosts}"
  #mtwilson remote ShowTrustHosts "${server_url_in}" | tee /tmp/${datestr}
  #local restoredTrustedHosts=`cat /tmp/${datestr} | head -n 1`
  msctl edit mtwilson.api.trust "${previousTrustedHosts}"
  msctl restart

#  echo "Remote mtwilson.api.trust: ${restoredTrustedHosts}"
}




setup_print_summary() {
  echo "Requirements summary:"
  if [ -n "$JAVA_HOME" ]; then
    echo "Java: $JAVA_VERSION"
  else
    echo "Java: not found"
  fi
  if using_glassfish; then
    if [ -n "$GLASSFISH_HOME" ]; then
      GLASSFISH_VERSION=`glassfish_version`
      echo "Glassfish: $GLASSFISH_VERSION"
    else
      echo "Glassfish: not found"
    fi
  fi
}

setup_interactive_install() {
  if using_mysql; then   
    if [ -n "$mysql" ]; then
      mysql_configure_connection "${package_config_filename}" mountwilson.mc.db
      mysql_create_database
      mtwilson setup InitDatabase mysql
    fi
  elif using_postgres; then
    if [ -n "$psql" ]; then
      postgres_configure_connection "${package_config_filename}" mountwilson.mc.db
      postgres_create_database
      mtwilson setup InitDatabase postgresql
    else
      echo "psql not defined"
      exit 1
    fi
  fi
  configure_keystore_dir
  configure_api_baseurl "${package_config_filename}"

  if [ -n "$GLASSFISH_HOME" ]; then
    glassfish_running
    if [ -z "$GLASSFISH_RUNNING" ]; then
      glassfish_start_report
    fi
  elif [ -n "$TOMCAT_HOME" ]; then
    tomcat_running
    if [ -z "$TOMCAT_RUNNING" ]; then
      tomcat_start_report
    fi
  fi  
 
  if [ -n "$MTWILSON_SETUP_NODEPLOY" ]; then
    webservice_start_report "${webservice_application_name}"
  else
    webservice_uninstall "${webservice_application_name}"
    webservice_install "${webservice_application_name}" "${package_dir}"/ManagementConsole.war
    webservice_running_report "${webservice_application_name}"
  fi

  bootstrap_first_user
}


setup() {
  #mysql_clear; java_clear; glassfish_clear;
  mtwilson setup-env > "${package_env_filename}"
  . "${package_env_filename}"
  java_detect
  echo "JAVA_HOME=$JAVA_HOME" >> $INSTALL_LOG_FILE
#  if [[ -z "$JAVA_HOME" || -z "$GLASSFISH_HOME" ]]; then
#      echo_warning "Missing one or more required packages"
#      setup_print_summary
#      exit 1
#  fi
  setup_interactive_install
}


RETVAL=0

# See how we were called.
case "$1" in
  version)
        echo "${package_name}"
  echo "Version ${VERSION:-Unknown}"
  echo "Build ${BUILD:-Unknown}"
        ;;
  start)
        webservice_start_report "${webservice_application_name}"
        ;;
  stop)
        webservice_stop_report "${webservice_application_name}"
        ;;
  status)
        if using_glassfish; then  
          glassfish_running_report
        elif using_tomcat; then
          tomcat_running_report
        fi
        webservice_running_report "${webservice_application_name}"
        ;;
  restart)
        webservice_stop_report "${webservice_application_name}"
        sleep 2
        webservice_start_report "${webservice_application_name}"
        ;;
  glassfish-restart)
        glassfish_restart
        ;;
  glassfish-stop)
        glassfish_shutdown
        ;;
  setup)
        setup
        ;;
  setup-admin-user)
        bootstrap_first_user
        ;;
  setup-env)
  # for sysadmin convenience
        mtwilson setup-env
        ;;
  setup-env-write)
  # for sysadmin convenience
        mtwilson setup-env > "${package_env_filename}"
  #echo "Saved environment in ${myenvdir}/${package_env_filename}"
        ;;
  edit)
        update_property_in_file "${2}" "${package_config_filename}" "${3}"
        ;;
  show)
        read_property_from_file "${2}" "${package_config_filename}"
        ;;
  uninstall)
        datestr=`date +%Y-%m-%d.%H%M`
        webservice_uninstall "${webservice_application_name}"
        if [ -f "${package_config_filename}" ]; then
          mkdir -p "${intel_conf_dir}"
          cp "${package_config_filename}" "${intel_conf_dir}"/${package_name}.properties.${datestr}
          echo "Saved configuration file in ${intel_conf_dir}/${package_name}.properties.${datestr}"
        fi
        # prevent disaster by ensuring that package_dir is inside /opt/intel
        if [[ "${package_dir}" == /opt/intel/* ]]; then
          rm -rf "${package_dir}"
        fi
  rm /usr/local/bin/${script_name}
        ;;
  help)
        echo "Usage: ${script_name} {setup|start|stop|status|uninstall}"
        ;;
  *)
        echo "Usage: ${script_name} {setup|start|stop|status|uninstall}"
        exit 1
esac

exit $RETVAL
