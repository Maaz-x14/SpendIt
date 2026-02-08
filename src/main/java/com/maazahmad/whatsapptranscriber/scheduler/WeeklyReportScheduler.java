package com.maazahmad.whatsapptranscriber.scheduler;

import com.maazahmad.whatsapptranscriber.model.User;
import com.maazahmad.whatsapptranscriber.repository.UserRepository;
import com.maazahmad.whatsapptranscriber.service.GoogleSheetsService;
import com.maazahmad.whatsapptranscriber.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final UserRepository userRepository;
    private final GoogleSheetsService sheetsService;
    private final WhatsAppService whatsAppService;

    @Scheduled(cron = "0 0 21 * * SUN") // Every Sunday at 9 PM
    public void sendWeeklySummaries() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            String summary = sheetsService.calculateAnalytics(null, null, null, "7_DAYS_AGO", "TODAY", user.getSpreadsheetId());
            whatsAppService.sendReply(user.getPhoneNumber(), "📈 *Your Weekly CFO Wrap-up*\n\n" + summary);
        }
    }
}
