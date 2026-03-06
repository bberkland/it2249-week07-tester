package edu.instructor.tester.controller;

import edu.instructor.tester.model.TestResult;
import edu.instructor.tester.service.EvaluationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
public class TesterController {

    private final EvaluationService evaluationService;

    public TesterController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** Landing page — the upload form. */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /** Receive the uploaded file and run the evaluation. */
    @PostMapping("/evaluate")
    public String evaluate(
            @RequestParam("file")      MultipartFile file,
            @RequestParam("inputSize") int inputSize,
            Model model,
            RedirectAttributes redirectAttributes) {

        // ── basic validation ──────────────────────────────────────────────────
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please choose a .java file to upload.");
            return "redirect:/";
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.endsWith(".java")) {
            redirectAttributes.addFlashAttribute("error",
                "Only .java files are accepted. Please upload the student's source file.");
            return "redirect:/";
        }

        if (inputSize < 1 || inputSize > 1000) {
            redirectAttributes.addFlashAttribute("error",
                "Input size must be between 1 and 1000.");
            return "redirect:/";
        }

        // ── read source ───────────────────────────────────────────────────────
        String sourceContent;
        try {
            sourceContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error",
                "Could not read the uploaded file: " + e.getMessage());
            return "redirect:/";
        }

        // ── run pipeline ───────────────────────────────────────────────────────
        TestResult result = evaluationService.evaluate(sourceContent, fileName, inputSize);

        model.addAttribute("result", result);
        model.addAttribute("source", sourceContent);   // for the source view panel
        return "results";
    }
}
