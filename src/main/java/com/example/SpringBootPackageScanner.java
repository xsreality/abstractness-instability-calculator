package com.example;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A utility class for scanning Spring Boot projects to identify packages and calculate instability metrics.
 */
public class SpringBootPackageScanner {
    private static final Logger logger = LoggerFactory.getLogger(SpringBootPackageScanner.class);

    /**
     * Scans the given Spring Boot project to find the main package and its top-level subpackages,
     * and calculates various metrics for these packages.
     *
     * @param projectPath The path to the Spring Boot project.
     * @return A map containing the calculated metrics for each top-level package.
     * @throws IOException If an I/O error occurs during the scan.
     */
    public static Map<String, Map<String, Double>> scanProject(String projectPath) throws IOException {
        logger.info("Starting project scan for path: {}", projectPath);
        Path path = Paths.get(projectPath);
        PackageFinder packageFinder = new PackageFinder(path);
        String mainPackage = packageFinder.findMainPackage();
        if (mainPackage == null || mainPackage.isEmpty()) {
            logger.error("No @SpringBootApplication found in the project.");
            throw new IOException("No @SpringBootApplication found in the project.");
        }
        logger.debug("Main package found: {}", mainPackage);

        List<String> topLevelPackages = packageFinder.findTopLevelPackages(mainPackage);
        if (topLevelPackages.isEmpty()) {
            logger.error("No subpackages found.");
            throw new IOException("No subpackages found.");
        }
        logger.debug("Top-level packages found: {}", topLevelPackages);

        InstabilityCalculator calculator = new InstabilityCalculator(path, topLevelPackages);
        Map<String, Map<String, Double>> metrics = calculator.calculateMetrics();
        logger.info("Project scan completed successfully.");
        return metrics;
    }

    /**
     * A utility class for finding packages in a Spring Boot project.
     */
    static class PackageFinder {
        private static final Logger logger = LoggerFactory.getLogger(PackageFinder.class);
        private final Path projectPath;

        /**
         * Constructs a PackageFinder with the given project path.
         *
         * @param projectPath The path to the Spring Boot project.
         */
        PackageFinder(Path projectPath) {
            this.projectPath = projectPath;
        }

        /**
         * Finds the main package containing the @SpringBootApplication annotation.
         *
         * @return The main package name, or null if not found.
         * @throws IOException If an I/O error occurs.
         */
        public String findMainPackage() throws IOException {
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
         * Finds all top-level packages that are direct subpackages of the main package.
         *
         * @param mainPackage The main package name.
         * @return A list of top-level package names.
         * @throws IOException If an I/O error occurs.
         */
        List<String> findTopLevelPackages(String mainPackage) throws IOException {
            logger.debug("Finding top-level packages for main package: {}", mainPackage);
            int targetDepth = mainPackage.split("\\.").length + 1;

            try (var walk = Files.walk(projectPath)) {
                return walk
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
            }
        }

        /**
         * Checks if a file contains the @SpringBootApplication annotation.
         *
         * @param file The file to check.
         * @return true if the file contains @SpringBootApplication, false otherwise.
         */
        private boolean containsSpringBootApplication(Path file) {
            try {
                List<String> lines = Files.readAllLines(file);
                boolean hasAnnotation = false;
                boolean isPublicClass = false;
                for (String line : lines) {
                    if (line.trim().startsWith("@SpringBootApplication")) {
                        hasAnnotation = true;
                    } else if (line.contains("public class") && line.contains("{")) {
                        isPublicClass = true;
                        break;
                    }
                }
                return hasAnnotation && isPublicClass;
            } catch (IOException e) {
                logger.error("Error reading file: {}", file, e);
                return false;
            }
        }

        /**
         * Extracts the package name from a Java file.
         *
         * @param file The Java file.
         * @return The package name.
         */
        private String extractPackage(Path file) {
            try {
                List<String> lines = Files.readAllLines(file);
                return lines.stream()
                        .filter(line -> line.startsWith("package "))
                        .map(line -> line.replace("package ", "").replace(";", "").trim())
                        .findFirst()
                        .orElse("");
            } catch (IOException e) {
                logger.error("Error extracting package from file: {}", file, e);
                return "";
            }
        }

        /**
         * Extracts the package path from a full file path.
         *
         * @param path The full file path.
         * @return The package path.
         */
        private String extractPackagePath(String path) {
            int index = path.indexOf("src/main/java");
            if (index == -1 || index + 14 >= path.length()) {
                return "";
            }
            return path.substring(index + 14);
        }

        /**
         * Checks if a package is a top-level package.
         *
         * @param pkg         The package to check.
         * @param mainPackage The main package.
         * @param targetDepth The target depth for top-level packages.
         * @return true if the package is a top-level package, false otherwise.
         */
        private boolean isTopLevelPackage(String pkg, String mainPackage, int targetDepth) {
            String[] pkgParts = pkg.split("\\.");
            return pkgParts.length == targetDepth &&
                    pkg.startsWith(mainPackage + ".") &&
                    !pkg.equals(mainPackage);
        }
    }

