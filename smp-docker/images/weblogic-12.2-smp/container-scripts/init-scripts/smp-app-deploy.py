    # Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
#
#Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#

# WLST Offline for deploying an application under APP_NAME packaged in APP_PKG_FILE located in APP_PKG_LOCATION
# It will read the domain under DOMAIN_HOME by default
#
# author: Monica Riccelli <monica.riccelli@oracle.com>
#
import os

# Deployment Information
domainhome = os.environ.get('WL_DOMAIN_HOME', '/u01/oracle/user_projects/domains/base_domain')
admin_name = os.environ.get('WL_ADMIN_NAME', 'AdminServer')
appVersion    = os.environ.get('SMP_VERSION', '5.0')
appfilename    = os.environ.get('WL_APP_FILE_NAME', 'smp')
appname    = os.environ.get('WL_APP_NAME', appfilename+'#'+appVersion)
appfile    = os.environ.get('APP_FILE', 'smp.war')
appdir     = os.environ.get('WL_DOMAIN_HOME')
cluster_name =   os.environ.get('WL_CLUSTER_NAME')
target_name =   os.environ.get('WL_DEPLOYMENT_TARGET')

print('Domain Home      : [%s]' % domainhome)
print('Admin Name       : [%s]' % admin_name)
print('Cluster Name     : [%s]' % cluster_name)
print('Deployment target: [%s]' % target_name)
print('Application Name : [%s]' % appname)
print('appfile          : [%s]' % appfile)
print('appdir           : [%s]' % appdir)
# Read Domain in Offline Mode
# ===========================
readDomain(domainhome)

# Create Application
# ==================
cd('/')
app = create(appname, 'AppDeployment')
app.setSourcePath(appdir + '/' + appfile)
app.setStagingMode('nostage')


# Assign application to AdminServer
# =================================
assign('AppDeployment', appname, 'Target', target_name)

# Update Domain, Close It, Exit
# ==========================
updateDomain()
closeDomain()
exit()
