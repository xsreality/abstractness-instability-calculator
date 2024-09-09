package com.example.softwaremetrics.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
    private final JavaClassAnalyzer javaClassAnalyzer;

    public PackageMetricsCalculator(JavaClassAnalyzer javaClassAnalyzer) {
        this.javaClassAnalyzer = javaClassAnalyzer;
    }

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
    public Map<String, Map<String, Double>> calculateMetrics(Path projectPath, List<String> packages) {
        logger.info("Calculating metrics for {} packages", packages.size());
        Map<String, Set<String>> outgoingDependencies = new ConcurrentHashMap<>();
        Map<String, Set<String>> incomingDependencies = new ConcurrentHashMap<>();
        Map<String, Integer> abstractClassCount = new ConcurrentHashMap<>();
        Map<String, Integer> totalClassCount = new ConcurrentHashMap<>();

        initializeMaps(packages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);

        javaClassAnalyzer.analyzeClasses(projectPath, packages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);

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