package com.doubleCalendar.memberBot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Команды бота участников: подписка, отписка и просмотр ближайших дат.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberBotCommandHandler {

    private final MemberSubscriberStore subscriberStore;
    private final MemberNotificationService notificationService;
    private final MemberBotMessageSender messageSender;
    private final MemberBotKeyboardFactory keyboardFactory;

    public void handleCommand(Integer peerId, String text) {
        String command = text == null ? "" : text.trim();

        switch (command) {
            case "/upcoming":
            case "upcoming":
            case "📅 Посмотреть ближайшие даты":
                send(peerId, notificationService.buildUpcomingEventsMessage());
                break;

            case "/subscribe":
            case "subscribe":
            case "🔔 Подписаться":
                handleSubscribe(peerId);
                break;

            case "/unsubscribe":
            case "unsubscribe":
            case "🔕 Отписаться":
                handleUnsubscribe(peerId);
                break;

            default:
                sendMenu(peerId);
        }
    }

    private void handleSubscribe(Integer peerId) {
        if (subscriberStore.subscribe(peerId)) {
            send(peerId, "🔔 Вы подписались на уведомления.\n" +
                    "Я сообщу о новой брони и об отмене события.");
        } else {
            send(peerId, "ℹ️ Вы уже подписаны на уведомления.");
        }
    }

    private void handleUnsubscribe(Integer peerId) {
        if (subscriberStore.unsubscribe(peerId)) {
            send(peerId, "🔕 Вы отписались от уведомлений.");
        } else {
            send(peerId, "ℹ️ Вы не были подписаны на уведомления.");
        }
    }

    private void sendMenu(Integer peerId) {
        String message = "📅 Календарь бронирования\n\n" +
                "🔔 Подписаться — получать уведомления о новой брони и отмене события\n" +
                "📅 Посмотреть ближайшие даты — 5 ближайших дат с описанием";
        send(peerId, message);
    }

    private void send(Integer peerId, String message) {
        messageSender.sendMessageWithKeyboard(peerId, message, keyboardFactory.createMainKeyboard());
    }
}
