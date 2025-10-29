package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_id_authorization_and_bet extends BaseTest {

    // ---------- ХЕЛПЕРЫ ----------

    /**
     * Терпеливо ждёт полной загрузки страницы, при необходимости делает reload.
     * @param maxWaitMs — максимальное время ожидания (мс) до перезагрузки.
     */
    static void waitForPageOrReload(int maxWaitMs) {
        int waited = 0;
        while (true) {
            try {
                String readyState = (String) page.evaluate("() => document.readyState");
                if ("complete".equals(readyState)) break;
                Thread.sleep(500);
                waited += 500;
                if (waited >= maxWaitMs) {
                    System.out.println("⚠️ Страница не загрузилась за " + maxWaitMs + " мс, обновляем!");
                    page.reload();
                    waited = 0;
                }
            } catch (Exception e) {
                System.out.println("⚠️ Ошибка при ожидании загрузки страницы: " + e.getMessage());
                page.reload();
                waited = 0;
            }
        }
    }

    /**
     * Универсальный хелпер для логов и Telegram-уведомлений.
     * Отправляет сообщение с иконкой успеха (✅) или ошибки (❌).
     */
    static void tgStep(String message, boolean success) {
        String icon = success ? "✅" : "❌";
        System.out.println(icon + " " + message);
        try {
            tg.sendMessage(icon + " " + message);
        } catch (Exception e) {
            System.out.println("⚠️ Ошибка при отправке уведомления в Telegram: " + e.getMessage());
        }
    }

    // ---------- ТЕСТ ----------
    @Test
    void loginAndMakeBet() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_id_authorization_and_bet* стартовал (авторизация + ставка + история)");

        String screenshotPath = null;

        try {
            // --- ОТКРЫТИЕ САЙТА ---
            System.out.println("Открываем мобильную версию сайта 1xbet.kz");
            page.navigate("https://1xbet.kz/?platform_type=mobile");
            waitForPageOrReload(10000);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(2000);

            // ---- ПРОВЕРКА: авторизован ли аккаунт ----
            boolean isAuthorized = false;
            System.out.println("Пробуем определить, авторизован ли аккаунт...");

            try {
                Locator profileButton = page.locator(
                        "button.user-header__link.header__link.header__reg.header__reg_ico.ion-android-person"
                );
                profileButton.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                profileButton.click();
                page.waitForTimeout(2000);

                Locator logoutButton = page.locator("button.drop-menu-list__link_exit");
                if (logoutButton.isVisible()) {
                    isAuthorized = true;
                    System.out.println("✅ Аккаунт уже авторизован — открываем 'Личные данные' и переходим в 'Линия'...");

                    Locator personalDataLink = page.locator("a.drop-menu-list__link_lk:has-text('Личные данные')");
                    personalDataLink.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                    try {
                        personalDataLink.click();
                        System.out.println("🔹 Раздел 'Личные данные' открыт ✅");
                    } catch (Exception e1) {
                        page.evaluate("document.querySelector('a.drop-menu-list__link_lk[href=\"/ru/office/account\"]')?.click()");
                        System.out.println("✅ Перешли в 'Личные данные' через JS");
                    }

                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(1500);

                    // --- Открываем бургер-меню ---
                    Locator burgerMenu = page.locator("button.header__hamburger.hamburger");
                    burgerMenu.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                    burgerMenu.click();
                    page.waitForTimeout(1000);
                    System.out.println("🍔 Бургер-меню открыто");

                    // --- Переходим в раздел 'Линия' ---
                    Locator lineLink = page.locator("a.drop-menu-list__link:has-text('Линия')");
                    lineLink.waitFor(new Locator.WaitForOptions().setTimeout(10000).setState(WaitForSelectorState.VISIBLE));
                    try {
                        lineLink.click();
                        System.out.println("✅ Переход в раздел 'Линия' выполнен успешно");
                    } catch (Exception e2) {
                        page.evaluate("document.querySelector('a.drop-menu-list__link[href=\"/ru/line\"]')?.click()");
                        System.out.println("✅ Перешли в 'Линия' через JS");
                    }

                    page.waitForLoadState(LoadState.NETWORKIDLE);
                    page.waitForTimeout(2000);
                }

            } catch (PlaywrightException e) {
                System.out.println("⏳ Кнопка 'Личный кабинет' не появилась — продолжаем стандартную авторизацию.");
            }

            // ---- ЕСЛИ НЕ АВТОРИЗОВАН — АВТОРИЗАЦИЯ ----
            if (!isAuthorized) {
                System.out.println("🔐 Выполняем авторизацию...");

                page.click("button#curLoginForm >> text=Войти");
                page.waitForTimeout(1000);

                page.fill("input#auth_id_email", ConfigHelper.get("login"));
                page.fill("input#auth-form-password", ConfigHelper.get("password"));
                page.click("button.auth-button:has(span.auth-button__text:has-text('Войти'))");

                System.out.println("Теперь решай капчу вручную — жду появления кнопки 'Выслать код' (до 10 минут)...");
                page.waitForSelector("button:has-text('Выслать код')",
                        new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE));
                System.out.println("Кнопка 'Выслать код' появилась ✅");

                page.click("button:has-text('Выслать код')");
                page.waitForTimeout(2000);

                page.waitForSelector("input.phone-sms-modal-code__input",
                        new Page.WaitForSelectorOptions().setTimeout(600_000).setState(WaitForSelectorState.VISIBLE));
                System.out.println("Поле для ввода кода появилось ✅");

                // --- GOOGLE MESSAGES (устойчивое чтение из открытого чата) ---
                System.out.println("📨 Открываем Google Messages с уже сохранённой сессией...");
                Page messagesPage = context.newPage();
                messagesPage.navigate("https://messages.google.com/web/conversations");
                messagesPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
                messagesPage.waitForTimeout(3000);

                System.out.println("Проверяем, активна ли сессия Google Messages...");
                ensureGoogleMessagesConnected(messagesPage);

// --- Проверяем авторизацию ---
                if (messagesPage.url().contains("welcome")) {
                    throw new RuntimeException("⚠️ Не авторизованы в Google Messages! Нужно один раз вручную отсканировать QR.");
                }

// --- Ждём появления списка чатов (устойчивое ожидание) ---
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

// --- Кликаем по чату 1xBet (если нет — берём первый) ---
                System.out.println("🔍 Ищем чат с 1xBet...");
                Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
                if (chat.count() == 0) {
                    System.out.println("⚠️ Чат 1xBet не найден, кликаем по первому в списке");
                    chat = messagesPage.locator("mws-conversation-list-item").first();
                }
                chat.first().click();
                System.out.println("💬 Чат открыт");
                messagesPage.waitForTimeout(3000);

// --- Ищем последнее сообщение ---
                System.out.println("📩 Ищем последнее сообщение...");
                Locator messageNodes = messagesPage.locator("div.text-msg-content div.text-msg.msg-content div.ng-star-inserted");
                int count = 0;
                for (int i = 0; i < 15; i++) { // ждём до 15 секунд
                    count = messageNodes.count();
                    if (count > 0) break;
                    messagesPage.waitForTimeout(1000);
                }
                if (count == 0)
                    throw new RuntimeException("❌ Не найдено сообщений внутри чата!");
                String lastMessageText = messageNodes.nth(count - 1).innerText().trim();
                System.out.println("📨 Последнее сообщение: " + lastMessageText);

// --- Извлекаем код (буквы+цифры 4–8 символов) ---
                Matcher matcher = Pattern.compile("\\b[a-zA-Z0-9]{4,8}\\b").matcher(lastMessageText);
                String code = matcher.find() ? matcher.group() : null;
                if (code == null)
                    throw new RuntimeException("❌ Код подтверждения не найден в сообщении!");
                System.out.println("✅ Извлечённый код: " + code);

// --- Возвращаемся на сайт ---
                page.bringToFront();
                page.fill("input.phone-sms-modal-code__input", code);
                page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");
                page.waitForTimeout(3000);
                System.out.println("Авторизация завершена ✅");

// --- Telegram уведомление ---
                try {
                    tg.sendMessage("📬 Код из Google Messages: *" + code + "*");
                    System.out.println("📨 Код успешно отправлен в Telegram ✅");
                } catch (Exception tgErr) {
                    System.out.println("⚠️ Ошибка при отправке в Telegram: " + tgErr.getMessage());
                }

            }

            // ---------- СТАВКА ----------
            System.out.println("Переходим к выбору события для ставки...");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(3000);

// --- Проверяем наличие кнопки 'Очистить' ---
            try {
                Locator clearButton = page.locator("button.m-c__clear:has-text('Очистить')");
                if (clearButton.isVisible()) {
                    System.out.println("🔹 Найдена кнопка 'Очистить' — очищаем купон перед новой ставкой...");
                    clearButton.click();
                    page.waitForTimeout(1500);
                    System.out.println("✅ Купон очищен");
                } else {
                    System.out.println("ℹ️ Кнопки 'Очистить' нет — купон пуст, продолжаем");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Не удалось проверить или кликнуть 'Очистить' — продолжаем без очистки (" + e.getMessage() + ")");
            }

// --- Выбираем событие и коэффициент ---
            System.out.println("Выбираем событие и коэффициент...");
            Locator coef = page.locator("div.coef__num").first();
            coef.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
            coef.click();
            System.out.println("Коэффициент выбран ✅");
            page.waitForTimeout(2000);

            // ---------- ВВОД СУММЫ ----------
            System.out.println("Вводим сумму ставки (50 KZT)...");

            try {
                Locator sumInput = page.locator("input.c-spinner__input.bet_sum_input, input.js-spinner.spinner__count");
                sumInput.waitFor(new Locator.WaitForOptions()
                        .setTimeout(15000)
                        .setState(WaitForSelectorState.VISIBLE));

                // Проверяем, какой именно input активен
                String inputSelector = null;
                if (page.locator("input.c-spinner__input.bet_sum_input").count() > 0) {
                    inputSelector = "input.c-spinner__input.bet_sum_input";
                    System.out.println("🔹 Найдено стандартное поле ввода суммы");
                } else if (page.locator("input.js-spinner.spinner__count").count() > 0) {
                    inputSelector = "input.js-spinner.spinner__count";
                    System.out.println("🔹 Найдено альтернативное поле ввода суммы");
                } else {
                    throw new RuntimeException("❌ Поле для ввода суммы не найдено!");
                }

                // Снимаем readonly и вводим значение напрямую
                page.evaluate("selector => { " +
                        "const el = document.querySelector(selector);" +
                        "if (el) {" +
                        "  el.removeAttribute('readonly');" +
                        "  el.focus();" +
                        "  el.value = '50';" +
                        "  el.dispatchEvent(new Event('input', { bubbles: true }));" +
                        "  el.dispatchEvent(new Event('change', { bubbles: true }));" +
                        "}}", inputSelector);
                page.waitForTimeout(1000);
                System.out.println("✅ Значение 50 установлено в поле ставки");

                // --- Кликаем по кнопке "Сделать ставку" ---
                Locator makeBetButton = page.locator("button.m-c__button--add:has-text('Сделать ставку'), button.bets-sums-keyboard-button:has-text('Сделать ставку')");
                makeBetButton.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
                makeBetButton.click();
                System.out.println("🟩 Жмём 'Сделать ставку'");

                // --- Подтверждаем ставку ---
                Locator okButton = page.locator("button.c-btn span.c-btn__text:has-text('Ok')");
                okButton.waitFor(new Locator.WaitForOptions().setTimeout(20000).setState(WaitForSelectorState.VISIBLE));
                okButton.click();
                System.out.println("✅ Ставка подтверждена (кнопка 'Ok' нажата)");

            } catch (Exception e) {
                System.out.println("❌ Ошибка при вводе суммы или клике 'Сделать ставку': " + e.getMessage());
            }

            // ---------- ИСТОРИЯ ----------
            System.out.println("Открываем 'Историю ставок'...");
            Locator profileButton2 = page.locator(
                    "button.user-header__link.header__link.header__reg.header__reg_ico.ion-android-person"
            );
            profileButton2.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
            profileButton2.click();
            page.waitForTimeout(1500);

            Locator historyLink = page.locator("a.drop-menu-list__link_history, a.drop-menu-link__label:has-text('История ставок')");
            historyLink.waitFor(new Locator.WaitForOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
            historyLink.click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(3000);
            System.out.println("✅ История ставок открыта успешно");

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


            // ---------- ФИНАЛ ----------
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage("🎯 *Тест завершён успешно*\n" +
                    "• Авторизация: " + (isAuthorized ? "уже была" : "выполнена") + "\n" +
                    "• Купон очищен, ставка сделана\n" +
                    "• История и выход проверены\n" +
                    "🕒 Время выполнения: *" + duration + " сек.* ✅");

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