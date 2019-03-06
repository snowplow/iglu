# Iglu Server

Iglu Server is a RESTful schema registry, allowing users to publish, test and serve schemas via an easy-to-use RESTful interface.

## Quickstart guide

Assuming [SBT][sbt] is installed, use the one-time `setup` subcommand to set up PostgreSQL entities that will be used by the Iglu Server:

```bash
$ sbt "run setup --config application.conf"
```

To run the server itself, use:

```bash
$ sbt "run --config application.conf"
```

Alternatively, you can use a [Docker][docker] image:

```bash
$ docker run --name igludb -e POSTGRES_PASSWORD=iglusecret -p 5432:5432 -d postgres
$ docker exec -i -t $POSTGRES_CONTAINER psql -U postgres -c "CREATE DATABASE igludb"
$ docker run -d snowplow-docker-registry.bintray.io/snowplow/iglu-server:0.6.0 setup --config application.conf
$ docker exec -i -t $POSTGRES_CONTAINER psql -U postgres \
    -c "INSERT INTO permissions VALUES ('8f02f01f-3bc1-414b-9277-46d723fb46ad', '', TRUE, 'CREATE_VENDOR'::schema_action, '{"CREATE", "DELETE"}'::key_action[])" \
    igludb
$ docker run --name igluserver -p 8080 -d snowplow-docker-registry.bintray.io/snowplow/iglu-server:0.6.0 setup --config application.conf
```

## Differences with 0.5.0

## Consistency

1. Impossible to add `1-0-1` if `1-0-0` does not exist
2. Impossible to add private schema if previous version was public (and vice versa)
3. Changes in authentication service. All should be backward-compatible, but now access can be much more fine-grained, e.g. "key that can only create schemas for certain vendor"
4. Impossible to add a schema desribed as `iglu:com.snplow/foo/jsonschema/1-0-0` in payload at the `com.acme/bar/jsonschema/1-0-0` endpoint. `POST /api/schemas` accepts only schemas with self-desribing payload, `PUT /api/schemas/vendor/name/format/version` accepts both plain and self-describing schemas, but in latter case always performs a check

## New features

1. `/api/debug` endpoint (when `debug = true` in configuration file)
2. `/api/meta` enpoint TODO
3. `patchesAllowed` setting, prohibiting patches in production servers and allowing in Mini

## About to change

1. No more `metadata`/`body` query parameters `repr=canonical/meta/uri` should be used (they're supported but deprecated) instead for all entrypoints returning list of schemas
2. Deprecation of all form fields
3. Swagger is available on `http://$SERVER/static/swagger-ui/index.html`
4. For schema validation it is recommended to use `/api/validatation` endpoint (`/api/schemas/validate` is available, but deprecated)


## Find out more

| **[Technical Docs][techdocs]**     | **[Setup Guide][setup]**     | **[Roadmap][roadmap]**           | **[Contributing][contributing]**           |
|-------------------------------------|-------------------------------|-----------------------------------|---------------------------------------------|
| [![i1][techdocs-image]][techdocs] | [![i2][setup-image]][setup] | [![i3][roadmap-image]][roadmap] | [![i4][contributing-image]][contributing] |

## Copyright and license

Iglu Server is copyright 2014-2019 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0][license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[docker]: https://www.docker.com/products/docker-engine
[sbt]: https://www.scala-sbt.org/

[techdocs-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/techdocs.png
[setup-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/setup.png
[roadmap-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/roadmap.png
[contributing-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/contributing.png

[techdocs]: https://github.com/snowplow/iglu/wiki/Scala-repo-server
[setup]: https://github.com/snowplow/iglu/wiki/Scala-repo-server-setup
[roadmap]: https://github.com/snowplow/iglu/wiki/Product-roadmap
[contributing]: https://github.com/snowplow/iglu/wiki/Contributing

[license]: http://www.apache.org/licenses/LICENSE-2.0


# Quckstart

## Migration

Navigate to http://localhost:8080/static/swagger-ui/index.html


When migrating, first change the config file, then run migration

## Differences

