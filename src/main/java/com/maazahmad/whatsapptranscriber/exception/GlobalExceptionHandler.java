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
        System.err.println("CATCHED ERROR: " + ex.getMessage());
        // We can't easily get the 'from' number here without more context, 
        // so we mainly rely on try-catches in the Async methods, 
        // but this stops the server from crashing or leaking stack traces.
    }
}
