package com.alibaba.assistant.agent.start.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DemoHomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/agent-console/index.html";
    }

    @GetMapping("/agent-console")
    public String agentConsole() {
        return "redirect:/agent-console/index.html";
    }

    @GetMapping("/agent-console/")
    public String agentConsoleSlash() {
        return "redirect:/agent-console/index.html";
    }
}
