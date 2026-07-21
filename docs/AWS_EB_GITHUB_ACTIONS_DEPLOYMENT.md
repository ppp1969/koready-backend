# GitHub Actions Elastic Beanstalk Deployment

## Purpose

`main` push runs the normal Gradle and Docker checks first. Only when both jobs pass,
the `Elastic Beanstalk Deploy` job publishes that exact commit to the staging EB environment.
Render remains the Swagger sharing environment and does not run the KTO collection worker.

The workflow does not keep an AWS access key in GitHub. It exchanges the GitHub Actions OIDC
token for a one-hour AWS role session, limited to this repository's `main` branch.

## One-time AWS setup

1. Open **IAM > Identity providers**. If `token.actions.githubusercontent.com` is absent,
   add an OpenID Connect provider with URL `https://token.actions.githubusercontent.com`
   and audience `sts.amazonaws.com`.
2. Open **CloudFormation** in the EB region and create the stack from
   `infra/aws/github-actions-eb-deploy-role.yaml`.
3. Supply the existing EB managed S3 bucket name. The bucket must be in the same region as EB.
   It normally follows `elasticbeanstalk-<region>-<account-id>`.
4. CloudFormation creates `KoreadyGitHubActionsEbDeployRole`. It can upload only source bundles
   under `resources/<application>/github-actions/` and can update the existing EB environment.

Do not place an AWS access key, secret key, Aiven password, or API key in repository variables.

## GitHub repository variables

In **Settings > Secrets and variables > Actions > Variables**, add these non-secret values:

| Variable | Value |
| --- | --- |
| `AWS_REGION` | EB region, currently `ap-northeast-2` |
| `AWS_EB_APPLICATION_NAME` | Existing EB application name |
| `AWS_EB_ENVIRONMENT_NAME` | Existing EB environment name |
| `AWS_EB_ARTIFACT_BUCKET` | Existing EB managed S3 bucket name |
| `AWS_EB_DEPLOY_ROLE_ARN` | `GitHubActionsEbDeployRoleArn` CloudFormation output |

The role ARN and bucket name identify infrastructure but are not credentials. No GitHub Actions
secret is required for this deployment path.

## Deployment and verification

1. Merge a pull request into `main`.
2. The `test` and `docker` jobs complete successfully.
3. `deploy_eb` packages the merged commit with `git archive`, creates an EB application version,
   updates the staging environment, waits for the environment update, and requests
   `/actuator/health/readiness`.
4. Inspect the `Elastic Beanstalk Deploy` job and the EB event stream if it fails.

The workflow is serialized with the `koready-staging-eb-deploy` concurrency group. A newer
merge waits for the previous deployment rather than cancelling it midway.

## Rollback

Use the EB console to select a previously healthy application version and deploy it to the
staging environment. This does not alter the database schema. Check readiness and the relevant
application logs after rollback.
