#!/bin/bash

tag=$1
release=${tag:11}

mkdir ~/.bintray/
FILE=$HOME/.bintray/.credentials
cat <<EOF >$FILE
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_USER
password = $BINTRAY_API_KEY
EOF

cd $TRAVIS_BUILD_DIR
cd 0-common/scala-core
pwd

project_version=$(sbt version -Dsbt.log.noformat=true | perl -ne 'print $1 if /(\d+\.\d+[^\r\n]*)/')
if [ "${project_version}" == "${release}" ]; then
    sbt +publish
    sbt +bintraySyncMavenCentral
    sbt "project iglu-core-circe" +publish
    sbt "project iglu-core-circe" +bintraySyncMavenCentral
    sbt "project iglu-core-json4s" +publish
    sbt "project iglu-core-json4s" +bintraySyncMavenCentral
else
    echo "Tag version '${release}' doesn't match version in scala project ('${project_version}'). Aborting!"
    exit 1
fi
