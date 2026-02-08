package com.maazahmad.whatsapptranscriber.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    private final ObjectMapper objectMapper;
    private Sheets sheetsService;

    @Value("${google.credentials.path}")
    private String credentialsPath;

    private static final String DEFAULT_SHEET = "Sheet1";

    @PostConstruct
    public void init() throws IOException, GeneralSecurityException {
        try (InputStream in = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

            this.sheetsService = new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("SpendTrace")
                    .build();
        }
    }

    public void setupHeaders(String spreadsheetId) {
        try {
            List<Object> headers = List.of("Date", "Item", "Amount", "Currency", "Merchant", "Category");
            ValueRange body = new ValueRange().setValues(List.of(headers));
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, "Sheet1!A1", body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();

            createAnalyticsSheet(spreadsheetId);
        } catch (Exception e) {
            System.err.println("Failed to set headers: " + e.getMessage());
        }
    }

    private void createAnalyticsSheet(String spreadsheetId) throws IOException {
        com.google.api.services.sheets.v4.model.AddSheetRequest addSheetRequest = new com.google.api.services.sheets.v4.model.AddSheetRequest()
                .setProperties(new com.google.api.services.sheets.v4.model.SheetProperties().setTitle("Analytics"));
    
        com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest batchRequest = new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                .setRequests(List.of(new com.google.api.services.sheets.v4.model.Request().setAddSheet(addSheetRequest)));
    
        try {
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
        } catch (Exception e) { /* Ignore if exists */ }

        List<List<Object>> analyticsData = List.of(
            List.of("Category Summary", "Total Spend"),
            List.of("=QUERY(Sheet1!A:F, \"select F, sum(C) where F is not null group by F label sum(C) ''\", 1)")
        );

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "Analytics!A1", new ValueRange().setValues(analyticsData))
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    @SneakyThrows
    public void logExpense(String jsonExpense, String spreadsheetId) {
        JsonNode root = objectMapper.readTree(jsonExpense);
        List<Object> rowData = List.of(
            root.path("date").asText(LocalDate.now().toString()),
            root.path("item").asText("Unknown"),
            root.path("amount").asDouble(0.0),
            root.path("currency").asText("PKR"),
            root.path("merchant").asText("Unknown"),
            root.path("category").asText("Uncategorized")
        );

        sheetsService.spreadsheets().values()
                .append(spreadsheetId, DEFAULT_SHEET + "!A1", new ValueRange().setValues(List.of(rowData)))
                .setValueInputOption("USER_ENTERED")
                .execute();
        System.out.println("✅ Expense logged to " + spreadsheetId);
    }

    @SneakyThrows
    public List<List<Object>> readAllRows(String spreadsheetId) {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, DEFAULT_SHEET + "!A:F")
                .execute();
        return response.getValues();
    }

    public String calculateAnalytics(String category, String merchant, String item, String startStr, String endStr, String spreadsheetId) {
        List<List<Object>> rows = readAllRows(spreadsheetId);
        if (rows == null || rows.isEmpty() || rows.size() < 2) return "⚠️ Your ledger is currently empty.";

        double total = 0.0;
        int matchCount = 0;
        String currency = "PKR";

        // Fix: Handle relative date strings
        LocalDate startDate = "7_DAYS_AGO".equalsIgnoreCase(startStr) ? LocalDate.now().minusDays(7) : parseDateSafely(startStr);
        LocalDate endDate = "TODAY".equalsIgnoreCase(endStr) ? LocalDate.now() : parseDateSafely(endStr);

        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row.size() < 4) continue;
            try {
                LocalDate rowDate = LocalDate.parse(row.get(0).toString());
                String rowItem = row.get(1).toString().toLowerCase();
                double rowAmount = Double.parseDouble(row.get(2).toString());
                String rowCategory = row.size() > 5 ? row.get(5).toString().toLowerCase() : "";

                if (startDate != null && rowDate.isBefore(startDate)) continue;
                if (endDate != null && rowDate.isAfter(endDate)) continue;
                if (isFilterActive(category) && !rowCategory.contains(category.toLowerCase())) continue;
                if (isFilterActive(item) && !rowItem.contains(item.toLowerCase())) continue;

                total += rowAmount;
                matchCount++;
            } catch (Exception e) {}
        }
        return matchCount == 0 ? "🔍 No records match." : String.format("📊 *Spending Report*\nTotal: *%.2f %s*\nTransactions: %d", total, currency, matchCount);
    }

    private LocalDate parseDateSafely(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || "null".equalsIgnoreCase(dateStr)) return null;
        try { return LocalDate.parse(dateStr); } catch (Exception e) { return null; }
    }

    private boolean isFilterActive(String filter) {
        return filter != null && !filter.isEmpty() && !"null".equalsIgnoreCase(filter);
    }

    @SneakyThrows
    public String editExpense(String targetItem, String targetDateStr, double newAmount, String newCurrency, String spreadsheetId) {
        List<List<Object>> rows = readAllRows(spreadsheetId);
        if (rows == null || rows.isEmpty()) return "⚠️ Ledger is empty.";
        String searchItem = targetItem.toLowerCase();
        boolean matchAnyDate = "LAST_MATCH".equalsIgnoreCase(targetDateStr);
        LocalDate searchDate = matchAnyDate ? null : LocalDate.parse(targetDateStr);

        for (int i = rows.size() - 1; i >= 0; i--) {
            List<Object> row = rows.get(i);
            if (row.size() < 4) continue;
            try {
                if (row.get(1).toString().toLowerCase().contains(searchItem) && 
                   (matchAnyDate || LocalDate.parse(row.get(0).toString()).isEqual(searchDate))) {
                    String range = DEFAULT_SHEET + "!C" + (i + 1) + ":D" + (i + 1);
                    sheetsService.spreadsheets().values().update(spreadsheetId, range, new ValueRange().setValues(List.of(List.of(newAmount, newCurrency))))
                            .setValueInputOption("USER_ENTERED").execute();
                    return String.format("✅ Updated **%s** to **%.2f %s**.", targetItem, newAmount, newCurrency);
                }
            } catch (Exception e) {}
        }
        return "❌ Expense not found.";
    }

    @SneakyThrows
    public String undoLastLog(String spreadsheetId) {
        List<List<Object>> rows = readAllRows(spreadsheetId);
        if (rows == null || rows.isEmpty()) return "⚠️ Nothing to undo.";
        sheetsService.spreadsheets().values().clear(spreadsheetId, DEFAULT_SHEET + "!A" + rows.size() + ":F" + rows.size(), new ClearValuesRequest()).execute();
        return "✅ Last entry deleted.";
    }
}
