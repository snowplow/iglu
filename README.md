# Iglu Schema Repository

[ ![Build Status][travis-image] ][travis]
[ ![Release][release-image] ][releases]
[ ![License][license-image] ][license]

Iglu is a machine-readable, open-source schema repository for **[JSON Schema][json-schema]** from the team at **[Snowplow Analytics][snowplow-website]**. A schema repository (sometimes called a registry) is like npm or Maven or git but holds data schemas instead of software or code.

Iglu is used extensively in **[Snowplow][snowplow-repo]**. For a presentation on how we came to build Iglu, see **[this blog post][snowplow-schema-post]**.

## Iglu technology 101

Iglu consists of two key components:

1. Clients that can resolve schemas from one or more Iglu repositories
2. Servers that can host an Iglu repository over HTTP

![iglu-technical-architecture][iglu-technical-architecture]

We also operate **[Iglu Central][iglu-central]** (**[repo][iglu-central-repo]**), which is like RubyGems.org or Maven Central but for storing publically-available JSON Schemas.

At this time, Iglu only supports **[self-describing JSON Schemas][self-desc-jsons]** that use **[SchemaVer][schemaver]**.

## Find out more

| **[Technical Docs][techdocs]**     | **[Setup Guide][setup]**     | **[Roadmap][roadmap]**           | **[Contributing][contributing]**           |
|-------------------------------------|-------------------------------|-----------------------------------|---------------------------------------------|
| [![i1][techdocs-image]][techdocs] | [![i2][setup-image]][setup] | [![i3][roadmap-image]][roadmap] | [![i4][contributing-image]][contributing] |

## Questions or need help?

All support for Iglu is handled through the standard Snowplow Analytics channels. Check out the **[Talk to us][talk-to-us]** page on the Snowplow wiki.

## Copyright and license

Iglu is copyright 2014-2016 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0][license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[travis-image]: https://travis-ci.org/snowplow/iglu.png?branch=master
[travis]: http://travis-ci.org/snowplow/iglu

[release-image]: https://img.shields.io/badge/release-6_Ceres-orange.svg?style=flat
[releases]: https://github.com/snowplow/snowplow/releases

[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
[license]: http://www.apache.org/licenses/LICENSE-2.0

[json-schema]: http://json-schema.org/
[snowplow-website]: http://snowplowanalytics.com
[snowplow-repo]: https://github.com/snowplow/snowplow
[iglu-central-repo]: https://github.com/snowplow/iglu-central

[snowplow-schema-post]: http://snowplowanalytics.com/blog/2014/06/06/making-snowplow-schemas-flexible-a-technical-approach/
[self-desc-jsons]: http://snowplowanalytics.com/blog/2014/05/15/introducing-self-describing-jsons/
[schemaver]: http://snowplowanalytics.com/blog/2014/05/13/introducing-schemaver-for-semantic-versioning-of-schemas/

[iglu-central]: http://iglucentral.com

[iglu-technical-architecture]: https://github.com/snowplow/iglu/wiki/technical-documentation/images/technical-architecture.png

[techdocs-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/techdocs.png
[setup-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/setup.png
[roadmap-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/roadmap.png
[contributing-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/contributing.png

[techdocs]: https://github.com/snowplow/iglu/wiki/Iglu-technical-documentation
[setup]: https://github.com/snowplow/iglu/wiki/Setting-up-Iglu
[roadmap]: https://github.com/snowplow/iglu/wiki/Product-roadmap
[contributing]: https://github.com/snowplow/iglu/wiki/Contributing

[talk-to-us]: https://github.com/snowplow/snowplow/wiki/Talk-to-us
