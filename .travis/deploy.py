#!/usr/bin/env python

"""
Script responsible for deploying Iglu subprojects
Need to be executed in Travis CI environment by tag (project/x.x.x)
Publish tag dependencies locally, build tag project using them and publish it
Each project's build process and dependencies need to be maintained manually
"""

# TODO: This script has known bug - if total output of all `execute` calls
# exceeds 65K - it hangs, which results in failed Travis build.
# That's why output of `sbt test` is supressed

from contextlib import contextmanager
import os
import re
import sys
import subprocess
import shutil


# Initial setup

if 'TRAVIS_TAG' in os.environ:
    TRAVIS_TAG = os.environ.get('TRAVIS_TAG')
else:
    sys.exit("Environment variable TRAVIS_TAG is unavailable")

if 'TRAVIS_BUILD_DIR' in os.environ:
    TRAVIS_BUILD_DIR = os.environ.get('TRAVIS_BUILD_DIR')
else:
    sys.exit("Environment variable TRAVIS_BUILD_DIR is unavailable")

if 'BINTRAY_SNOWPLOW_MAVEN_USER' in os.environ:
    BINTRAY_SNOWPLOW_MAVEN_USER = os.environ.get('BINTRAY_SNOWPLOW_MAVEN_USER')
else:
    sys.exit("Environment variable BINTRAY_SNOWPLOW_MAVEN_USER is unavailable")

if 'BINTRAY_SNOWPLOW_MAVEN_API_KEY' in os.environ:
    BINTRAY_SNOWPLOW_MAVEN_API_KEY = os.environ.get('BINTRAY_SNOWPLOW_MAVEN_API_KEY')
else:
    sys.exit("Environment variable BINTRAY_SNOWPLOW_MAVEN_API_KEY is unavailable")

if 'BINTRAY_SNOWPLOW_GENERIC_USER' in os.environ:
    BINTRAY_SNOWPLOW_GENERIC_USER = os.environ.get('BINTRAY_SNOWPLOW_GENERIC_USER')
else:
    sys.exit("Environment variable BINTRAY_SNOWPLOW_GENERIC_USER is unavailable")

if 'BINTRAY_SNOWPLOW_GENERIC_API_KEY' in os.environ:
    BINTRAY_SNOWPLOW_GENERIC_API_KEY = os.environ.get('BINTRAY_SNOWPLOW_GENERIC_API_KEY')
else:
    sys.exit("Environment variable BINTRAY_SNOWPLOW_GENERIC_API_KEY is unavailable")

try:
    (project, version) = TRAVIS_TAG.split("/")
except ValueError:
    sys.exit("Tag " + TRAVIS_TAG + " has unknown format, should be project-name/x.x.x")


# Helper functions

def output_if_error(sbt_output):
    """Callback to print stderr and fail deploy if exit status not successful"""
    (stdout, stderr) = sbt_output.communicate()
    if sbt_output.returncode != 0:
        print("Process has been failed.\n" + stdout)
        sys.exit(stderr)


def output_everything(sbt_output):
    """Callback to print stdout"""
    (stdout, stderr) = sbt_output.communicate()
    if sbt_output.returncode == 0:
        print(stdout)
    else:
        print("Process has been failed.\n" + stdout)
        sys.exit(stderr)


def get_version(sbt_output):
    """Callback to extract version from sbt command output"""


def execute(command, callback=output_if_error):
    """Execute shell command with optional callback"""
    formatted_command = " ".join(command) if (type(command) == list) else command
    print("Executing [{0}]".format(formatted_command))
    output = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if hasattr(callback, '__call__'):
        return callback(output)
    else:
        return output


def check_version():
    """Fail deploy if tag version doesn't match SBT version"""
    sbt_output = execute(['sbt', 'version', '-Dsbt.log.noformat=true'], None)

    print(sbt_output.stderr.read())
    for line in sbt_output.stdout.read().split("\n"):
        print(line)
        match = re.search('\[info\]\s*(\d+\.\d+\.\d+.*)$', line)
        if match:
            if match.group(1) != version:
                sys.exit("Version extracted from TRAVIS_TAG [{0}] doesn't conform declared in SBT [{1}]".format(version, match.group(1)))
            else:
                return

    sys.exit("Cannot find version in SBT output:\n" + str(sbt_output))


