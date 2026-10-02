package com.doubleCalendar.memberBot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Команды бота участников: просмотр ближайших дат.
 * Подпиской на уведомления пользователь управляет в самом ВК.
 */
@Component
@RequiredArgsConstructor
public class MemberBotCommandHandler {

    private final MemberSubscriberStore subscriberStore;
    private final MemberNotificationService notificationService;
    private final MemberBotMessageSender messageSender;
    private final MemberBotKeyboardFactory keyboardFactory;

    public void handleCommand(Integer peerId, String text) {
        // Подпиской/отпиской управляет сам пользователь во ВКонтакте.
        // Любой, кто написал боту, автоматически становится получателем уведомлений.
        subscriberStore.subscribe(peerId);

        String command = text == null ? "" : text.trim();

        switch (command) {
            case "/upcoming":
            case "upcoming":
            case "📅 Посмотреть ближайшие даты":
                send(peerId, notificationService.buildUpcomingEventsMessage());
                break;

            default:
                sendMenu(peerId);
        }
    }

    private void sendMenu(Integer peerId) {
        String message = "📅 Календарь бронирования\n\n" +
                "Я буду присылать уведомления о новой брони и об отмене события.\n" +
                "Нажмите кнопку, чтобы посмотреть 5 ближайших дат с описанием.";
        send(peerId, message);
    }

    private void send(Integer peerId, String message) {
        messageSender.sendMessageWithKeyboard(peerId, message, keyboardFactory.createMainKeyboard());
    }
}
