package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.BeforeAll;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class BaseTest {
    protected static Playwright playwright;
    protected static Browser browser;
    protected static BrowserContext context;
    protected static Page page;
    protected static TelegramNotifier tg;

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
        browser = playwright.chromium().launch( // ← добавлен объект browser
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of("--start-maximized"))
        );

        context = playwright.chromium().launchPersistentContext(
                userDataDir,
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(false)
                        .setViewportSize(null)
                        .setArgs(List.of(
                                "--start-maximized",
                                "--disable-blink-features=AutomationControlled",
                                "--disable-web-security",
                                "--disable-features=IsolateOrigins,site-per-process",
                                "--disable-infobars",
                                "--disable-notifications",
                                "--no-sandbox",
                                "--disable-gpu",
                                "--disable-dev-shm-usage",
                                "--ignore-certificate-errors",
                                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/122.0.0.0 Safari/537.36"
                        ))
        );

        // --- Получаем первую вкладку ---
        if (context.pages().isEmpty()) {
            page = context.newPage();
        } else {
            page = context.pages().get(0);
        }

        page.setDefaultTimeout(60000);

        // --- Telegram Notifier ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);

        System.out.println("🧠 Пользователь ОС: " + osUser);
        System.out.println("✅ Persistent Chrome профиль загружен успешно!");
    }
    // ---------- ХЕЛПЕР: ПРОВЕРКА СЕССИИ GOOGLE MESSAGES ----------
    protected static void ensureGoogleMessagesConnected(Page messagesPage) {
        try {
            messagesPage.waitForTimeout(2000); // ждём загрузку UI

            // Проверяем наличие кнопки "Обновить"
            Locator refreshButton = messagesPage.locator("button:has-text('Обновить')");
            if (refreshButton.isVisible()) {
                System.out.println("⚠️ Потеряно соединение с Google Messages — жмём 'Обновить'");
                refreshButton.click();
                messagesPage.waitForTimeout(3000);

                // Проверяем, исчезла ли кнопка
                if (refreshButton.isVisible()) {
                    System.out.println("❌ После нажатия 'Обновить' кнопка не исчезла — возможно, требуется повторная авторизация (QR).");
                    tg.sendMessage("⚠️ Google Messages: требуется повторная авторизация через QR.");
                } else {
                    System.out.println("✅ Соединение с Google Messages восстановлено.");
                }
            } else {
                System.out.println("🔹 Соединение с Google Messages активно, всё хорошо.");
            }

        } catch (PlaywrightException e) {
            System.out.println("⚠️ Ошибка при проверке состояния Google Messages: " + e.getMessage());
        }
    }

}