@contextmanager
def bintray_credentials(user, key):
    """Context manager allowing to use different credentials and delete them after use"""
    bintray_dir = os.path.expanduser("~/.bintray")
    credentials = os.path.join(bintray_dir, ".credentials")

    if os.path.isdir(bintray_dir) and os.path.isfile(credentials):
        sys.exit("Bintray credentials already exist. They should be deleted after each use")
    else:
        print("Creating bintray credentials")
        os.mkdir(bintray_dir)
        with open(credentials, 'a') as f:
            f.write("realm = Bintray API Realm\n")
            f.write('user = {0}\n'.format(user))
            f.write('host = api.bintray.com\n')
            f.write('password = {0}'.format(key))

        yield

        print("Deleting bintray credentials")
        shutil.rmtree(bintray_dir)


# Structure helping to maintain dependency graph
# This need to be maintained manually. Order doesn't matter
DEPENDENCY_GRAPH = {
    'scala-core': [],
    'schema-ddl': ['scala-core'],
    'igluctl': ['scala-core', 'schema-ddl']
}


def get_dependencies(package):
    """Get list of dependencies for package"""
    def get_deps(package):
        return flatten([get_deps(p) + [p] for p in DEPENDENCY_GRAPH[package]])

    def flatten(seq):
        return [item for sublist in seq for item in sublist]

    def uniquify(seq):
        checked = []
        for e in seq:
            if e not in checked:
                checked.append(e)
        return checked

    return uniquify(get_deps(package))


# Local publishing functions

def publish_local_scalacore():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "scala-core"))
    execute(['sbt', '+test', '--warn'])
    execute(['sbt', '+publishLocal'])
    execute(['sbt', 'project igluCoreCirce', '+test', '--warn'])
    execute(['sbt', 'project igluCoreCirce', '+publishLocal', '--warn'])
    execute(['sbt', 'project igluCoreJson4s', '+test'])
    execute(['sbt', 'project igluCoreJson4s', '+publishLocal', '--warn'])


def publish_local_schemaddl():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "schema-ddl"))
    execute(['sbt', '+test', '--warn'])
    execute(['sbt', '+publishLocal'])


def publish_local_igluctl():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "igluctl"))
    execute(['sbt', '+test', '--warn'])
    execute(['sbt', '+publishLocal'])


def publish_local(project):
    if project == "scala-core":
        publish_local_scalacore()
    elif project == "schema-ddl":
        publish_local_schemaddl()
    elif project == "igluctl":
        publish_local_igluctl()
    else:
        sys.exit(TRAVIS_TAG + " is unknwon project. You need to add it to deploy.py")


# Final publishing functions

def publish_scalacore():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "scala-core"))
    check_version()
    with bintray_credentials(BINTRAY_SNOWPLOW_MAVEN_USER, BINTRAY_SNOWPLOW_MAVEN_API_KEY):
        execute(['sbt', '+test', '--warn'])
        execute(['sbt', '+publish'])
        execute(['sbt', '+bintraySyncMavenCentral'])
        execute(['sbt', 'project igluCoreCirce', '+test', '--warn'])
        execute(['sbt', 'project igluCoreCirce', '+publish'])
        execute(['sbt', 'project igluCoreCirce', '+bintraySyncMavenCentral'])
        execute(['sbt', 'project igluCoreJson4s', '+test', '--warn'])
        execute(['sbt', 'project igluCoreJson4s', '+publish'])
        execute(['sbt', 'project igluCoreJson4s', '+bintraySyncMavenCentral'])


def publish_schemaddl():
    os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "schema-ddl"))
    check_version()
    execute(['sbt', '+test', '--warn'])
    with bintray_credentials(BINTRAY_SNOWPLOW_MAVEN_USER, BINTRAY_SNOWPLOW_MAVEN_API_KEY):
        execute(['sbt', '+publish'])
        execute(['sbt', '+bintraySyncMavenCentral'])


def publish_igluctl():
    with bintray_credentials(BINTRAY_SNOWPLOW_GENERIC_USER, BINTRAY_SNOWPLOW_GENERIC_API_KEY):
        os.chdir(os.path.join(TRAVIS_BUILD_DIR, "0-common", "igluctl"))
        check_version()
        print("Test and publish igluctl")
        execute(['sbt', 'test', '--warn'])
        execute(['sbt', 'universal:publish'])


def publish():
    if project == "scala-core":
        publish_scalacore()
    elif project == "schema-ddl":
        publish_schemaddl()
    elif project == "igluctl":
        publish_igluctl()
    else:
        sys.exit(TRAVIS_TAG + " is unknwon project. You need to add it to deploy.py")


if __name__ == "__main__":
    # Publish locally all dependencies
    [publish_local(dependency) for dependency in get_dependencies(project)]

    # Publish project specified by tag
    publish()
