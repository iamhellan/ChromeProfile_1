package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static org.example.v2_MOBI_id_authorization_fastgames_ПРОЦЕСС.waitForPageOrReload;

public class v2_number_authorization extends BaseTest {

    @Test
    void loginByPhoneAndPassword() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_number_authorization* стартовал (авторизация по номеру с Google Messages)");

        try {
            System.out.println("Открываем сайт 1xbet.kz");
            page.navigate("https://1xbet.kz/?whn=mobile&platform_type=desktop");
            waitForPageOrReload(10000);

// ---- ПРОВЕРКА: авторизован ли аккаунт ----
            System.out.println("Пробуем определить, авторизован ли аккаунт...");

            try {
                Locator lkButton = page.locator("a.header-lk-box-link[title='Личный кабинет']");
                lkButton.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                System.out.println("Кнопка 'Личный кабинет' найдена ✅");

                lkButton.click();
                page.waitForTimeout(2000);

                Locator logoutButton = page.locator("a.ap-left-nav__item_exit");
                if (logoutButton.isVisible()) {
                    System.out.println("🔹 Аккаунт уже авторизован — выполняем выход...");
                    logoutButton.click();

                    page.waitForTimeout(1000);
                    page.click("button.swal2-confirm.swal2-styled");
                    page.waitForTimeout(2000);

                    System.out.println("✅ Успешно вышли из аккаунта, продолжаем сценарий входа...");
                } else {
                    System.out.println("ℹ️ Меню выхода не найдено — вероятно, пользователь не был авторизован.");
                }

            } catch (PlaywrightException e) {
                System.out.println("⏳ Кнопка 'Личный кабинет' не появилась или не кликабельна. Продолжаем сценарий входа.");
            }


            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(1000);
            page.click("button#login-form-call");

            System.out.println("Выбираем метод входа по телефону");
            page.waitForTimeout(1000);
            page.click("button.c-input-material__custom.custom-functional-button");

            // --- Креды из config.properties ---
            String phone = ConfigHelper.get("phone");
            String password = ConfigHelper.get("password");

            System.out.println("Вводим номер телефона: " + phone);
            page.fill("input.phone-input__field[type='tel']", phone);

            System.out.println("Вводим пароль");
            page.fill("input[type='password']", password);

            System.out.println("Жмём 'Войти' в форме авторизации");
            page.waitForTimeout(1000);
            page.click("button.auth-button.auth-button--block.auth-button--theme-secondary");

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
            System.out.println("Жмём 'Выслать код'");
            Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
            try {
                sendCodeButton.click();
                System.out.println("Кнопка 'Выслать код' нажата ✅");
            } catch (Exception e) {
                System.out.println("Первая попытка клика не удалась, пробуем через JS...");
                page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
            }

            // ---- ЖДЁМ ПОЛЕ ДЛЯ КОДА ----
            System.out.println("Теперь решай капчу вручную — я жду поле для кода (до 10 минут)...");
            try {
                page.waitForSelector("input.phone-sms-modal-code__input",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Поле для кода появилось! Достаём код из Google Messages...");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Поле для ввода кода не появилось — капча не решена или что-то пошло не так!");
            }

            // --- GOOGLE MESSAGES (через persistent context из BaseTest) ---
            System.out.println("📨 Открываем Google Messages с уже сохранённой сессией...");
            Page messagesPage = context.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");
            ensureGoogleMessagesConnected(messagesPage);
            messagesPage.waitForTimeout(2000);

            // --- Открываем первый чат ---
            Locator chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.click();
            messagesPage.waitForTimeout(1000);

            // --- Берём последнее сообщение ---
            Locator messageNodes = messagesPage.locator(
                    "mws-message-part-content div.text-msg-content div.text-msg.msg-content div.ng-star-inserted"
            );
            int count = messageNodes.count();
            if (count == 0)
                throw new RuntimeException("Сообщения не найдены! (Проверь, что чат не пуст и аккаунт авторизован)");

            String smsText = messageNodes.nth(count - 1).innerText();
            System.out.println("Содержимое последнего SMS: " + smsText);

            // --- Извлекаем код подтверждения ---
            Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b").matcher(smsText);
            String code = matcher.find() ? matcher.group() : null;
            if (code == null)
                throw new RuntimeException("Код подтверждения не найден в сообщении!");
            System.out.println("Извлечённый код подтверждения: " + code);

            // --- Вводим код ---
            page.bringToFront();
            System.out.println("Вводим код подтверждения");
            page.fill("input.phone-sms-modal-code__input", code);

            System.out.println("Жмём 'Подтвердить'");
            page.click("button:has-text('Подтвердить')");
            System.out.println("Авторизация завершена ✅");

            // --- ЛИЧНЫЙ КАБИНЕТ ---
            System.out.println("Открываем 'Личный кабинет'");
            page.waitForTimeout(1000);
            page.click("a.header-lk-box-link[title='Личный кабинет']");

            // --- ЗАКРЫТИЕ ПОПАПА ---
            System.out.println("Пробуем закрыть popup-крестик после входа в ЛК (если есть)");
            try {
                Locator closeCrossLk = page.locator("div.box-modal_close.arcticmodal-close");
                closeCrossLk.waitFor(new Locator.WaitForOptions().setTimeout(2000).setState(WaitForSelectorState.ATTACHED));
                if (closeCrossLk.isVisible()) {
                    closeCrossLk.click();
                    System.out.println("Крестик найден и закрыт ✅");
                }
            } catch (Exception ignored) {}

            // --- ВЫХОД ---
            System.out.println("Жмём 'Выход'");
            page.waitForTimeout(1000);
            page.click("a.ap-left-nav__item_exit");

            System.out.println("Подтверждаем выход кнопкой 'ОК'");
            page.waitForTimeout(1000);
            page.click("button.swal2-confirm.swal2-styled");

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage(
                    "✅ *Тест успешно завершён:* v2_number_authorization\n" +
                            "• Авторизация — выполнена\n" +
                            "• Код из Google Messages — получен\n" +
                            "• Личный кабинет — проверен\n" +
                            "• Выход — произведён\n\n" +
                            "🕒 Время выполнения: *" + duration + " сек.*"
            );

        } catch (Exception e) {
            System.out.println("❌ Ошибка в тесте: " + e.getMessage());
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_number_authorization");
            tg.sendMessage("🚨 Ошибка в тесте *v2_number_authorization*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }
}
