package dev.analysis.mcp.utils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Java source files to extract dependency relationships.
 *
 * <p>This class analyzes Java source files to build a graph of dependencies between classes.
 * Dependencies are extracted from:</p>
 *
 * <ul>
 *   <li>Import statements</li>
 *   <li>Extended classes (inheritance)</li>
 *   <li>Implemented interfaces</li>
 *   <li>Field type declarations</li>
 *   <li>Method call expressions (resolved to declaring types)</li>
 * </ul>
 *
 * <p>The parser uses JavaParser with symbol solving to resolve type references
 * across the codebase.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * JavaDependencyParser parser = new JavaDependencyParser();
 * Path repoRoot = Path.of("/path/to/project");
 * List<Path> javaFiles = List.of(
 *     Path.of("/path/to/project/src/main/java/Service.java"),
 *     Path.of("/path/to/project/src/main/java/Controller.java")
 * );
 *
 * Set<JavaDependencyParser.DependencyEdge> edges = parser.parse(repoRoot, javaFiles);
 *
 * for (DependencyEdge edge : edges) {
 *     System.out.println(edge.from() + " -> " + edge.to());
 * }
 * }</pre>
 *
 * <h2>Example Input:</h2>
 * <p>Given {@code Service.java}:</p>
 * <pre>{@code
 * package com.example.service;
 *
 * import java.util.List;
 * import com.example.repository.UserRepository;
 * import com.example.model.User;
 * import com.example.util.Helper;
 *
 * public class Service extends BaseService implements IService {
 *     private UserRepository repository;
 *     private Helper helper;
 *     private List<User> users;
 *     private String name;
 *
 *     public void doSomething(User user) {
 *         repository.save(user);
 *         helper.process(user);
 *     }
 * }
 * }</pre>
 *
 * <h2>Example Output:</h2>
 * <p>The parser will extract these edges:</p>
 * <ul>
 *   <li>{@code com.example.service.Service -> java.util.List}
 *       <br/><i>(from import statement)</i></li>
 *   <li>{@code com.example.service.Service -> com.example.repository.UserRepository}
 *       <br/><i>(from import statement)</i></li>
 *   <li>{@code com.example.service.Service -> com.example.model.User}
 *       <br/><i>(from import statement)</i></li>
 *   <li>{@code com.example.service.Service -> com.example.util.Helper}
 *       <br/><i>(from import statement)</i></li>
 *   <li>{@code com.example.service.Service -> com.example.service.BaseService}
 *       <br/><i>(from extends clause)</i></li>
 *   <li>{@code com.example.service.Service -> com.example.service.IService}
 *       <br/><i>(from implements clause)</i></li>
 *   <li>{@code com.example.service.Service -> com.example.repository.UserRepository}
 *       <br/><i>(from field type: UserRepository repository)</i></li>
 *   <li>{@code com.example.service.Service -> com.example.util.Helper}
 *       <br/><i>(from field type: Helper helper)</i></li>
 *   <li>{@code com.example.service.Service -> java.util.List}
 *       <br/><i>(from field type: List&lt;User&gt; users)</i></li>
 *   <li>{@code com.example.service.Service -> com.example.repository.UserRepository}
 *       <br/><i>(from method call: repository.save())</i></li>
 *   <li>{@code com.example.service.Service -> com.example.util.Helper}
 *       <br/><i>(from method call: helper.process())</i></li>
 * </ul>
 *
 * <h2>Edge Deduplication:</h2>
 * <p>Note that some edges may be duplicated (e.g., UserRepository appears from import, field, and method call).
 * These duplicates are automatically removed since {@code DependencyEdge} is stored in a {@code Set}.</p>
 *
 * <h2>Processing Flow:</h2>
 * <p>The {@code parse} method processes each file in the following order:</p>
 * <ol>
 *   <li>Initialize JavaParser with CombinedTypeSolver (Reflection + JavaParser type solvers)</li>
 *   <li>For each Java file:
 *     <ol>
 *       <li>Parse the file into a CompilationUnit</li>
 *       <li>Find all class/interface declarations</li>
 *       <li>For each declaration, extract:
 *         <ul>
 *           <li>Direct imports (non-static, non-wildcard)</li>
 *           <li>Extended types (extends)</li>
 *           <li>Implemented types (implements)</li>
 *           <li>Field types</li>
 *           <li>Method call target types (via symbol resolution)</li>
 *         </ul>
 *       </li>
 *     </ol>
 *   </li>
 *   <li>Remove self-referencing edges (from == to)</li>
 *   <li>Return the complete set of dependency edges</li>
 * </ol>
 */
