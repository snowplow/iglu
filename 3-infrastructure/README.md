# Infrastructure

In this directory you can find links to infrastructure-related repositories for deploying Snowplow Iglu Server.

## Available Terraform modules

| **Module**                                            | **Description**                                                                                          |
|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| [Iglu Server AWS][terraform-aws-iglu-server-ec2]      | Terraform module to deploy Snowplow Iglu Server application on AWS running on top of EC2                 |
| [Iglu Server Google][terraform-google-iglu-server-ce] | Terraform module to deploy a Snowplow Iglu Server application on Google running on top of Compute Engine |
| [Iglu Server Helm Chart][iglu-server-helm-chart]      | Helm Chart to deploy a Snowplow Iglu Server on a Kubernetes cluster using the Helm package manager      |

[terraform-aws-iglu-server-ec2]: https://github.com/snowplow-devops/terraform-aws-iglu-server-ec2
[terraform-google-iglu-server-ce]: https://github.com/snowplow-devops/terraform-google-iglu-server-ce
[iglu-server-helm-chart]: https://github.com/snowplow-devops/helm-charts/tree/main/charts/snowplow-iglu-server