    /**
     * A utility class for calculating instability and abstractness metrics of packages.
     */
    static class InstabilityCalculator {
        private static final Logger logger = LoggerFactory.getLogger(InstabilityCalculator.class);
        private final Path projectPath;
        private final List<String> packages;

        /**
         * Constructs an InstabilityCalculator with the given project path and packages.
         *
         * @param projectPath The path to the Spring Boot project.
         * @param packages    The list of packages to analyze.
         */
        InstabilityCalculator(Path projectPath, List<String> packages) {
            this.projectPath = projectPath;
            this.packages = packages;
        }

        /**
         * Calculates the instability, abstractness, and distance from main sequence metrics for all packages.
         *
         * @return A map of package names to their metrics (instability, abstractness, and distance).
         * @throws IOException If an I/O error occurs.
         */
        Map<String, Map<String, Double>> calculateMetrics() throws IOException {
            logger.info("Calculating metrics for {} packages", packages.size());
            Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();
            Map<String, Set<String>> incomingDependencies = new ConcurrentHashMap<>();
            Map<String, Integer> abstractClassCount = new ConcurrentHashMap<>();
            Map<String, Integer> totalClassCount = new ConcurrentHashMap<>();

            packages.forEach(pkg -> {
                dependencies.put(pkg, ConcurrentHashMap.newKeySet());
                incomingDependencies.put(pkg, ConcurrentHashMap.newKeySet());
                abstractClassCount.put(pkg, 0);
                totalClassCount.put(pkg, 0);
            });

            try (var walk = Files.walk(projectPath)) {
                walk.parallel()
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".class"))
                        .forEach(file -> {
                            analyzeClassFile(file, dependencies, incomingDependencies, abstractClassCount, totalClassCount);
                        });
            }

            logger.debug("Dependency analysis completed. Calculating final metrics.");
            return computeMetrics(dependencies, incomingDependencies, abstractClassCount, totalClassCount);
        }

