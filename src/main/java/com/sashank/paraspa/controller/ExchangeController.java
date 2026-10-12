package com.sashank.paraspa.controller;

import com.sashank.paraspa.dto.request.CreateExchangeRequest;
import com.sashank.paraspa.dto.response.ExchangeResponse;
import com.sashank.paraspa.service.ExchangeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/exchanges")
public class ExchangeController {

    @Autowired
    private ExchangeService exchangeService;

    @PostMapping
    public ResponseEntity<ExchangeResponse> createExchange(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody CreateExchangeRequest request) {
        ExchangeResponse response = exchangeService.createExchange(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ExchangeResponse>> getMyExchanges(
            @RequestAttribute("userId") Long userId) {
        List<ExchangeResponse> exchanges = exchangeService.getMyExchanges(userId);
        return ResponseEntity.ok(exchanges);
    }

    @GetMapping("/sent")
    public ResponseEntity<List<ExchangeResponse>> getSentExchanges(
            @RequestAttribute("userId") Long userId) {
        List<ExchangeResponse> exchanges = exchangeService.getSentExchanges(userId);
        return ResponseEntity.ok(exchanges);
    }

    @GetMapping("/received")
    public ResponseEntity<List<ExchangeResponse>> getReceivedExchanges(
            @RequestAttribute("userId") Long userId) {
        List<ExchangeResponse> exchanges = exchangeService.getReceivedExchanges(userId);
        return ResponseEntity.ok(exchanges);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExchangeResponse> getExchangeById(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {
        ExchangeResponse exchange = exchangeService.getExchangeById(userId, id);
        return ResponseEntity.ok(exchange);
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<ExchangeResponse> acceptExchange(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {
        ExchangeResponse response = exchangeService.acceptExchange(userId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ExchangeResponse> rejectExchange(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {
        ExchangeResponse response = exchangeService.rejectExchange(userId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ExchangeResponse> completeExchange(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long id) {
        ExchangeResponse response = exchangeService.completeExchange(userId, id);
        return ResponseEntity.ok(response);
    }
}

