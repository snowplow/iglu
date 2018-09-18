#!/usr/bin/dumb-init /bin/sh
set -e

# If the config directory has been mounted through -v, we chown it.
if [ "$(stat -c %u ${SNOWPLOW_CONFIG_PATH})" != "$(id -u snowplow)" ]; then
  chown -R snowplow:snowplow ${SNOWPLOW_CONFIG_PATH}
fi

ENTRYPOINT=/opt/docker/bin/iglu-server

# Make an artifact executable for snowplow:snowplow
chmod +x $ENTRYPOINT

# Make sure we run the app as the snowplow user
exec gosu snowplow:snowplow $ENTRYPOINT "$@"