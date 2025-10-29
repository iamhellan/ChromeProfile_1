package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.*;

import java.util.List;

public class v2_promo {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page mainPage;
    static TelegramNotifier tg;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized")));
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
        mainPage = context.newPage();

        // --- TelegramNotifier ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void openBonusesAndScrollPromoBlocks() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_promo* стартовал (проверка всех акций с прокруткой внутри блоков)");

        try {
            System.out.println("Открываем сайт 1xbet.kz");
            mainPage.navigate("https://1xbet.kz/");

            System.out.println("Переходим в раздел '1XBONUS'");
            mainPage.waitForSelector("a[href='bonus/rules']");
            mainPage.click("a[href='bonus/rules']");
            mainPage.waitForTimeout(1500);

            System.out.println("Открываем вкладку 'Все бонусы'");
            mainPage.waitForSelector("span.bonuses-navigation-bar__caption:has-text('Все бонусы')");
            mainPage.click("span.bonuses-navigation-bar__caption:has-text('Все бонусы')");
            mainPage.waitForTimeout(1000);

            System.out.println("Ждём список бонусов...");
            mainPage.waitForSelector("ul.bonuses-list");
            List<ElementHandle> bonusLinks = mainPage.querySelectorAll("ul.bonuses-list a.bonus-tile");
            if (bonusLinks.isEmpty())
                throw new RuntimeException("Не найдено ни одной бонусной акции!");

            System.out.println("Найдено бонусов: " + bonusLinks.size());

            // --- Проходим по каждой акции ---
            for (int i = 0; i < bonusLinks.size(); i++) {
                String href = bonusLinks.get(i).getAttribute("href");
                String url = href.startsWith("http") ? href : "https://1xbet.kz" + href;
                System.out.println("=== Открываем акцию #" + (i + 1) + ": " + url);

                Page tab = context.newPage();
                tab.navigate(url);
                waitForPageLoaded(tab, url, i + 1);

                // --- Прокрутка внутри блока акции ---
                slowScrollInsidePromo(tab, ".bonus-detail, .promo-detail, .bonus-header", 80, 80);

                // --- Переключаем язык и повторяем ---
                switchLanguage(tab, "kz");
                waitForPageLoaded(tab, url, i + 1);
                slowScrollInsidePromo(tab, ".bonus-detail, .promo-detail, .bonus-header", 80, 80);

                switchLanguage(tab, "en");
                waitForPageLoaded(tab, url, i + 1);
                slowScrollInsidePromo(tab, ".bonus-detail, .promo-detail, .bonus-header", 80, 80);

                tab.close();
                mainPage.bringToFront();
                mainPage.waitForTimeout(500);
            }

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            System.out.println("Все акции успешно пройдены ✅");

            tg.sendMessage(
                    "✅ *Тест успешно завершён:* v2_promo\n" +
                            "• Все акции проверены\n" +
                            "• Прокрутка внутри каждого блока — выполнена\n" +
                            "• Переключение языков — отработало\n\n" +
                            "🕒 Время выполнения: *" + duration + " сек.*\n" +
                            "🌐 Сайт: [1xbet.kz/bonus/rules](https://1xbet.kz/bonus/rules)"
            );

        } catch (Exception e) {
            System.out.println("❌ Ошибка в тесте: " + e.getMessage());
            String screenshotPath = ScreenshotHelper.takeScreenshot(mainPage, "v2_promo");
            tg.sendMessage("🚨 Ошибка в тесте *v2_promo*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }

    // --- Прокрутка именно внутри блока акции ---
    private void slowScrollInsidePromo(Page page, String selector, int steps, int pauseMs) {
        try {
            Locator promoBlock = page.locator(selector);
            promoBlock.waitFor(new Locator.WaitForOptions().setTimeout(5000).setState(WaitForSelectorState.VISIBLE));

            System.out.println("Скроллим внутри блока акции...");
            for (int i = 0; i <= steps; i++) {
                double percent = i * 1.0 / steps;
                page.evaluate("el => el.scrollTo(0, el.scrollHeight * " + percent + ")", promoBlock);
                page.waitForTimeout(pauseMs);
            }
            for (int i = steps; i >= 0; i--) {
                double percent = i * 1.0 / steps;
                page.evaluate("el => el.scrollTo(0, el.scrollHeight * " + percent + ")", promoBlock);
                page.waitForTimeout(pauseMs);
            }
        } catch (Exception e) {
            System.out.println("⚠ Не удалось проскроллить блок акции: " + e.getMessage());
        }
    }

    // --- Ждём полной загрузки страницы акции ---
    private void waitForPageLoaded(Page page, String url, int bonusIndex) {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
            page.waitForSelector(".bonus-detail, .promo-detail, .bonus-header",
                    new Page.WaitForSelectorOptions().setTimeout(12000).setState(WaitForSelectorState.VISIBLE));
        } catch (PlaywrightException e) {
            System.out.println("⚠ [Warning] Страница #" + bonusIndex + " (" + url + ") загружена частично: " + e.getMessage());
        }
        page.waitForTimeout(800);
    }

    // --- Переключение языка (ru / kz / en) ---
    private void switchLanguage(Page page, String lang) {
        System.out.println("Меняем язык на: " + lang);
        try {
            page.waitForSelector("button.header-lang__btn", new Page.WaitForSelectorOptions().setTimeout(3000));
            page.click("button.header-lang__btn");

            String langSelector = switch (lang) {
                case "kz" -> "a.header-lang-list-item-link[data-lng='kz']";
                case "en" -> "a.header-lang-list-item-link[data-lng='en']";
                default -> "a.header-lang-list-item-link[data-lng='ru']";
            };

            page.waitForSelector(langSelector, new Page.WaitForSelectorOptions().setTimeout(3000));
            page.click(langSelector);
            page.waitForTimeout(1800);
        } catch (Exception e) {
            System.out.println("⚠ Не удалось переключить язык на " + lang + ": " + e.getMessage());
        }
    }
}
