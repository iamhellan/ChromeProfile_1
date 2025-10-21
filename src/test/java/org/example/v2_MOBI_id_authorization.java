package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_id_authorization {
    static Playwright playwright;
    static BrowserContext context;
    static Page page;
    static TelegramNotifier tg;

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

        // --- Определяем окружение ---
        String osUser = System.getProperty("user.name").toLowerCase();
        String activeProfile;
        if (osUser.contains("zhntm")) {
            activeProfile = "home";
        } else if (osUser.contains("b.zhantemirov")) {
            activeProfile = "work";
        } else {
            activeProfile = "home";
        }

        // --- Загружаем путь из config.properties ---
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
    void loginWithIdAndSms() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_id_authorization* стартовал (авторизация по ID + SMS)");

        String screenshotPath = null;

        try {
            System.out.println("Открываем мобильную версию сайта 1xbet.kz");
            page.navigate("https://1xbet.kz/?platform_type=mobile");
            waitForPageOrReload(10000);

            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(1000);
            page.click("button#curLoginForm >> text=Войти");

            System.out.println("Вводим ID");
            page.waitForTimeout(1000);
            page.fill("input#auth_id_email", ConfigHelper.get("login"));

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
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Кнопка 'Выслать код' появилась ✅");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Кнопка 'Выслать код' не появилась — капча не решена или что-то пошло не так!");
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

            // --- Google Messages ---
            System.out.println("Открываем Google Messages (используем сохранённую авторизацию)");
            Browser tempBrowser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            BrowserContext messagesContext = tempBrowser.newContext(
                    new Browser.NewContextOptions()
                            .setStorageStatePath(Paths.get("messages-session.json"))
                            .setViewportSize(1920, 1080)
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/127.0.0.0 Safari/537.36")
            );

            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

// Проверяем, не просит ли логин или QR
            if (messagesPage.url().contains("welcome")) {
                System.out.println("⚠️ Похоже, сессия Google Messages устарела или не найдена!");
                System.out.println("🔹 Перейди вручную в Google_Messages.java, чтобы обновить сессию через QR.");
                tg.sendMessage("⚠️ Сессия Google Messages недействительна. Требуется ручное обновление через QR.");
                throw new RuntimeException("Сессия Google Messages недействительна");
            }

            System.out.println("✅ Сессия активна — читаем последнее сообщение от 1xBet...");

// Берём последний чат
            Locator chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.click();
            messagesPage.waitForTimeout(1000);

// Читаем текст последнего сообщения
            Locator messageNodes = messagesPage.locator("div.text-msg.msg-content div.ng-star-inserted");
            int count = messageNodes.count();
            if (count == 0) throw new RuntimeException("❌ Сообщений не найдено в Google Messages!");

            String smsText = messageNodes.nth(count - 1).innerText();
            System.out.println("📩 Содержимое SMS: " + smsText);

// Извлекаем код
            Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b").matcher(smsText);
            String code = matcher.find() ? matcher.group() : null;
            if (code == null) throw new RuntimeException("❌ Не удалось извлечь код подтверждения из SMS: " + smsText);

            System.out.println("✅ Код подтверждения найден: " + code);

            // --- Вводим код на сайте ---
            page.bringToFront();
            System.out.println("Вводим код подтверждения");
            page.fill("input.phone-sms-modal-code__input", code);

            System.out.println("Жмём 'Подтвердить'");
            page.click("button:has-text('Подтвердить')");

            // --- Заходим в Личный кабинет ---
            closeIdentificationPopupIfVisible();
            page.waitForTimeout(2000);
            System.out.println("Открываем 'Личный кабинет'");
            page.click("a.header-lk-box-link[title='Личный кабинет']");

            System.out.println("Жмём 'Выход'");
            page.waitForTimeout(1000);
            page.click("button.drop-menu-list__link_exit");

            System.out.println("Подтверждаем выход кнопкой 'Ок'");
            page.waitForTimeout(1000);
            page.click("button.swal2-confirm.swal2-styled");

            long duration = (System.currentTimeMillis() - startTime) / 1000;

            // --- Telegram Summary ---
            String msg = "✅ *v2_MOBI_id_authorization завершён успешно*\n\n"
                    + "🕒 Время выполнения: *" + duration + " сек.*\n"
                    + "📲 Код подтверждения: `" + code + "`\n"
                    + "\n🌐 [1xbet.kz/mobile](https://1xbet.kz/?platform_type=mobile)";
            tg.sendMessage(msg);

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_id_authorization_error");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_id_authorization*: \n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
