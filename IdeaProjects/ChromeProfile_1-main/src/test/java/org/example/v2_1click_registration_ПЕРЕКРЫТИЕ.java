package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.util.Random;

public class v2_1click_registration_ПЕРЕКРЫТИЕ extends BaseTest {

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
        page.evaluate("(() => {" +
                "const kill = sel => document.querySelectorAll(sel).forEach(n=>{" +
                "try{n.style.pointerEvents='none';n.style.zIndex='0';n.removeAttribute('onclick');}catch(e){}" +
                "});" +
                "kill('.arcticmodal-container_i2');" +
                "kill('.arcticmodal-container_i');" +
                "kill('.v--modal-background-click');" +
                "kill('#modals-container *');" +
                "kill('#post-reg-new-overlay');" +
                "kill('section.swal2-container');" +
                "kill('div.swal2-container');" +
                "kill('swal2-container');" +
                "kill('header.l-main-inner__header');" +
                "document.querySelectorAll('*').forEach(el=>{" +
                "  const z=parseInt(window.getComputedStyle(el).zIndex)||0;" +
                "  if(z>9000){el.style.pointerEvents='none';el.style.zIndex='0';}" +
                "});" +
                "document.querySelectorAll('header').forEach(el=>{" +
                "  const z=parseInt(getComputedStyle(el).zIndex)||0;" +
                "  if(z>1000){el.style.pointerEvents='none';el.style.zIndex='0';}" +
                "});" +
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

            // --- ПЕРЕХОД В РАЗДЕЛ 'ПЛАТЕЖИ' ---
            System.out.println("Открываем раздел 'Платежи' перед регистрацией...");
            try {
                Locator paymentsLink = page.locator("a.header-topbar-widgets__link[title='Платежи']");
                paymentsLink.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                paymentsLink.first().click();
                page.waitForLoadState(LoadState.NETWORKIDLE);
                System.out.println("Раздел 'Платежи' открыт ✅");
            } catch (Exception e) {
                System.out.println("⚠ Не удалось кликнуть по 'Платежи', пробуем через JS...");
                page.evaluate("document.querySelector(\"a.header-topbar-widgets__link[title='Платежи']\")?.click()");
                page.waitForLoadState(LoadState.NETWORKIDLE);
                System.out.println("Переход в 'Платежи' через JS выполнен ✅");
            }

            // --- РЕГИСТРАЦИЯ ---
            System.out.println("Жмём 'Регистрация'");
            page.locator("button#registration-form-call").click();

            System.out.println("Ожидаем модалку регистрации");
            page.waitForSelector("div.arcticmodal-container",
                    new Page.WaitForSelectorOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
            pause(2000);

            System.out.println("Кликаем по вкладке 'В 1 клик'");
            try {
                page.locator("div.arcticmodal-container button.c-registration__tab:has-text('В 1 клик')").first().click();
            } catch (Exception e) {
                jsClick(page, "div.arcticmodal-container button.c-registration__tab:has-text('В 1 клик')");
            }

            System.out.println("Вводим рандомный промокод");
            page.fill("input#popup_registration_ref_code", randomPromo(8));

            System.out.println("Отказываемся от бонусов, затем соглашаемся");
            try {
                page.locator("div.c-registration-bonus__item.c-registration-bonus__item--close").first().click();
                page.locator("div.c-registration-bonus__item:has(.c-registration-bonus__title:has-text('Принять'))").first().click();
            } catch (Exception e) {
                errors.append("Не удалось обработать бонусы\n");
            }

            System.out.println("Жмём 'Зарегистрироваться'");
            Locator regModalButton = page.locator(
                    "div.arcticmodal-container div.c-registration__button.submit_registration:has(span.c-registration-button__label:has-text('Зарегистрироваться'))"
            );
            regModalButton.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
            regModalButton.first().click();

            // ---- ЖДЁМ РЕШЕНИЯ КАПЧИ ----
            System.out.println("Теперь решай капчу вручную — жду появления кнопки 'Копировать' (до 10 минут)...");
            page.waitForSelector("#js-post-reg-copy-login-password",
                    new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE));
            System.out.println("Капча решена ✅ — кнопка 'Копировать' появилась!");

            System.out.println("Извлекаем логин и пароль нового аккаунта...");
            Locator accountBlock = page.locator("div.post-registration__data").first();
            accountBlock.waitFor(new Locator.WaitForOptions().setTimeout(5000).setState(WaitForSelectorState.VISIBLE));
            credsInfo = accountBlock.innerText().trim();

            // --- КОПИРОВАНИЕ ---
            System.out.println("Кликаем 'Копировать'");
            try {
                neutralizeOverlayIfNeeded(page);
                page.evaluate("document.querySelectorAll('.swal2-container, .swal2-shown, #post-reg-new-overlay').forEach(e=>e.remove())");
                page.waitForTimeout(500);
                Locator copyButton = page.locator("#js-post-reg-copy-login-password").first();
                copyButton.click(new Locator.ClickOptions().setForce(true));
                System.out.println("Кнопка 'Копировать' нажата с force(true) ✅");
            } catch (Exception e) {
                System.out.println("⚠ Не удалось кликнуть 'Копировать': " + e.getMessage());
                errors.append("Ошибка при копировании\n");
            }

            // --- ПРОВЕРКА БЕЛОГО ЭКРАНА ---
            try {
                if (!page.locator("body:has-text('1xBet')").isVisible()) {
                    System.out.println("⚠ Страница побелела — перезагружаем...");
                    page.reload();
                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    System.out.println("🔄 Страница восстановлена после белого экрана");
                }
            } catch (Exception ignored) {}

            // --- КНОПКА "ОК" / CONFIRM ---
            System.out.println("Ждём и кликаем 'ОК' или любую swal2-кнопку...");
            try {
                neutralizeOverlayIfNeeded(page);
                Locator okUniversal = page.locator("button.swal2-confirm.swal2-styled");
                okUniversal.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                okUniversal.first().click(new Locator.ClickOptions().setForce(true));
                System.out.println("Кнопка 'ОК/Confirm' нажата ✅");
            } catch (Exception e) {
                page.evaluate("document.querySelectorAll('.swal2-confirm').forEach(e=>e.click())");
                System.out.println("Кнопка 'ОК/Confirm' нажата fallback через JS ✅");
            }

            // --- ОТПРАВКА НА E-MAIL ---
            System.out.println("Отправляем креды на e-mail...");
            try {
                neutralizeOverlayIfNeeded(page);
                String email = ConfigHelper.get("email");
                page.locator("a#form_mail_after_submit").first().click();
                page.locator("input.post-email__input[type='email']").fill(email);
                page.locator("button.js-post-email-content-form__btn:not([disabled])").click();
            } catch (Exception e) {
                errors.append("Ошибка при отправке e-mail\n");
            }

            clickIfVisible(page, "#closeModal");
            clickIfVisible(page, ".arcticmodal-close.c-registration__close");
            clickIfVisible(page, "a.ap-left-nav__item_exit");
            clickIfVisible(page, "button.swal2-confirm.swal2-styled");

            waitUntilLoggedOutOrHeal(page);
            long duration = (System.currentTimeMillis() - startTime) / 1000;

            tg.sendMessage("✅ *v2_1click_registration завершён*\n\n"
                    + (credsInfo.isEmpty() ? "⚠ Логин/пароль не извлечены\n" : "🆕 *Креды:*\n```\n" + credsInfo + "\n```\n")
                    + (errors.isEmpty() ? "Без ошибок ✅" : "⚠ Ошибки:\n" + errors)
                    + "\n🕒 " + duration + " сек.\n🌐 [1xbet.kz](https://1xbet.kz)");

        } catch (Exception e) {
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_1click_registration");
            tg.sendMessage("🚨 Критическая ошибка в *v2_1click_registration*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }
}
