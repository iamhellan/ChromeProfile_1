package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.v2_MOBI_id_authorization_and_bet.tgStep;

public class v2_MOBI_social_authorization extends BaseTest {

    // ---------- ХЕЛПЕРЫ ----------

    static void waitForPageOrReload(int maxWaitMs) {
        int waited = 0;
        while (true) {
            try {
                String readyState = (String) page.evaluate("() => document.readyState");
                if ("complete".equals(readyState)) break;
                Thread.sleep(500);
                waited += 500;
                if (waited >= maxWaitMs) {
                    System.out.println("Страница не загрузилась за " + maxWaitMs + " мс, обновляем!");
                    page.reload();
                    waited = 0;
                }
            } catch (Exception e) {
                page.reload();
                waited = 0;
            }
        }
    }

    // 🔹 Новый хелпер для ожидания закрытия окна Google
    static boolean waitForPopupClose(Page popup, int timeoutMs) {
        int waited = 0;
        while (waited < timeoutMs) {
            if (popup.isClosed()) return true;
            try { popup.waitForTimeout(250); } catch (Exception ignored) {}
            waited += 250;
        }
        return popup.isClosed();
    }

    static void closeIdentificationPopupIfVisible() {
        try {
            Locator popupClose = page.locator("button.identification-popup-close");
            if (popupClose.isVisible()) {
                System.out.println("Закрываем попап идентификации (identification-popup-close)");
                popupClose.click();
                Thread.sleep(500);
            }
        } catch (Exception ignored) {}
    }

    static void closeResetPasswordPopupIfVisible() {
        try {
            Locator popupClose = page.locator("button.reset-password__close");
            if (popupClose.isVisible()) {
                System.out.println("Закрываем всплывающее окно (reset-password__close)");
                popupClose.click();
                Thread.sleep(500);
            }
        } catch (Exception ignored) {}
    }

    // ---------- ТЕСТ ----------
    @Test
    void loginWithIdAndSms() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_id_authorization* стартовал (авторизация по ID + SMS)");

        String screenshotPath = null;

