package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleMessages extends BaseTest {

    @Test
    void extractCodeFromGoogleMessages() {
        try {
            System.out.println("📨 Открываем Google Messages с уже сохранённой сессией...");
            Page messagesPage = context.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");
            messagesPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            messagesPage.waitForTimeout(3000);

            System.out.println("Проверяем, активна ли сессия Google Messages...");
            ensureGoogleMessagesConnected(messagesPage);

            // --- ЕСЛИ СЕССИЯ УПАЛА И ПОЯВИЛАСЬ КНОПКА "ОБНОВИТЬ" ---
            try {
                Locator refreshButton = messagesPage.locator("button:has-text('Обновить')");
                if (refreshButton.isVisible()) {
                    System.out.println("⚠️ Обнаружено сообщение 'Не удалось подключиться'. Жмём 'Обновить'...");
                    try {
                        refreshButton.click();
                    } catch (Exception e1) {
                        System.out.println("Первая попытка клика не сработала, пробуем через JS...");
                        messagesPage.evaluate("document.querySelector('button.refresh-button')?.click()");
                    }

                    // Ждём, пока страница обновится и чаты загрузятся
                    System.out.println("⌛ Ждём восстановления чатов после обновления...");
                    boolean reconnected = false;
                    for (int i = 0; i < 20; i++) {
                        if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                            reconnected = true;
                            break;
                        }
                        messagesPage.waitForTimeout(1000);
                    }
                    if (reconnected) {
                        System.out.println("✅ Соединение с Google Messages восстановлено после 'Обновить'");
                    } else {
                        System.out.println("❌ После нажатия 'Обновить' чаты так и не появились!");
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️ Ошибка при проверке кнопки 'Обновить': " + e.getMessage());
            }


            // --- Проверяем авторизацию ---
            if (messagesPage.url().contains("welcome")) {
                throw new RuntimeException("⚠️ Не авторизованы в Google Messages! Нужно один раз вручную отсканировать QR.");
            }

            // Повторная проверка на случай, если после 'Обновить' страница ещё грузится
            if (messagesPage.locator("button:has-text('Обновить')").isVisible()) {
                System.out.println("🔁 Кнопка 'Обновить' всё ещё видна — пробуем перезагрузить страницу...");
                messagesPage.reload();
                messagesPage.waitForTimeout(4000);
                ensureGoogleMessagesConnected(messagesPage);
            }


            // --- Ждём появления списка чатов (устойчивое ожидание через цикл) ---
            System.out.println("⌛ Ждём появления списка чатов...");
            boolean chatsLoaded = false;
            for (int i = 0; i < 20; i++) {
                if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                    chatsLoaded = true;
                    break;
                }
                messagesPage.waitForTimeout(1000);
            }
            if (!chatsLoaded)
                throw new RuntimeException("❌ Чаты не появились в Google Messages — возможно, не успели подгрузиться.");
            System.out.println("✅ Список чатов успешно найден");

            // --- Кликаем по чату 1xBet (если нет — берём первый) ---
            System.out.println("🔍 Ищем чат с 1xBet...");
            Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
            if (chat.count() == 0) {
                System.out.println("⚠️ Чат 1xBet не найден, кликаем по первому в списке");
                chat = messagesPage.locator("mws-conversation-list-item").first();
            }
            chat.first().click();
            System.out.println("💬 Чат открыт");
            messagesPage.waitForTimeout(3000);

            // --- Ищем последнее сообщение ---
            System.out.println("📩 Ищем последнее сообщение...");
            Locator messageNodes = messagesPage.locator("div.text-msg-content div.text-msg.msg-content div.ng-star-inserted");
            int count = 0;
            for (int i = 0; i < 15; i++) { // ждём до 15 секунд
                count = messageNodes.count();
                if (count > 0) break;
                messagesPage.waitForTimeout(1000);
            }
            if (count == 0)
                throw new RuntimeException("❌ Не найдено сообщений внутри чата!");
            String lastMessageText = messageNodes.nth(count - 1).innerText().trim();
            System.out.println("📨 Последнее сообщение: " + lastMessageText);

            // --- Извлекаем код (буквы+цифры 4–8 символов) ---
            Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b").matcher(lastMessageText);
            String code = matcher.find() ? matcher.group() : null;
            if (code == null)
                throw new RuntimeException("❌ Код подтверждения не найден в сообщении!");
            System.out.println("✅ Извлечённый код: " + code);

            // --- Открываем новую вкладку и вставляем код ---
            System.out.println("🧭 Открываем новую вкладку и вставляем код...");
            Page newTab = context.newPage();
            newTab.setContent("<html><body style='font-family: sans-serif; font-size: 24px; padding: 20px;'>" +
                    "<h2>Ваш код подтверждения:</h2>" +
                    "<p style='color: green; font-weight: bold;'>" + code + "</p>" +
                    "</body></html>");
            newTab.waitForTimeout(2000);
            System.out.println("✅ Код вставлен в новую вкладку: " + code);

            // --- Telegram уведомление ---
            try {
                tg.sendMessage("📬 Код из Google Messages: *" + code + "*");
                System.out.println("📨 Код успешно отправлен в Telegram ✅");
            } catch (Exception tgErr) {
                System.out.println("⚠️ Ошибка при отправке в Telegram: " + tgErr.getMessage());
            }

            System.out.println("🎯 Тест успешно завершён!");

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            tg.sendMessage("🚨 Ошибка в тесте Google Messages: " + e.getMessage());
        }
    }

    protected static void ensureGoogleMessagesConnected(Page messagesPage) {
        System.out.println("🔄 Проверяем состояние Google Messages...");
        messagesPage.waitForTimeout(2000);

        try {
            Locator refreshButton = messagesPage.locator("button:has-text('Обновить')");
            if (refreshButton.isVisible()) {
                System.out.println("⚠️ Обнаружено сообщение 'Не удалось подключиться'. Жмём 'Обновить'...");
                try {
                    refreshButton.click();
                } catch (Exception e1) {
                    System.out.println("Первый клик не сработал, пробуем через JS...");
                    messagesPage.evaluate("document.querySelector('button.refresh-button')?.click()");
                }
                messagesPage.waitForTimeout(4000);
            }

            if (messagesPage.locator("mws-conversation-list-item").count() == 0) {
                System.out.println("⌛ Ждём появления чатов...");
                boolean chatsLoaded = false;
                for (int i = 0; i < 20; i++) {
                    if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                        chatsLoaded = true;
                        break;
                    }
                    messagesPage.waitForTimeout(1000);
                }
                if (chatsLoaded)
                    System.out.println("✅ Чаты успешно подгрузились");
                else
                    System.out.println("❌ Чаты не появились — возможно, интернет отвалился.");
            } else {
                System.out.println("✅ Google Messages в онлайне, чаты загружены");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Ошибка при проверке подключения: " + e.getMessage());
        }
    }


    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
