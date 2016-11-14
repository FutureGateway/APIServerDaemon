#
# APIServerDaemon makefile
#
PKGNUM = 0
VERSION = $$(git tag | tail -n 1 | sed s/v//)
DEBPKGDIR = APIServerDaemon_$(VERSION)-$(PKGNUM)
SRCPKGDIR = packages
DEBPKGNAME = APIServerDaemon_$(VERSION)-$(PKGNUM).deb
PKGDIR = /tmp/$(DEBPKGDIR)
FGUSER = futuregateway
HOMEDIR = home
define DEBCONTROL
Package: APIServerDaemon 
Version: %VERSION% 
Section: base
Priority: optional
Architecture: all
Depends: openssh-client, openssh-server, mysql-client, openjdk-7-jdk, ant, maven, tomcat7 
Homepage: https://github.com/indigo-dc/APIServerDaemon
Maintainer: Riccardo Bruno <riccardo.bruno@ct.infn.it>
Description: FutureGateway' API Server queue daemon component
 FutureGateway' APIServer daemon
 This package installs the FutureGateway' APIServer daemon.
endef

define DEBPOSTINST
#!/bin/bash
#
# APIServerDaemon post-installation script
#
FGPRODUCT=APIServerDaemon
FGUSER=$(FGUSER)
HOMEDIR=$(HOMEDIR)

# Timestamp
get_ts() {
  TS=$$(date +%y%m%d%H%M%S)
}

# Output function notify messages
# Arguments: $$1 - Message to print
#            $$2 - No new line if not zero
#            $$3 - No timestamp if not zero
out() {
  # Get timestamp in TS variable  
  get_ts

  # Prepare output flags
  OUTCMD=echo
  MESSAGE=$$1
  NONEWLINE=$$2
  NOTIMESTAMP=$$3 
  if [ "$$NONEWLINE" != "" -a $$((1*NONEWLINE)) -ne 0 ]; then
    OUTCMD=printf
  fi
  if [ "$$3" != "" -a $$((1*NOTIMESTAMP)) -ne 0 ]; then
    TS=""
  fi
  OUTMSG=$$(echo $$TS" "$$MESSAGE)
  $$OUTCMD "$$OUTMSG" >&1
  if [ "$$FGLOG" != "" ]; then
    $$OUTCMD "$$OUTMSG" >> $$FGLOG
  fi  
}

# Error function notify about errors
err() {
  get_ts
  echo $$TS" "$$1 >&2 
}

# Check if the FGUSER exists
check_user() {
  getent passwd $$FGUSER >/dev/null 2>&1
  return $$?
}

# Show output and error files
outf() {
  OUTF=$$1
  ERRF=$$2
  if [ "$$OUTF" != "" -a -f "$$OUTF" ]; then
    while read out_line; do
      out "$$out_line"
    done < $$OUTF
  fi
  if [ "$$ERRF" != "" -a -f "$$ERRF" ]; then
    while read err_line; do
      err "$$err_line"
    done < $$ERRF
  fi
}

# Execute the given command
# $$1 Command to execute
# $$2 If not null does not print the command (optional)
cmd_exec() {
  TMPOUT=$$(mktemp /tmp/fg_out_XXXXXXXX)
  TMPERR=$$(mktemp /tmp/fg_err_XXXXXXXX)
  if [ "$$2" = "" ]; then
    out "Executing: '""$$1""'"
  fi  
  eval "$$1" >$$TMPOUT 2>$$TMPERR
  RET=$?
  outf $$TMPOUT $$TMPERR
  rm -f $$TMPOUT
  rm -f $$TMPERR
  return $$RET
}

#
# Post installation script
#
out "$$FGPRODUCT post install (begin)"
if check_user; then
  out "User $$FGUSER already exists"
else
  out "Creating $$FGUSER"
  cmd_exec "adduser --disabled-password --gecos \"\" $F$GUSER"
fi
cmd_exec "tar xvfz /$$HOMEDIR/$$FGUSER/APIServerDaemon_lib.tar.gz -C /$$HOMEDIR/$$FGUSER/$$FGPRODUCT/web/WEB-INF"
cmd_exec "export CATALINA_BASE=\$$(/usr/share/tomcat7/bin/catalina.sh version | grep CATALINA_BASE | awk '{ print \$$3 }' | xargs echo)"
cmd_exec "export CATALINA_HOME=\$$(/usr/share/tomcat7/bin/catalina.sh version | grep CATALINA_HOME | awk '{ print \$$3 }' | xargs echo)"
cmd_exec "cd /$$HOMEDIR/$$FGUSER/$$FGPRODUCT && ant all && cd -"
cmd_exec "mkdir -p $$CATALINA_HOME/webapps"
cmd_exec "cp /$$HOMEDIR/$$FGUSER/$$FGPRODUCT/dist/$$FGPRODUCT.war /var/lib/tomcat7/webapps"
out "$$FGPRODUCT post install (end)"
endef

help:
	@echo "APIServerDaemon Makefile"
	@echo "Rules:"
	@echo "    deb  - Create the $(DEBPKGNAME) package file (Ubuntu 14.04LTS)"
	@echo "    help - Display this message"
 
export DEBPOSTINST
export DEBCONTROL
deb:
	@echo "Creating deb package: '"$(DEBPKGNAME)"'"
	@echo "Pakcage dir: '"$(PKGDIR)"'"
	@if [ -d $(PKGDIR) ]; then rm -rf $(PKGDIR); fi
	@mkdir -p $(PKGDIR)/DEBIAN
	@mkdir -p $(PKGDIR)/$(HOMEDIR)/$(FGUSER)/$(DEBPKGDIR)
	@cd $(PKGDIR)/$(HOMEDIR)/$(FGUSER) && ln -s $$(ls -1 | head -n 1) APIServerDaemon && cd -
	@cp -r . $(PKGDIR)/$(HOMEDIR)/$(FGUSER)/$(DEBPKGDIR)
	@rm -rf $(PKGDIR)/.git
	@[ -f APIServerDaemon_lib.tar.gz ] || wget http://sgw.indigo-datacloud.eu/fgsetup/APIServerDaemon_lib.tar.gz -O $(PKGDIR)/$(HOMEDIR)/$(FGUSER)/APIServerDaemon_lib.tar.gz
	@mkdir -p $(SRCPKGDIR)
	@echo "$$DEBCONTROL" > $(PKGDIR)/DEBIAN/control
	@echo "$$DEBPOSTINST" > $(PKGDIR)/DEBIAN/postinst
	@chmod 775 $(PKGDIR)/DEBIAN/postinst
	sed -i -e "s/%VERSION%/$(VERSION)-$(PKGNUM)/" $(PKGDIR)/DEBIAN/control
	@dpkg-deb --build $(PKGDIR)
	@cp /tmp/$(DEBPKGNAME) $(SRCPKGDIR)
	#@rm -rf $(PKGDIR)
