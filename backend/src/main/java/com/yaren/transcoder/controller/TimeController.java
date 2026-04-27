package com.yaren.transcoder.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/time")
public class TimeController {

    @GetMapping
    public ResponseEntity<Map<String, Long>> getServerTime() {
        return ResponseEntity.ok(Map.of("serverTime", System.currentTimeMillis()));
    }
}
