package com.example.softwaremetrics.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
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
     * @param modulePackages    A list of package names to analyze.
     * @return A map where the key is the package name and the value is another map
     * containing calculated metrics such as 'Instability', 'Abstractness'
     * and 'Distance'.
     * @
     */
    public Map<String, PackageMetrics> calculateMetrics(Path projectPath, List<String> modulePackages) {
        logger.info("Calculating metrics for {} packages", modulePackages.size());
        Map<String, Set<String>> outgoingDependencies = new ConcurrentHashMap<>();
        Map<String, Set<String>> incomingDependencies = new ConcurrentHashMap<>();
        Map<String, Integer> abstractClassCount = new ConcurrentHashMap<>();
        Map<String, Integer> totalClassCount = new ConcurrentHashMap<>();

        initializeMaps(modulePackages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);

        javaClassAnalyzer.analyzeClasses(projectPath, modulePackages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);

        logger.debug("Dependency analysis completed. Calculating final metrics.");
        return computeMetrics(modulePackages, outgoingDependencies, incomingDependencies, abstractClassCount, totalClassCount);
    }

    private void initializeMaps(List<String> modulePackages, Map<String, Set<String>> outgoingDependencies,
                                Map<String, Set<String>> incomingDependencies,
                                Map<String, Integer> abstractClassCount,
                                Map<String, Integer> totalClassCount) {
        modulePackages.forEach(pkg -> {
            outgoingDependencies.put(pkg, ConcurrentHashMap.newKeySet());
            incomingDependencies.put(pkg, ConcurrentHashMap.newKeySet());
            abstractClassCount.put(pkg, 0);
            totalClassCount.put(pkg, 0);
        });
    }

    private Map<String, PackageMetrics> computeMetrics(List<String> modulePackages,
                                                       Map<String, Set<String>> outgoingDependencies,
                                                       Map<String, Set<String>> incomingDependencies,
                                                       Map<String, Integer> abstractClassCount,
                                                       Map<String, Integer> totalClassCount) {
        Map<String, PackageMetrics> metrics = new ConcurrentHashMap<>();
        for (String pkg : modulePackages) {
            int ce = outgoingDependencies.getOrDefault(pkg, Set.of()).size();
            int ca = incomingDependencies.getOrDefault(pkg, Set.of()).size();
            double instability = (ce + ca == 0) ? 0.0 : (double) ce / (ce + ca);

            int abstractClasses = abstractClassCount.getOrDefault(pkg, 0);
            int totalClasses = totalClassCount.getOrDefault(pkg, 0);
            double abstractness = (totalClasses == 0) ? 0.0 : (double) abstractClasses / totalClasses;

            double distance = Math.abs(abstractness + instability - 1.0);

            PackageMetrics pkgMetrics = new PackageMetrics();
            pkgMetrics.setPackageName(pkg);
            pkgMetrics.setCe(ce);
            pkgMetrics.setEfferentDependencies(new ArrayList<>(outgoingDependencies.getOrDefault(pkg, Set.of())));
            pkgMetrics.setCa(ca);
            pkgMetrics.setAfferentDependencies(new ArrayList<>(incomingDependencies.getOrDefault(pkg, Set.of())));
            pkgMetrics.setAbstractClassCount(abstractClasses);
            pkgMetrics.setTotalClassCount(totalClasses);
            pkgMetrics.setAbstractness(abstractness);
            pkgMetrics.setInstability(instability);
            pkgMetrics.setDistance(distance);

            metrics.put(pkg, pkgMetrics);

            logger.debug("Metrics for package {}: I={}, A={}, D={}, CE={}, CA={}",
                    pkg, instability, abstractness, distance, ce, ca);
        }

        return metrics;
    }
}