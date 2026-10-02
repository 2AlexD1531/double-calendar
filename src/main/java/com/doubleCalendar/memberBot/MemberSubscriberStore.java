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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Хранилище получателей уведомлений о брони.
 * <p>
 * Получатель добавляется автоматически, когда он пишет боту. Подпиской/отпиской
 * пользователь управляет нативно в ВК (заблокировать сообщество или отключить его
 * уведомления). Список хранится в файле {@code vk.member-bot.subscribers-file}
 * (по умолчанию {@code data/member-bot-subscribers.txt}), поэтому переживает перезапуск.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSubscriberStore {

    private final MemberBotConfig memberConfig;

    /** peerId получателей в порядке регистрации. */
    private final Set<Integer> subscribers = new LinkedHashSet<>();

    @PostConstruct
    public void load() {
        synchronized (subscribers) {
            subscribers.clear();
            Path path = getFilePath();
            if (path == null || !Files.exists(path)) {
                log.info("Файл получателей не найден: {}", path);
                return;
            }

            try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
                lines.map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(line -> {
                            try {
                                subscribers.add(Integer.parseInt(line));
                            } catch (NumberFormatException e) {
                                log.warn("Некорректный peerId в файле получателей: {}", line);
                            }
                        });
            } catch (IOException e) {
                log.error("Ошибка чтения файла получателей {}: {}", path, e.getMessage());
                return;
            }

            log.info("Загружено получателей уведомлений: {}", subscribers.size());
        }
    }

    /**
     * Регистрация получателя уведомлений (вызывается при любом входящем сообщении).
     *
     * @return true, если получатель добавлен; false, если он уже был зарегистрирован
     */
    public boolean subscribe(Integer peerId) {
        if (peerId == null) {
            return false;
        }
        synchronized (subscribers) {
            boolean added = subscribers.add(peerId);
            if (added) {
                save();
                log.info("✅ Новый получатель уведомлений: {}", peerId);
            }
            return added;
        }
    }

    /**
     * Список получателей уведомлений.
     */
    public List<Integer> getRecipients() {
        synchronized (subscribers) {
            return new ArrayList<>(subscribers);
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
     * Сохранение получателей в файл.
     */
    private void save() {
        Path path = getFilePath();
        if (path == null) {
            log.warn("Файл получателей не задан, изменения не сохраняются");
            return;
        }

        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, subscribers.stream().map(String::valueOf).toList(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Ошибка сохранения файла получателей {}: {}", path, e.getMessage());
        }
    }
}
