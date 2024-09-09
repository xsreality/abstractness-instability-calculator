package com.example.softwaremetrics.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A utility class for finding the main package and top-level packages
 * within a Spring Boot project. This class helps in locating the
 * package containing the `@SpringBootApplication` annotation and
 * other top-level packages based on the main package.
 */
@Component
public class PackageFinder {

    private static final Logger logger = LoggerFactory.getLogger(PackageFinder.class);

    /**
     * Finds the main package containing the `@SpringBootApplication` annotation within the given project path.
     *
     * @param projectPath the root path of the project where the search for the main package should begin
     * @return the name of the main package containing the `@SpringBootApplication` annotation, or `null` if not found
     * @throws IOException if an I/O error occurs while reading the project files
     */
    public String findMainPackage(Path projectPath) throws IOException {
        logger.debug("Searching for main package in project path: {}", projectPath);
        try (var walk = Files.walk(projectPath)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(this::containsSpringBootApplication)
                    .map(this::extractPackage)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Finds top-level packages within a given project path for the specified main package.
     *
     * @param projectPath the root path of the project where the search for top-level packages should begin
     * @param mainPackage the main package name to determine the target depth for top-level packages
     * @return a list of top-level package names found within the project path
     * @throws IOException if an I/O error occurs while reading the project files
     */
    public List<String> findTopLevelPackages(Path projectPath, String mainPackage) throws IOException {
        logger.debug("Finding top-level packages for main package: {} in project path: {}", mainPackage, projectPath);
        int targetDepth = mainPackage.split("\\.").length + 1;

        try (var walk = Files.walk(projectPath)) {
            List<String> packages = walk
                    .filter(Files::isDirectory)
                    .map(Path::toString)
                    .filter(path -> path.contains("src/main/java"))
                    .map(this::extractPackagePath)
                    .filter(path -> !path.isEmpty())
                    .map(path -> path.replace('/', '.').trim())
                    .filter(pkg -> isTopLevelPackage(pkg, mainPackage, targetDepth))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        
            logger.debug("Found {} top-level packages: {}", packages.size(), packages);
            return packages;
        }
    }

    private boolean containsSpringBootApplication(Path file) {
        try {
            return Files.lines(file).anyMatch(line -> line.contains("@SpringBootApplication"));
        } catch (IOException e) {
            logger.error("Error reading file: {}", file, e);
            return false;
        }
    }

    private String extractPackage(Path file) {
        try {
            return Files.lines(file)
                    .filter(line -> line.startsWith("package"))
                    .map(line -> line.split("\\s+")[1].replace(";", ""))
                    .findFirst()
                    .orElse("");
        } catch (IOException e) {
            logger.error("Error extracting package from file: {}", file, e);
            return "";
        }
    }

    private String extractPackagePath(String path) {
        int index = path.indexOf("src/main/java");
        if (index == -1 || index + 14 >= path.length()) {
            logger.warn("Invalid path structure: {}", path);
            return "";
        }
        return path.substring(index + 14);
    }

    private boolean isTopLevelPackage(String pkg, String mainPackage, int targetDepth) {
        String[] pkgParts = pkg.split("\\.");
        return pkgParts.length == targetDepth &&
                pkg.startsWith(mainPackage + ".") &&
                !pkg.equals(mainPackage);
    }

    public List<String> findPackages(String projectPath) throws IOException {
        Path srcMainJava = Path.of(projectPath, "src", "main", "java");
        if (!Files.exists(srcMainJava)) {
            throw new IOException("Invalid project structure: src/main/java directory not found");
        }

        return Files.walk(srcMainJava)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(this::extractPackage)
                .filter(pkg -> !pkg.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}