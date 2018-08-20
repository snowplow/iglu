# Schema DDL

[![Release][release-image]][releases] [![License][license-image]][license]

Schema DDL is a set of Abstract Syntax Trees and generators for producing various DDL and Schema formats.
It's tightly coupled with other tools from **[Snowplow Platform][snowplow]** like
**[Iglu][iglu]** and **[Self-describing JSON][self-describing]**.

Schema DDL itself does not provide any CLI and expose only Scala API.

## Quickstart

Schema DDL is compiled against Scala 2.11 and 2.12 and availble on Maven Central. In order to use it with SBT, include following module:

```scala
libraryDependencies += "com.snowplowanalytics" %% "schema-ddl" % "0.8.0"
```

## Current features

### Flatten Schema

To process JSON Schema in typesafe manner sometimes it's necessary to represent it's nested structure as map of paths to properties.
``schemaddl.generators.SchemaFlattener.flattenJsonSchema`` can be used for that.
It accepts JSON Schema as ``json4s.JValue`` and returns ``schemaddl.FlatSchema``.

### Redshift DDL

Current main feature of Schema DDL is to produce Redshift table DDL (with or without Snowplow-specific data).
``schemaddl.generators.redshift.getTableDdl`` method can be used for that.
It accepts ``schemaddl.FlatSchema`` and produces Redshift DDL file with warnings like product types
(eg. boolean, string) which cannot be correctly translated into DDL without some manual labor.

Also there's ``schemaddl.generators.redshift.Ddl`` module providing AST-like structures for generating DDL in flexible and type-safe manner.

### JSON Paths

Amazon Redshift uses **[COPY][redshift-copy]** command to load data into table.
To map data into columns JSONPaths file used.
It may be generated with ``schemaddl.generators.redshift.JsonPathGenerator.getJsonPathsFile`` method.
Which accepts list of ``schemaddl.generators.redshift.Ddl.Column`` objects (which can be taken from ``Table`` DDL object) and returns JSONPaths file as a string.
It's coupled with ``Table`` object to preserve structure of the table.
For example, you may want to modify list of your ``Column``s by rearranging it depending on some properties,
but JSONPaths file always should have the same order of fields and thus we cannot rely on ``FlatSchema`` object.


## Copyright and License

Schema DDL is copyright 2014-2017 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0][license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
[license]: http://www.apache.org/licenses/LICENSE-2.0

[release-image]: http://img.shields.io/badge/release-0.6.0-blue.svg?style=flat
[releases]: https://github.com/snowplow/iglu/releases

[snowplow]: https://github.com/snowplow/snowplow
[iglu]: https://github.com/snowplow/iglu
[self-describing]: http://snowplowanalytics.com/blog/2014/05/15/introducing-self-describing-jsons/
[redshift-copy]: http://docs.aws.amazon.com/redshift/latest/dg/r_COPY.html
