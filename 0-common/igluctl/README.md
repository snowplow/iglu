# Igluctl

[ ![Build Status] [travis-image] ] [travis] [ ![License] [license-image] ] [license]

Igluctl is command-line tool, allowing to perform most common tasks with **[Iglu] [iglu]** registries, such as:

* Generating static registry with DDLs
* Linting JSON Schemas
* Pushing JSON Schemas from static registry to full-featured

## User Quickstart

Download the latest Igluctl from Bintray:

```bash
$ wget http://dl.bintray.com/snowplow/snowplow-generic/igluctl_0.1.0.zip
$ unzip igluctl_0.1.0.zip
```

Assuming you have a recent JVM installed.

## CLI

### Generate DDL

You can transform JSON Schema into Redshift (other storages are coming) DDL, using `igluctl static generate` command.
This functionality was previously implemented as **[Schema Guru] [schema-guru]** (pre-0.7.0) `ddl` subcommand

```bash
$ ./igluctl_0.1.0 static generate {{input}}
```

### Push JSON Schemas

You can push your JSON Schemas from local filesystem to Iglu Scala Registry in batch manner using `igluctl static push` command.
This functionality was previously implemented as `registry-sync.sh` shell script.

```bash
$ ./igluctl_0.1.0 static push {{input}} {{registry_host}} {{apikey}}
```

### Linting

You can check your JSON Schema for vairous common mistakes using `igluctl lint` command.

```bash
$ ./igluctl_0.1.0 lint {{input}}
```

This check will include JSON Syntax validation (`required` is not empty, `maximum` is integer etc)
and also "sanity check", which checks that particular JSON Schema can always validate at least one possible JSON.


## Copyright and License

Igluctl is copyright 2016 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0] [license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


[travis]: https://travis-ci.org/snowplow/iglu
[travis-image]: https://travis-ci.org/snowplow/iglu.png?branch=master

[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
[license]: http://www.apache.org/licenses/LICENSE-2.0

[iglu]: https://github.com/snowplow/iglu
[schema-guru]: https://github.com/snowplow/schema-guru
