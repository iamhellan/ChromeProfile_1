package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_id_authorization_fastgames {
    static Playwright playwright;
    static BrowserContext context;
    static Page page;
    static TelegramNotifier tg;

    // ===== ХЕЛПЕРЫ ИЗ РЕФЕРЕНСА ==============================================

    // Умное ожидание полной загрузки с авто-Reload
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

    // ===== УТИЛИТЫ ИГР (как в исходнике, без изменений сценария) =============

    private Frame findFrameWithSelector(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Page pg : p.context().pages()) {
                for (Frame f : pg.frames()) {
                    try {
                        if (f.locator(selector).count() > 0) {
                            System.out.println("[DEBUG] Нашли селектор в фрейме: " + f.url());
                            return f;
                        }
                    } catch (Throwable ignore) {}
                }
            }
            p.waitForTimeout(300);
        }
        return null;
    }

    private Locator smartLocator(Page p, String selector, int timeoutMs) {
        Locator direct = p.locator(selector);
        if (direct.count() > 0) return direct;
        Frame f = findFrameWithSelector(p, selector, timeoutMs);
        if (f != null) return f.locator(selector);
        throw new RuntimeException("Элемент не найден: " + selector);
    }

    private void robustClick(Page p, Locator loc, int timeoutMs, String debugName) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        RuntimeException lastErr = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                loc.first().scrollIntoViewIfNeeded();
                loc.first().click(new Locator.ClickOptions().setTimeout(3000));
                return;
            } catch (RuntimeException e1) {
                lastErr = e1;
                try {
                    loc.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                    return;
                } catch (RuntimeException e2) {
                    lastErr = e2;
                    try {
                        loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}))");
                        return;
                    } catch (RuntimeException e3) { lastErr = e3; }
                }
            }
            p.waitForTimeout(200);
        }
        if (lastErr != null) throw lastErr;
    }

    private void clickFirstEnabled(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Locator group;
            try {
                group = smartLocator(p, selector, 1500);
            } catch (RuntimeException e) {
                p.waitForTimeout(200);
                continue;
            }
            int count = group.count();
            for (int i = 0; i < count; i++) {
                Locator candidate = group.nth(i);
                boolean visible;
                try { visible = candidate.isVisible(); } catch (Throwable t) { visible = false; }
                if (!visible) continue;
                boolean enabled;
                try { enabled = (Boolean) candidate.evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))"); } catch (Throwable t) { enabled = true; }
                if (enabled) {
                    robustClick(p, candidate, 8000, selector + " [nth=" + i + "]");
                    return;
                }
            }
            p.waitForTimeout(200);
        }
        throw new RuntimeException("Не дождались активного элемента: " + selector);
    }

    private Page clickCardMaybeOpensNewTab(Locator card) {
        int before = context.pages().size();
        robustClick(page, card, 30000, "game-card");
        page.waitForTimeout(600);
        int after = context.pages().size();
        if (after > before) {
            Page newPage = context.pages().get(after - 1);
            newPage.bringToFront();
            return newPage;
        }
        return page;
    }

    private void passTutorialIfPresent(Page gamePage) {
        for (int i = 1; i <= 5; i++) {
            try {
                Locator nextBtn = smartLocator(gamePage, "div[role='button']:has-text('Далее')", 600);
                if (nextBtn.count() == 0 || !nextBtn.first().isVisible()) break;
                robustClick(gamePage, nextBtn.first(), 2000, "Далее");
                gamePage.waitForTimeout(150);
            } catch (RuntimeException ignore) { break; }
        }
        try {
            Locator understood = smartLocator(gamePage, "div[role='button']:has-text('Я всё понял')", 600);
            if (understood.count() > 0 && understood.first().isVisible()) {
                robustClick(gamePage, understood.first(), 2000, "Я всё понял");
            }
        } catch (RuntimeException ignore) {}
    }

    private void setStake50ViaChip(Page gamePage) {
        Locator chip50 = smartLocator(gamePage, "div.chip-text:has-text('50')", 2000);
        robustClick(gamePage, chip50.first(), 12000, "chip-50");
    }

    private void waitRoundToSettle(Page gamePage, int maxMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxMs) {
            Locator anyBet = gamePage.locator("div[role='button'][data-market][data-outcome]:has-text('Сделать ставку')");
            try {
                if (anyBet.count() > 0 && anyBet.first().isVisible()) {
                    boolean enabled = (Boolean) anyBet.first().evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))");
                    if (enabled) return;
                }
            } catch (Throwable ignore) {}
            gamePage.waitForTimeout(150);
        }
    }

    private Page openGameByHrefContains(Page originPage, String hrefContains, String fallbackMenuText) {
        Frame f = findFrameWithSelector(originPage, "a[href*='" + hrefContains + "']", 5000);
        if (f == null && fallbackMenuText != null) {
            f = findFrameWithSelector(originPage, "span.text-hub-header-game-title:has-text('" + fallbackMenuText + "')", 5000);
        }
        if (f == null) throw new RuntimeException("Не нашли игру: " + hrefContains);
        Locator link = f.locator("a[href*='" + hrefContains + "']");
        link.first().scrollIntoViewIfNeeded();
        return clickCardMaybeOpensNewTab(link.first());
    }

    private Page openUniqueBoxingFromHub(Page originPage) {
        String innerSpan = "a.menu-sports-item-inner[href*='productId=boxing'] span.text-hub-header-game-title:has-text('Бокс')";
        Frame f = findFrameWithSelector(originPage, innerSpan, 8000);
        if (f == null) throw new RuntimeException("Не нашли уникальную кнопку 'Бокс'");
        Locator link = f.locator(innerSpan).first().locator("xpath=ancestor::a");
        return clickCardMaybeOpensNewTab(link.first());
    }

    // ===== НАСТРОЙКА ==========================================================

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
            activeProfile = "home"; // fallback
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

    // ===== ТЕСТ ===============================================================

    @Test
    void loginAndPlayFastGames() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_id_authorization_fastgames* стартовал (авторизация + быстрые игры)");

        String screenshotPath = null;

        try {
            // === Авторизация ===
            page.navigate("https://1xbet.kz/?platform_type=mobile");
            waitForPageOrReload(10000);

            page.click("button#curLoginForm >> text=Войти");
            page.fill("input#auth_id_email", ConfigHelper.get("login"));
            page.fill("input#auth-form-password", ConfigHelper.get("password"));
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

            // ---- ЖМЁМ "ВЫСЛАТЬ КОД" ----
            page.click("button:has-text('Выслать код')");

            // ---- ЖДЁМ ПОЛЕ ДЛЯ ВВОДА КОДА ----
            page.waitForSelector("input.phone-sms-modal-code__input",
                    new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE));

            // --- Google Messages ---
            System.out.println("Открываем Google Messages (авторизованная сессия)");
            BrowserContext messagesContext = playwright.chromium().launchPersistentContext(
                    Paths.get("messages-session.json"),
                    new BrowserType.LaunchPersistentContextOptions().setHeadless(false)
            );
            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

            if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
                messagesPage.waitForTimeout(1000);
                messagesPage.click("button:has-text('Нет, не нужно')");
            }

            Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
            lastMessage.waitFor();
            String smsText = lastMessage.innerText();
            Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b").matcher(smsText);
            String code = matcher.find() ? matcher.group() : null;

            if (code == null) throw new RuntimeException("Код не найден в SMS");
            System.out.println("Код подтверждения: " + code);

            // --- Вводим код ---
            page.bringToFront();
            page.fill("input.phone-sms-modal-code__input", code);
            page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

            closeIdentificationPopupIfVisible();
            closeResetPasswordPopupIfVisible();

            // === Быстрые игры === (сценарий без изменений)
            page.click("button.header__hamburger.hamburger");
            page.click("a.drop-menu-list__link[href*='fast-games']");

            // Crash Boxing
            Locator crashTile = page.locator("div.tile__cell img[alt='Crash boxing']").first();
            Page gamePage = clickCardMaybeOpensNewTab(crashTile);
            passTutorialIfPresent(gamePage);
            clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes']", 30000);
            clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes_2']", 30000);
            waitRoundToSettle(gamePage, 25000);

            // Нарды
            Page nardsPage = openGameByHrefContains(gamePage, "nard", "Нарды");
            passTutorialIfPresent(nardsPage);
            setStake50ViaChip(nardsPage);
            clickFirstEnabled(nardsPage, "span[role='button'][data-market='dice'][data-outcome='blue']", 20000);
            waitRoundToSettle(nardsPage, 25000);

            // Дартс
            Page dartsPage = openGameByHrefContains(nardsPage, "darts?cid", "Дартс");
            passTutorialIfPresent(dartsPage);
            setStake50ViaChip(dartsPage);
            clickFirstEnabled(dartsPage, "span[role='button'][data-market='1-4-5-6-9-11-15-16-17-19']", 20000);
            waitRoundToSettle(dartsPage, 25000);

            // Дартс - Фортуна
            Page dartsFortunePage = openGameByHrefContains(dartsPage, "darts-fortune", "Дартс - Фортуна");
            passTutorialIfPresent(dartsFortunePage);
            setStake50ViaChip(dartsFortunePage);
            clickFirstEnabled(dartsFortunePage, "div[data-outcome='ONE_TO_EIGHT']", 20000);
            waitRoundToSettle(dartsFortunePage, 25000);

            // Больше/Меньше
            Page hiloPage = openGameByHrefContains(dartsFortunePage, "darts-hilo", "Больше/Меньше");
            passTutorialIfPresent(hiloPage);
            setStake50ViaChip(hiloPage);
            clickFirstEnabled(hiloPage, "div[role='button'][data-market][data-outcome]:has-text('Больше')", 45000);
            waitRoundToSettle(hiloPage, 30000);

            // Буллиты NHL21
            Page shootoutPage = openGameByHrefContains(hiloPage, "shootout", "Буллиты NHL21");
            passTutorialIfPresent(shootoutPage);
            setStake50ViaChip(shootoutPage);
            clickFirstEnabled(shootoutPage, "div[role='button'].market-button:has-text('Да')", 45000);
            waitRoundToSettle(shootoutPage, 35000);

            // Бокс
            Page boxingPage = openUniqueBoxingFromHub(shootoutPage);
            passTutorialIfPresent(boxingPage);
            setStake50ViaChip(boxingPage);
            clickFirstEnabled(boxingPage, "div[role='button'].contest-panel-outcome-button", 20000);
            waitRoundToSettle(boxingPage, 20000);

            long duration = (System.currentTimeMillis() - startTime) / 1000;

            String msg = "✅ *v2_MOBI_id_authorization_fastgames завершён успешно*\n\n"
                    + "🕒 Время выполнения: *" + duration + " сек.*\n"
                    + "🎮 Пройден блок 'Быстрые игры' (Crash Boxing, Нарды, Дартс, Дартс-Фортуна, Больше/Меньше, Буллиты NHL21, Бокс)\n"
                    + "\n🌐 [1xbet.kz/mobile](https://1xbet.kz/?platform_type=mobile)";
            tg.sendMessage(msg);

            System.out.println("Готово ✅");

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_id_authorization_fastgames_error");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_id_authorization_fastgames*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
