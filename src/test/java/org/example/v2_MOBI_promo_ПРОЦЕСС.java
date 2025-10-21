package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class v2_MOBI_promo_ПРОЦЕСС {
    static Playwright playwright;
    static BrowserContext context;
    static Page page;
    static TelegramNotifier tg;

    // --- Хелперы ---
    static void slowScroll(Page tab, boolean down, int msPerStep) {
        int steps = 8;
        int scrollHeight = ((Double) tab.evaluate("() => document.body.scrollHeight")).intValue();
        for (int i = 1; i <= steps; i++) {
            int position = down
                    ? scrollHeight * i / steps
                    : scrollHeight * (steps - i) / steps;
            tab.evaluate("window.scrollTo(0, " + position + ")");
            tab.waitForTimeout(msPerStep);
        }
    }

    // --- Настройка окружения ---
    @BeforeAll
    static void setupAll() {
        playwright = Playwright.create();

        // Определяем окружение
        String osUser = System.getProperty("user.name").toLowerCase();
        String activeProfile;
        if (osUser.contains("zhntm")) {
            activeProfile = "home";
        } else if (osUser.contains("b.zhantemirov")) {
            activeProfile = "work";
        } else {
            activeProfile = "home";
        }

        // Загружаем путь профиля Chrome
        String key = "chrome.profile." + activeProfile;
        String userDataDirPath = ConfigHelper.get(key);
        Path userDataDir = Paths.get(userDataDirPath);
        if (!userDataDir.toFile().exists()) {
            throw new RuntimeException("❌ Профиль Chrome не найден: " + userDataDir.toAbsolutePath());
        }

        System.out.println("✅ Активный профиль Chrome: " + activeProfile);
        System.out.println("📁 Путь: " + userDataDir.toAbsolutePath());

        // Persistent Context
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
                                "--user-agent=Mozilla/5.0 (Linux; Android 11; SM-G998B) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.74 Mobile Safari/537.36"
                        ))
        );

        page = context.pages().get(0);
        page.setDefaultTimeout(60000);

        // Telegram Notifier
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);

        System.out.println("🧠 Пользователь ОС: " + osUser);
        System.out.println("✅ Persistent Chrome профиль загружен успешно!");
    }

    // --- Основной тест ---
    @Test
    void promoTest() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_promo_ПРОЦЕСС* стартовал (проверка раздела Акции & Promo)");

        String screenshotPath = null;

        try {
            System.out.println("Открываем сайт...");
            page.navigate("https://1xbet.kz/?platform_type=mobile");

            // Бургер-меню
            System.out.println("Открываем бургер-меню");
            page.waitForSelector("button.header__hamburger");
            page.click("button.header__hamburger");

            // Акции & Promo
            System.out.println("Жмём 'Акции & Promo' через JS");
            page.waitForSelector("span.drop-menu-list__link");
            page.evaluate(
                    "Array.from(document.querySelectorAll('span.drop-menu-list__link')).find(e => e.innerText.includes('Акции')).click()"
            );

            // Ждём блок с акциями
            System.out.println("Ждём блок с акциями...");
            Locator promoBlock = page.locator("div.drop-menu-list_inner");
            promoBlock.waitFor(new Locator.WaitForOptions().setTimeout(5000));

            // Получаем все акции
            List<Locator> promoLinks = promoBlock.locator("a.drop-menu-list__link").all();
            System.out.println("Найдено акций: " + promoLinks.size());
            int idx = 1;

            for (Locator link : promoLinks) {
                String title = link.innerText().replace("\n", " ").trim();
                String href = link.getAttribute("href");
                if (href == null || href.isBlank()) continue;
                String url = href.startsWith("http") ? href : "https://1xbet.kz" + href;

                System.out.println("[" + idx + "] Открываем акцию: " + title + " (" + url + ")");
                Page tab = context.newPage();
                tab.navigate(url);

                // Скроллим вниз и вверх
                slowScroll(tab, true, 1200);
                slowScroll(tab, false, 400);

                System.out.println("[" + idx + "] Закрываем вкладку");
                tab.close();

                idx++;
            }

            long duration = (System.currentTimeMillis() - startTime) / 1000;

            String msg = "✅ *v2_MOBI_promo_ПРОЦЕСС завершён успешно*\n\n"
                    + "📊 Проверено акций: *" + (idx - 1) + "*\n"
                    + "🕒 Время выполнения: *" + duration + " сек.*\n"
                    + "\n🌐 [1xbet.kz/mobile](https://1xbet.kz/?platform_type=mobile)";
            tg.sendMessage(msg);

            System.out.println("Тест завершён успешно ✅");

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_promo_error");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_promo_ПРОЦЕСС*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
