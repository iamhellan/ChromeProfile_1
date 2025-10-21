package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Random;

public class v2_1click_registration {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;
    static TelegramNotifier tg;

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
        java.nio.file.Path userDataDir = java.nio.file.Paths.get(userDataDirPath);

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

        // --- TelegramNotifier ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);

        System.out.println("🧠 Пользователь ОС: " + osUser);
        System.out.println("✅ Persistent Chrome профиль загружен успешно!");
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    // ---------- ХЕЛПЕРЫ ----------
    static void pauseShort() { pause(150); }
    static void pauseMedium() { pause(350); }
    static void pause(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    static void jsClick(Page page, String selector) {
        page.evaluate("selector => document.querySelector(selector)?.click()", selector);
    }

    static void neutralizeOverlayIfNeeded(Page page) {
        page.evaluate("(() => {" +
                "const kill = sel => document.querySelectorAll(sel).forEach(n=>{" +
                "try{n.style.pointerEvents='none'; n.style.zIndex='0'; n.removeAttribute('onclick');}catch(e){}" +
                "});" +
                "kill('.arcticmodal-container_i2');" +
                "kill('.arcticmodal-container_i');" +
                "kill('.v--modal-background-click');" +
                "kill('#modals-container *');" +
                "kill('.pf-main-container-wrapper-th-4 *');" +
                "kill('#post-reg-new-overlay');" + // 💥 Добавлен перекрывающий overlay
                "})();");
    }

    static void clickIfVisible(Page page, String selector) {
        try {
            Locator loc = page.locator(selector);
            if (loc.count() > 0 && loc.first().isVisible()) {
                neutralizeOverlayIfNeeded(page);
                loc.first().click(new Locator.ClickOptions().setTimeout(5000));
                pauseShort();
            }
        } catch (Exception e) {
            System.out.println("⚠ Не удалось кликнуть по " + selector + ": " + e.getMessage());
        }
    }

    static void waitUntilLoggedOutOrHeal(Page page) {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            boolean isLoggedOut = page.locator("button#registration-form-call").isVisible()
                    || Boolean.TRUE.equals(page.evaluate("() => !document.body.innerText.includes('Личный кабинет')"));
            if (isLoggedOut) return;

            neutralizeOverlayIfNeeded(page);
            clickIfVisible(page, "button.swal2-confirm.swal2-styled");
            clickIfVisible(page, "button.identification-popup-close");
            page.waitForTimeout(300);
        }
        page.navigate("https://1xbet.kz/");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    static String randomPromo(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    // ---------- ТЕСТ ----------
    @Test
    void v2_registration() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_1click_registration* стартовал (регистрация в 1 клик)");

        StringBuilder errors = new StringBuilder();
        String credsInfo = "";

        try {
            System.out.println("Открываем сайт 1xbet.kz (десктоп)");
            page.navigate("https://1xbet.kz/");
            pauseMedium();

            // --- РЕГИСТРАЦИЯ ---
            try {
                System.out.println("Жмём 'Регистрация'");
                page.locator("button#registration-form-call").click();
            } catch (Exception e) {
                errors.append("Не удалось нажать 'Регистрация'\n");
            }

            System.out.println("Ожидаем модалку регистрации");
            page.waitForSelector("div.arcticmodal-container",
                    new Page.WaitForSelectorOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
            pause(2000);

            System.out.println("Кликаем по вкладке 'В 1 клик' для надёжности");
            try {
                page.locator("div.arcticmodal-container button.c-registration__tab:has-text('В 1 клик')").first().click();
            } catch (Exception e) {
                jsClick(page, "div.arcticmodal-container button.c-registration__tab:has-text('В 1 клик')");
            }

            // Промокод
            System.out.println("Вводим рандомный промокод");
            try {
                String promo = randomPromo(8);
                page.fill("input#popup_registration_ref_code", promo);
            } catch (Exception e) {
                errors.append("Ошибка при вводе промокода\n");
            }

            // Бонусы
            System.out.println("Отказываемся от бонусов, затем соглашаемся");
            try {
                page.locator("div.c-registration-bonus__item.c-registration-bonus__item--close").first().click();
                page.locator("div.c-registration-bonus__item:has(.c-registration-bonus__title:has-text('Принять'))").first().click();
            } catch (Exception e) {
                errors.append("Не удалось обработать бонусы\n");
            }

            // Зарегистрироваться
            System.out.println("Жмём 'Зарегистрироваться' (в модалке регистрации)");
            try {
                Locator regModalButton = page.locator(
                        "div.arcticmodal-container div.c-registration__button.submit_registration:has(span.c-registration-button__label:has-text('Зарегистрироваться'))"
                );
                regModalButton.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                regModalButton.first().click();
            } catch (Exception e) {
                jsClick(page, "div.c-registration__button.submit_registration span.c-registration-button__label");
                errors.append("Клик 'Зарегистрироваться' через JS\n");
            }

            // ---- УМНОЕ ОЖИДАНИЕ КАПЧИ ----
            System.out.println("Теперь решай капчу вручную — жду появления кнопки 'Копировать' (до 10 минут)...");
            try {
                page.waitForSelector("#js-post-reg-copy-login-password",
                        new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE));
                System.out.println("Капча решена ✅ — кнопка 'Копировать' появилась!");
            } catch (PlaywrightException e) {
                errors.append("Капча не решена вовремя\n");
            }

            // Извлекаем креды
            System.out.println("Извлекаем логин и пароль нового аккаунта...");
            try {
                Locator accountBlock = page.locator("div.post-registration__data").first();
                accountBlock.waitFor(new Locator.WaitForOptions().setTimeout(5000).setState(WaitForSelectorState.VISIBLE));
                credsInfo = accountBlock.innerText().trim();
            } catch (Exception e) {
                errors.append("Не удалось извлечь креды\n");
            }

            // Копировать
            System.out.println("Кликаем 'Копировать'");
            try {
                page.locator("#js-post-reg-copy-login-password").first().click();
            } catch (Exception e) {
                jsClick(page, "#js-post-reg-copy-login-password");
                errors.append("Копирование через JS\n");
            }

            // --- КНОПКА "ОК" ---
            System.out.println("Ждём и кликаем 'ОК'...");
            try {
                Locator okButton = page.locator("div.swal2-actions button.swal2-confirm.swal2-styled:has-text('ОК')");
                okButton.waitFor(new Locator.WaitForOptions().setTimeout(8000).setState(WaitForSelectorState.VISIBLE));
                neutralizeOverlayIfNeeded(page);
                page.waitForTimeout(500);
                try {
                    okButton.first().click();
                    System.out.println("Кнопка 'ОК' нажата ✅");
                } catch (Exception e2) {
                    page.evaluate("document.querySelector('div.swal2-actions button.swal2-confirm.swal2-styled')?.click()");
                    System.out.println("Кнопка 'ОК' нажата через JS ✅");
                }
            } catch (Exception e) {
                System.out.println("⚠ Кнопка 'ОК' не найдена: " + e.getMessage());
                errors.append("Кнопка ОК не нажата\n");
            }

            // Отправка на e-mail
            System.out.println("Отправляем креды на e-mail...");
            try {
                String email = ConfigHelper.get("email");
                page.locator("a#form_mail_after_submit").first().click();
                page.locator("input.post-email__input[type='email']").fill(email);
                page.locator("button.js-post-email-content-form__btn:not([disabled])").click();
            } catch (Exception e) {
                errors.append("Ошибка при отправке e-mail\n");
            }

            // Закрытие окон и выход
            clickIfVisible(page, "#closeModal");
            clickIfVisible(page, ".arcticmodal-close.c-registration__close");
            clickIfVisible(page, "a.ap-left-nav__item_exit");
            clickIfVisible(page, "button.swal2-confirm.swal2-styled");

            waitUntilLoggedOutOrHeal(page);
            long duration = (System.currentTimeMillis() - startTime) / 1000;

            // --- Telegram summary ---
            String message = "✅ *v2_1click_registration завершён*\n\n"
                    + (credsInfo.isEmpty() ? "⚠ Логин/пароль не извлечены\n" : "🆕 *Креды:*\n```\n" + credsInfo + "\n```\n")
                    + (errors.isEmpty() ? "Без ошибок ✅" : "⚠ Ошибки:\n" + errors)
                    + "\n🕒 " + duration + " сек.\n🌐 [1xbet.kz](https://1xbet.kz)";
            tg.sendMessage(message);

        } catch (Exception e) {
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_1click_registration");
            tg.sendMessage("🚨 Критическая ошибка в *v2_1click_registration*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }
}
