package koready_backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class ArchitectureRulesTest {

	private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("koready_backend");

	@Test
	void featurePackagesDoNotHaveCycles() {
		slices()
			.matching("koready_backend.(*)..")
			.should().beFreeOfCycles()
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}

	@Test
	void domainDoesNotDependOnOuterLayersOrFrameworks() {
		noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
				"..application..",
				"..controller..",
				"..infrastructure..",
				"org.springframework..",
				"jakarta.persistence..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}

	@Test
	void applicationDoesNotDependOnAdapters() {
		noClasses()
			.that().resideInAPackage("..application..")
			.should().dependOnClassesThat().resideInAnyPackage(
				"..controller..",
				"..infrastructure..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}

	@Test
	void adaptersDoNotDependOnEachOther() {
		noClasses()
			.that().resideInAPackage("..controller..")
			.should().dependOnClassesThat().resideInAPackage("..infrastructure..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);

		noClasses()
			.that().resideInAPackage("..infrastructure..")
			.should().dependOnClassesThat().resideInAPackage("..controller..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}

	@Test
	void restControllersStayInControllerPackages() {
		classes()
			.that().areAnnotatedWith(RestController.class)
			.should().resideInAPackage("..controller..")
			.allowEmptyShould(true)
			.check(PRODUCTION_CLASSES);
	}
}
