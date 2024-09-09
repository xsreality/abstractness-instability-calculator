package com.example.softwaremetrics.domain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for calculating various metrics for a given set of Java packages within a project.
 * The metrics include instability, abstractness, and distance from the main sequence.
 */
@Component
public class PackageMetricsCalculator {

    private static final Logger logger = LoggerFactory.getLogger(PackageMetricsCalculator.class);

    /**
     * Calculates various metrics for the given list of packages within a project.
     *
     * @param projectPath The path to the root directory of the project.
     * @param packages    A list of package names to analyze.
     * @return A map where the key is the package name and the value is another map
     * containing calculated metrics such as 'Instability', 'Abstractness'
     * and 'Distance'.
     * @
     */
    public Map<String, Map<String, Double>> calculateMetrics(Path projectPath, List<String> packages) throws IOException {
        logger.info("Calculating metrics for {} packages", packages.size());
        Map<String, Set<String>> outgoingDependencies = new ConcurrentHashMap<>();
        Map<String, Set<String>> incomingDependencies = new ConcurrentHashMap<>();
        Map<String, Integer> abstractClassCount = new ConcurrentHashMap<>();
        Map<String, Integer> totalClassCount = new ConcurrentHashMap<>();

        initializeMaps(packages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);

        analyzeClasses(projectPath, packages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);

        logger.debug("Dependency analysis completed. Calculating final metrics.");
        return computeMetrics(packages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);
    }

    private void initializeMaps(List<String> packages, Map<String, Set<String>> outgoingDependencies,
                                Map<String, Set<String>> incomingDependencies,
                                Map<String, Integer> abstractClassCount,
                                Map<String, Integer> totalClassCount) {
        packages.forEach(pkg -> {
            outgoingDependencies.put(pkg, ConcurrentHashMap.newKeySet());
            incomingDependencies.put(pkg, ConcurrentHashMap.newKeySet());
            abstractClassCount.put(pkg, 0);
            totalClassCount.put(pkg, 0);
        });
    }

    private void analyzeClasses(Path projectPath, List<String> packages,
                                Map<String, Set<String>> outgoingDependencies,
                                Map<String, Set<String>> incomingDependencies,
                                Map<String, Integer> abstractClassCount,
                                Map<String, Integer> totalClassCount) throws IOException {
        try (var walk = Files.walk(projectPath)) {
            walk.parallel()
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .forEach(file -> {
                        analyzeClassFile(file, packages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);
                    });
        }
    }

    private void analyzeClassFile(Path file, List<String> packages,
                                  Map<String, Set<String>> outgoingDependencies,
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
            totalClassCount.merge(packageName, 1, Integer::sum);
            if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0 || (classNode.access & Opcodes.ACC_INTERFACE) != 0) {
                abstractClassCount.merge(packageName, 1, Integer::sum);
            }

            for (MethodNode method : classNode.methods) {
                analyzeMethod(method, packageName, packages, outgoingDependencies, incomingDependencies);
            }
        } catch (IOException e) {
            logger.error("Error analyzing class file: {}", file, e);
        }
    }

    private void analyzeMethod(MethodNode method, String packageName, List<String> packages,
                           Map<String, Set<String>> outgoingDependencies,
                           Map<String, Set<String>> incomingDependencies) {
    // Analyze method signature
    Type returnType = Type.getReturnType(method.desc);
    addDependency(packageName, getPackageName(returnType.getClassName()), packages, outgoingDependencies, incomingDependencies);

    // Analyze parameter types
    for (Type paramType : Type.getArgumentTypes(method.desc)) {
        addDependency(packageName, getPackageName(paramType.getClassName()), packages, outgoingDependencies, incomingDependencies);
    }

    // Analyze exceptions
    method.exceptions.forEach(exception -> {
        String exceptionName = Type.getObjectType(exception).getClassName();
        String exceptionPackage = getPackageName(exceptionName);
        addDependency(packageName, exceptionPackage, packages, outgoingDependencies, incomingDependencies);
    });

    // Analyze method body
    method.instructions.forEach(instruction -> {
        if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode methodInsn) {
            String methodOwner = Type.getObjectType(methodInsn.owner).getClassName();
            String methodPackage = getPackageName(methodOwner);
            addDependency(packageName, methodPackage, packages, outgoingDependencies, incomingDependencies);
        } else if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode fieldInsn) {
            String fieldOwner = Type.getObjectType(fieldInsn.owner).getClassName();
            String fieldPackage = getPackageName(fieldOwner);
            addDependency(packageName, fieldPackage, packages, outgoingDependencies, incomingDependencies);
        } else if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode typeInsn) {
            String typeName = Type.getObjectType(typeInsn.desc).getClassName();
            String typePackage = getPackageName(typeName);
            addDependency(packageName, typePackage, packages, outgoingDependencies, incomingDependencies);
        }
    });

    // Analyze local variables
    if (method.localVariables != null) {
        for (org.objectweb.asm.tree.LocalVariableNode localVar : method.localVariables) {
            String localVarType = Type.getType(localVar.desc).getClassName();
            addDependency(packageName, getPackageName(localVarType), packages, outgoingDependencies, incomingDependencies);
        }
    }
}

    private void addDependency(String fromPackage, String toPackage, List<String> packages,
                               Map<String, Set<String>> outgoingDependencies,
                               Map<String, Set<String>> incomingDependencies) {
        if (packages.contains(toPackage) && !fromPackage.equals(toPackage)) {
            outgoingDependencies.computeIfAbsent(fromPackage, _ -> ConcurrentHashMap.newKeySet()).add(toPackage);
            incomingDependencies.computeIfAbsent(toPackage, _ -> ConcurrentHashMap.newKeySet()).add(fromPackage);
        }
    }

    private String getPackageName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : className.substring(0, lastDotIndex);
    }

    private Map<String, Map<String, Double>> computeMetrics(List<String> packages,
                                                            Map<String, Set<String>> outgoingDependencies,
                                                            Map<String, Set<String>> incomingDependencies,
                                                            Map<String, Integer> abstractClassCount,
                                                            Map<String, Integer> totalClassCount) {
        Map<String, Map<String, Double>> metrics = new ConcurrentHashMap<>();
        for (String pkg : packages) {
            int ce = outgoingDependencies.getOrDefault(pkg, Set.of()).size();
            int ca = incomingDependencies.getOrDefault(pkg, Set.of()).size();
            double instability = (ce + ca == 0) ? 0.0 : (double) ce / (ce + ca);

            int abstractClasses = abstractClassCount.getOrDefault(pkg, 0);
            int totalClasses = totalClassCount.getOrDefault(pkg, 0);
            double abstractness = (totalClasses == 0) ? 0.0 : (double) abstractClasses / totalClasses;

            double distance = Math.abs(abstractness + instability - 1.0);

            Map<String, Double> packageMetrics = new ConcurrentHashMap<>();
            packageMetrics.put("Instability", Math.round(instability * 10000.0) / 10000.0);
            packageMetrics.put("Abstractness", Math.round(abstractness * 10000.0) / 10000.0);
            packageMetrics.put("Distance", Math.round(distance * 10000.0) / 10000.0);
            metrics.put(pkg, packageMetrics);

            logger.debug("Metrics for package {}: I={}, A={}, D={}, CE={}, CA={}",
                    pkg, packageMetrics.get("Instability"),
                    packageMetrics.get("Abstractness"),
                    packageMetrics.get("Distance"),
                    ce, ca);
        }
        return metrics;
    }
}