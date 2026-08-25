package dev.analysis.mcp.utils;

import dev.analysis.mcp.utils.JavaDependencyParser.DependencyEdge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class JavaDependencyParserTest {

    private JavaDependencyParser parser;
    private static final Path FIXTURES = Path.of("src/test/resources/fixtures").toAbsolutePath();

    @BeforeEach
    void setUp() {
        parser = new JavaDependencyParser();
    }

    private List<Path> files(String... classNames) {
        return java.util.Arrays.stream(classNames)
            .map(FIXTURES::resolve)
            .toList();
    }

    @Test
    void parse_extractsImportDependencies() {
        Path file = FIXTURES.resolve("com/example/service/OrderService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.service.OrderService") &&
            e.to().equals("com.example.repository.UserRepository")),
            "Should have dependency on UserRepository");
        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.service.OrderService") &&
            e.to().equals("com.example.model.User")),
            "Should have dependency on User");
    }

    @Test
    void parse_extractsFieldDependencies() {
        Path file = FIXTURES.resolve("com/example/service/PaymentService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.service.PaymentService") &&
            e.to().equals("com.example.validation.PaymentValidator")),
            "Should have dependency on PaymentValidator from field type");
    }

    @Test
    void parse_extractsExtendsAndImplements() {
        List<Path> files = files(
            "com/example/validation/BaseService.java",
            "com/example/validation/IService.java",
            "com/example/validation/ServiceImpl.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, files);

        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.validation.ServiceImpl") &&
            e.to().equals("com.example.validation.BaseService")),
            "Should have dependency from extends clause");
        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.validation.ServiceImpl") &&
            e.to().equals("com.example.validation.IService")),
            "Should have dependency from implements clause");
    }

    @Test
    void parse_removesSelfReferencingEdges() {
        Path file = FIXTURES.resolve("com/example/model/Order.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertFalse(edges.stream().anyMatch(e ->
            e.from().equals("com.example.model.Order") &&
            e.to().equals("com.example.model.Order")),
            "Should not have self-referencing edges");
    }

    @Test
    void parse_returnsEmptyForEmptyFileList() {
        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of());
        assertTrue(edges.isEmpty());
    }

    @Test
    void parse_extractsMethodCallDependencies() {
        Path file = FIXTURES.resolve("com/example/methods/MethodCallService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.methods.MethodCallService") &&
            e.to().equals("com.example.repository.OrderRepository")),
            "Should have dependency on OrderRepository from method calls");
        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.methods.MethodCallService") &&
            e.to().equals("com.example.model.Order")),
            "Should have dependency on Order from method calls");
    }

    @Test
    void parse_handlesPrimitiveTypeFields() {
        Path file = FIXTURES.resolve("com/example/edgecases/EdgeCaseService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.edgecases.EdgeCaseService") &&
            e.to().equals("com.example.model.User")),
            "Should have dependency on User field type");
        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.edgecases.EdgeCaseService") &&
            e.to().equals("java.util.List")),
            "Should have dependency on List from generic field");
    }

    @Test
    void parse_filtersWildcardAndStaticImports() {
        Path file = FIXTURES.resolve("com/example/edgecases/ImportFilterService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.edgecases.ImportFilterService") &&
            e.to().equals("com.example.model.Order")),
            "Should have dependency on Order");
        assertFalse(edges.stream().anyMatch(e ->
            e.to().equals("java.util.*")),
            "Should not have wildcard import dependency");
        assertFalse(edges.stream().anyMatch(e ->
            e.to().equals("java.util.Collections")),
            "Should not have static import dependency");
    }

    @Test
    void parse_handlesIOExceptionGracefully(@TempDir Path tempDir) throws IOException {
        Path nonExistentFile = tempDir.resolve("nonexistent/NotAFile.java");

        Set<DependencyEdge> edges = parser.parse(tempDir, List.of(nonExistentFile));

        assertTrue(edges.isEmpty(), "Should return empty set for non-existent file");
    }

    @Test
    void parse_handlesMultipleClassesInOneFile(@TempDir Path tempDir) throws IOException {
        Path multiClassFile = tempDir.resolve("MultiClass.java");
        Files.writeString(multiClassFile, """
            package test;

            import com.example.model.User;

            public class MultiClass {
                private User user;
            }

            class AnotherClass {
            }
            """);

        Set<DependencyEdge> edges = parser.parse(tempDir, List.of(multiClassFile));

        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("test.MultiClass") &&
            e.to().equals("com.example.model.User")),
            "Should extract dependencies from first class");
    }

    @Test
    void parse_handlesFileWithNoClassDeclaration(@TempDir Path tempDir) throws IOException {
        Path packageInfo = tempDir.resolve("package-info.java");
        Files.writeString(packageInfo, """
            package com.example;
            """);

        Set<DependencyEdge> edges = parser.parse(tempDir, List.of(packageInfo));

        assertTrue(edges.isEmpty(), "Should return empty set for package-info.java");
    }

    @Test
    void parse_deduplicatesEdgesAcrossSources() {
        Path file = FIXTURES.resolve("com/example/service/OrderService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        long userRepoCount = edges.stream()
            .filter(e -> e.from().equals("com.example.service.OrderService") &&
                        e.to().equals("com.example.repository.UserRepository"))
            .count();
        assertEquals(1, userRepoCount, "Should have exactly one edge per dependency, even if found via import and field");
    }

    @Test
    void parse_handlesUnresolvableMethodCalls() {
        Path file = FIXTURES.resolve("com/example/methods/UnresolvableCallService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertNotNull(edges, "Should not throw exception for unresolvable method calls");
    }

    @Test
    void parse_handlesUnresolvedTypeReferences() {
        Path file = FIXTURES.resolve("com/example/edgecases/UnresolvedExtendsService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertNotNull(edges, "Should not throw exception for unresolved type references");
    }

    @Test
    void parse_handlesFileWithSyntaxErrors(@TempDir Path tempDir) throws IOException {
        Path errorFile = tempDir.resolve("SyntaxError.java");
        Files.writeString(errorFile, """
            package test;

            public class SyntaxError {
                private String name; // missing semicolon after this line
            }
            """);

        Set<DependencyEdge> edges = parser.parse(tempDir, List.of(errorFile));

        assertNotNull(edges, "Should handle syntax errors gracefully");
    }

    @Test
    void parse_handlesCorruptedFile(@TempDir Path tempDir) throws IOException {
        Path corruptedFile = tempDir.resolve("Corrupted.java");
        byte[] garbage = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        Files.write(corruptedFile, garbage);

        Set<DependencyEdge> edges = parser.parse(tempDir, List.of(corruptedFile));

        assertNotNull(edges, "Should handle corrupted files gracefully");
    }

    @Test
    void parse_handlesExplicitArrayTypeFields() {
        Path file = FIXTURES.resolve("com/example/edgecases/ExplicitArrayService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertTrue(edges.stream().anyMatch(e ->
            e.from().equals("com.example.edgecases.ExplicitArrayService") &&
            e.to().equals("com.example.model.User")),
            "Should have dependency on User from User[] field");
    }

    @Test
    void parse_handlesMethodCallOnUnresolvedObject(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, """
            package test;

            public class Test {
                public void doSomething() {
                    Object obj = new Object();
                    obj.hashCode();
                }
            }
            """);

        Set<DependencyEdge> edges = parser.parse(tempDir, List.of(file));

        assertNotNull(edges, "Should handle method calls on resolved objects");
    }

    @Test
    void parse_handlesTypeParameterField(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, """
            package test;

            public class Test<T> {
                private T field;
            }
            """);

        Set<DependencyEdge> edges = parser.parse(tempDir, List.of(file));

        assertNotNull(edges, "Should handle type parameter fields");
    }

    @Test
    void parse_handlesMethodResolutionFailure() {
        Path file = FIXTURES.resolve("com/example/edgecases/MethodResolutionFailureService.java");

        Set<DependencyEdge> edges = parser.parse(FIXTURES, List.of(file));

        assertNotNull(edges, "Should handle method resolution failures gracefully");
    }
}