        private void analyzeClassFile(Path file, Map<String, Set<String>> dependencies,
                                      Map<String, Set<String>> incomingDependencies,
                                      Map<String, Integer> abstractClassCount,
                                      Map<String, Integer> totalClassCount) {
            try {
                ClassReader classReader = new ClassReader(Files.newInputStream(file));
                ClassNode classNode = new ClassNode();
                classReader.accept(classNode, 0);

                String className = Type.getObjectType(classNode.name).getClassName();
                String packageName = getPackageName(className);

                if (!packages.contains(packageName)) return;

                logger.trace("Analyzing class: {}", className);
                totalClassCount.put(packageName, totalClassCount.get(packageName) + 1);
                if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0 || (classNode.access & Opcodes.ACC_INTERFACE) != 0) {
                    abstractClassCount.put(packageName, abstractClassCount.get(packageName) + 1);
                }

                for (MethodNode method : classNode.methods) {
                    analyzeMethod(method, packageName, dependencies, incomingDependencies);
                }
            } catch (IOException e) {
                logger.error("Error analyzing class file: {}", file, e);
            }
        }

        private void analyzeMethod(MethodNode method, String packageName,
                                   Map<String, Set<String>> dependencies,
                                   Map<String, Set<String>> incomingDependencies) {
            method.exceptions.forEach(exception -> {
                String exceptionName = Type.getObjectType((String) exception).getClassName();
                String exceptionPackage = getPackageName(exceptionName);
                if (packages.contains(exceptionPackage) && !exceptionPackage.equals(packageName)) {
                    dependencies.get(packageName).add(exceptionPackage);
                    incomingDependencies.get(exceptionPackage).add(packageName);
                    logger.trace("Dependency found: {} -> {} (exception)", packageName, exceptionPackage);
                }
            });

            method.instructions.forEach(instruction -> {
                if (instruction.getOpcode() == Opcodes.INVOKEVIRTUAL ||
                        instruction.getOpcode() == Opcodes.INVOKESTATIC ||
                        instruction.getOpcode() == Opcodes.INVOKEINTERFACE ||
                        instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
                    String methodOwner = ((org.objectweb.asm.tree.MethodInsnNode) instruction).owner;
                    String methodPackage = getPackageName(Type.getObjectType(methodOwner).getClassName());
                    if (packages.contains(methodPackage) && !methodPackage.equals(packageName)) {
                        dependencies.get(packageName).add(methodPackage);
                        incomingDependencies.get(methodPackage).add(packageName);
                        logger.trace("Dependency found: {} -> {} (method call)", packageName, methodPackage);
                    }
                }
            });
        }

        private String getPackageName(String className) {
            int lastDotIndex = className.lastIndexOf('.');
            return (lastDotIndex == -1) ? "" : className.substring(0, lastDotIndex);
        }

        /**
         * Computes the instability, abstractness, and distance from main sequence metrics for all packages.
         *
         * @param dependencies         The map of outgoing dependencies.
         * @param incomingDependencies The map of incoming dependencies.
         * @param abstractClassCount   The map of abstract class counts.
         * @param totalClassCount      The map of total class counts.
         * @return A map of package names to their metrics (instability, abstractness, and distance).
         */
        private Map<String, Map<String, Double>> computeMetrics(
                Map<String, Set<String>> dependencies,
                Map<String, Set<String>> incomingDependencies,
                Map<String, Integer> abstractClassCount,
                Map<String, Integer> totalClassCount) {
            Map<String, Map<String, Double>> metrics = new ConcurrentHashMap<>();
            for (String pkg : packages) {
                int ce = dependencies.get(pkg).size();
                int ca = incomingDependencies.get(pkg).size();
                double instability = (ce + ca == 0) ? 0.0 : (double) ce / (ce + ca);

                int abstractClasses = abstractClassCount.get(pkg);
                int totalClasses = totalClassCount.get(pkg);
                double abstractness = (totalClasses == 0) ? 0.0 : (double) abstractClasses / totalClasses;

                double distance = Math.abs(abstractness + instability - 1.0);

                Map<String, Double> packageMetrics = new ConcurrentHashMap<>();
                packageMetrics.put("Instability", Math.round(instability * 10000.0) / 10000.0);
                packageMetrics.put("Abstractness", Math.round(abstractness * 10000.0) / 10000.0);
                packageMetrics.put("Distance", Math.round(distance * 10000.0) / 10000.0);
                metrics.put(pkg, packageMetrics);

                logger.debug("Metrics for package {}: I={}, A={}, D={}",
                        pkg, packageMetrics.get("Instability"),
                        packageMetrics.get("Abstractness"),
                        packageMetrics.get("Distance"));
            }
            return metrics;
        }
    }
}
