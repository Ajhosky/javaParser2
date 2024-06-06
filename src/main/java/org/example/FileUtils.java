package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtils {

    private static final Logger logger = Logger.getLogger(FileUtils.class.getName());

    /**
     * Method to list all Java files in the specified project directory.
     *
     * @param directoryName the path to the project directory
     * @return a list of Java files in the directory
     * @throws IOException if an I/O error occurs
     */
    public static List<File> listJavaFiles(String directoryName) throws IOException {
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
}
