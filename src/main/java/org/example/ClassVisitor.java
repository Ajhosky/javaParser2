package org.example;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Visitor class to collect information from the Java file.
 */
public class ClassVisitor extends VoidVisitorAdapter<Void> {
    private final Map<String, Object> result = new HashMap<>(); // Map to store collected information
    private final Set<String> methods = new HashSet<>(); // Set to store method names
    private final Map<String, Map<String, Object>> methodDetails = new HashMap<>(); // Map to store method details (start/end lines)
    private final Map<String, Set<Map<String, Object>>> methodCalls = new HashMap<>(); // Map to store method calls
    private final Set<String> objectCreations = new HashSet<>(); // Set to store created objects
    private final Set<String> imports = new HashSet<>(); // Set to store import statements
    private final Map<String, Map<String, Object>> fields = new HashMap<>(); // Updated to store field names, types, access, and annotations
    private final Set<Map<String, String>> classAnnotations = new HashSet<>(); // Set to store class annotations
    private final Map<String, Set<Map<String, String>>> methodAnnotations = new HashMap<>(); // Set to store method annotations
    private final Set<Map<String, String>> fieldAnnotations = new HashSet<>(); // Set to store field annotations
    private final Map<String, String> methodReturnTypes = new HashMap<>(); // Map to store method return types
    private final Map<String, Map<String, String>> methodParameters = new HashMap<>(); // Map to store method parameters
    private final Set<Map<String, String>> databaseOperations = new HashSet<>(); // Set to store database operations
    private final List<Map<String, Object>> endpoints = new ArrayList<>(); // List to store endpoint information
    private final List<Map<String, Object>> innerClassList = new ArrayList<>(); // List to store inner classes
    private String packageName = ""; // String to store package name
    private String extendsClass = ""; // String to store extended class name
    private final Set<String> implementsInterfaces = new HashSet<>(); // Set to store implemented interfaces
    private final String code; // String to store the source code
    private final Path relativeFilePath; // Path to store the relative file path
    private String currentMethod = null; // String to store the current method name being visited
    private String structType = ""; // String to store the type (class or interface)
    private final Map<String, String> classDetails = new HashMap<>(); // Map to store class details
    private final Path projectDir; // Store projectDir for inner class visitors
    private final Path filePath; // Store filePath for inner class visitors

    /**
     * Constructor to initialize ClassVisitor with the source code, file path, and project directory.
     *
     * @param code the source code of the Java file
     * @param filePath the file path of the Java file
     * @param projectDir the path to the project directory
     */
    public ClassVisitor(String code, Path filePath, Path projectDir) {
        this.code = code;
        this.filePath = filePath;
        this.projectDir = projectDir;
        this.relativeFilePath = projectDir.relativize(filePath);
    }

    @Override
    public void visit(PackageDeclaration n, Void arg) {
        super.visit(n, arg);
        packageName = n.getNameAsString(); // Store the package name
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        super.visit(n, arg);
        structType = n.isInterface() ? "Interface" : "Class"; // Determine if it's a class or interface
        result.put("structType", structType); // Store the type (class/interface)
        result.put("className", n.getNameAsString()); // Store the class/interface name
        result.put("packageName", packageName); // Store the package name
        result.put("classAccess", n.getAccessSpecifier().asString());
        if (n.getExtendedTypes().isNonEmpty()) {
            extendsClass = n.getExtendedTypes(0).getNameAsString(); // Store the extended class name
            result.put("extend", extendsClass);
        }
        if (n.getImplementedTypes().isNonEmpty()) {
            n.getImplementedTypes().forEach(implementedType ->
                    implementsInterfaces.add(implementedType.getNameAsString())); // Store implemented interfaces
            result.put("implementList", implementsInterfaces);
        }
        n.getAnnotations().forEach(annotation -> {
            Map<String, String> annotationDetails = new HashMap<>();
            annotationDetails.put("Annotation", annotation.getNameAsString());
            annotationDetails.put("Details", annotation.toString());
            classAnnotations.add(annotationDetails); // Store class annotations
        });

        // Store class details
        classDetails.put("className", n.getNameAsString());
        classDetails.put("packageName", packageName);

        // Visit inner classes
        n.getMembers().forEach(member -> {
            if (member instanceof ClassOrInterfaceDeclaration) {
                ClassVisitor innerClassVisitor = new ClassVisitor(code, filePath, projectDir); // Pass filePath and projectDir
                member.accept(innerClassVisitor, arg);
                innerClassList.add(innerClassVisitor.getResult());
            }
        });
    }

