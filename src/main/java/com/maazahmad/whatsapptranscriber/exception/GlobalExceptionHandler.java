package com.maazahmad.whatsapptranscriber.exception;

import com.maazahmad.whatsapptranscriber.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final WhatsAppService whatsAppService;

    @ExceptionHandler(Exception.class)
    public void handleAllExceptions(Exception ex) {
        // High-vis logging for your journalctl
        System.err.println("🚨 CRITICAL SYSTEM ERROR: " + ex.getMessage());
        ex.printStackTrace();
    }
}
