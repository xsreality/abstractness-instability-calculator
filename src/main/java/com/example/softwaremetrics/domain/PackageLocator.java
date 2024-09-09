package com.example.softwaremetrics.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PackageLocator {

    private static final Logger logger = LoggerFactory.getLogger(PackageLocator.class);

    private final JavaClassAnalyzer javaClassAnalyzer;
    private final ProjectPathTraverser projectPathTraverser;

    public PackageLocator(JavaClassAnalyzer javaClassAnalyzer, ProjectPathTraverser projectPathTraverser) {
        this.javaClassAnalyzer = javaClassAnalyzer;
        this.projectPathTraverser = projectPathTraverser;
    }

    public String findMainPackage(Path projectPath) {
        logger.debug("Searching for main package in project path: {}", projectPath);
        Path srcMainJavaPath = projectPath.resolve("src/main/java");
        if (!Files.exists(srcMainJavaPath)) {
            logger.warn("src/main/java directory not found in project path: {}", projectPath);
            return null;
        }
        List<Path> javaFiles = projectPathTraverser.findJavaFiles(srcMainJavaPath);
        return javaFiles.stream()
                .filter(javaClassAnalyzer::containsSpringBootApplication)
                .map(javaClassAnalyzer::extractPackage)
                .findFirst()
                .orElse(null);
    }

    public List<String> findTopLevelPackages(Path projectPath, String mainPackage) {
        logger.debug("Finding top-level packages for main package: {} in project path: {}", mainPackage, projectPath);
        Path srcMainJavaPath = projectPath.resolve("src/main/java");
        if (!Files.exists(srcMainJavaPath)) {
            logger.warn("src/main/java directory not found in project path: {}", projectPath);
            return List.of();
        }

        List<Path> javaPackages = projectPathTraverser.findPackages(srcMainJavaPath);

        int targetDepth = mainPackage.split("\\.").length + 1;

        return javaPackages.stream()
                .map(srcMainJavaPath::relativize)
                .map(Path::toString)
                .map(path -> path.replace(File.separator, "."))
                .filter(pkg -> !pkg.isEmpty())
                .filter(pkg -> isTopLevelPackage(pkg, mainPackage, targetDepth))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private boolean isTopLevelPackage(String pkg, String mainPackage, int targetDepth) {
        return pkg.split("\\.").length == targetDepth &&
                pkg.startsWith(mainPackage + ".") &&
                !pkg.equals(mainPackage);
    }
}