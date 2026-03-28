package com.tobyshorturl.web.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginViewController {

    @GetMapping("/app/login")
    public String login() {
        return "login";
    }
}
