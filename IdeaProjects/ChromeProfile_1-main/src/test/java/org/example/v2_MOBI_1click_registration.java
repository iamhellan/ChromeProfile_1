package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class v2_MOBI_1click_registration {
    static Playwright playwright;
    static BrowserContext browser; // persistent context
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

    static void closeRegistrationPopupIfVisible() {
        try {
            Locator crossBtn = page.locator("button.popup-registration__close");
            if (crossBtn.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                System.out.println("Кликаем крестик popup-registration__close (вместо 'Продолжить')");
                crossBtn.click();
                Thread.sleep(700);
            }
        } catch (Exception ignored) {}
    }

    static String generatePromoCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) code.append(chars.charAt(rand.nextInt(chars.length())));
        return code.toString();
    }

    // ---------- НАСТРОЙКА ----------
    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();

        // --- Определяем, где запущено окружение ---
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

        // --- Запуск браузера с persistent контекстом ---
        browser = playwright.chromium().launchPersistentContext(
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

        page = browser.pages().get(0);
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
    void registration1ClickFullFlow() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_1click_registration* стартовал (мобильная регистрация в 1 клик)");

        StringBuilder errors = new StringBuilder();
        String promoCode = generatePromoCode();
        String screenshotPath = null;
        String credsInfo = ""; // будет использовано в Telegram-сводке

        try {
            System.out.println("Открываем мобильную версию сайта...");
            page.navigate("https://1xbet.kz/?platform_type=mobile");
            waitForPageOrReload(10000);

            // --- Проверка: если уже авторизован, выходим из аккаунта ---
            try {
                page.waitForTimeout(2000);
                boolean isAlreadyLoggedIn = !page.locator("button.header-btn--registration").isVisible();

                if (isAlreadyLoggedIn) {
                    System.out.println("Обнаружен уже авторизованный пользователь — выполняем выход из аккаунта...");

                    // --- Пробуем закрыть любые всплывающие окна, если они есть ---
                    try {
                        Locator anyClose = page.locator(
                                "button[aria-label='Закрыть'], " +
                                        ".arcticmodal-close, " +
                                        "button.reset-password__close, " +
                                        "button.identification-popup-close, " +
                                        "button.popup-registration__close"
                        );

                        int visibleCount = 0;
                        for (int i = 0; i < anyClose.count(); i++) {
                            Locator closeBtn = anyClose.nth(i);
                            if (closeBtn.isVisible()) {
                                visibleCount++;
                                try {
                                    System.out.println("Закрываем всплывающее окно №" + (i + 1));
                                    closeBtn.click();
                                    page.waitForTimeout(700);
                                } catch (Exception e2) {
                                    System.out.println("⚠ Не удалось кликнуть по окну №" + (i + 1) + ": " + e2.getMessage());
                                }
                            }
                        }

                        if (visibleCount == 0) {
                            System.out.println("Нет всплывающих окон — идём дальше ✅");
                        } else {
                            System.out.println("Закрыто всплывающих окон: " + visibleCount);
                        }
                    } catch (Exception e) {
                        System.out.println("⚠ Ошибка при попытке закрыть всплывающее окно: " + e.getMessage());
                    }

                    // --- Пробуем войти в ЛК и выйти ---
                    System.out.println("Открываем меню (ЛК)");
                    page.waitForSelector("button.user-header__link.header__reg_ico");
                    page.click("button.user-header__link.header__reg_ico");
                    Thread.sleep(1000);

                    System.out.println("Нажимаем 'Выход'");
                    page.waitForSelector("button.drop-menu-list__link_exit");
                    page.click("button.drop-menu-list__link_exit");
                    Thread.sleep(500);

                    System.out.println("Подтверждаем выход (ОК)");
                    page.waitForSelector("button.swal2-confirm.swal2-styled");
                    page.click("button.swal2-confirm.swal2-styled");
                    Thread.sleep(1000);

                    // --- Возвращаемся на главную ---
                    System.out.println("Возвращаемся на главную страницу...");
                    page.navigate("https://1xbet.kz/?platform_type=mobile");
                    waitForPageOrReload(7000);
                    System.out.println("✅ Готово — продолжаем сценарий регистрации");
                } else {
                    System.out.println("Пользователь не авторизован — начинаем регистрацию с нуля.");
                }

            } catch (Exception e) {
                System.out.println("⚠ Ошибка при проверке статуса авторизации: " + e.getMessage());
            }

            System.out.println("Кликаем 'Регистрация'");
            page.waitForSelector("button.header-btn--registration");
            page.click("button.header-btn--registration");
            waitForPageOrReload(10000);
            Thread.sleep(1000);

            page.waitForSelector("button.c-registration__tab:has-text('В 1 клик')");
            page.click("button.c-registration__tab:has-text('В 1 клик')");
            waitForPageOrReload(5000);
            Thread.sleep(1000);

            page.fill("input#registration_ref_code", promoCode);
            Thread.sleep(1000);

            page.click("div.c-registration__block--bonus .multiselect__select");
            page.waitForSelector(".multiselect__option .c-registration-select--refuse-bonuses");
            Thread.sleep(500);
            page.click(".multiselect__option .c-registration-select--refuse-bonuses:has-text('Отказ от бонусов')");
            Thread.sleep(1000);

            page.click("div.c-registration__block--bonus .multiselect__select");
            page.waitForSelector(".multiselect__option .c-registration-select--sport-bonus");
            Thread.sleep(500);
            page.click(".multiselect__option .c-registration-select--sport-bonus:has-text('Получать бонусы')");
            Thread.sleep(1000);

            page.click("div.submit_registration");
            System.out.println("Ожидаем ручного ввода капчи...");
            page.waitForSelector("div#js-post-reg-copy-login-password",
                    new Page.WaitForSelectorOptions().setTimeout(600_000)
                            .setState(WaitForSelectorState.VISIBLE));

            System.out.println("Кликаем 'Копировать' логин/пароль");
            page.click("div#js-post-reg-copy-login-password");
            Thread.sleep(500);

            // --- Извлекаем креды (логин/пароль) из пост-регистрационного блока ---
            try {
                Locator accountBlock = page.locator("div.post-registration__data").first();
                accountBlock.waitFor(new Locator.WaitForOptions().setTimeout(3000).setState(WaitForSelectorState.VISIBLE));
                credsInfo = accountBlock.innerText().trim();
                System.out.println("Извлечённые креды:\n" + credsInfo);
            } catch (Exception ex) {
                System.out.println("⚠ Не удалось извлечь креды автоматически: " + ex.getMessage());
            }

            page.waitForSelector("button.swal2-confirm.swal2-styled");
            page.click("button.swal2-confirm.swal2-styled");
            Thread.sleep(500);

            System.out.println("Кликаем 'Получить по SMS'");
            page.waitForSelector("button#account-info-button-sms");
            page.click("button#account-info-button-sms");
            Thread.sleep(500);
            closeResetPasswordPopupIfVisible();
            closeIdentificationPopupIfVisible();

            System.out.println("Кликаем 'Сохранить в файл'");
            page.waitForSelector("a#account-info-button-file");
            page.click("a#account-info-button-file");
            Thread.sleep(500);
            closeIdentificationPopupIfVisible();

            System.out.println("Кликаем 'Сохранить картинкой'");
            page.waitForSelector("a#account-info-button-image");
            page.click("a#account-info-button-image");
            Thread.sleep(500);
            closeIdentificationPopupIfVisible();

            System.out.println("Кликаем 'Выслать на e-mail'");
            page.waitForSelector("a#form_mail_after_submit");
            page.click("a#form_mail_after_submit");
            Thread.sleep(500);

            String email = ConfigHelper.get("email");
            page.waitForSelector("input.js-post-email-content-form__input");
            page.fill("input.js-post-email-content-form__input", email);
            page.waitForSelector("button.js-post-email-content-form__btn:not([disabled])");
            page.click("button.js-post-email-content-form__btn:not([disabled])");
            Thread.sleep(500);
            closeIdentificationPopupIfVisible();

            // --- Кликаем 'Продолжить' (если появилось после регистрации) ---
            try {
                Locator continueButton = page.locator("a#continue-button-after-reg");
                if (continueButton.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                    System.out.println("Нажимаем 'Продолжить' после регистрации");
                    continueButton.click();
                    page.waitForTimeout(3000);

                    // --- Ждём появления окна идентификации ---
                    System.out.println("Ожидаем появление окна идентификации...");
                    try {
                        page.waitForSelector("a.identification-popup-link.identification-popup-transition__link",
                                new Page.WaitForSelectorOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                        System.out.println("Окно идентификации появилось ✅");

                        // --- Нажимаем 'Пройти идентификацию' ---
                        Locator idLink = page.locator("a.identification-popup-link.identification-popup-transition__link");
                        if (idLink.isVisible()) {
                            System.out.println("Нажимаем 'Пройти идентификацию'");
                            idLink.click();
                            page.waitForTimeout(3000);
                        }
                    } catch (Exception e) {
                        System.out.println("⚠ Окно идентификации не появилось: " + e.getMessage());
                    }

                } else {
                    System.out.println("Кнопка 'Продолжить' не появилась — идём дальше");
                }
            } catch (Exception e) {
                System.out.println("⚠ Ошибка при нажатии на кнопку 'Продолжить': " + e.getMessage());
            }

            // --- Закрываем всплывающее окно ---
            try {
                Locator closeAfterId = page.locator("button.reset-password__close[title='Закрыть']");
                if (closeAfterId.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                    System.out.println("Закрываем всплывающее окно после идентификации (reset-password__close)");
                    closeAfterId.click();
                    page.waitForTimeout(1000);
                } else {
                    System.out.println("Окно после идентификации не появилось — идём дальше");
                }
            } catch (Exception e) {
                System.out.println("⚠ Ошибка при закрытии окна после идентификации: " + e.getMessage());
            }

            // --- Открываем меню профиля перед выходом ---
            try {
                Locator menuButton = page.locator("button.user-header__link.header__link.header__reg.header__reg_ico");
                menuButton.waitFor(new Locator.WaitForOptions()
                        .setTimeout(5000)
                        .setState(WaitForSelectorState.VISIBLE));
                if (menuButton.isVisible()) {
                    System.out.println("Кликаем по кнопке 'Открыть меню' (ion-android-person)");
                    menuButton.click();
                    page.waitForTimeout(1000);
                } else {
                    System.out.println("⚠ Кнопка меню профиля не видна, возможно, уже раскрыто меню.");
                }
            } catch (Exception e) {
                System.out.println("⚠ Не удалось кликнуть по кнопке 'Открыть меню': " + e.getMessage());
            }


// --- Нажимаем 'Выход' ---
            System.out.println("Нажимаем 'Выход'");
            try {
                page.waitForSelector("button.drop-menu-list__link_exit");
                page.click("button.drop-menu-list__link_exit");
                page.waitForTimeout(500);

                System.out.println("Подтверждаем выход (ОК)");
                page.waitForSelector("button.swal2-confirm.swal2-styled");
                page.click("button.swal2-confirm.swal2-styled");
                page.waitForTimeout(1000);
                System.out.println("Выход завершён ✅");
            } catch (Exception e) {
                System.out.println("⚠ Ошибка при выходе: " + e.getMessage());
            }

            long duration = (System.currentTimeMillis() - startTime) / 1000;

            // --- Итоговое сообщение в Telegram (вместе с кредами) ---
            StringBuilder msg = new StringBuilder();
            msg.append("✅ *v2_MOBI_1click_registration завершён успешно*\n\n");
            msg.append("🕒 Время выполнения: *").append(duration).append(" сек.*\n");
            msg.append("🎟️ Промокод: `").append(promoCode).append("`\n");
            if (credsInfo != null && !credsInfo.isEmpty()) {
                msg.append("🆕 *Креды нового аккаунта:*\n");
                msg.append("```\n").append(credsInfo).append("\n```");
            } else {
                msg.append("⚠ Логин/пароль не извлечены автоматически\n");
            }
            msg.append("\n🌐 [1xbet.kz/mobile](https://1xbet.kz/?platform_type=mobile)");

            tg.sendMessage(msg.toString());

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_1click_registration_error");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_1click_registration*: \n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}