    @Override
    public void visit(FieldDeclaration n, Void arg) {
        super.visit(n, arg);
        for (VariableDeclarator var : n.getVariables()) {
            Map<String, Object> fieldDetails = new HashMap<>();
            fieldDetails.put("type", var.getType().asString());
            fieldDetails.put("access", n.getAccessSpecifier().asString());
            Set<Map<String, String>> annotations = new HashSet<>();
            n.getAnnotations().forEach(annotation -> {
                Map<String, String> annotationDetails = new HashMap<>();
                annotationDetails.put("Annotation", annotation.getNameAsString());
                annotationDetails.put("Details", annotation.toString());
                annotations.add(annotationDetails); // Store field annotations
                fieldAnnotations.add(annotationDetails); // Store all field annotations in a separate set
            });
            fieldDetails.put("annotations", annotations);
            fields.put(var.getNameAsString(), fieldDetails); // Store field names, types, access, and annotations
        }
    }

    @Override
    public void visit(MethodDeclaration n, Void arg) {
        super.visit(n, arg);
        String methodName = n.getNameAsString();
        methods.add(methodName); // Store the method name
        methodReturnTypes.put(methodName, n.getType().asString()); // Store the method return type

        Map<String, String> parameters = new HashMap<>();
        for (Parameter param : n.getParameters()) {
            if (!methodName.equals("main")) {
                parameters.put(param.getNameAsString(), param.getType().asString()); // Store parameter name and type
            }
        }
        if (!methodName.equals("main")) {
            methodParameters.put(methodName, parameters); // Store method parameters
        }

        Set<String> requestBodyParameters = new HashSet<>();
        for (Parameter param : n.getParameters()) {
            if (param.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("RequestBody"))) {
                requestBodyParameters.add(param.getType().asString() + " " + param.getNameAsString());
            }
        }

        // Store the start and end lines of the method
        Map<String, Object> details = new HashMap<>();
        details.put("methodAccess", n.getAccessSpecifier().asString());
        details.put("ReturnType", n.getType().asString()); // Add return type to method details
        details.put("StartLine", n.getBegin().map(pos -> pos.line).orElse(-1));
        details.put("EndLine", n.getEnd().map(pos -> pos.line).orElse(-1));
        details.put("Code", n.toString()); // Add the method code

        // Add parameters for the main method directly to the details map
        if (methodName.equals("main")) {
            List<String> mainMethodParameters = new ArrayList<>();
            for (Parameter param : n.getParameters()) {
                mainMethodParameters.add(param.getType().asString() + " " + param.getNameAsString());
            }
            details.put("methodParameters", mainMethodParameters);
        } else {
            details.put("methodParameters", parameters);
        }

        methodDetails.put(methodName, details);

        Set<Map<String, String>> relevantMethodAnnotations = new HashSet<>();
        for (AnnotationExpr annotation : n.getAnnotations()) {
            Map<String, String> annotationDetails = new HashMap<>();
            annotationDetails.put("Annotation", annotation.getNameAsString());
            annotationDetails.put("Details", annotation.toString());
            relevantMethodAnnotations.add(annotationDetails); // Store method annotations
            // Check for endpoint annotations
            if (Arrays.asList("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping").contains(annotation.getNameAsString())) {
                Map<String, Object> endpoint = new HashMap<>();
                endpoint.put("MethodName", methodName);
                endpoint.put("Annotation", annotation.getNameAsString());
                endpoint.put("Path", extractPathFromAnnotation(annotation));
                endpoint.put("HTTPMethod", extractHttpMethodFromAnnotation(annotation.getNameAsString()));
                endpoint.put("Parameters", parameters);
                endpoint.put("RequestBodyParameters", requestBodyParameters);
                endpoints.add(endpoint);
            }
        }
        methodAnnotations.put(methodName, relevantMethodAnnotations); // Store relevant method annotations
        currentMethod = methodName; // Set the current method being visited

