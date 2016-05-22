# Scala Iglu Core [![Build Status](https://travis-ci.org/snowplow/iglu.png)](https://travis-ci.org/snowplow/iglu)

Core entities for working with Iglu in Scala.

Recent documentation can be found on dedicated wiki page: **[Iglu Scala Core] [techdocs]**.

## Developer quickstart

Assuming git, **[Vagrant] [vagrant-install]** and **[VirtualBox] [virtualbox-install]** installed:

```bash
 host> git clone https://github.com/snowplow/iglu
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


[snowplow-repo]: https://github.com/snowplow/snowplow
[snowplow-website]: http://snowplowanalytics.com

[vagrant-install]: http://docs.vagrantup.com/v2/installation/index.html
[virtualbox-install]: https://www.virtualbox.org/wiki/Downloads

[techdocs]: https://github.com/snowplow/iglu/wiki/Scala-iglu-core
[roadmap]: https://github.com/snowplow/iglu/wiki/Product-roadmap
[setup]: https://github.com/snowplow/iglu/wiki/Scala-iglu-core#setup
[contributing]: https://github.com/snowplow/iglu/wiki/Contributing

[techdocs-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/techdocs.png
[setup-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/setup.png
[roadmap-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/roadmap.png
[contributing-image]: https://d3i6fms1cm1j0i.cloudfront.net/github/images/contributing.png

[license]: http://www.apache.org/licenses/LICENSE-2.0
