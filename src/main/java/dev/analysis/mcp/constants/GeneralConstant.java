package dev.analysis.mcp.constants;

/**
 * Centralized constants used throughout the application.
 *
 * <p>This class contains hardcoded string values that are used across multiple
 * classes to ensure consistency and ease of maintenance.</p>
 */
public final class GeneralConstant {

    private GeneralConstant() {}

    public static final String SERVER_NAME = "dependency-analyzer-mcp";
    public static final String SERVER_VERSION = "0.0.1";

    public static final String TOOL_GET_DEPENDENCIES = "get_dependencies";
    public static final String TOOL_GET_DEPENDENCIES_DESC = "Return all classes that the given class depends on";

    public static final String TOOL_GET_REVERSE_DEPENDENCIES = "get_reverse_dependencies";
    public static final String TOOL_GET_REVERSE_DEPENDENCIES_DESC = "Return all classes that depend on the given class";

    public static final String TOOL_TRACE_DEPENDENCY_CHAIN = "trace_dependency_chain";
    public static final String TOOL_TRACE_DEPENDENCY_CHAIN_DESC = "Trace a dependency path from one class to another";

    public static final String PARAM_CLASS = "class";
    public static final String PARAM_FROM = "from";
    public static final String PARAM_TO = "to";

    public static final String SCHEMA_TYPE_OBJECT = "object";
    public static final String SCHEMA_TYPE_STRING = "string";

    public static final String ERROR_MISSING_CLASS_PARAM = "Missing required parameter: class";
    public static final String ERROR_MISSING_FROM_TO_PARAMS = "Missing required parameters: from, to";
    public static final String ERROR_GRAPH_NOT_INITIALIZED = "Dependency graph not initialized";

    public static final String ARG_REPO_PATH_PREFIX = "--repoPath=";
    public static final String USAGE_MESSAGE = "Missing required argument --repoPath=/path/to/repo (or provide as first positional arg)";
}
