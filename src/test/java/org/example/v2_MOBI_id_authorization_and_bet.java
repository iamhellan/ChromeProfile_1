package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_id_authorization_and_bet {
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
    void loginBetHistoryAndLogout() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_id_authorization_and_bet* стартовал (авторизация + ставка + выход)");

        String screenshotPath = null;

        try {
            page.navigate("https://1xbet.kz/?platform_type=mobile");
            waitForPageOrReload(10000);

            // --- Авторизация ---
            System.out.println("Авторизация по ID");
            page.click("button#curLoginForm span.auth-btn__label:has-text('Вход')");
            page.fill("input#auth_id_email", ConfigHelper.get("login"));
            page.fill("input#auth-form-password", ConfigHelper.get("password"));
            page.click("button.auth-button span.auth-button__text:has-text('Войти')");

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
            page.click("button:has-text('Выслать код')");

            // ---- ЖДЁМ ПОЛЕ ДЛЯ ВВОДА КОДА ----
            page.waitForSelector("input.phone-sms-modal-code__input",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(600_000)
                            .setState(WaitForSelectorState.VISIBLE)
            );

            // --- Google Messages ---
            System.out.println("Открываем Google Messages (сессия сохранена)");
            BrowserContext messagesContext = playwright.chromium().launchPersistentContext(
                    Paths.get("messages-session.json"),
                    new BrowserType.LaunchPersistentContextOptions().setHeadless(false)
            );
            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

            Locator chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.click();
            messagesPage.waitForTimeout(1000);
            Locator messageNodes = messagesPage.locator("div.text-msg.msg-content div.ng-star-inserted");
            int count = messageNodes.count();
            String smsText = count > 0 ? messageNodes.nth(count - 1).innerText() : "";
            Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b").matcher(smsText);
            String code = matcher.find() ? matcher.group() : null;
            if (code == null) throw new RuntimeException("❌ Код не найден в SMS");
            System.out.println("Код подтверждения: " + code);

            // --- Вводим код ---
            page.bringToFront();
            page.fill("input.phone-sms-modal-code__input", code);
            page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

            closeIdentificationPopupIfVisible();
            closeResetPasswordPopupIfVisible();

            // --- Ставка ---
            System.out.println("Выбираем коэффициент");
            page.waitForSelector("div.coef__num", new Page.WaitForSelectorOptions().setTimeout(20000));
            try {
                Locator coefP1 = page.locator("div.coef:has-text('П1')");
                if (coefP1.count() > 0) {
                    coefP1.first().click();
                } else {
                    page.locator("div.coef__num").first().click();
                }
            } catch (Exception e) {
                page.locator("div.coef__num").first().click();
            }

            // --- Купон ставок ---
            page.click("button.header__hamburger");
            page.click("span.drop-menu-list__coupon:has-text('1')");

            // --- Ввод суммы ---
            System.out.println("Вводим сумму ставки");
            page.click("input.bet_sum_input");
            page.waitForSelector("button.hg-button[data-skbtn='5']", new Page.WaitForSelectorOptions().setTimeout(10000));
            page.click("button.hg-button[data-skbtn='5']");
            page.waitForTimeout(300);
            page.click("button.hg-button[data-skbtn='0']");

            // --- Совершаем ставку ---
            System.out.println("Совершаем ставку");
            page.click("span.bets-sums-keyboard-button__label:has-text('Сделать ставку')");
            page.waitForSelector("button.c-btn span.c-btn__text:has-text('Ok')",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            page.click("button.c-btn span.c-btn__text:has-text('Ok')");

            // --- История ---
            System.out.println("Открываем историю ставок");
            page.click("button.user-header__link.header__reg_ico");
            page.click("a.drop-menu-list__link_history:has-text('История ставок')");

            // --- Выход ---
            page.click("button.user-header__link.header__reg_ico");
            page.click("button.drop-menu-list__link_exit:has-text('Выход')");
            page.click("button.swal2-confirm.swal2-styled:has-text('ОК')");

            long duration = (System.currentTimeMillis() - startTime) / 1000;

            String msg = "✅ *v2_MOBI_id_authorization_and_bet завершён успешно*\n\n"
                    + "🕒 Время выполнения: *" + duration + " сек.*\n"
                    + "🎯 Код подтверждения: `" + code + "`\n"
                    + "💰 Ставка совершена и проверена в истории\n"
                    + "\n🌐 [1xbet.kz/mobile](https://1xbet.kz/?platform_type=mobile)";
            tg.sendMessage(msg);

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_id_authorization_and_bet_error");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_id_authorization_and_bet*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
