#!/bin/bash

set -e 
tag=$1

project="scala-core/"
project_len=${#project}

cicd=${tag:0:${project_len}}
release=${tag:${project_len}}

limit=30    # Timeout to extend Travis build time (from 10 mins)

if [ "${cicd}" == "${project}" ]; then
    if [ "${release}" == "" ]; then
        echo "WARNING! No release specified! Ignoring."
        exit 2
    fi
else
    echo "This can't be deployed - there's no ${project} tag! (Is the travis condition set?)"
    exit 1
fi

mkdir ~/.bintray/
FILE=$HOME/.bintray/.credentials
cat <<EOF >$FILE
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_SNOWPLOW_MAVEN_USER
password = $BINTRAY_SNOWPLOW_MAVEN_API_KEY
EOF

cd "${TRAVIS_BUILD_DIR}/0-common/scala-core"

project_version=$(sbt version -Dsbt.log.noformat=true | tail -n 1 | perl -ne 'print $1 if /(\d+\.\d+[^\r\n]*)/')

function travis_wait {
  minutes=0
  while kill -0 $! >/dev/null 2>&1; do
    echo -n -e " \b" # never leave evidences!
  
    if [ $minutes == $limit ]; then
      break;
    fi
  
    minutes=$((minutes+1))
  
    sleep 60
  done
}

if [ "${project_version}" == "${release}" ]; then
    # igluCore
    echo "DEPLOY: testing iglu-core..."
    sbt +test --warn
    echo "DEPLOY: publishing iglu-core..."
    sbt +publish
    echo "DEPLOY: publishing iglu-core to Maven Central..."
    sbt +bintraySyncMavenCentral &
    travis_wait

    # igluCoreCirce
    echo "DEPLOY: testing iglu-core-circe..."
    sbt "project igluCoreCirce" +test --warn
    echo "DEPLOY: publishing iglu-core-circe..."
    sbt "project igluCoreCirce" +publish
    echo "DEPLOY: publishing iglu-core-circe to Maven Central..."
    sbt "project igluCoreCirce" +bintraySyncMavenCentral &
    travis_wait

    # igluCoreJson4s
    echo "DEPLOY: testing iglu-core-json4s..."
    sbt "project igluCoreJson4s" +test --warn
    echo "DEPLOY: publishing iglu-core-json4s..."
    sbt "project igluCoreJson4s" +publish
    echo "DEPLOY: publishing iglu-core-json4s to Maven Central..."
    sbt "project igluCoreJson4s" +bintraySyncMavenCentral &
    travis_wait
    echo "DEPLOY: Iglu Core deployed..."


else
    echo "Tag version '${release}' doesn't match version in scala project ('${project_version}'). Aborting!"
    exit 1
fi
