package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.v2_MOBI_id_authorization_fastgames_ПРОЦЕСС.waitForPageOrReload;

public class v2_social_authorization extends BaseTest {

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginWithSms() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_social_authorization* стартовал (авторизация через Google + код из Google Messages)");

        try {
            // --- СТАРТ ---
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

            // --- ВХОД ---
            System.out.println("Жмём 'Войти' в шапке");
            page.click("button#login-form-call");
            page.waitForTimeout(800);

            System.out.println("Жмём кнопку входа через Google");
            page.click("a.auth-social__link--google");
            System.out.println("Пробуем определить, как открылась авторизация Google...");

// --- УНИВЕРСАЛЬНАЯ ОБРАБОТКА GOOGLE ВХОДА ---
            Page googlePage = null;
            for (int i = 0; i < 15; i++) {
                // если появилось второе окно — это popup
                if (context.pages().size() > 1) {
                    googlePage = context.pages().get(context.pages().size() - 1);
                    System.out.println("✅ Найдено всплывающее окно Google");
                    break;
                }
                // если редирект случился в текущей вкладке
                if (page.url().contains("accounts.google.com")) {
                    googlePage = page;
                    System.out.println("✅ Google авторизация открылась в той же вкладке");
                    break;
                }
                page.waitForTimeout(500);
            }

// --- ЕСЛИ GOOGLE ОКНО НЕ НАЙДЕНО ---
            if (googlePage == null) {
                System.out.println("⚠️ Окно авторизации Google не появилось — возможно, сессия уже активна.");
            } else {
                try {
                    googlePage.waitForLoadState();
                    String currentUrl = googlePage.url();
                    if (currentUrl.contains("accounts.google.com")) {
                        System.out.println("🔐 Вводим Google email и пароль");

                        String googleEmail = ConfigHelper.get("google.email");
                        String googlePassword = ConfigHelper.get("google.password");

                        if (googlePage.locator("input[type='email']").count() > 0) {
                            System.out.println("Вводим email");
                            googlePage.fill("input[type='email']", googleEmail);
                            googlePage.click("button:has-text('Далее')");
                            googlePage.waitForTimeout(1500);
                        }

                        if (googlePage.locator("input[type='password']").count() > 0) {
                            System.out.println("Вводим пароль");
                            googlePage.fill("input[type='password']", googlePassword);
                            googlePage.click("button:has-text('Далее')");
                            googlePage.waitForTimeout(2000);
                        }

                        // ждём закрытия popup или возврата на 1xbet
                        for (int i = 0; i < 60; i++) {
                            if (googlePage.isClosed()) {
                                System.out.println("Окно Google закрылось ✅");
                                break;
                            }
                            if (googlePage.url().contains("1xbet.kz")) {
                                System.out.println("Редирект из Google выполнен ✅");
                                break;
                            }
                            googlePage.waitForTimeout(500);
                        }

                    } else {
                        System.out.println("✅ Уже вошли в Google — пропускаем ввод логина/пароля");
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Ошибка при обработке окна Google: " + e.getMessage());
                }
            }

// --- ВОЗВРАЩАЕМСЯ НА 1XBET ---
            Page mainPage = null;
            for (Page p : context.pages()) {
                if (p.url().contains("1xbet.kz")) {
                    mainPage = p;
                    break;
                }
            }

            if (mainPage == null) {
                System.out.println("⚠️ Не найдено окно 1xbet — открываем заново.");
                mainPage = context.newPage();
                mainPage.navigate("https://1xbet.kz/");
            }

            page = mainPage;
            page.bringToFront();
            System.out.println("✅ Успешно вернулись на сайт 1xbet.kz");


            // --- ЖМЁМ 'ВЫСЛАТЬ КОД' ---
            System.out.println("Жмём 'Выслать код'");
            Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
            try {
                sendCodeButton.click();
                System.out.println("Кнопка 'Выслать код' нажата ✅");
            } catch (Exception e) {
                System.out.println("Первая попытка клика не удалась, пробуем через JS...");
                page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
                System.out.println("JS-клик по 'Выслать код' выполнен ✅");
            }

            // --- ЖДЁМ ПОЛЕ ДЛЯ КОДА ---
            System.out.println("Теперь решай капчу вручную — я жду поле для кода (до 10 минут)...");
            page.waitForSelector("input.phone-sms-modal-code__input",
                    new Page.WaitForSelectorOptions()
                            .setTimeout(600_000)
                            .setState(WaitForSelectorState.VISIBLE)
            );
            System.out.println("Поле для кода появилось! Достаём код из Google Messages...");


            // --- GOOGLE MESSAGES ---
            System.out.println("📨 Открываем Google Messages с уже сохранённой сессией...");
            Page messagesPage = context.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");
            messagesPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            messagesPage.waitForTimeout(3000);
            ensureGoogleMessagesConnected(messagesPage);

            if (messagesPage.url().contains("welcome")) {
                throw new RuntimeException("⚠️ Не авторизованы в Google Messages! Нужно один раз вручную отсканировать QR.");
            }

            System.out.println("⌛ Ждём появления списка чатов...");
            boolean chatsLoaded = false;
            for (int i = 0; i < 20; i++) {
                if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                    chatsLoaded = true;
                    break;
                }
                messagesPage.waitForTimeout(1000);
            }
            if (!chatsLoaded)
                throw new RuntimeException("❌ Чаты не появились в Google Messages — возможно, не успели подгрузиться.");
            System.out.println("✅ Список чатов успешно найден");

            System.out.println("🔍 Ищем чат с 1xBet...");
            Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
            if (chat.count() == 0) chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.first().click();
            System.out.println("💬 Чат открыт");
            messagesPage.waitForTimeout(3000);

            System.out.println("📩 Ищем последнее сообщение...");
            Locator messageNodes = messagesPage.locator("div.text-msg-content div.text-msg.msg-content div.ng-star-inserted");
            int count = 0;
            for (int i = 0; i < 15; i++) {
                count = messageNodes.count();
                if (count > 0) break;
                messagesPage.waitForTimeout(1000);
            }
            if (count == 0)
                throw new RuntimeException("❌ Не найдено сообщений внутри чата!");
            String lastMessageText = messageNodes.nth(count - 1).innerText().trim();
            System.out.println("📨 Последнее сообщение: " + lastMessageText);

            Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b").matcher(lastMessageText);
            String code = matcher.find() ? matcher.group() : null;
            if (code == null)
                throw new RuntimeException("❌ Код подтверждения не найден в сообщении!");
            System.out.println("✅ Извлечённый код: " + code);

            System.out.println("Возвращаемся на сайт 1xbet.kz");
            page.bringToFront();

            System.out.println("Вводим код подтверждения");
            page.fill("input.phone-sms-modal-code__input", code);

            System.out.println("Жмём 'Подтвердить'");
            page.click("button:has-text('Подтвердить')");
            System.out.println("Авторизация завершена ✅");

            // --- ЛИЧНЫЙ КАБИНЕТ ---
            System.out.println("Открываем 'Личный кабинет'");
            page.waitForTimeout(800);
            page.click("a.header-lk-box-link[title='Личный кабинет']");

            System.out.println("Пробуем закрыть popup-крестик после входа в ЛК (если он вообще есть)");
            try {
                Locator closeCrossLk = page.locator("div.box-modal_close.arcticmodal-close");
                closeCrossLk.waitFor(new Locator.WaitForOptions().setTimeout(2000).setState(WaitForSelectorState.ATTACHED));
                if (closeCrossLk.isVisible()) {
                    closeCrossLk.click();
                    System.out.println("Крестик в ЛК найден и нажат ✅");
                } else {
                    System.out.println("Крестика в ЛК нет — идём дальше");
                }
            } catch (Exception e) {
                System.out.println("Всплывашки в ЛК или крестика нет, игнорируем и двигаемся дальше");
            }

            // --- ВЫХОД ---
            System.out.println("Жмём 'Выход'");
            page.waitForTimeout(800);
            page.click("a.ap-left-nav__item_exit");

            System.out.println("Подтверждаем выход кнопкой 'ОК'");
            page.waitForTimeout(800);
            page.click("button.swal2-confirm.swal2-styled");

            System.out.println("Выход завершён ✅ (браузер остаётся открытым)");

            // --- ФИНАЛ / ТГ ---
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage(
                    "✅ *Тест успешно завершён:* v2_social_authorization\n" +
                            "• Авторизация — через Google выполнена\n" +
                            "• Код из Google Messages — получен и применён\n" +
                            "• Личный кабинет — проверен\n" +
                            "• Выход — произведён\n\n" +
                            "🕒 Время выполнения: *" + duration + " сек.*\n" +
                            "🌐 Сайт: [1xbet.kz](https://1xbet.kz)\n" +
                            "_Браузер остаётся открытым для ручной проверки._"
            );

        } catch (Exception e) {
            System.out.println("❌ Ошибка в тесте: " + e.getMessage());
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_social_authorization");
            tg.sendMessage("🚨 Ошибка в тесте *v2_social_authorization*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }
}
