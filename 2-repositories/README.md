# Iglu repositories

An [Iglu repository][techdocs] (or registry) acts as a store of data schemas. Hosting JSON Schemas in an Iglu repository allows you to use those schemas in Iglu-capable systems such as [Snowplow][snowplow-website].

## Available Iglu repositories

| **Repository**                | **Category** | **Description**                                            | **Status**       |
|:------------------------------|:-------------|:-----------------------------------------------------------|:-----------------|
| [Iglu Central][r1]            | Remote       | A public repository of JSON Schemas hosted by Snowplow     | Production-ready |
| [Iglu Server][r2]             | Remote       | A RESTful Iglu repository server written in Scala          | Production-ready |
| [JVM-embedded repo][r3]       | Embedded     | An Iglu repository embedded in a Java or Scala application | Production-ready |
| [Static registry][r4]         | Remote       | An Iglu repository server structured as a static website   | Production-ready |
| [Example Schema Registry][r5] | Remote       | An example schema registry                                 | Example          |

[r1]: https://docs.snowplow.io/docs/pipeline-components-and-applications/iglu/iglu-repositories/iglu-central/
[r2]: https://docs.snowplow.io/docs/pipeline-components-and-applications/iglu/iglu-repositories/iglu-server/
[r3]: https://docs.snowplow.io/docs/pipeline-components-and-applications/iglu/iglu-repositories/jvm-embedded-repo/
[r4]: https://docs.snowplow.io/docs/pipeline-components-and-applications/iglu/iglu-repositories/static-repo/
[r5]: https://github.com/snowplow/iglu-example-schema-registry

[snowplow-website]: https://snowplow.io
[techdocs]: https://docs.snowplow.io/docs/pipeline-components-and-applications/iglu/iglu-repositories/
