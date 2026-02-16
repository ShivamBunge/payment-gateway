package com.payment.gateway.TransactionPlatform.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {
    @RequestMapping("/")
    public String greet(){
        return "Hello to my world";
    }
}
