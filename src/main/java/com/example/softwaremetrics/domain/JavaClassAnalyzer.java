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
import java.util.stream.Stream;

/**
 * JavaClassAnalyzer provides utility methods to analyze Java class files for various metrics
 * such as dependencies, package information, and class counts.
 */
@Component
public class JavaClassAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(JavaClassAnalyzer.class);

    /**
     * Checks whether the given file contains the @SpringBootApplication annotation.
     *
     * @param file the Path to the file to be checked
     * @return true if the file contains the @SpringBootApplication annotation, false otherwise
     */
    boolean containsSpringBootApplication(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            logger.debug("Analyzing file {}", file);
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

    void analyzeClasses(Path projectPath, List<String> packages,
                        Map<String, Set<String>> outgoingDependencies,
                        Map<String, Set<String>> incomingDependencies,
                        Map<String, Integer> abstractClassCount,
                        Map<String, Integer> totalClassCount) {
        try (var walk = Files.walk(projectPath)) {
            walk.parallel()
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .forEach(file -> analyzeClassFile(file, packages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount));
        } catch (IOException e) {
            logger.error("Error while analyzing classes for {}", projectPath, e);
            throw new IllegalStateException(e);
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
}