        methodCalls.putIfAbsent(methodName, new HashSet<>()); // Initialize method call set
    }

    private String extractPathFromAnnotation(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            return ((SingleMemberAnnotationExpr) annotation).getMemberValue().toString().replace("\"", "");
        } else if (annotation instanceof NormalAnnotationExpr) {
            for (MemberValuePair pair : ((NormalAnnotationExpr) annotation).getPairs()) {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("path")) {
                    return pair.getValue().toString().replace("\"", "");
                }
            }
        } else if (annotation instanceof MarkerAnnotationExpr) {
            // For marker annotations without any members, return an empty path
            return "";
        }
        return "";
    }

    private String extractHttpMethodFromAnnotation(String annotationName) {
        switch (annotationName) {
            case "GetMapping":
                return "GET";
            case "PostMapping":
                return "POST";
            case "PutMapping":
                return "PUT";
            case "DeleteMapping":
                return "DELETE";
            default:
                return "REQUEST";
        }
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        super.visit(n, arg);
        if (currentMethod != null) {
            Map<String, Object> methodCallInfo = new HashMap<>();
            methodCallInfo.put("MethodName", n.getNameAsString());

            // Check if the method call has a scope
            if (n.getScope().isPresent()) {
                String scope = n.getScope().get().toString();
                methodCallInfo.put("Scope", scope);

                // Extract class and package information from the scope
                String[] scopeParts = scope.split("\\.");
                if (scopeParts.length > 0) {
                    methodCallInfo.put("FromClass", scopeParts[scopeParts.length - 1]);
                    if (scopeParts.length > 1) {
                        String fromPackage = String.join(".", Arrays.copyOf(scopeParts, scopeParts.length - 1));
                        methodCallInfo.put("FromPackage", fromPackage);
                    }
                }

                // Additional logic to handle static method calls like ok(), noContent(), created()
                if (scope.equals("ok") || scope.equals("noContent") || scope.equals("created")) {
                    methodCallInfo.put("FromClass", "ResponseEntity");
                    methodCallInfo.put("FromPackage", "org.springframework.http");
                }

                // If the scope is a method call like ok(), body() etc.
                if (scope.equals("ok()") || scope.equals("noContent()") || scope.equals("created()")) {
                    methodCallInfo.put("FromClass", "ResponseEntity");
                    methodCallInfo.put("FromPackage", "org.springframework.http");
                }
            } else {
                methodCallInfo.put("Scope", "this"); // If no scope, it's called on the current class
                methodCallInfo.put("FromClass", classDetails.get("className"));
                methodCallInfo.put("FromPackage", classDetails.get("packageName"));
            }

            // Check if it's a database operation
            boolean isDatabaseOperation = n.getNameAsString().matches("executeQuery|executeUpdate|execute|prepareStatement|createStatement|setAutoCommit|commit|rollback|close");
            methodCallInfo.put("isDatabaseOperation", isDatabaseOperation);

            methodCalls.get(currentMethod).add(methodCallInfo); // Store method calls

            // Store database operation separately if it's a database operation
            if (isDatabaseOperation) {
                Map<String, String> dbOperation = new HashMap<>();
                dbOperation.put("Operation", n.getNameAsString());
                dbOperation.put("Method", currentMethod);
                dbOperation.put("Line", String.valueOf(n.getBegin().map(pos -> pos.line).orElse(-1)));
                dbOperation.put("Details", n.toString());
                databaseOperations.add(dbOperation); // Store database operation
            }
        }
    }

    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        super.visit(n, arg);
        objectCreations.add(n.getType().getNameAsString()); // Store created objects
    }

    @Override
    public void visit(ImportDeclaration n, Void arg) {
        super.visit(n, arg);
        imports.add(n.getNameAsString()); // Store import statements
    }

    /**
     * Method to get the collected information as a map.
     *
     * @return a map containing the collected information
     */
    public Map<String, Object> getResult() {
        result.put("packageName", packageName);
        result.put("importList", imports);
        result.put("fieldList", fields);
        result.put("FieldAnnotations", fieldAnnotations);
        result.put("Code", code);
        result.put("FilePath", relativeFilePath.toString().replace("\\", "/")); // Add the relative file path to the result
        result.put("Extends", extendsClass);
        result.put("Implements", implementsInterfaces);
        result.put("parent_class", extendsClass);

        // Add the correct parent class or interface
        if (!extendsClass.isEmpty()) {
            result.put("parent_class", extendsClass);
        } else if (!implementsInterfaces.isEmpty()) {
            result.put("parent_class", implementsInterfaces.iterator().next());
        } else {
            result.put("parent_class", "None");
        }

        // Add class annotations to the result
        result.put("ClassAnnotations", classAnnotations);

        // Create a list to hold method details
        List<Map<String, Object>> methodsList = new ArrayList<>();
        for (String method : methods) {
            Map<String, Object> methodInfo = new HashMap<>();
            methodInfo.put("MethodName", method); // Order adjusted
            methodInfo.put("ReturnType", methodReturnTypes.get(method)); // Order adjusted
            methodInfo.put("Details", methodDetails.get(method)); // Order adjusted
            methodInfo.put("MethodCalls", methodCalls.get(method)); // Order adjusted
            methodInfo.put("Annotations", methodAnnotations.get(method)); // Add annotations to method info
            methodsList.add(methodInfo); // Add method info to the list
        }

        // Add methods list to the result
        result.put("methodList", methodsList);

        // Add object creations to the result
        result.put("ObjectCreations", objectCreations);

        // Add database operations to the result
        result.put("DatabaseOperations", databaseOperations);

        // Add endpoints to the result
        result.put("Endpoints", endpoints);

        // Add inner classes to the result
        result.put("innerClassList", innerClassList);

        return result;
    }
}
