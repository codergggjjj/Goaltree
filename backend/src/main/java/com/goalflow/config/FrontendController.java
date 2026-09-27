package com.goalflow.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping({"/", "/goals", "/goals/{goalId}", "/settings"})
    public String index() {
        return "forward:/index.html";
    }
}
