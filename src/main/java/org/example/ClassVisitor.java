package org.example;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visitor class to collect information from the Java file.
 */
public class ClassVisitor extends VoidVisitorAdapter<Void> {
    private final Map<String, Object> result = new LinkedHashMap<>();
    private final Set<String> methods = new HashSet<>();
    private final Map<String, Map<String, Object>> methodDetails = new HashMap<>();
    private final Map<String, Set<Map<String, Object>>> methodCalls = new HashMap<>();
    private final Set<String> objectCreations = new HashSet<>();
    private final Set<String> imports = new HashSet<>();
    private final Map<String, Map<String, Object>> fields = new HashMap<>();
    private final Set<Map<String, String>> classAnnotations = new HashSet<>();
    private final Map<String, Set<Map<String, String>>> methodAnnotations = new HashMap<>();
    private final Set<Map<String, String>> fieldAnnotations = new HashSet<>();
    private final Map<String, String> methodReturnTypes = new HashMap<>();
    private final Map<String, Map<String, String>> methodParameters = new HashMap<>();
    private final Set<Map<String, String>> databaseOperations = new HashSet<>();
    private final List<Map<String, Object>> endpoints = new ArrayList<>();
    private final List<Map<String, Object>> innerClassList = new ArrayList<>();
    private final List<Map<String, Object>> scheduledTasks = new ArrayList<>();
    private final Map<String, String> methodAccess = new HashMap<>();
    private String packageName = "";
    private String extendsClass = "";
    private final Set<String> implementsInterfaces = new HashSet<>();
    private final String code;
    private final Path relativeFilePath;
    private String currentMethod = null;
    private String structType = "";
    private final Map<String, String> classDetails = new HashMap<>();
    private final Path projectDir;
    private final Path filePath;
    private String basePath = "";

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
        // Process class-level annotations first to ensure basePath is set
        n.getAnnotations().forEach(annotation -> {
            if (annotation.getNameAsString().equals("RequestMapping")) {
                basePath = extractPathFromAnnotation(annotation);
            }
        });

        super.visit(n, arg);
        structType = n.isInterface() ? "Interface" : "Class";
        result.put("structType", structType);
        result.put("className", n.getNameAsString());
        result.put("packageName", packageName);
        result.put("classAccess", n.getAccessSpecifier().asString());
        if (n.getExtendedTypes().isNonEmpty()) {
            extendsClass = n.getExtendedTypes(0).getNameAsString();
            result.put("extend", extendsClass);
        }
        if (n.getImplementedTypes().isNonEmpty()) {
            n.getImplementedTypes().forEach(implementedType ->
                    implementsInterfaces.add(implementedType.getNameAsString()));
            result.put("implementList", implementsInterfaces);
        }
        n.getAnnotations().forEach(annotation -> {
            Map<String, String> annotationDetails = new HashMap<>();
            annotationDetails.put("Annotation", annotation.getNameAsString());
            annotationDetails.put("Details", annotation.toString());
            classAnnotations.add(annotationDetails);
        });

        classDetails.put("className", n.getNameAsString());
        classDetails.put("packageName", packageName);

