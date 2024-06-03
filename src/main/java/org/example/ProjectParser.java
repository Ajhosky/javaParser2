package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ProjectParser class to parse Java source files in a specified directory,
 * extract various pieces of information, and save the result in JSON format.
 */
public class ProjectParser {

    private static final Logger logger = Logger.getLogger(ProjectParser.class.getName());

    /**
     * Main method to initiate parsing of Java source files.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        try {
            // Set the path to your Java project directory
            String projectDir = "C:\\Users\\user\\downloads\\bankensApi";
            List<File> javaFiles = listJavaFiles(projectDir);  // List all Java files in the specified directory

            // List to store parsed information from each Java file
            List<Map<String, Object>> parsedFiles = new ArrayList<>();
            for (File file : javaFiles) {
                try {
                    // Read the file content
                    FileInputStream in = new FileInputStream(file);
                    // Parse the file content into a CompilationUnit (AST root node)
                    CompilationUnit cu = JavaParser.parse(in);

                    // Create a visitor to collect information from the CompilationUnit
                    ClassVisitor classVisitor = new ClassVisitor(new String(Files.readAllBytes(file.toPath())));
                    // Visit the CompilationUnit with the created visitor
                    cu.accept(classVisitor, null);

                    // Add the collected information to the list
                    parsedFiles.add(classVisitor.getResult());
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error reading file: " + file.getAbsolutePath(), e);
                }
            }

            try {
                // Convert the result to JSON using Jackson ObjectMapper
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedFiles);

                // Save the JSON result to a file in the "src" directory
                Files.write(Paths.get("src", "parsed_output.json"), json.getBytes());
                logger.info("JSON output successfully written to src/parsed_output.json");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error writing JSON output file.", e);
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error initializing ProjectParser.", e);
        }
    }

    /**
     * Method to list all Java files in the specified project directory.
     *
     * @param directoryName the path to the project directory
     * @return a list of Java files in the directory
     * @throws IOException if an I/O error occurs
     */
    private static List<File> listJavaFiles(String directoryName) throws IOException {
        List<File> fileList = new ArrayList<>();
        try {
            // Walk through the directory and collect all .java files
            Files.walk(Paths.get(directoryName))
                    .filter(Files::isRegularFile) // Filter to include only regular files
                    .filter(path -> path.toString().endsWith(".java")) // Filter to include only .java files
                    .forEach(path -> fileList.add(path.toFile())); // Add each file to the fileList
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error walking through directory: " + directoryName, e);
            throw e;
        }
        return fileList;
    }

    /**
     * Visitor class to collect information from the Java file.
     */
    private static class ClassVisitor extends VoidVisitorAdapter<Void> {
        private final Map<String, Object> result = new HashMap<>(); // Map to store collected information
        private final Set<String> methods = new HashSet<>(); // Set to store method names
        private final Map<String, Map<String, Object>> methodDetails = new HashMap<>(); // Map to store method details (start/end lines)
        private final Map<String, Set<String>> methodCalls = new HashMap<>(); // Map to store method calls
        private final Set<String> objectCreations = new HashSet<>(); // Set to store created objects
        private final Set<String> imports = new HashSet<>(); // Set to store import statements
        private final Set<String> fields = new HashSet<>(); // Set to store field names
        private final Set<String> classAnnotations = new HashSet<>(); // Set to store class annotations
        private final Set<String> fieldAnnotations = new HashSet<>(); // Set to store field annotations
        private final Set<String> methodAnnotations = new HashSet<>(); // Set to store method annotations
        private final Map<String, String> methodReturnTypes = new HashMap<>(); // Map to store method return types
        private final Map<String, Set<String>> methodParameters = new HashMap<>(); // Map to store method parameters
        private String packageName = ""; // String to store package name
        private String extendsClass = ""; // String to store extended class name
        private final Set<String> implementsInterfaces = new HashSet<>(); // Set to store implemented interfaces
        private final String code; // String to store the source code
        private String currentMethod = null; // String to store the current method name being visited

