package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import java.util.List;

public class v2_MOBI_promo_ПРОЦЕСС extends BaseTest {

    // --- Хелпер: плавный скролл ---
    static void slowScroll(Page tab, boolean down, int msPerStep) {
        try {
            int steps = 8;
            int scrollHeight = ((Double) tab.evaluate("() => document.body.scrollHeight")).intValue();
            for (int i = 1; i <= steps; i++) {
                int position = down
                        ? scrollHeight * i / steps
                        : scrollHeight * (steps - i) / steps;
                tab.evaluate("window.scrollTo(0, " + position + ")");
                tab.waitForTimeout(msPerStep);
            }
        } catch (PlaywrightException e) {
            System.out.println("⚠️ Ошибка при скролле: " + e.getMessage());
        }
    }

    @Test
    void promoTest() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_promo_ПРОЦЕСС* стартовал (проверка раздела Акции & Promo)");

        String screenshotPath = null;

        try {
            // --- 1. Главная страница ---
            System.out.println("Открываем сайт...");
            page.navigate("https://1xbet.kz/?platform_type=mobile");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // --- 2. Бургер-меню ---
            System.out.println("Открываем бургер-меню");
            page.waitForSelector("button.header__hamburger", new Page.WaitForSelectorOptions().setTimeout(15000));
            page.click("button.header__hamburger");
            page.waitForTimeout(1000);

            // --- 3. Раздел “Акции & Promo” ---
            System.out.println("Жмём 'Акции & Promo' (надёжный клик для mobile)");

            Locator promoMenu = page.locator("span.drop-menu-list__link:has-text('Акции')");
            promoMenu.waitFor(new Locator.WaitForOptions()
                    .setTimeout(10000)
                    .setState(WaitForSelectorState.VISIBLE));

            try {
                promoMenu.click();
                System.out.println("✅ Клик по 'Акции & Promo' выполнен");
            } catch (Exception e) {
                System.out.println("⚠️ Обычный клик не сработал, пробуем через JS...");
                page.evaluate("""
                    const el = Array.from(document.querySelectorAll('span.drop-menu-list__link'))
                        .find(e => e.innerText && e.innerText.includes('Акции'));
                    if (el) el.click();
                """);
            }

            // --- 4. Проверяем, появилось ли подменю ---
            System.out.println("Проверяем, появилось ли подменю...");
            page.waitForTimeout(1000);
            Locator submenu = page.locator("a.drop-menu-list__link[href*='promo'], a.drop-menu-list__link:has-text('Бонус'), a.drop-menu-list__link:has-text('Турнир')");

            boolean submenuVisible = false;
            try {
                submenu.first().waitFor(new Locator.WaitForOptions()
                        .setTimeout(5000)
                        .setState(WaitForSelectorState.VISIBLE));
                submenuVisible = true;
            } catch (Exception ignored) { }

            if (!submenuVisible) {
                System.out.println("⚠️ Подменю не появилось, пробуем клик повторно...");
                promoMenu.click(new Locator.ClickOptions().setForce(true));
                page.waitForTimeout(1500);
                submenu.first().waitFor(new Locator.WaitForOptions()
                        .setTimeout(10000)
                        .setState(WaitForSelectorState.VISIBLE));
            }

            System.out.println("✅ Подменю с акциями успешно раскрыто!");

            // --- 5. Получаем все акции ---
            List<Locator> promoLinks = submenu.all();
            System.out.println("Найдено акций: " + promoLinks.size());

            int idx = 1;
            for (Locator link : promoLinks) {
                String title = link.innerText().replace("\n", " ").trim();
                String href = link.getAttribute("href");

                if (href == null || href.isBlank()) continue;
                String url = href.startsWith("http") ? href : "https://1xbet.kz" + href;

                System.out.println("[" + idx + "] Открываем акцию: " + title + " (" + url + ")");
                Page tab = context.newPage();

                try {
                    tab.navigate(url, new Page.NavigateOptions().setTimeout(15000));
                    tab.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    slowScroll(tab, true, 1000);
                    slowScroll(tab, false, 400);
                } catch (Exception e) {
                    System.out.println("⚠️ Ошибка при открытии акции: " + title + " → " + e.getMessage());
                } finally {
                    System.out.println("[" + idx + "] Закрываем вкладку");
                    tab.close();
                }

                idx++;
            }

            // --- 6. Финал ---
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

            String safeMsg = e.getMessage() == null ? "Неизвестная ошибка" :
                    e.getMessage().replace("<", "&lt;").replace(">", "&gt;");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_promo_ПРОЦЕСС*:\n```\n" + safeMsg + "\n```");

            if (screenshotPath != null)
                tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
