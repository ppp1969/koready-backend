package koready_backend.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class GitHubActionsEbDeployRoleTemplateTest {

	private static final Path TEMPLATE =
		Path.of("infra", "aws", "github-actions-eb-deploy-role.yaml");
	private static final String BUCKET_ARN =
		"arn:${AWS::Partition}:s3:::${BeanstalkArtifactBucketName}";
	private static final String ENVIRONMENT_EXTENSION_PREFIX =
		"resources/environments/${BeanstalkEnvironmentId}/_runtime/"
			+ "_embedded_extensions/${BeanstalkApplicationName}/*";
	private static final String GLOBAL_EXTENSION_PREFIX =
		"resources/_runtime/_embedded_extensions/${BeanstalkApplicationName}/*";
	private static final String ENVIRONMENT_VERSION_PREFIX =
		"resources/environments/${BeanstalkEnvironmentId}/_runtime/"
			+ "_versions/${BeanstalkApplicationName}/*";
	private static final String BEANSTALK_ADMIN_POLICY_ARN =
		"arn:${AWS::Partition}:iam::aws:policy/AdministratorAccess-AWSElasticBeanstalk";

	@Test
	void attachesTheAwsManagedElasticBeanstalkAdministratorPolicy() throws IOException {
		Map<String, Object> properties = roleProperties();
		List<Map<String, Object>> managedPolicyArns = list(properties.get("ManagedPolicyArns"));

		assertEquals(List.of(BEANSTALK_ADMIN_POLICY_ARN), managedPolicyArns.stream()
			.map(policy -> intrinsic(policy, "Fn::Sub"))
			.toList());
	}

	@Test
	void grantsOnlyTheRequiredReadsForBeanstalkRuntimeExtensions() throws IOException {
		List<Map<String, Object>> statements = deploymentPolicyStatements();

		Map<String, Object> objectAccess = statement(statements,
			"EnvironmentEmbeddedExtensions");
		assertEquals(List.of("s3:GetObject", "s3:GetObjectVersion"),
			objectAccess.get("Action"));
		List<Map<String, Object>> objectResources = list(objectAccess.get("Resource"));
		assertEquals(List.of(
			BUCKET_ARN + "/" + ENVIRONMENT_EXTENSION_PREFIX,
			BUCKET_ARN + "/" + GLOBAL_EXTENSION_PREFIX),
			objectResources.stream()
				.map(resource -> intrinsic(resource, "Fn::Sub"))
				.toList());

		Map<String, Object> bucketList = statement(statements,
			"BeanstalkManagedBucketList");
		assertEquals(List.of("s3:ListBucket"), bucketList.get("Action"));
		assertEquals(BUCKET_ARN,
			intrinsic(bucketList.get("Resource"), "Fn::Sub"));
		assertEquals(false, bucketList.containsKey("Condition"));
		assertEquals(1, statements.stream()
			.filter(statement -> list(statement.get("Action")).contains("s3:ListBucket"))
			.count());

		Map<String, Object> objectMetadata = statement(statements,
			"BeanstalkManagedObjectMetadata");
		assertEquals(List.of("s3:GetObjectAcl"), objectMetadata.get("Action"));
		assertEquals(BUCKET_ARN + "/*",
			intrinsic(objectMetadata.get("Resource"), "Fn::Sub"));

		Map<String, Object> environmentVersions = statement(statements,
			"EnvironmentApplicationVersions");
		assertEquals(List.of("s3:GetObject", "s3:PutObject", "s3:PutObjectAcl", "s3:DeleteObject"),
			environmentVersions.get("Action"));
		assertEquals(BUCKET_ARN + "/" + ENVIRONMENT_VERSION_PREFIX,
			intrinsic(environmentVersions.get("Resource"), "Fn::Sub"));
	}

	@Test
	void grantsTemplateReadOnlyForTheTargetBeanstalkEnvironmentStack()
		throws IOException {
		Map<String, Object> stackAccess = statement(deploymentPolicyStatements(),
			"EnvironmentStackTemplate");

		assertEquals(List.of(
			"cloudformation:GetTemplate",
			"cloudformation:DescribeStackResource"),
			stackAccess.get("Action"));
		assertEquals(
			"arn:${AWS::Partition}:cloudformation:${AWS::Region}:${AWS::AccountId}:"
				+ "stack/awseb-${BeanstalkEnvironmentId}-stack/*",
			intrinsic(stackAccess.get("Resource"), "Fn::Sub"));
	}

	@Test
	void grantsOnlyReadAccessToTheBeanstalkAutoScalingGroup() throws IOException {
		Map<String, Object> autoScalingAccess = statement(environmentPolicyStatements(),
			"InspectBeanstalkAutoScalingGroup");

		assertEquals(List.of("autoscaling:DescribeAutoScalingGroups"),
			autoScalingAccess.get("Action"));
		assertEquals("*", autoScalingAccess.get("Resource"));
	}

	private List<Map<String, Object>> deploymentPolicyStatements() throws IOException {
		return policyStatements("UploadBeanstalkSourceBundle");
	}

	private List<Map<String, Object>> environmentPolicyStatements() throws IOException {
		return policyStatements("UpdateKoreadyBeanstalkEnvironment");
	}

	private List<Map<String, Object>> policyStatements(String policyName) throws IOException {
		Map<String, Object> properties = roleProperties();
		List<Map<String, Object>> policies = list(properties.get("Policies"));
		Map<String, Object> deploymentPolicy = policies.stream()
			.filter(policy -> policyName.equals(policy.get("PolicyName")))
			.findFirst()
			.orElseThrow();
		Map<String, Object> policyDocument = map(deploymentPolicy.get("PolicyDocument"));
		return list(policyDocument.get("Statement"));
	}

	private Map<String, Object> roleProperties() throws IOException {
		Map<String, Object> template = new Yaml().load(Files.readString(TEMPLATE));
		Map<String, Object> resources = map(template.get("Resources"));
		Map<String, Object> role = map(resources.get("GitHubActionsEbDeployRole"));
		return map(role.get("Properties"));
	}

	private Map<String, Object> statement(List<Map<String, Object>> statements, String sid) {
		return statements.stream()
			.filter(statement -> sid.equals(statement.get("Sid")))
			.findFirst()
			.orElseThrow();
	}

	private String intrinsic(Object value, String name) {
		return (String) map(value).get(name);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> list(Object value) {
		return (List<Map<String, Object>>) value;
	}
}
