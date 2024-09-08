package com.example.controller;

import com.example.SpringBootPackageScanner;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.Map;

@Controller
public class PackageScannerController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/scan")
    public String scan(@RequestParam String path, Model model) {
        try {
            Map<String, Map<String, Double>> metrics = SpringBootPackageScanner.scanProject(path);
            model.addAttribute("metrics", metrics);
            return "graph :: graph";
        } catch (IOException e) {
            model.addAttribute("error", "Error scanning project: " + e.getMessage());
            return "graph :: error";
        }
    }
}