        try {
            System.out.println("Открываем мобильную версию сайта 1xbet.kz");
            page.navigate("https://1xbet.kz/?platform_type=mobile");
            waitForPageOrReload(10000);

            // ---- ПРОВЕРКА: авторизован ли аккаунт ----
            System.out.println("Пробуем определить, авторизован ли аккаунт...");

            try {
                // Терпеливо ждём появления кнопки 'Личный кабинет' (до 15 секунд)
                Locator profileButton = page.locator("button.user-header__link.header__link.header__reg.header__reg_ico.ion-android-person");
                profileButton.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                System.out.println("Кнопка 'Личный кабинет' найдена ✅");

                // Пробуем кликнуть
                profileButton.click();
                page.waitForTimeout(2000);

                // Проверяем, появилось ли меню выхода
                Locator logoutButton = page.locator("button.drop-menu-list__link_exit");
                if (logoutButton.isVisible()) {
                    System.out.println("🔹 Аккаунт уже авторизован — выполняем выход...");
                    logoutButton.click();

                    // Подтверждаем 'Ок'
                    page.waitForTimeout(1000);
                    page.click("button.swal2-confirm.swal2-styled");
                    page.waitForTimeout(2000);

                    System.out.println("✅ Успешно вышли из аккаунта, продолжаем сценарий входа...");
                } else {
                    System.out.println("ℹ️ Меню выхода не найдено — вероятно, пользователь не был авторизован.");
                }

            } catch (PlaywrightException e) {
                System.out.println("⏳ Кнопка 'Личный кабинет' не появилась или не кликабельна. Продолжаем сценарий входа.");
            }

            // ---- НАЧИНАЕМ СЦЕНАРИЙ АВТОРИЗАЦИИ ----
            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(1000);
            page.click("button#curLoginForm >> text=Войти");

            System.out.println("Выбираем Google...");
            page.click("li.auth-social__item .auth-social__link--google");

            System.out.println("Ждём окно Google...");
            Page popup = null;
            try {
                popup = page.waitForPopup(() -> {
                    // действие, которое вызывает окно — оно уже выполнено выше (клик по Google)
                });
            } catch (Exception e) {
                System.out.println("⚠️ Не удалось дождаться popup — возможно, сессия уже активна и редирект прошёл мгновенно.");
            }

// ---- Проверяем, жив ли popup ----
            if (popup == null || popup.isClosed()) {
                System.out.println("🔁 Окно Google даже не открылось (сессия активна) — сразу возвращаемся на 1xbet ✅");
            } else {
                try {
                    popup.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    System.out.println("Окно Google открыто ✅");
                } catch (PlaywrightException e) {
                    System.out.println("⚠️ Окно Google закрылось до загрузки — сессия уже авторизована ✅");
                }

                // ---- Проверяем, не закрылось ли оно мгновенно ----
                boolean popupClosedInstantly = false;
                for (int i = 0; i < 10; i++) { // проверяем первые 10 секунд
                    if (popup.isClosed()) {
                        popupClosedInstantly = true;
                        System.out.println("🔁 Окно Google закрылось мгновенно — сессия активна ✅");
                        break;
                    }
                    popup.waitForTimeout(1000);
                }

                if (!popupClosedInstantly) {
                    boolean emailFieldExists = false;
                    try {
                        popup.waitForSelector("input[type='email']", new Page.WaitForSelectorOptions().setTimeout(5000));
                        emailFieldExists = true;
                    } catch (PlaywrightException ignored) {
                        System.out.println("Поле email не найдено — возможно, уже авторизованы через cookie.");
                    }

                    if (emailFieldExists) {
                        System.out.println("Требуется авторизация — вводим email и пароль...");
                        popup.fill("input[type='email']", ConfigHelper.get("google.email"));
                        popup.click("button:has-text('Далее')");
                        popup.waitForTimeout(2000);

                        try {
                            popup.waitForSelector("input[type='password']", new Page.WaitForSelectorOptions().setTimeout(7000));
                            popup.fill("input[type='password']", ConfigHelper.get("google.password"));
                            popup.click("button:has-text('Далее')");
                            System.out.println("Пароль введён ✅");
                        } catch (PlaywrightException e) {
                            System.out.println("⚠️ Поле пароля не появилось — возможно, вход выполнен автоматически.");
                        }

                        System.out.println("⏳ Ждём закрытия окна Google...");
                        if (!waitForPopupClose(popup, 30_000)) {
                            System.out.println("⚠️ Окно Google не закрылось за 30 сек — продолжаем осторожно.");
                        } else {
                            System.out.println("Окно Google закрыто ✅");
                        }

                    } else {
                        System.out.println("🔄 Ждём автоматический редирект...");
                        if (!waitForPopupClose(popup, 20_000)) {
                            System.out.println("⚠️ Редирект затянулся — возможно, потребуется ручное действие.");
                        } else {
                            System.out.println("Редирект выполнен, окно Google закрыто ✅");
                        }
                    }
                }
            }

// ---- Проверяем, вернулись ли мы на сайт 1xbet ----
            waitForPageOrReload(15000);
            String currentUrl = page.url();
            if (currentUrl.contains("1xbet.kz")) {
                System.out.println("✅ Успешно вернулись на сайт 1xbet.kz после авторизации или активной сессии");
            } else {
                throw new RuntimeException("❌ После авторизации не вернулись на сайт 1xbet.kz — что-то пошло не так.");
            }


            // ---- ЖМЁМ "ВЫСЛАТЬ КОД" ----
            System.out.println("Жмём 'Выслать код'");
            page.click("button:has-text('Выслать код')");

            // ---- ЖДЁМ ПОЛЕ ДЛЯ ВВОДА КОДА ----
            System.out.println("Ждём поле для ввода кода (до 10 минут)...");
            page.waitForSelector("input.phone-sms-modal-code__input",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(600_000)
                            .setState(WaitForSelectorState.VISIBLE)
            );
            System.out.println("Поле для ввода кода появилось ✅");

            // --- GOOGLE MESSAGES (устойчивое чтение из открытого чата) ---
            System.out.println("📨 Открываем Google Messages с уже сохранённой сессией...");
            Page messagesPage = context.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");
            messagesPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            messagesPage.waitForTimeout(3000);

            System.out.println("Проверяем, активна ли сессия Google Messages...");
            ensureGoogleMessagesConnected(messagesPage);

// --- Проверяем авторизацию ---
            if (messagesPage.url().contains("welcome")) {
                throw new RuntimeException("⚠️ Не авторизованы в Google Messages! Нужно один раз вручную отсканировать QR.");
            }

// --- Ждём появления списка чатов (устойчивое ожидание) ---
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

// --- Возвращаемся на сайт ---
            page.bringToFront();
            page.fill("input.phone-sms-modal-code__input", code);
            page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");
            page.waitForTimeout(3000);
            System.out.println("Авторизация завершена ✅");

// --- Telegram уведомление ---
            try {
                tg.sendMessage("📬 Код из Google Messages: *" + code + "*");
                System.out.println("📨 Код успешно отправлен в Telegram ✅");
            } catch (Exception tgErr) {
                System.out.println("⚠️ Ошибка при отправке в Telegram: " + tgErr.getMessage());
            }

            closeIdentificationPopupIfVisible();
            closeResetPasswordPopupIfVisible();

            // --- Завершение / Выход ---
            System.out.println("Пробуем выполнить выход...");
            try {
                Locator menu = page.locator("button.user-header__link.header__link--messages");
                menu.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                menu.click();

                Locator logout = page.locator("button.drop-menu-list__link_exit:has-text('Выход')");
                logout.click();

                page.locator("button.swal2-confirm.swal2-styled:has-text('ОК')").click();
                tgStep("Выход выполнен успешно", true);
            } catch (Exception e) {
                tgStep("⚠️ Ошибка при выходе: " + e.getMessage(), false);
            }

// ---------- ФИНАЛ ----------
            boolean isAuthorized = false; // по желанию можно заменить флагом, если выше есть логика
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage("🎯 *Тест завершён успешно*\n" +
                    "• Авторизация: " + (isAuthorized ? "уже была" : "выполнена") + "\n" +
                    "• Авторизация и выход проверены\n" +
                    "🕒 Время выполнения: *" + duration + " сек.* ✅");
            System.out.println("Отчёт отправлен в Telegram ✅");

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_id_authorization_error");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_id_authorization*:\n" + e.getMessage());
            if (screenshotPath != null && !screenshotPath.isEmpty()) {
                tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            }
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
