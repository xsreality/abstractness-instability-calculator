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
import java.util.stream.Stream;
import java.util.HashSet;
import java.util.Arrays;

/**
 * JavaClassAnalyzer provides utility methods to analyze Java class files for various metrics
 * such as dependencies, package information, and class counts.
 */
@Component
public class JavaClassAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(JavaClassAnalyzer.class);

    private static final List<String> JAVA_NATIVE_PACKAGES = Arrays.asList(
        "java.", "javax.", "sun.", "com.sun.", "org.w3c.", "org.xml."
    );

    private static final Set<String> BASIC_TYPES = new HashSet<>(Arrays.asList(
        "boolean", "byte", "char", "short", "int", "long", "float", "double", "void"
    ));

    /**
     * Checks whether the given file contains the @SpringBootApplication annotation.
     *
     * @param file the Path to the file to be checked
     * @return true if the file contains the @SpringBootApplication annotation, false otherwise
     */
    boolean containsSpringBootApplication(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.anyMatch(line -> line.contains("@SpringBootApplication"));
        } catch (IOException e) {
            logger.error("Error reading file: {}", file, e);
            return false;
        }
    }

    String extractPackage(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines
                    .filter(line -> line.startsWith("package"))
                    .map(line -> line.split("\\s+")[1].replace(";", ""))
                    .findFirst()
                    .orElse("");
        } catch (IOException e) {
            logger.error("Error extracting package from file: {}", file, e);
            return "";
        }
    }

    void analyzeClasses(Path projectPath, List<String> modulePackages,
                        Map<String, Set<String>> outgoingDependencies,
                        Map<String, Set<String>> incomingDependencies,
                        Map<String, Integer> abstractClassCount,
                        Map<String, Integer> totalClassCount) {
        try (var walk = Files.walk(projectPath)) {
            walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .filter(this::isNotTestClass)
                    .forEach(file -> analyzeClassFile(file, modulePackages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount));
        } catch (IOException e) {
            logger.error("Error while analyzing classes for {}", projectPath, e);
            throw new IllegalStateException(e);
        }
    }

    private boolean isNotTestClass(Path path) {
        return !path.toString().contains("target/test-classes");
    }

    private void analyzeClassFile(Path file, List<String> modulePackages,
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
            String topLevelPackage = extractTopLevelPackageFrom(packageName, modulePackages);

            if (topLevelPackage == null) return;

            logger.trace("Analyzing class: {}", className);
            totalClassCount.merge(topLevelPackage, 1, Integer::sum);
            if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0 || (classNode.access & Opcodes.ACC_INTERFACE) != 0) {
                abstractClassCount.merge(topLevelPackage, 1, Integer::sum);
            }

            Set<String> dependencies = new HashSet<>();
            for (MethodNode method : classNode.methods) {
                analyzeDependencies(method, dependencies);
            }

            for (String dependency : dependencies) {
                String dependencyPackage = getPackageName(dependency);
                String dependencyTopLevelPackage = extractTopLevelPackageFrom(dependencyPackage, modulePackages);
                if (!topLevelPackage.equals(dependencyTopLevelPackage) && !isExcludedDependency(dependency)) {
                    outgoingDependencies.computeIfAbsent(topLevelPackage, _ -> new HashSet<>()).add(dependency);
                    if (dependencyTopLevelPackage != null) {
                        incomingDependencies.computeIfAbsent(dependencyTopLevelPackage, _ -> new HashSet<>()).add(className);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error analyzing class file: {}", file, e);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isExcludedDependency(String dependency) {
        return isJavaNativePackage(dependency) || isBasicType(dependency);
    }

    private boolean isJavaNativePackage(String packageName) {
        return JAVA_NATIVE_PACKAGES.stream().anyMatch(packageName::startsWith);
    }

    private boolean isBasicType(String typeName) {
        return BASIC_TYPES.contains(typeName) || BASIC_TYPES.contains(getPackageName(typeName));
    }

    private void analyzeDependencies(MethodNode method, Set<String> dependencies) {
        // Analyze method signature
        Type returnType = Type.getReturnType(method.desc);
        addDependencyIfNotExcluded(dependencies, returnType.getClassName());

        // Analyze parameter types
        for (Type paramType : Type.getArgumentTypes(method.desc)) {
            addDependencyIfNotExcluded(dependencies, paramType.getClassName());
        }

        // Analyze exceptions
        method.exceptions.forEach(exception -> {
            String exceptionName = Type.getObjectType(exception).getClassName();
            addDependencyIfNotExcluded(dependencies, exceptionName);
        });

        // Analyze method body
        method.instructions.forEach(instruction -> {
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode methodInsn) {
                String methodOwner = Type.getObjectType(methodInsn.owner).getClassName();
                addDependencyIfNotExcluded(dependencies, methodOwner);
            } else if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode fieldInsn) {
                String fieldOwner = Type.getObjectType(fieldInsn.owner).getClassName();
                addDependencyIfNotExcluded(dependencies, fieldOwner);
            } else if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode typeInsn) {
                String typeName = Type.getObjectType(typeInsn.desc).getClassName();
                addDependencyIfNotExcluded(dependencies, typeName);
            }
        });

        // Analyze local variables
        if (method.localVariables != null) {
            for (org.objectweb.asm.tree.LocalVariableNode localVar : method.localVariables) {
                String localVarType = Type.getType(localVar.desc).getClassName();
                addDependencyIfNotExcluded(dependencies, localVarType);
            }
        }
    }

    private void addDependencyIfNotExcluded(Set<String> dependencies, String dependency) {
        if (!isExcludedDependency(dependency)) {
            dependencies.add(dependency);
        }
    }

    private String extractTopLevelPackageFrom(String packageName, List<String> packages) {
        return packages.stream()
                .filter(packageName::startsWith)
                .findFirst()
                .orElse(null);
    }

    private String getPackageName(String className) {
        int lastDotIndex = className.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : className.substring(0, lastDotIndex);
    }
}
