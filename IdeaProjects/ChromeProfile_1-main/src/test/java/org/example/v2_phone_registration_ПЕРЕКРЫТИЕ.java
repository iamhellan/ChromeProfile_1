package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Paths;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_phone_registration_ПЕРЕКРЫТИЕ extends BaseTest {

    // ---------- ЛАЙТ-БОТВАП: вызывать BaseTest.setUpAll() один раз ----------
    @BeforeAll
    static void beforeAllFix() {
        try {
            if (BaseTest.class.getDeclaredField("context").get(null) != null) {
                System.out.println("🧩 Контекст уже активен, повторная инициализация не требуется ✅");
                return;
            }
            java.lang.reflect.Method method = BaseTest.class.getDeclaredMethod("setUpAll");
            method.setAccessible(true);
            method.invoke(null);
            System.out.println("🧩 BaseTest.setUpAll() вызван вручную ✅");
        } catch (NoSuchFieldException ignored) {
            try {
                java.lang.reflect.Method method = BaseTest.class.getDeclaredMethod("setUpAll");
                method.setAccessible(true);
                method.invoke(null);
                System.out.println("🧩 BaseTest.setUpAll() вызван вручную ✅");
            } catch (Exception e) {
                throw new RuntimeException("❌ Не удалось вызвать BaseTest.setUpAll(): " + e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new RuntimeException("❌ Не удалось вызвать BaseTest.setUpAll(): " + e.getMessage(), e);
        }
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
        page.evaluate("(() => { " +
                "const kill = sel => document.querySelectorAll(sel).forEach(n=>{try{n.style.pointerEvents='none';n.style.zIndex='0';n.removeAttribute('onclick');}catch(e){}});" +
                "kill('.arcticmodal-container_i2');" +
                "kill('.arcticmodal-container_i');" +
                "kill('.v--modal-background-click');" +
                "kill('#modals-container *');" +
                "kill('.pf-main-container-wrapper-th-4 *');" +
                "kill('#post-reg-new-overlay');" +
                "document.querySelectorAll('div').forEach(el=>{const z=parseInt(getComputedStyle(el).zIndex)||0;if(z>9000){el.style.pointerEvents='none';el.style.zIndex='0';}});" +
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

    static String extractDigits(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("\\b\\d{4,8}\\b").matcher(text);
        return m.find() ? m.group() : null;
    }

    // ---------- УСТОЙЧИВЫЙ SweetAlert2 ----------
    static void clickSwalOk(Page page) {
        System.out.println("Пробуем нажать 'ОК' (усиленный вариант SweetAlert2)...");

        try {
            // 1️⃣ Дожидаемся появления окна
            page.waitForSelector("div.swal2-popup.swal2-modal",
                    new Page.WaitForSelectorOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
            page.waitForTimeout(400); // дождаться анимации

            // 2️⃣ Снимаем все перекрытия
            page.evaluate("() => { document.querySelectorAll('*').forEach(el => { if (parseInt(getComputedStyle(el).zIndex) > 1000) el.style.pointerEvents = 'none'; }); }");

            // 3️⃣ Находим кнопку и пробуем обычный клик
            Locator okBtn = page.locator("button.swal2-confirm.swal2-styled");
            if (okBtn.count() == 0) {
                System.out.println("❔ Кнопка 'ОК' не найдена");
                return;
            }

            boolean clicked = false;
            for (int i = 0; i < 5; i++) {
                try {
                    okBtn.first().click(new Locator.ClickOptions().setTimeout(2000));
                    clicked = true;
                    break;
                } catch (Exception e) {
                    page.evaluate("document.querySelector('button.swal2-confirm.swal2-styled')?.click()");
                    page.waitForTimeout(300);
                }
            }

            if (clicked) System.out.println("Кнопка 'ОК' нажата ✅");
            else System.out.println("⚠ Не удалось нажать кнопку 'ОК' даже через JS");

            // 4️⃣ Ждём закрытия popup
            page.waitForSelector("div.swal2-popup.swal2-modal",
                    new Page.WaitForSelectorOptions().setTimeout(8000).setState(WaitForSelectorState.DETACHED));

        } catch (Exception e) {
            System.out.println("⚠ Ошибка clickSwalOk: " + e.getMessage());
        }
    }


    // ---------- ТЕСТ ----------
    @Test
    void v2_registration_by_phone() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_phone_registration* стартовал (регистрация по номеру телефона)");

        StringBuilder errors = new StringBuilder();
        String credsInfo = "";
        String smsCode = null;

        try {
            System.out.println("Открываем сайт 1xbet.kz (десктоп)");
            page.navigate("https://1xbet.kz/");
            pauseMedium();

            // --- РЕГИСТРАЦИЯ ---
            System.out.println("Жмём 'Регистрация'");
            try {
                page.locator("button#registration-form-call").click();
            } catch (Exception e) {
                errors.append("Не удалось нажать 'Регистрация'\n");
            }

            System.out.println("Ожидаем модалку регистрации");
            page.waitForSelector("div.arcticmodal-container",
                    new Page.WaitForSelectorOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
            pause(1500);

            // --- ВКЛАДКА "ПО ТЕЛЕФОНУ" ---
            System.out.println("Переходим на вкладку 'По телефону'");
            try {
                page.locator("div.arcticmodal-container button.c-registration__tab:has-text('По телефону')").first().click();
            } catch (Exception e) {
                jsClick(page, "div.arcticmodal-container button.c-registration__tab:has-text('По телефону')");
            }

            // --- БОНУСЫ ---
            System.out.println("Обрабатываем бонусы (отказ → принять)");
            try {
                page.locator("div.c-registration-bonus__item.c-registration-bonus__item--close").first().click();
                page.locator("div.c-registration-bonus__item:has(.c-registration-bonus__title:has-text('Принять'))").first().click();
            } catch (Exception e) {
                errors.append("Не удалось обработать бонусы\n");
            }

            // --- ВВОД ТЕЛЕФОНА ---
            System.out.println("Вводим номер телефона из config.properties");
            try {
                String phone = ConfigHelper.get("phone");
                Locator phoneInput = page.locator("input[type='tel'].phone-input__field").first();
                phoneInput.waitFor(new Locator.WaitForOptions().setTimeout(8000).setState(WaitForSelectorState.VISIBLE));
                phoneInput.fill(phone);
            } catch (Exception e) {
                errors.append("Ошибка при вводе номера телефона\n");
            }

            // --- ЖДЁМ РЕШЕНИЯ КАПЧИ ---
            System.out.println("Теперь решай капчу вручную — я жду доступность кнопки 'Отправить sms' (до 10 минут)...");
            try {
                page.waitForSelector("button#button_send_sms:not([disabled])",
                        new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE));
                System.out.println("Капча решена ✅ — 'Отправить sms' доступна!");
            } catch (Exception e) {
                errors.append("Капча не решена вовремя (кнопка 'Отправить sms' не активна)\n");
            }

            // --- ОТПРАВИТЬ SMS ---
            System.out.println("Жмём 'Отправить sms'");
            try {
                Locator sendSms = page.locator("button#button_send_sms");
                sendSms.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                try {
                    sendSms.click();
                } catch (Exception e) {
                    System.out.println("Первая попытка не удалась, кликаем через JS");
                    jsClick(page, "button#button_send_sms");
                }

                // Попап (если всплывёт) — безопасный клик
                page.waitForTimeout(1000);
                if (page.locator("div.swal2-popup.swal2-modal, div.swal2-container").count() > 0) {
                    clickSwalOk(page);
                } else {
                    System.out.println("SweetAlert2 не появился — продолжаем ✅");
                }
            } catch (Exception e) {
                errors.append("Не удалось нажать 'Отправить sms'\n");
            }

            // --- GOOGLE MESSAGES ---
            System.out.println("--- Google Messages: открываем сохранённую сессию и достаём код ---");
            try (BrowserContext messagesContext = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(Paths.get("messages-session.json"))
            )) {
                Page messagesPage = messagesContext.newPage();
                messagesPage.navigate("https://messages.google.com/web/conversations");

                Locator chat = messagesPage.locator("mws-conversation-list-item").first();
                chat.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                chat.click();
                messagesPage.waitForTimeout(1000);

                String text = null;
                try {
                    Locator lastMsg = messagesPage.locator("div.text-msg.msg-content div.ng-star-inserted").last();
                    if (lastMsg.count() > 0) text = lastMsg.innerText();
                } catch (Exception ignored) {}

                if (text == null || extractDigits(text) == null) {
                    String whole = messagesPage.locator("body").innerText();
                    smsCode = extractDigits(whole);
                } else {
                    smsCode = extractDigits(text);
                }

                if (smsCode == null) throw new RuntimeException("Не удалось извлечь код из Google Messages");
                System.out.println("Код из SMS: " + smsCode);
            } catch (Exception e) {
                errors.append("Ошибка при получении кода из Google Messages: ").append(e.getMessage()).append("\n");
            }

            // --- ВВОД КОДА ---
            System.out.println("Вводим код подтверждения и жмём 'Подтвердить'");
            try {
                Locator codeInput = page.locator("#popup_registration_phone_confirmation").first();
                codeInput.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                codeInput.fill(smsCode);

                Locator confirmBtn = page.locator("button.confirm_sms.reg_button_sms.c-registration__button--inside").first();
                try {
                    confirmBtn.click();
                } catch (Exception e) {
                    jsClick(page, "button.confirm_sms.reg_button_sms.c-registration__button--inside");
                }
            } catch (Exception e) {
                errors.append("Не удалось ввести/подтвердить SMS-код\n");
            }

            // --- ПРОМОКОД ---
            System.out.println("Вводим рандомный промокод");
            try {
                String promo = randomPromo(8);
                page.fill("#popup_registration_ref_code", promo);
            } catch (Exception e) {
                errors.append("Ошибка при вводе промокода\n");
            }

            // --- ЗАРЕГИСТРИРОВАТЬСЯ ---
            System.out.println("Жмём 'Зарегистрироваться'");
            try {
                Locator regBtn = page.locator("div.c-registration__button.submit_registration span.c-registration-button__label:has-text('Зарегистрироваться')");
                regBtn.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                try {
                    regBtn.first().click();
                } catch (Exception e) {
                    jsClick(page, "div.c-registration__button.submit_registration span.c-registration-button__label");
                }
            } catch (Exception e) {
                errors.append("Не удалось нажать 'Зарегистрироваться'\n");
            }

            // --- ЖДЁМ КНОПКУ "КОПИРОВАТЬ" ---
            System.out.println("Ждём завершения пост-регистрации — кнопку 'Копировать' (до 10 минут)...");
            try {
                page.waitForSelector("#js-post-reg-copy-login-password",
                        new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE));
                System.out.println("Пост-регистрационное окно готово ✅");
            } catch (PlaywrightException e) {
                errors.append("Пост-регистрационное окно не появилось вовремя\n");
            }

            // --- ИЗВЛЕКАЕМ КРЕДЫ ---
            System.out.println("Извлекаем логин и пароль нового аккаунта…");
            try {
                Locator accountBlock = page.locator("div.post-registration__data").first();
                accountBlock.waitFor(new Locator.WaitForOptions().setTimeout(8000).setState(WaitForSelectorState.VISIBLE));
                credsInfo = accountBlock.innerText().trim();
            } catch (Exception e) {
                errors.append("Не удалось извлечь креды\n");
            }

            // --- КОПИРОВАТЬ ---
            System.out.println("Кликаем 'Копировать'");
            try {
                neutralizeOverlayIfNeeded(page);
                page.waitForTimeout(400);
                Locator copyButton = page.locator("#js-post-reg-copy-login-password").first();
                copyButton.waitFor(new Locator.WaitForOptions().setTimeout(8000).setState(WaitForSelectorState.VISIBLE));
                try {
                    copyButton.click();
                    clickSwalOk(page);
                } catch (Exception e) {
                    page.evaluate("document.querySelector('#js-post-reg-copy-login-password')?.click()");
                }
                clickSwalOk(page);
            } catch (Exception e) {
                errors.append("Кнопка 'Копировать' не нажата\n");
            }

            // --- ВЫХОД ---
            clickIfVisible(page, "#closeModal");
            clickIfVisible(page, ".arcticmodal-close.c-registration__close");
            clickIfVisible(page, "a.ap-left-nav__item_exit");
            clickIfVisible(page, "button.swal2-confirm.swal2-styled");

            waitUntilLoggedOutOrHeal(page);

            long duration = (System.currentTimeMillis() - startTime) / 1000;

            String message = "✅ *v2_phone_registration завершён*\n\n"
                    + (credsInfo.isEmpty() ? "⚠ Логин/пароль не извлечены\n" : "🆕 *Креды:*\n```\n" + credsInfo + "\n```\n")
                    + (smsCode == null ? "⚠ SMS-код не найден в Google Messages\n" : "🔐 Код из SMS: `" + smsCode + "`\n")
                    + (errors.isEmpty() ? "Без ошибок ✅" : "⚠ Ошибки:\n" + errors)
                    + "\n🕒 " + duration + " сек.\n🌐 1xbet.kz";
            tg.sendMessage(message);

        } catch (Exception e) {
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_phone_registration");
            tg.sendMessage("🚨 Критическая ошибка в *v2_phone_registration*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }
}
