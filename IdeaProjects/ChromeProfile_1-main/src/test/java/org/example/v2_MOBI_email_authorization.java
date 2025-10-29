package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.BaseTest.ensureGoogleMessagesConnected;
import static org.example.v2_MOBI_id_authorization_and_bet.tgStep;

public class v2_MOBI_email_authorization extends BaseTest {

    @Test
    void loginWithEmailAndSms() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_email_authorization* стартовал (авторизация по Email + SMS)");

        String screenshotPath = null;

        try {
            System.out.println("Открываем мобильную версию сайта 1xbet.kz");
            page.navigate("https://1xbet.kz/?platform_type=mobile");

            // --- Проверяем авторизацию ---
            System.out.println("Пробуем определить, авторизован ли аккаунт...");
            try {
                Locator profileButton = page.locator("button.user-header__link.header__link.header__reg.header__reg_ico.ion-android-person");
                profileButton.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                System.out.println("Кнопка 'Личный кабинет' найдена ✅");

                profileButton.click();
                page.waitForTimeout(2000);

                Locator logoutButton = page.locator("button.drop-menu-list__link_exit");
                if (logoutButton.isVisible()) {
                    System.out.println("🔹 Аккаунт уже авторизован — выполняем выход...");
                    logoutButton.click();
                    page.waitForTimeout(1000);
                    page.click("button.swal2-confirm.swal2-styled");
                    page.waitForTimeout(2000);
                } else {
                    System.out.println("ℹ️ Пользователь не авторизован, продолжаем вход.");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Кнопка 'Личный кабинет' не найдена, продолжаем сценарий входа.");
            }

            // --- Авторизация ---
            System.out.println("Жмём 'Войти' в шапке");
            page.click("button#curLoginForm >> text=Войти");

            System.out.println("Вводим Email");
            page.fill("input#auth_id_email", ConfigHelper.get("email"));

            System.out.println("Вводим пароль");
            page.fill("input#auth-form-password", ConfigHelper.get("password"));

            System.out.println("Жмём кнопку 'Войти'");
            page.click("button.auth-button:has(span.auth-button__text:has-text('Войти'))");

            // ---- ЖДЁМ РЕШЕНИЯ КАПЧИ ----
            System.out.println("Теперь решай капчу вручную — жду появления кнопки 'Выслать код' (до 10 минут)...");
            page.waitForSelector("button:has-text('Выслать код')",
                    new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE)
            );
            System.out.println("Кнопка 'Выслать код' появилась ✅");

            System.out.println("Жмём 'Выслать код'");
            page.click("button:has-text('Выслать код')");

            System.out.println("Ждём поле для ввода кода...");
            page.waitForSelector("input.phone-sms-modal-code__input",
                    new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE)
            );
            System.out.println("Поле появилось ✅");

            // --- Google Messages ---
            System.out.println("📨 Открываем Google Messages с сохранённой сессией...");
            Page messagesPage = context.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");
            messagesPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            messagesPage.waitForTimeout(3000);

            ensureGoogleMessagesConnected(messagesPage);

            if (messagesPage.url().contains("welcome")) {
                throw new RuntimeException("⚠️ Не авторизованы в Google Messages! Нужно отсканировать QR.");
            }

            System.out.println("⌛ Ждём появления списка чатов...");
            for (int i = 0; i < 20 && messagesPage.locator("mws-conversation-list-item").count() == 0; i++)
                messagesPage.waitForTimeout(1000);
            System.out.println("✅ Чаты найдены");

            Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
            if (chat.count() == 0) chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.first().click();
            messagesPage.waitForTimeout(2000);

            Locator messages = messagesPage.locator("div.text-msg-content div.text-msg.msg-content div.ng-star-inserted");
            int count = messages.count();
            String text = messages.nth(count - 1).innerText().trim();
            System.out.println("📨 Последнее сообщение: " + text);

            Matcher m = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b").matcher(text);
            String code = m.find() ? m.group() : null;
            if (code == null) throw new RuntimeException("❌ Код не найден!");
            System.out.println("✅ Код: " + code);

            // --- Возврат на сайт ---
            page.bringToFront();
            page.fill("input.phone-sms-modal-code__input", code);
            page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");
            page.waitForTimeout(3000);
            System.out.println("Авторизация завершена ✅");

            tg.sendMessage("📬 Код из Google Messages: *" + code + "*");

            // --- Завершение / Выход ---
            System.out.println("Пробуем выполнить выход...");
            try {
                Locator menu = page.locator("button.user-header__link.header__link--messages");
                menu.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                menu.click();

                Locator logout = page.locator("button.drop-menu-list__link_exit:has-text('Выход')");
                logout.click();

                page.locator("button.swal2-confirm.swal2-styled:has-text('ОК')").click();
                tgStep("Выход выполнен успешно", true);
            } catch (Exception e) {
                tgStep("⚠️ Ошибка при выходе: " + e.getMessage(), false);
            }

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage("🎯 *Тест завершён успешно*\n🕒 Время выполнения: *" + duration + " сек.* ✅");
            System.out.println("Тест завершён ✅");

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_email_authorization_error");
            tg.sendMessage("🚨 Ошибка в *v2_MOBI_email_authorization*:\n" + e.getMessage());
            if (screenshotPath != null && !screenshotPath.isEmpty())
                tg.sendPhoto(screenshotPath, "Скриншот ошибки");
        }
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