public class JavaDependencyParser {

    private static final Logger log = LoggerFactory.getLogger(JavaDependencyParser.class);

    /**
     * Represents a directed dependency edge from one class to another.
     *
     * <p>This record models a single dependency relationship in the codebase,
     * where the source class (from) depends on the target class (to).</p>
     *
     * @param from the fully qualified name of the source class (e.g., "com.example.Service")
     * @param to the fully qualified name of the target class (e.g., "com.example.Repository")
     *
     * @see JavaDependencyParser#parse(Path, List)
     */
    public record DependencyEdge(String from, String to) {}

    /**
     * Parses Java source files and extracts dependency relationships.
     *
     * <p>This method analyzes each Java file and collects all dependency edges.
     * It handles various types of dependencies including imports, inheritance,
     * interface implementation, field types, and method call targets.</p>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Path repoRoot = Path.of("/my/project");
     * List<Path> files = List.of(Path.of("/my/project/Service.java"));
     *
     * Set<DependencyEdge> edges = parser.parse(repoRoot, files);
     *
     * // edges contains all discovered dependencies
     * edges.forEach(e -> System.out.println(e.from() + " depends on " + e.to()));
     *
     * // Sample output:
     * // com.example.service.Service depends on com.example.repository.UserRepository
     * // com.example.service.Service depends on com.example.model.User
     * // com.example.service.Service depends on com.example.service.BaseService
     * // com.example.service.Service depends on com.example.service.IService
     * }</pre>
     *
     * <h3>Dependency Sources:</h3>
     * <p>The method extracts dependencies from these AST elements:</p>
     * <table border="1">
     *   <tr><th>Source</th><th>Description</th></tr>
     *   <tr><td>Import</td><td>Non-wildcard, non-static import statements</td></tr>
     *   <tr><td>Extends</td><td>Parent class in class declaration</td></tr>
     *   <tr><td>Implements</td><td>Interfaces in class declaration</td></tr>
     *   <tr><td>Field</td><td>Type of declared fields (including generics)</td></tr>
     *   <tr><td>Method Call</td><td>Declaring type of resolved method calls</td></tr>
     * </table>
     *
     * <h3>Error Handling:</h3>
     * <p>The method logs warnings for file read failures and debug messages for parse failures,
     * but continues processing other files. Files that cannot be read or parsed are skipped.</p>
     *
     * @param repoRoot the root directory of the repository (used for type resolution)
     * @param javaFiles list of paths to Java source files to parse
     * @return a set of dependency edges representing class relationships
     */
    public Set<DependencyEdge> parse(Path repoRoot, List<Path> javaFiles) {
        JavaParser parser = createParser(repoRoot);
        Set<DependencyEdge> edges = new HashSet<>();

        for (Path file : javaFiles) {
            parseFile(file, parser, edges);
        }

        edges.removeIf(e -> e.from().equals(e.to()));
        return edges;
    }

