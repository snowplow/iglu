# Iglu Scala Core [![Build Status](https://travis-ci.org/snowplow/iglu.png)](https://travis-ci.org/snowplow/iglu)

Core entities for work with Iglu in Scala.

### SchemaKey

`SchemaKey` is class holding information used to describe Schema or Instance.
For Self-describing JSONs it placed as URI in `$.schema` key.
For JSON Schema it placed in `$.self` key as JSON Object.

More information about Self-describing JSONs can be found in blog post: **[Introducing self-describing JSONs] [introducing-self-describing]**

### SchemaVer

`SchemaVer` is class holding information about Schema version in SchemaVer format.

More information can be found in blog post: **[Introducing SchemaVer for semantic versioning of schemas] [introducing-schemaver]**

### SchemaCriterion

`SchemaCriterion` is class holding information allowing to filter (or query) Schemas by specified parameters.

### SelfDescribed

Iglu Scala Core designed to be zero-dependency and generic as possible.
That is why instead of providing hardcoded dependencies, we're providing a type class `SelfDescribed` that our users can easily instantiate themselves.
It is intended to allow any 3rd-party libraries (Json4s, Jackson, circe etc) and even non-JSON formats like Thrift to seamlessly use entities from `iglu-core`.

`SelfDescribed` is an essence of Self-describing entity, telling user that entity of some type `E` can contain a reference to its own Schema, and can extract it as `SchemaKey`.
Instance example for Json4s library can be found in IgluCoreCommon.scala file in test suite.

## Developer quickstart

Assuming git, **[Vagrant] [vagrant-install]** and **[VirtualBox] [virtualbox-install]** installed:

```bash
 host> git clone https://github.com/snowplow/iglu
 host> cd iglu
 host> vagrant up && vagrant ssh
guest> cd /vagrant/0-common/scala-core
guest> sbt compile
```

## Find out more

| **[Technical Docs] [techdocs]**     | **[Setup Guide] [setup]**     | **[Roadmap] [roadmap]**           | **[Contributing] [contributing]**           |
|-------------------------------------|-------------------------------|-----------------------------------|---------------------------------------------|
| [![i1] [techdocs-image]] [techdocs] | [![i2] [setup-image]] [setup] | [![i3] [roadmap-image]] [roadmap] | [![i4] [contributing-image]] [contributing] |

## Copyright and license

Iglu Scala Core is copyright 2016 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0] [license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[introducing-self-describing]: http://snowplowanalytics.com/blog/2014/05/15/introducing-self-describing-jsons/
[introducing-schemaver]: http://snowplowanalytics.com/blog/2014/05/13/introducing-schemaver-for-semantic-versioning-of-schemas/

[iglu-wiki]: https://github.com/snowplow/iglu/wiki
[snowplow-schema-post]: http://snowplowanalytics.com/blog/2014/06/06/making-snowplow-schemas-flexible-a-technical-approach/

[snowplow-repo]: https://github.com/snowplow/snowplow
[snowplow-website]: http://snowplowanalytics.com

[vagrant-install]: http://docs.vagrantup.com/v2/installation/index.html
[virtualbox-install]: https://www.virtualbox.org/wiki/Downloads

[techdocs]: https://github.com/snowplow/iglu/wiki/Scala-client
[setup]: https://github.com/snowplow/iglu/wiki/Scala-client-setup
[roadmap]: https://github.com/snowplow/iglu/wiki/Product-roadmap
[contributing]: https://github.com/snowplow/iglu/wiki/Contributing

[techdocs-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/techdocs.png
[setup-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/setup.png
[roadmap-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/roadmap.png
[contributing-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/contributing.png

[license]: http://www.apache.org/licenses/LICENSE-2.0
