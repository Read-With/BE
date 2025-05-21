package com.kw.readwith.web.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class healthController {

    @GetMapping("/health")
    public String checkHealth () {
        return "I'm healthy!";
    }

}