        /**
         * Constructor to initialize ClassVisitor with the source code.
         *
         * @param code the source code of the Java file
         */
        public ClassVisitor(String code) {
            this.code = code;
        }

        @Override
        public void visit(PackageDeclaration n, Void arg) {
            super.visit(n, arg);
            packageName = n.getNameAsString(); // Store the package name
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            result.put("ClassName", n.getNameAsString()); // Store the class/interface name
            if (n.getExtendedTypes().isNonEmpty()) {
                extendsClass = n.getExtendedTypes(0).getNameAsString(); // Store the extended class name
                result.put("Extends", extendsClass);
            }
            if (n.getImplementedTypes().isNonEmpty()) {
                n.getImplementedTypes().forEach(implementedType ->
                        implementsInterfaces.add(implementedType.getNameAsString())); // Store implemented interfaces
                result.put("Implements", implementsInterfaces);
            }
            n.getAnnotations().forEach(annotation -> classAnnotations.add(annotation.getNameAsString())); // Store class annotations
        }

        @Override
        public void visit(FieldDeclaration n, Void arg) {
            super.visit(n, arg);
            n.getVariables().forEach(var -> fields.add(var.getNameAsString())); // Store field names
            n.getAnnotations().forEach(annotation -> fieldAnnotations.add(annotation.getNameAsString())); // Store field annotations
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            String methodName = n.getNameAsString();
            methods.add(methodName); // Store the method name
            methodReturnTypes.put(methodName, n.getType().asString()); // Store the method return type
            Set<String> parameters = new HashSet<>();
            n.getParameters().forEach(param -> parameters.add(param.getType().asString() + " " + param.getNameAsString())); // Store method parameters
            methodParameters.put(methodName, parameters);
            n.getAnnotations().forEach(annotation -> methodAnnotations.add(annotation.getNameAsString())); // Store method annotations
            currentMethod = methodName; // Set the current method being visited
            methodCalls.putIfAbsent(methodName, new HashSet<>()); // Initialize method call set

            // Store the start and end lines of the method
            Map<String, Object> details = new HashMap<>();
            details.put("StartLine", n.getBegin().map(pos -> pos.line).orElse(-1));
            details.put("EndLine", n.getEnd().map(pos -> pos.line).orElse(-1));
            methodDetails.put(methodName, details);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (currentMethod != null) {
                methodCalls.get(currentMethod).add(n.getNameAsString()); // Store method calls
                if (n.getScope().isPresent() && n.getScope().get() instanceof com.github.javaparser.ast.expr.NameExpr) {
                    methodCalls.get(currentMethod).add(n.getScope().get().toString() + "." + n.getNameAsString()); // Store scoped method calls
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
            result.put("PackageName", packageName);
            result.put("ClassAnnotations", classAnnotations);
            result.put("Fields", fields);
            result.put("FieldAnnotations", fieldAnnotations);
            result.put("Imports", imports);
            result.put("Code", code);
            result.put("Extends", extendsClass);
            result.put("Implements", implementsInterfaces);

            // Create a list to hold method details
            List<Map<String, Object>> methodsList = new ArrayList<>();
            for (String method : methods) {
                Map<String, Object> methodInfo = new HashMap<>();
                methodInfo.put("MethodName", method);
                methodInfo.put("ReturnType", methodReturnTypes.get(method));
                methodInfo.put("Parameters", methodParameters.get(method));
                methodInfo.put("Annotations", methodAnnotations);
                methodInfo.put("Details", methodDetails.get(method));
                methodInfo.put("Calls", methodCalls.get(method));
                methodsList.add(methodInfo); // Add method info to the list
            }

            // Add methods list to the result
            result.put("Methods", methodsList);

            // Add object creations to the result
            result.put("ObjectCreations", objectCreations);

            return result;
        }
    }
}
