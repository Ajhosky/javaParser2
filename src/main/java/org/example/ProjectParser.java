package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
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
        if (args.length != 2) {
            System.out.println("Usage: java ProjectParser <projectDir> <outputJsonFile>");
            return;
        }

        String projectDir = args[0];
        String outputJsonFile = args[1];

        try {
            parseProjectToJson(projectDir, outputJsonFile);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during parsing and JSON generation.", e);
        }
    }

    /**
     * Parses Java source files in the specified directory and writes the result to a JSON file.
     *
     * @param projectDir the path to the project directory
     * @param outputJsonFile the name of the JSON file to be created
     * @throws Exception if an error occurs during parsing or writing the JSON file
     */
    public static void parseProjectToJson(String projectDir, String outputJsonFile) throws Exception {
        List<File> javaFiles = listJavaFiles(projectDir);  // List all Java files in the specified directory

        // Configure JavaParser to use Java 17
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        JavaParser javaParser = new JavaParser(parserConfiguration);

        // List to store parsed information from each Java file
        List<Map<String, Object>> parsedFiles = new ArrayList<>();
        for (File file : javaFiles) {
            try (FileInputStream in = new FileInputStream(file)) {
                // Read the file content and parse it into a CompilationUnit (AST root node)
                CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow(() -> new Exception("Parsing failed"));

                // Create a visitor to collect information from the CompilationUnit
                ClassVisitor classVisitor = new ClassVisitor(new String(Files.readAllBytes(file.toPath())), file.getAbsolutePath(), projectDir);
                // Visit the CompilationUnit with the created visitor
                cu.accept(classVisitor, null);

                // Add the collected information to the list
                parsedFiles.add(classVisitor.getResult());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error reading file: " + file.getAbsolutePath(), e);
            }
        }

        try {
            // Convert the result to JSON using Jackson ObjectMapper
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsedFiles);

            // Save the JSON result to the specified file
            outputJsonFile += ".json";
            Files.write(Paths.get(outputJsonFile), json.getBytes());
            logger.info("JSON output successfully written to " + outputJsonFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing JSON output file.", e);
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
        private final Map<String, Set<Map<String, String>>> methodCalls = new HashMap<>(); // Map to store method calls
        private final Set<String> objectCreations = new HashSet<>(); // Set to store created objects
        private final Set<String> imports = new HashSet<>(); // Set to store import statements
        private final Map<String, String> fields = new HashMap<>(); // Map to store field names and types
        private final Set<String> classAnnotations = new HashSet<>(); // Set to store class annotations
        private final Set<String> fieldAnnotations = new HashSet<>(); // Set to store field annotations
        private final Set<String> methodAnnotations = new HashSet<>(); // Set to store method annotations
        private final Map<String, String> methodReturnTypes = new HashMap<>(); // Map to store method return types
        private final Map<String, Set<String>> methodParameters = new HashMap<>(); // Map to store method parameters
        private String packageName = ""; // String to store package name
        private String extendsClass = ""; // String to store extended class name
        private final Set<String> implementsInterfaces = new HashSet<>(); // Set to store implemented interfaces
        private final String code; // String to store the source code
        private final String relativeFilePath; // String to store the relative file path
        private String currentMethod = null; // String to store the current method name being visited
        private String structType = ""; // String to store the type (class or interface)

        /**
         * Constructor to initialize ClassVisitor with the source code, file path, and project directory.
         *
         * @param code the source code of the Java file
         * @param filePath the file path of the Java file
         * @param projectDir the path to the project directory
         */
        public ClassVisitor(String code, String filePath, String projectDir) {
            this.code = code;
            this.relativeFilePath = Paths.get(projectDir).relativize(Paths.get(filePath)).toString();
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
            result.put("StructType", structType); // Store the type (class/interface)
            result.put("classAccess", n.getAccessSpecifier().asString());
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
            for (VariableDeclarator var : n.getVariables()) {
                fields.put(var.getNameAsString(), var.getType().asString()); // Store field names and types
            }
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
            details.put("methodAccess", n.getAccessSpecifier().asString());
            details.put("ReturnType", n.getType().asString()); // Add return type to method details
            details.put("StartLine", n.getBegin().map(pos -> pos.line).orElse(-1));
            details.put("EndLine", n.getEnd().map(pos -> pos.line).orElse(-1));
            methodDetails.put(methodName, details);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (currentMethod != null) {
                Map<String, String> methodCallInfo = new HashMap<>();
                methodCallInfo.put("MethodName", n.getNameAsString());

                // Check if the method call has a scope
                if (n.getScope().isPresent()) {
                    methodCallInfo.put("Scope", n.getScope().get().toString());
                } else {
                    methodCallInfo.put("Scope", "this"); // If no scope, it's called on the current class
                }

                methodCalls.get(currentMethod).add(methodCallInfo); // Store method calls
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
            result.put("FilePath", relativeFilePath.replace("\\", "/")); // Add the relative file path to the result
            result.put("Extends", extendsClass);
            result.put("Implements", implementsInterfaces);

            // Add the correct parent class or interface
            if (!extendsClass.isEmpty()) {
                result.put("ParentClass", extendsClass);
            } else if (!implementsInterfaces.isEmpty()) {
                result.put("ParentClass", implementsInterfaces.iterator().next());
            } else {
                result.put("ParentClass", "None");
            }

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