    private JavaParser createParser(Path repoRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(repoRoot));

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(typeSolver));

        return new JavaParser(config);
    }

    private void parseFile(Path file, JavaParser parser, Set<DependencyEdge> edges) {
        try {
            Optional<CompilationUnit> cuOpt = parser.parse(file).getResult();
            if (cuOpt.isEmpty()) {
                return;
            }
            processCompilationUnit(cuOpt.get(), edges);
        } catch (IOException e) {
            log.warn("Failed to read file {}: {}", file, e.getMessage());
        } catch (Exception e) {
            log.debug("Failed to parse {}: {}", file, e.getMessage());
        }
    }

    private void processCompilationUnit(CompilationUnit cu, Set<DependencyEdge> edges) {
        String sourceClass = findSourceClass(cu);
        if (sourceClass == null) {
            return;
        }

        addImportDependencies(cu, sourceClass, edges);
        addInheritanceDependencies(cu, sourceClass, edges);
        addFieldDependencies(cu, sourceClass, edges);
        addMethodCallDependencies(cu, sourceClass, edges);
    }

    private String findSourceClass(CompilationUnit cu) {
        return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .findFirst()
                .flatMap(this::resolveDeclaredTypeFqn)
                .orElse(null);
    }

    private void addImportDependencies(CompilationUnit cu, String sourceClass, Set<DependencyEdge> edges) {
        cu.getImports().stream()
                .filter(imp -> !imp.isAsterisk() && !imp.isStatic())
                .map(imp -> new DependencyEdge(sourceClass, imp.getNameAsString()))
                .forEach(edges::add);
    }

    private void addInheritanceDependencies(CompilationUnit cu, String sourceClass, Set<DependencyEdge> edges) {
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(decl -> {
            decl.getExtendedTypes().forEach(t -> resolveTypeFqn(t).ifPresent(to ->
                    edges.add(new DependencyEdge(sourceClass, to))));
            decl.getImplementedTypes().forEach(t -> resolveTypeFqn(t).ifPresent(to ->
                    edges.add(new DependencyEdge(sourceClass, to))));
        });
    }

    private void addFieldDependencies(CompilationUnit cu, String sourceClass, Set<DependencyEdge> edges) {
        cu.findAll(FieldDeclaration.class).forEach(field -> {
            Type t = field.getElementType();
            resolveTypeFqn(t).ifPresent(to -> edges.add(new DependencyEdge(sourceClass, to)));
        });
    }

    private void addMethodCallDependencies(CompilationUnit cu, String sourceClass, Set<DependencyEdge> edges) {
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            extractMethodCallTarget(call).ifPresent(to ->
                    edges.add(new DependencyEdge(sourceClass, to)));
        });
    }

    private Optional<String> extractMethodCallTarget(MethodCallExpr call) {
        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            String to = resolved.declaringType().getQualifiedName();
            return (to != null && !to.isBlank()) ? Optional.of(to) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> resolveDeclaredTypeFqn(ClassOrInterfaceDeclaration decl) {
        try {
            ResolvedReferenceTypeDeclaration resolved = decl.resolve();
            return Optional.ofNullable(resolved.getQualifiedName());
        } catch (Exception e) {
            log.debug("Symbol resolution failed for {}, using AST fallback: {}", decl.getNameAsString(), e.getMessage());
            return decl.findCompilationUnit()
                    .flatMap(cu -> cu.getPackageDeclaration())
                    .map(pkg -> pkg.getNameAsString() + "." + decl.getNameAsString());
        }
    }

    private Optional<String> resolveTypeFqn(Type type) {
        try {
            if (type.isPrimitiveType()) {
                return Optional.empty();
            }
            if (type.isArrayType()) {
                return resolveTypeFqn(type.asArrayType().getComponentType());
            }
            if (type.isClassOrInterfaceType()) {
                return resolveTypeFqn(type.asClassOrInterfaceType());
            }
            return Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<String> resolveTypeFqn(ClassOrInterfaceType type) {
        try {
            ResolvedReferenceType r = type.resolve().asReferenceType();
            return Optional.ofNullable(r.getQualifiedName());
        } catch (Exception e) {
            log.debug("Symbol resolution failed for type {}, using fallback", type.getNameAsString());
            return Optional.empty();
        }
    }
}
