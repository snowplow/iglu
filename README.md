# Snowplow Iglu

[![Latest release][latest-release-badge]][latest-release]
[![License][license-image]][license]
[![Discourse posts][discourse-image]][discourse]

[![Snowplow logo][logo-image]][website]

## Overview

Iglu is a machine-readable, open-source schema repository for **[JSON Schema][json-schema]** from the team at **[Snowplow][website]**. A schema repository (also called a registry) is like npm or Maven or git, but holds data schemas instead of software or code.

Iglu is used extensively in **[Snowplow][snowplow-github]**. For a presentation on how we came to build Iglu, see **[this blog post][snowplow-schema-post]**.

### Table of contents

* [Where to start?](#where-to-start)
* [Iglu technology 101](#iglu-technology-101)
* [About this umbrella repository](#about-this-repository)

### Where to start?

The [documentation][documentation] is a great place to learn more, especially:

* [Iglu Common Architecture][iglu-docs-architecture]
* [Iglu Clients][iglu-docs-clients]
* [Iglu Repositories][iglu-docs-repositories]

Would rather dive into the code? Then you are already in the right place!

---

## Iglu technology 101

[![Iglu architecture][iglu-architecture-image]][iglu-docs-architecture]

The repository structure outlines the interrelations among the architectural components of Iglu. To briefly explain these components:

* **[Common][iglu-common]**: Common libraries and tools of the Iglu ecosystem.
* **[Clients][iglu-clients]**: Iglu clients are used for interacting with Iglu server repos and for resolving schemas in embedded and remote Iglu schema repositories.
* **[Repositories][iglu-repositories]**: Iglu repositories act as stores of data schemas, that can be embedded or hosted over HTTP.
* **[Infrastructure][iglu-infrastructure]**: Containers (e.g. terraform-modules) bundling infrastructure as code configuration for Iglu Server.

---

## About this repository

This repository is an umbrella repository for all loosely-coupled Iglu components and is updated on each component release.

Since August 2022, all components have been extracted into their dedicated repositories and are still here as [git submodules][submodules]. This repository serves as an entry point and as a historical artifact.

### Common

* [Igluctl][igluctl]
* [Scala Core][scala-core]
* [Schema DDL][schema-ddl]

### Clients

* [JavaScript][javascript-client]
* [Objective C][objc-client]
* [Ruby][ruby-client]
* [Scala][scala-client]

### Repositories

* [Example Schema Registry][example-schema-registry]
* [Iglu Central][iglu-central]
* [Iglu Server][iglu-server]
* [JVM Embedded Repo][jvm-embedded-repo]
* [Static Registry][static-registry]

### Infrastructure

* [Terraform AWS Iglu Server EC2][terraform-aws-iglu-server-ec2]
* [Terraform Google Iglu Server Compute Engine][terraform-google-iglu-server-ce]
* [Iglu Server Helm Chart][iglu-server-helm-chart]

## Copyright and license

Iglu is copyright 2014-2023 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0][license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[latest-release-badge]: https://img.shields.io/github/last-commit/snowplow/iglu?label=latest%20release
[latest-release]: https://

[license-image]: https://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
[license]: https://www.apache.org/licenses/LICENSE-2.0

[discourse-image]: https://img.shields.io/discourse/posts?server=https%3A%2F%2Fdiscourse.snowplow.io
[discourse]: https://discourse.snowplow.io/

[logo-image]: media/snowplow_logo.png
[website]: https://snowplow.io
[snowplow-github]: https://github.com/snowplow/snowplow
[documentation]: https://docs.snowplow.io

[iglu-architecture-image]: media/iglu_architecture.png
[iglu-docs-architecture]: https://docs.snowplow.io/docs/pipeline-components-and-applications/iglu/common-architecture/
[iglu-docs-clients]: https://docs.snowplow.io/docs/pipeline-components-and-applications/iglu/iglu-clients/
[iglu-docs-repositories]: https://docs.snowplow.io/docs/pipeline-components-and-applications/iglu/iglu-repositories/

[json-schema]: https://json-schema.org/
[snowplow-schema-post]: https://snowplow.io/blog/2014/06/06/making-snowplow-schemas-flexible-a-technical-approach/

[submodules]: https://git-scm.com/book/en/v2/Git-Tools-Submodules

[iglu-common]: ./0-common
[iglu-clients]: ./1-clients
[iglu-repositories]: ./2-repositories
[iglu-infrastructure]: ./3-infrastructure

[igluctl]: https://github.com/snowplow-incubator/igluctl.git
[scala-core]: https://github.com/snowplow/iglu-scala-core.git
[schema-ddl]: https://github.com/snowplow/schema-ddl.git

[javascript-client]: https://github.com/snowplow/iglu-javascript-client.git
[objc-client]: https://github.com/snowplow/iglu-objc-client.git
[ruby-client]: https://github.com/snowplow/iglu-ruby-client.git
[scala-client]: https://github.com/snowplow/iglu-scala-client.git

[example-schema-registry]: https://github.com/snowplow/iglu-example-schema-registry.git
[iglu-central]: https://github.com/snowplow/iglu-central.git
[iglu-server]: https://github.com/snowplow-incubator/iglu-server.git
[jvm-embedded-repo]: ./2-repositories/jvm-embedded-repo
[static-registry]: ./2-repositories/static-registry

[terraform-aws-iglu-server-ec2]: https://github.com/snowplow-devops/terraform-aws-iglu-server-ec2.git
[terraform-google-iglu-server-ce]: https://github.com/snowplow-devops/terraform-google-iglu-server-ce.git
[iglu-server-helm-chart]: https://github.com/snowplow-devops/helm-charts/tree/main/charts/snowplow-iglu-server
