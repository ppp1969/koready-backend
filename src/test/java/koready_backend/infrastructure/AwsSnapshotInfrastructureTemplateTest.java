package koready_backend.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class AwsSnapshotInfrastructureTemplateTest {

	private static final Path TEMPLATE =
		Path.of("infra", "aws", "kto-snapshot-storage.yaml");

	@Test
	void provisionsAPrivateEncryptedVersionedBucketAndADeleteFreeWriterRole()
		throws IOException {
		Map<String, Object> template = new Yaml().load(Files.readString(TEMPLATE));
		Map<String, Object> resources = map(template.get("Resources"));
		Map<String, Object> bucket = resourceProperties(resources, "SnapshotBucket");

		Map<String, Object> publicAccess = map(
			bucket.get("PublicAccessBlockConfiguration"));
		assertEquals(Boolean.TRUE, publicAccess.get("BlockPublicAcls"));
		assertEquals(Boolean.TRUE, publicAccess.get("BlockPublicPolicy"));
		assertEquals(Boolean.TRUE, publicAccess.get("IgnorePublicAcls"));
		assertEquals(Boolean.TRUE, publicAccess.get("RestrictPublicBuckets"));
		assertEquals("Enabled", map(bucket.get("VersioningConfiguration")).get("Status"));

		List<Map<String, Object>> ownershipRules = list(
			map(bucket.get("OwnershipControls")).get("Rules"));
		assertEquals("BucketOwnerEnforced", ownershipRules.getFirst().get("ObjectOwnership"));

		String bucketYaml = new Yaml().dump(bucket);
		assertTrue(bucketYaml.contains("AES256"));

		Map<String, Object> role = resourceProperties(resources, "SnapshotWriterRole");
		String roleYaml = new Yaml().dump(role);
		assertTrue(roleYaml.contains("s3:PutObject"));
		assertTrue(roleYaml.contains("s3:GetObject"));
		assertTrue(roleYaml.contains("s3:GetBucketLocation"));
		assertTrue(roleYaml.contains("s3:ListBucket"));
		assertFalse(roleYaml.contains("s3:DeleteObject"));
		assertBucketLocationIsNotSubjectToThePrefixCondition(role);

		Map<String, Object> policy = resourceProperties(resources, "SnapshotBucketPolicy");
		String policyYaml = new Yaml().dump(policy);
		assertTrue(policyYaml.contains("aws:SecureTransport"));
		assertTrue(policyYaml.contains("s3:if-none-match"));
	}

	private void assertBucketLocationIsNotSubjectToThePrefixCondition(
		Map<String, Object> role
	) {
		List<Map<String, Object>> policies = list(role.get("Policies"));
		Map<String, Object> policyDocument = map(policies.getFirst().get("PolicyDocument"));
		List<Map<String, Object>> statements = list(policyDocument.get("Statement"));
		Map<String, Object> bucketLocation = statements.stream()
			.filter(statement -> "BucketLocation".equals(statement.get("Sid")))
			.findFirst()
			.orElseThrow();
		assertEquals(List.of("s3:GetBucketLocation"), bucketLocation.get("Action"));
		assertFalse(bucketLocation.containsKey("Condition"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> resourceProperties(
		Map<String, Object> resources,
		String resourceName
	) {
		return map(map(resources.get(resourceName)).get("Properties"));
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
