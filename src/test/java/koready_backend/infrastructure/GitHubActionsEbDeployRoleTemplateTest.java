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
	private static final String EXTENSION_PREFIX =
		"resources/environments/${BeanstalkEnvironmentId}/_runtime/"
			+ "_embedded_extensions/${BeanstalkApplicationName}/*";

	@Test
	void grantsOnlyTheRequiredReadsForBeanstalkRuntimeExtensions() throws IOException {
		List<Map<String, Object>> statements = deploymentPolicyStatements();

		Map<String, Object> objectAccess = statement(statements,
			"EnvironmentEmbeddedExtensions");
		assertEquals(List.of("s3:GetObject", "s3:GetObjectVersion"),
			objectAccess.get("Action"));
		assertEquals(BUCKET_ARN + "/" + EXTENSION_PREFIX,
			intrinsic(objectAccess.get("Resource"), "Fn::Sub"));

		Map<String, Object> prefixAccess = statement(statements,
			"EnvironmentEmbeddedExtensionsPrefix");
		assertEquals(List.of("s3:ListBucket"), prefixAccess.get("Action"));
		assertEquals(BUCKET_ARN,
			intrinsic(prefixAccess.get("Resource"), "Fn::Sub"));
		Map<String, Object> condition = map(prefixAccess.get("Condition"));
		Map<String, Object> stringLike = map(condition.get("StringLike"));
		List<Map<String, Object>> prefixes = list(stringLike.get("s3:prefix"));
		assertEquals(EXTENSION_PREFIX,
			intrinsic(prefixes.getFirst(), "Fn::Sub"));
	}

	private List<Map<String, Object>> deploymentPolicyStatements() throws IOException {
		Map<String, Object> template = new Yaml().load(Files.readString(TEMPLATE));
		Map<String, Object> resources = map(template.get("Resources"));
		Map<String, Object> role = map(resources.get("GitHubActionsEbDeployRole"));
		Map<String, Object> properties = map(role.get("Properties"));
		List<Map<String, Object>> policies = list(properties.get("Policies"));
		Map<String, Object> deploymentPolicy = policies.stream()
			.filter(policy -> "UploadBeanstalkSourceBundle".equals(policy.get("PolicyName")))
			.findFirst()
			.orElseThrow();
		Map<String, Object> policyDocument = map(deploymentPolicy.get("PolicyDocument"));
		return list(policyDocument.get("Statement"));
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
