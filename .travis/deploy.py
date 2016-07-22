#!/usr/bin/env python

import os
import re
import sys
import subprocess


if 'TRAVIS_TAG' in os.environ:
    TRAVIS_TAG = os.environ.get('TRAVIS_TAG')
else:
    sys.exit("Environment variable TRAVIS_TAG is unavailable")

if 'TRAVIS_BUILD_DIR' in os.environ:
    TRAVIS_BUILD_DIR = os.environ.get('TRAVIS_BUILD_DIR')
else:
    sys.exit("Environment variable TRAVIS_BUILD_DIR is unavailable")

if 'BINTRAY_USER' in os.environ:
    BINTRAY_USER = os.environ.get('BINTRAY_USER')
else:
    sys.exit("Environment variable BINTRAY_USER is unavailable")

if 'BINTRAY_API_KEY' in os.environ:
    BINTRAY_API_KEY = os.environ.get('BINTRAY_API_KEY')
else:
    sys.exit("Environment variable BINTRAY_API_KEY is unavailable")

try:
    (project, version) = TRAVIS_TAG.split("/")
except ValueError:
    sys.exit("Tag " + TRAVIS_TAG + " has unknown format, should be project/")


# Structure helping to maintain dependency graph
# This need to be maintained manually. Order doesn't matter
deps = {
    'scala-core': [],
    'schema-ddl': ['scala-core'],
    'igluctl': ['scala-core', 'schema-ddl']
}


def get_deps(package):
    return flatten([get_deps(p) + [p] for p in deps.get(package)])

def flatten(seq):
    return [item for sublist in seq for item in sublist]

def uniquify(seq):
   checked = []
   for e in seq:
       if e not in checked:
           checked.append(e)
   return checked

def build_deps(package):
    return uniquify(get_deps(package))

def check_version(version):
    sbt_version = execute(['sbt', 'version', '-Dsbt.log.noformat=true'], get_version)

    if sbt_version != version:
        sys.exit("Version extracted from TRAVIS_TAG [{0}] doesn't conform declared in SBT [{1}]".format(version, sbt_version))


def create_bintray():
    bintray_dir = os.path.expanduser("~/.bintray")
    credentials = os.path.join(bintray_dir, ".credentials")

    if os.path.isdir(bintray_dir) and os.path.isfile(credentials):
        return
    else:
        os.mkdir(bintray_dir)
        with open(credentials, 'a') as f:
            f.write("realm = Bintray API Realm\n")
            f.write('user = {0}\n'.format(BINTRAY_USER))
            f.write('host = api.bintray.com\n')
            f.write('password = {0}'.format(BINTRAY_API_KEY))



def publish_local_scalacore():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "scala-core"))
    execute(['sbt', '+publishLocal'], output_if_error)
    execute(['sbt', 'project iglu-core-circe', '+publishLocal'], output_if_error)
    execute(['sbt', 'project iglu-core-json4s', '+publishLocal'], output_if_error)


def publish_local_schemaddl():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "schema-ddl"))
    execute(['sbt', '+publishLocal'], output_if_error)


def publish_local_igluctl():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "igluctl"))
    execute(['sbt', '+publishLocal'], output_if_error)


def publish_scalacore():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "scala-core"))
    check_version(version)
    execute(['sbt', '+publish'], output_if_error)
    execute(['sbt', '+bintraySyncMavenCentral'], output_if_error)
    execute(['sbt', 'project iglu-core-circe', '+publish'], output_if_error)
    execute(['sbt', 'project iglu-core-circe', '+bintraySyncMavenCentral'], output_if_error)
    execute(['sbt', 'project iglu-core-json4s', '+publish'], output_if_error)
    execute(['sbt', 'project iglu-core-json4s', '+bintraySyncMavenCentral'], output_if_error)


def publish_schemaddl():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "schemaddl"))
    check_version(version)
    execute(['sbt', '+publish'], output_if_error)
    execute(['sbt', '+bintraySyncMavenCentral'], output_if_error)


def publish_igluctl():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "igluctl"))
    check_version(version)
    execute(['sbt', '+publish'], output_if_error)
    execute(['sbt', '+bintraySyncMavenCentral'], output_if_error)



def output_if_error(sbt_output):
    (stdout, stderr) = sbt_output.communicate()
    if sbt_output.returncode != 0:
        print("Process has been failed.\n" + stdout)
        sys.exit(stderr)


def execute(command, callback=output_if_error):
    formatted_command = " ".join(command) if (type(command) == list) else command
    print("Executing [{0}]".format(formatted_command))
    output = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if hasattr(callback, '__call__'):
        return callback(output)
    else:
        return output


def get_version(sbt_output):
    for line in sbt_output.stdout.read().split("\n"):
        match = re.search('\[info\]\s*(\d+\.\d+\.\d+.*)$', line)
        if match: return match.group(1)
    sys.exit("Cannot find version in SBT output:\n" + str(sbt_output))


def publish():
    if project == "scala-core":
        publish_scalacore()
    elif project == "schema-ddl":
        publish_schemaddl()
    elif project == "igluctl":
        publish_igluctl()
    else:
        sys.exit(TRAVIS_TAG + " is unknwon project. You need to add it to cicd.py")


def publishLocal(project):
    if project == "scala-core":
        publish_local_scalacore()
    elif project == "schema-ddl":
        publish_local_schemaddl()
    elif project == "igluctl":
        publish_local_igluctl()
    else:
        sys.exit(TRAVIS_TAG + " is unknwon project. You need to add it to cicd.py")


# Publish locally all dependencies
[publishLocal(dependency) for dependency in build_deps(project)]

# Publish project specified by tag
create_bintray()
publish()
