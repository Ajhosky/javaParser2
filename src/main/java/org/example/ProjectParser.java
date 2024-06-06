package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

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
        List<File> javaFiles = FileUtils.listJavaFiles(projectDir);  // List all Java files in the specified directory

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
                ClassVisitor classVisitor = new ClassVisitor(new String(Files.readAllBytes(file.toPath())), file.toPath(), Paths.get(projectDir));
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
}