        // Visit inner classes
        n.getMembers().forEach(member -> {
            if (member instanceof ClassOrInterfaceDeclaration) {
                ClassVisitor innerClassVisitor = new ClassVisitor(code, filePath, projectDir);
                member.accept(innerClassVisitor, arg);
                innerClassList.add(innerClassVisitor.getResult());
            }
        });
    }

    @Override
    public void visit(RecordDeclaration n, Void arg) {
        super.visit(n, arg);
        structType = "Record"; // Set structType to Record for records
        result.put("structType", structType); // Store the type (record)
        result.put("className", n.getNameAsString()); // Store the record name
        result.put("packageName", packageName); // Store the package name
        result.put("classAccess", n.getAccessSpecifier().asString());
        if (n.getImplementedTypes().isNonEmpty()) {
            n.getImplementedTypes().forEach(implementedType ->
                    implementsInterfaces.add(implementedType.getNameAsString())); // Store implemented interfaces
            result.put("implementList", implementsInterfaces);
        }
        n.getAnnotations().forEach(annotation -> {
            Map<String, String> annotationDetails = new HashMap<>();
            annotationDetails.put("Annotation", annotation.getNameAsString());
            annotationDetails.put("Details", annotation.toString());
            classAnnotations.add(annotationDetails); // Store record annotations
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
    public void visit(EnumDeclaration n, Void arg) {
        super.visit(n, arg);
        structType = "Enum"; // Set structType to Enum for enums
        result.put("structType", structType); // Store the type (enum)
        result.put("className", n.getNameAsString()); // Store the enum name
        result.put("packageName", packageName); // Store the package name
        result.put("classAccess", n.getAccessSpecifier().asString());
        n.getAnnotations().forEach(annotation -> {
            Map<String, String> annotationDetails = new HashMap<>();
            annotationDetails.put("Annotation", annotation.getNameAsString());
            annotationDetails.put("Details", annotation.toString());
            classAnnotations.add(annotationDetails); // Store enum annotations
        });

        // Store enum details
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
        String previousMethod = currentMethod; // Save the previous method
        currentMethod = n.getNameAsString(); // Set the current method name
        methods.add(currentMethod); // Add the method name to the set of methods

        // Collect method details
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ReturnType", n.getType().asString());
        details.put("methodAccess", n.getAccessSpecifier().asString());
        details.put("startLine", n.getBegin().map(pos -> pos.line).orElse(-1));
        details.put("endLine", n.getEnd().map(pos -> pos.line).orElse(-1));
        details.put("methodCode", n.toString()); // Add the method code

        // Collect parameters
        Map<String, String> parameters = new LinkedHashMap<>();
        n.getParameters().forEach(p -> parameters.put(p.getNameAsString(), p.getType().asString()));
        methodParameters.put(currentMethod, parameters);
        details.put("Parameters", parameters); // Add parameters to method details

        methodDetails.put(currentMethod, details);

        // Collect return type
        methodReturnTypes.put(currentMethod, n.getType().asString());

        // Collect access specifier
        methodAccess.put(currentMethod, n.getAccessSpecifier().asString());

        // Collect method annotations
        Set<Map<String, String>> annotations = new HashSet<>();
        n.getAnnotations().forEach(annotation -> {
            Map<String, String> annotationDetails = new HashMap<>();
            annotationDetails.put("Annotation", annotation.getNameAsString());
            annotationDetails.put("Details", annotation.toString());
            annotations.add(annotationDetails);
        });
        methodAnnotations.put(currentMethod, annotations);

        // Collect endpoint details if present
        n.getAnnotations().forEach(annotation -> {
            String annotationName = annotation.getNameAsString();
            if (annotationName.matches("GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping")) {
                Map<String, Object> endpoint = new LinkedHashMap<>();
                endpoint.put("Method", currentMethod);
                endpoint.put("HttpMethod", extractHttpMethodFromAnnotation(annotationName));
                endpoint.put("Path", combinePaths(basePath, extractPathFromAnnotation(annotation)));
                endpoint.put("Parameters", parameters);

                // Collect response type and package
                String responseType = n.getType().asString();
                endpoint.put("ResponseType", responseType);
                String genericType = extractGenericType(responseType);
                if (genericType != null) {
                    endpoint.put("ResponseGenericType", genericType);
                    String responsePackage = resolveFromImports(genericType).orElse("Unknown Package");
                    endpoint.put("ResponsePackage", responsePackage);
                } else {
                    endpoint.put("ResponsePackage", resolveFromImports(responseType).orElse("Unknown Package"));
                }

                // Extract returned body and package
                String returnedBody = extractReturnedBody(n);
                endpoint.put("ReturnedBody", returnedBody);
                if (returnedBody != null) {
                    String returnedBodyPackage = resolveFromImports(returnedBody).orElse("Unknown Package");
                    endpoint.put("ReturnedBodyPackage", returnedBodyPackage);
                } else {
                    endpoint.put("ReturnedBodyPackage", "Unknown Package");
                }

                endpoints.add(endpoint);

            }
        });

        super.visit(n, arg); // Visit child nodes
        currentMethod = previousMethod; // Restore the previous method
    }

    private String extractGenericType(String type) {
        Pattern pattern = Pattern.compile("<(.*?)>");
        Matcher matcher = pattern.matcher(type);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
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
        }
        return "";
    }

    private String combinePaths(String basePath, String methodPath) {
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        if (!methodPath.startsWith("/")) {
            methodPath = "/" + methodPath;
        }
        return basePath + methodPath;
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

    private String extractCronExpression(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr) {
            for (MemberValuePair pair : ((NormalAnnotationExpr) annotation).getPairs()) {
                if (pair.getNameAsString().equals("cron")) {
                    return pair.getValue().toString().replace("\"", "");
                }
            }
        }
        return "";
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
        super.visit(n, arg);
        if (currentMethod != null) {
            Map<String, Object> methodCallInfo = new LinkedHashMap<>();
            methodCallInfo.put("MethodName", n.getNameAsString());

            // Resolve the class where the method is defined
            String fromClass = resolveFromClass(n);
            if (fromClass != null) {
                methodCallInfo.put("FromClass", fromClass);
            } else {
                methodCallInfo.put("Scope", "Unknown");
                methodCallInfo.put("FromClass", "Unknown");
            }

            // Add line number information
            methodCallInfo.put("Line", n.getBegin().map(pos -> pos.line).orElse(-1));

            // Initialize the method call set if not already present
            methodCalls.computeIfAbsent(currentMethod, k -> new HashSet<>());
            methodCalls.get(currentMethod).add(methodCallInfo);
        }
    }

    private boolean methodExistsInClassOrInterface(String className, String methodName) {
        // This function should check if a given method exists in the specified class or interface
        // Implement this based on your context, e.g., by using reflection or analyzing the AST
        // This is a placeholder and needs to be implemented
        return false;
    }

    private String resolveFromClass(MethodCallExpr methodCallExpr) {
        if (methodCallExpr.getScope().isPresent()) {
            String scope = methodCallExpr.getScope().get().toString();

            // Check if the method is called on a field
            if (fields.containsKey(scope)) {
                return fields.get(scope).get("type").toString();
            }

            // Check if the scope is a known class from imports or fields
            Optional<String> resolvedClass = resolveFromImports(scope);
            if (resolvedClass.isPresent()) {
                return resolvedClass.get();
            }

            // Check if the scope is a method parameter
            if (currentMethod != null) {
                Map<String, String> parameters = methodParameters.get(currentMethod);
                if (parameters != null && parameters.containsKey(scope)) {
                    return parameters.get(scope);
                }
            }

            // Check if the method belongs to the current class
            if (scope.equals("this") || scope.equals(classDetails.get("className"))) {
                return classDetails.get("className");
            }
        }

        // If there's no explicit scope, check if it's a method in the current class or its parents
        if (!methodCallExpr.getScope().isPresent()) {
            String methodName = methodCallExpr.getNameAsString();
            if (methods.contains(methodName)) {
                return classDetails.get("className");
            }

            if (!extendsClass.isEmpty() && methodExistsInClassOrInterface(extendsClass, methodName)) {
                return extendsClass;
            }

            for (String interfaceName : implementsInterfaces) {
                if (methodExistsInClassOrInterface(interfaceName, methodName)) {
                    return interfaceName;
                }
            }

            // Check if the method is a static method from an import
            for (String importStr : imports) {
                if (importStr.endsWith("." + methodName)) {
                    return importStr.substring(0, importStr.lastIndexOf('.'));
                }
            }
        }

        // Check if the scope is a static method
        String staticMethodClass = resolveStaticMethodClass(methodCallExpr.getNameAsString());
        if (staticMethodClass != null) {
            return staticMethodClass;
        }

        return null; // Return null if the class cannot be resolved
    }

    private String resolveStaticMethodClass(String methodName) {
        // Check if the method name matches known static methods
        // You can expand this with a map or more sophisticated logic if needed
        if (methodName.equals("ok") || methodName.equals("noContent") || methodName.equals("created")) {
            return "ResponseEntity";
        }

        return null;
    }

    private Optional<String> resolveFromImports(String className) {
        return imports.stream()
                .filter(imp -> imp.endsWith("." + className) || imp.equals(className))
                .findFirst();
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
            Map<String, Object> methodInfo = new LinkedHashMap<>();
            methodInfo.put("MethodName", method);
            methodInfo.put("ReturnType", methodReturnTypes.get(method));
            methodInfo.put("Parameters", methodParameters.get(method)); // Add parameters to method info
            methodInfo.put("Details", methodDetails.get(method));
            methodInfo.put("MethodCalls", methodCalls.get(method));
            methodInfo.put("Annotations", methodAnnotations.get(method)); // Add annotations to method info
            methodsList.add(methodInfo);
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

        // Add scheduled tasks to the result
        result.put("ScheduledTasks", scheduledTasks);

        return result;
    }

    private String extractReturnedBody(MethodDeclaration n) {
        // Analyze the method body to find return statements
        ReturnFinder returnFinder = new ReturnFinder();
        returnFinder.visit(n.getBody().orElse(null), null);
        return returnFinder.getReturnedType();
    }

    private class ReturnFinder extends VoidVisitorAdapter<Void> {
        private String returnedType;

        @Override
        public void visit(ReturnStmt n, Void arg) {
            super.visit(n, arg);
            Expression expr = n.getExpression().orElse(null);
            if (expr != null) {
                if (expr instanceof ObjectCreationExpr) {
                    returnedType = ((ObjectCreationExpr) expr).getType().getNameAsString();
                } else if (expr instanceof MethodCallExpr) {
                    returnedType = ((MethodCallExpr) expr).getNameAsString();
                } else {
                    returnedType = expr.toString();
                }
            }
        }

        public String getReturnedType() {
            return returnedType;
        }
    }
}
