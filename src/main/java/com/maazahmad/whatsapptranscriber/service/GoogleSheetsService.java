package com.maazahmad.whatsapptranscriber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    private final ObjectMapper objectMapper;
    private Sheets sheetsService;

    @Value("${google.sheets.id}")
    private String spreadsheetId;

    @Value("${google.credentials.path}")
    private String credentialsPath;

    @PostConstruct
    public void init() throws IOException, GeneralSecurityException {
        // Load the JSON key file from resources
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ClassPathResource(credentialsPath).getInputStream())
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        // Build the Sheets API client
        this.sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("SpendTrace")
                .build();
    }

    @SneakyThrows
    public void logExpense(String jsonExpense) {
        // 1. Parse the Llama-3 JSON
        JsonNode root = objectMapper.readTree(jsonExpense);

        // 2. Extract fields (Handle potential nulls safely)
        String date = root.path("date").asText("N/A");
        String item = root.path("item").asText("Unknown");
        double amount = root.path("amount").asDouble(0.0);
        String currency = root.path("currency").asText("PKR");
        String merchant = root.path("merchant").asText("Unknown");
        String category = root.path("category").asText("Uncategorized");

        // 3. Prepare the Row Data
        // Order: Date | Item | Amount | Currency | Merchant | Category
        List<Object> rowData = List.of(date, item, amount, currency, merchant, category);
        ValueRange body = new ValueRange().setValues(List.of(rowData));

        // 4. Append to the Sheet
        // "Sheet1!A1" tells Google to start looking at A1 and append to the next empty row
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, "Sheet1!A1", body)
                .setValueInputOption("USER_ENTERED") // Allows Google to format numbers/dates automatically
                .execute();

        System.out.println("✅ Expense logged to Google Sheets!");
    }
}