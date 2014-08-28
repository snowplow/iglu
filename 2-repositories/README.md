# Iglu repositories

An Iglu repository acts as a store of data schemas (currently JSON Schemas only). Hosting JSON Schemas in an Iglu repository allows you to use those schemas in Iglu-capable systems such as [Snowplow] [snowplow-wiki].

## Available repositories

We currently have two Iglu "repo" technologies available for deploying your Iglu repository - follow the links to find out more:

| **Repository**           | **Category** | **Description**                                            | **Status**       |
|:-------------------------|:-------------|:-----------------------------------------------------------|:-----------------|
| [JVM-embedded repo] [r1] | Embedded     | An Iglu repository embedded in a Java or Scala application | Production-ready |
| [Static repo] [r2]       | Remote       | An Iglu repository server structured as a static website   | Production-ready |
| [Scala repo server] [r3] | Remote       | A RESTful Iglu repository server written in Scala          | Beta             |

<a name="iglu-central" />
## Iglu Central

Iglu Central ([http://iglucentral.com] [iglucentral-website]) is a public repository of JSON Schemas hosted by [Snowplow Analytics] [snowplow-website].

We do not git-submodule Iglu Central into the main Iglu repository because it is fast-moving. Its GitHub repository is [snowplow/iglu-central] [iglucentral-repo].

## Find out more

| **[Technical Docs] [techdocs]**     | **[Setup Guide] [setup]**     | **[Roadmap] [roadmap]**           | **[Contributing] [contributing]**           |
|-------------------------------------|-------------------------------|-----------------------------------|---------------------------------------------|
| [![i1] [techdocs-image]] [techdocs] | [![i2] [setup-image]] [setup] | [![i3] [roadmap-image]] [roadmap] | [![i4] [contributing-image]] [contributing] |

[snowplow-wiki]: https://github.com/snowplow/snowplow/wiki

[r1]: ./jvm-embedded-repo
[r2]: ./static-repo
[r3]: ./scala-repo-server

[iglucentral-website]: http://iglucentral.com/
[iglucentral-repo]: https://github.com/snowplow/iglu-central
[snowplow-website]: http://snowplowanalytics.com

[techdocs-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/techdocs.png
[setup-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/setup.png
[roadmap-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/roadmap.png
[contributing-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/contributing.png

[techdocs]: https://github.com/snowplow/iglu/wiki/Iglu-repositories
[setup]: https://github.com/snowplow/iglu/wiki/Setting-up-an-Iglu-repository
[roadmap]: https://github.com/snowplow/iglu/wiki/Product-roadmap
[contributing]: https://github.com/snowplow/iglu/wiki/Contributing
