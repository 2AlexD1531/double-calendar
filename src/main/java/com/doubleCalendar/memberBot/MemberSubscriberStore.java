package com.doubleCalendar.memberBot;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Хранилище подписчиков уведомлений о брони.
 * <p>
 * Бот закрытый: получают уведомления только те, кто подписался по приглашению.
 * Подписчики хранятся в файле ({@code vk.bot.subscribers-file}, по умолчанию {@code data/vk-subscribers.txt}),
 * поэтому переживают перезапуск приложения.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSubscriberStore {

    private final MemberBotConfig memberConfig;

    /** peerId подписчиков в порядке подписки. */
    private final Set<Integer> subscribers = new LinkedHashSet<>();

    @PostConstruct
    public void load() {
        synchronized (subscribers) {
            subscribers.clear();
            Path path = getFilePath();
            if (path == null || !Files.exists(path)) {
                log.info("Файл подписчиков не найден: {}", path);
                return;
            }

            try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
                lines.map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(line -> {
                            try {
                                subscribers.add(Integer.parseInt(line));
                            } catch (NumberFormatException e) {
                                log.warn("Некорректный peerId в файле подписчиков: {}", line);
                            }
                        });
            } catch (IOException e) {
                log.error("Ошибка чтения файла подписчиков {}: {}", path, e.getMessage());
                return;
            }

            log.info("Загружено подписчиков уведомлений: {}", subscribers.size());
        }
    }

    /**
     * Подписка на уведомления.
     *
     * @return true, если подписка добавлена; false, если пользователь уже подписан
     */
    public boolean subscribe(Integer peerId) {
        if (peerId == null) {
            return false;
        }
        synchronized (subscribers) {
            boolean added = subscribers.add(peerId);
            if (added) {
                save();
                log.info("✅ Новый подписчик уведомлений: {}", peerId);
            }
            return added;
        }
    }

    /**
     * Отписка от уведомлений.
     *
     * @return true, если подписка удалена; false, если пользователь не был подписан
     */
    public boolean unsubscribe(Integer peerId) {
        if (peerId == null) {
            return false;
        }
        synchronized (subscribers) {
            boolean removed = subscribers.remove(peerId);
            if (removed) {
                save();
                log.info("✅ Подписчик отписан: {}", peerId);
            }
            return removed;
        }
    }

    public boolean isSubscribed(Integer peerId) {
        if (peerId == null) {
            return false;
        }
        synchronized (subscribers) {
            return subscribers.contains(peerId);
        }
    }

    /**
     * Список получателей уведомлений: подписчики.
     */
    public List<Integer> getRecipients() {
        synchronized (subscribers) {
            return new ArrayList<>(subscribers);
        }
    }

    /**
     * Список подписчиков без возможности изменения.
     */
    public List<Integer> getSubscribers() {
        return Collections.unmodifiableList(getRecipients());
    }

    public int count() {
        synchronized (subscribers) {
            return subscribers.size();
        }
    }

    private Path getFilePath() {
        String configured = memberConfig.getSubscribersFile();
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }
        return Paths.get(configured.trim());
    }

    /**
     * Сохранение подписчиков в файл.
     */
    private void save() {
        Path path = getFilePath();
        if (path == null) {
            log.warn("Файл подписчиков не задан, изменения не сохраняются");
            return;
        }

        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, subscribers.stream().map(String::valueOf).toList(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Ошибка сохранения файла подписчиков {}: {}", path, e.getMessage());
        }
    }
}
