package com.example.softwaremetrics.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
        List<Path> javaFiles = projectPathTraverser.findJavaFiles(projectPath);
        return javaFiles.stream()
                .filter(javaClassAnalyzer::containsSpringBootApplication)
                .map(javaClassAnalyzer::extractPackage)
                .findFirst()
                .orElse(null);
    }

    public List<String> findTopLevelPackages(Path projectPath, String mainPackage) {
        logger.debug("Finding top-level packages for main package: {} in project path: {}", mainPackage, projectPath);
        List<Path> javaFiles = projectPathTraverser.findJavaFiles(projectPath);

        int targetDepth = mainPackage.split("\\.").length + 1;

        return javaFiles.stream()
                .map(Path::getParent)
                .map(Path::toString)
                .filter(path -> path.contains("src/main/java"))
                .map(this::extractPackagePath)
                .filter(path -> !path.isEmpty())
                .map(path -> path.replace('/', '.').trim())
                .filter(pkg -> isTopLevelPackage(pkg, mainPackage, targetDepth))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
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
}