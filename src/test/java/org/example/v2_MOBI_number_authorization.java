package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_number_authorization {
    static Playwright playwright;
    static BrowserContext context;
    static Page page;
    static TelegramNotifier tg;

    // ---------- ХЕЛПЕРЫ (из референса) ----------
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

    static void closeIdentificationPopupIfVisible() {
        try {
            Locator popupClose = page.locator("button.identification-popup-close");
            if (popupClose.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                System.out.println("Закрываем попап идентификации (identification-popup-close)");
                popupClose.click();
                Thread.sleep(500);
            }
        } catch (Exception ignored) {}
    }

    static void closeResetPasswordPopupIfVisible() {
        try {
            Locator popupClose = page.locator("button.reset-password__close");
            if (popupClose.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                System.out.println("Закрываем всплывающее окно (reset-password__close)");
                popupClose.click();
                Thread.sleep(500);
            }
        } catch (Exception ignored) {}
    }

    // ---------- НАСТРОЙКА ----------
    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();

        // --- Определяем окружение (home/work) ---
        String osUser = System.getProperty("user.name").toLowerCase();
        String activeProfile;
        if (osUser.contains("zhntm")) {
            activeProfile = "home";
        } else if (osUser.contains("b.zhantemirov")) {
            activeProfile = "work";
        } else {
            activeProfile = "home"; // fallback
        }

        // --- Загружаем путь профиля Chrome из config.properties ---
        String key = "chrome.profile." + activeProfile;
        String userDataDirPath = ConfigHelper.get(key);
        Path userDataDir = Paths.get(userDataDirPath);
        if (!userDataDir.toFile().exists()) {
            throw new RuntimeException("❌ Профиль Chrome не найден: " + userDataDir.toAbsolutePath());
        }

        System.out.println("✅ Активный профиль Chrome: " + activeProfile);
        System.out.println("📁 Путь: " + userDataDir.toAbsolutePath());

        // --- Persistent Context ---
        context = playwright.chromium().launchPersistentContext(
                userDataDir,
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(false)
                        .setArgs(List.of(
                                "--start-maximized",
                                "--disable-blink-features=AutomationControlled",
                                "--disable-web-security",
                                "--disable-features=IsolateOrigins,site-per-process",
                                "--disable-infobars",
                                "--no-sandbox",
                                "--disable-gpu",
                                "--disable-dev-shm-usage",
                                "--ignore-certificate-errors",
                                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/122.0.0.0 Safari/537.36"
                        ))
        );

        page = context.pages().get(0);
        page.setDefaultTimeout(60000);

        // --- Telegram Notifier ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);

        System.out.println("🧠 Пользователь ОС: " + osUser);
        System.out.println("✅ Persistent Chrome профиль загружен успешно!");
    }

    // ---------- ТЕСТ ----------
    @Test
    void loginWithPhoneAndSms() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_number_authorization* стартовал (авторизация по номеру телефона + SMS)");

        String screenshotPath = null;

        try {
            System.out.println("Открываем мобильную версию сайта 1xbet.kz");
            page.navigate("https://1xbet.kz/?platform_type=mobile");
            waitForPageOrReload(10000);

            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(1000);
            page.click("button#curLoginForm");

            System.out.println("Выбираем способ входа по номеру телефона");
            page.waitForTimeout(1000);
            page.click("button.c-input-material__custom.custom-functional-button");

            System.out.println("Вводим номер телефона");
            page.waitForTimeout(1000);
            page.fill("input.phone-input__field[type='tel']", ConfigHelper.get("phone"));

            System.out.println("Вводим пароль");
            page.waitForTimeout(1000);
            page.fill("input#auth-form-password", ConfigHelper.get("password"));

            System.out.println("Жмём кнопку 'Войти'");
            page.waitForTimeout(1000);
            page.click("button.auth-button:has(span.auth-button__text:has-text('Войти'))");

            // ---- ЖДЁМ РЕШЕНИЯ КАПЧИ ----
            System.out.println("Теперь решай капчу вручную — я жду появление кнопки 'Выслать код' (до 10 минут)...");
            try {
                page.waitForSelector("button:has-text('Выслать код')",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000) // максимум 10 минут
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Кнопка 'Выслать код' появилась ✅");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Кнопка 'Выслать код' не появилась — капча не решена или что-то пошло не так!");
            }

            // --- Отправляем код подтверждения ---
            System.out.println("Жмём 'Выслать код'");
            Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
            try {
                sendCodeButton.click();
                System.out.println("Кнопка 'Выслать код' нажата ✅");
            } catch (Exception e) {
                System.out.println("Первая попытка клика не удалась, пробуем через JS...");
                page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
            }

            // ---- ЖДЁМ ПОЛЕ ДЛЯ ВВОДА КОДА ----
            System.out.println("Теперь решай капчу вручную — я жду поле для кода (до 10 минут)...");
            try {
                page.waitForSelector("input.phone-sms-modal-code__input",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Поле для кода появилось! Достаём код из Google Messages...");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Поле для ввода кода не появилось — капча не решена или что-то пошло не так!");
            }

            // --- Google Messages ---
            System.out.println("Открываем Google Messages (авторизованная сессия)");
            BrowserContext messagesContext = playwright.chromium().launchPersistentContext(
                    Paths.get("messages-session.json"),
                    new BrowserType.LaunchPersistentContextOptions().setHeadless(false)
            );
            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

            System.out.println("Закрываем уведомление 'Нет, не нужно' (если есть)");
            if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
                messagesPage.waitForTimeout(1000);
                messagesPage.click("button:has-text('Нет, не нужно')");
            }

            System.out.println("Жмём кнопку 'Подключить, отсканировав QR-код'");
            messagesPage.waitForTimeout(1000);
            messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код')").click();

            System.out.println("Ищем последнее сообщение от 1xBet");
            Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
            lastMessage.waitFor();

            String smsText = lastMessage.innerText();
            System.out.println("Содержимое SMS: " + smsText);

            // Код может содержать и буквы, и цифры (6–8 символов)
            Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b");
            Matcher matcher = pattern.matcher(smsText);
            String code = null;
            if (matcher.find()) {
                code = matcher.group();
            }

            if (code == null) {
                throw new RuntimeException("Не удалось извлечь код подтверждения из SMS: " + smsText);
            }

            System.out.println("Извлечённый код подтверждения: " + code);

            // --- Вставляем код на сайте ---
            page.bringToFront();
            System.out.println("Вводим код подтверждения");
            page.waitForTimeout(1000);
            page.fill("input.phone-sms-modal-code__input", code);

            System.out.println("Жмём 'Подтвердить'");
            page.waitForTimeout(1000);
            page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

            closeIdentificationPopupIfVisible();
            closeResetPasswordPopupIfVisible();

            // --- Заходим в Личный кабинет ---
            System.out.println("Открываем 'Личный кабинет'");
            page.waitForTimeout(2000);
            page.click("button.user-header__link.header__link.header__reg");

            System.out.println("Жмём 'Выход'");
            page.waitForTimeout(1000);
            page.click("button.drop-menu-list__link_exit");

            System.out.println("Подтверждаем выход кнопкой 'Ок'");
            page.waitForTimeout(1000);
            page.click("button.swal2-confirm.swal2-styled");

            long duration = (System.currentTimeMillis() - startTime) / 1000;

            // --- Telegram Summary ---
            String msg = "✅ *v2_MOBI_number_authorization завершён успешно*\n\n"
                    + "🕒 Время выполнения: *" + duration + " сек.*\n"
                    + "📲 Код подтверждения: `" + code + "`\n"
                    + "\n🌐 [1xbet.kz/mobile](https://1xbet.kz/?platform_type=mobile)";
            tg.sendMessage(msg);

            System.out.println("Выход завершён ✅ (браузер остаётся открытым)");

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_number_authorization_error");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_number_authorization*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }

    // Браузер остаётся открытым
    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
