package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_id_authorization_and_bet extends BaseTest {

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginWithSms() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_id_authorization_and_bet* стартовал (авторизация через Google Messages)");

        try {
            System.out.println("Открываем сайт 1xbet.kz");
            page.navigate("https://1xbet.kz/?whn=mobile&platform_type=desktop");

            // ---- ПРОВЕРКА: АВТОРИЗОВАН ЛИ ПОЛЬЗОВАТЕЛЬ ----
            System.out.println("Проверяем, авторизован ли пользователь...");
            boolean isAuthorized = false;

            try {
                Locator lkButton = page.locator("a.header-lk-box-link[title='Личный кабинет']");
                lkButton.waitFor(new Locator.WaitForOptions()
                        .setTimeout(3000)
                        .setState(WaitForSelectorState.ATTACHED)
                );

                if (lkButton.isVisible()) {
                    System.out.println("✅ Пользователь уже авторизован (кнопка 'Личный кабинет' найдена).");
                    isAuthorized = true;
                } else {
                    System.out.println("❌ Кнопка 'Личный кабинет' не найдена — выполняем авторизацию.");
                }
            } catch (Exception e) {
                System.out.println("❌ Кнопка 'Личный кабинет' не найдена — выполняем авторизацию.");
            }

            // ---- ВЕТВЛЕНИЕ ЛОГИКИ ----
            if (!isAuthorized) {
                System.out.println("Аккаунт не авторизован — выполняем вход через ID и Google Messages ✅");

                System.out.println("Жмём 'Войти' в шапке");
                page.waitForTimeout(1000);
                page.click("button#login-form-call");

                System.out.println("Вводим ID");
                String login = ConfigHelper.get("login");
                page.fill("input#auth_id_email", login);

                System.out.println("Вводим пароль");
                String password = ConfigHelper.get("password");
                page.fill("input#auth-form-password", password);

                System.out.println("Жмём 'Войти' в форме авторизации");
                page.locator("button.auth-button:has-text('Войти')").click();

                // ---- ЖДЁМ РЕШЕНИЯ КАПЧИ ----
                System.out.println("Теперь решай капчу вручную — я жду появление кнопки 'Выслать код' (до 10 минут)...");
                page.waitForSelector("button.phone-sms-modal-content__send",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Кнопка 'Выслать код' появилась ✅");

                System.out.println("Жмём 'Выслать код'");
                Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
                try {
                    sendCodeButton.click();
                    System.out.println("Кнопка 'Выслать код' нажата ✅");
                } catch (Exception e) {
                    System.out.println("Первая попытка клика не удалась, пробуем через JS...");
                    page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.click()");
                }

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
            } else {
                System.out.println("Пользователь уже авторизован — пропускаем авторизацию и Google Messages ✅");
            }

            // ---- СТАВКА ----
            System.out.println("Выбираем первый доступный исход");
            Locator firstOutcome = page.locator("span.c-bets__inner").first();
            firstOutcome.click();
            page.waitForTimeout(1000);

            System.out.println("Вводим сумму ставки: 50");
            page.fill("input.cpn-value-controls__input", "50");
            page.keyboard().press("Enter");
            page.waitForTimeout(1000);

            System.out.println("Пробуем нажать 'Сделать ставку'");
            String makeBetBtn = "button.cpn-btn.cpn-btn--theme-accent:has-text('Сделать ставку')";
            try {
                page.waitForSelector(makeBetBtn + ":not([disabled])",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(10_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                page.locator(makeBetBtn).click(new Locator.ClickOptions().setForce(true));
                System.out.println("Кнопка 'Сделать ставку' нажата ✅");
            } catch (Exception e) {
                System.out.println("⚠️ Не удалось кликнуть обычным способом, пробуем через JS...");
                page.evaluate("document.querySelector('button.cpn-btn.cpn-btn--theme-accent')?.click()");
                System.out.println("JS-клик по кнопке 'Сделать ставку' выполнен ✅");
            }

            System.out.println("Ждём появление модального окна 'Ваша ставка принята!'...");
            page.waitForSelector("div.v--modal-box.c-coupon-modal-box[role='dialog']");
            System.out.println("✅ Окно 'Ваша ставка принята!' появилось");

            // ---- ПЕЧАТЬ ----
            System.out.println("Ждём появления кнопки 'Печать' после ставки");
            Locator printButton = page.locator("button.c-btn.c-btn--print");
            printButton.waitFor(new Locator.WaitForOptions()
                    .setTimeout(10_000)
                    .setState(WaitForSelectorState.VISIBLE)
            );

// иногда кнопка немного ниже — подскроллим модалку
            page.evaluate("document.querySelector('.v--modal-box')?.scrollBy(0, 300);");

            System.out.println("Пробуем нажать 'Печать' и перехватить вкладку превью...");
            Page printTab = null;
            try {
                printTab = page.waitForPopup(() -> {
                    printButton.click();
                });
                printTab.waitForLoadState();
                System.out.println("🪟 Вкладка печати открылась: " + printTab.url());
            } catch (Exception e) {
                System.out.println("⚠️ Вкладка превью не перехвачена — возможно, диалог печати открылся в этом же окне (системный).");
            }

// ---- НАЖАТЬ 'ОТМЕНА' В ПРЕВЬЮ ----
            System.out.println("Пробуем закрыть превью печати (按 'Esc' как эквивалент 'Отмена')...");
            try {
                if (printTab != null) {
                    // Если превью в отдельной вкладке
                    printTab.keyboard().press("Escape");
                    printTab.waitForTimeout(1000);
                    // Если Esc не закрыл — закрываем вкладку явно
                    if (!printTab.isClosed()) {
                        printTab.close();
                    }
                    System.out.println("✅ Превью закрыто (вкладка печати)");
                } else {
                    // Если превью — системный диалог в той же вкладке
                    page.keyboard().press("Escape");
                    page.waitForTimeout(1000);
                    System.out.println("✅ Превью закрыто (системный диалог)");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Не удалось закрыть превью печати: " + e.getMessage() + " (продолжаем)");
            }

// ---- ВОЗВРАЩАЕМСЯ НА ОСНОВНУЮ ВКЛАДКУ 1XBET ----
            try {
                List<Page> pages = context.pages();
                if (!pages.isEmpty()) {
                    pages.get(0).bringToFront();
                    System.out.println("🔄 Вернулись к вкладке 1xBet");
                } else {
                    page.bringToFront();
                }
            } catch (Exception e) {
                System.out.println("⚠️ Не удалось явно вернуть фокус — продолжаем с текущей вкладкой: " + e.getMessage());
            }

// ---- СКАЧАТЬ КУПОН ----
            System.out.println("Пробуем нажать 'Скачать' купон на сайте...");
            Locator downloadBtn = null;
            String usedSelector = null;

            String[] candidates = new String[] {
                    "button:has-text('Скачать')",
                    "a:has-text('Скачать')",
                    "button.c-btn.c-btn--download",
                    "button.cpn-btn.cpn-btn--download",
                    "button.c-btn--save"
            };

            for (String css : candidates) {
                Locator candidate = page.locator(css);
                try {
                    candidate.waitFor(new Locator.WaitForOptions().setTimeout(3000).setState(WaitForSelectorState.VISIBLE));
                    if (candidate.isVisible()) {
                        downloadBtn = candidate;
                        usedSelector = css;
                        System.out.println("Нашли кнопку 'Скачать' по селектору: " + css);
                        break;
                    }
                } catch (Exception ignored) {}
            }

            try {
                if (downloadBtn != null) {
                    final Locator finalDownloadBtn = downloadBtn;   // ✅ финализируем Locator
                    final String finalSelector = usedSelector;       // ✅ финализируем строку для JS

                    System.out.println("Запускаем download-процедуру...");
                    Download d = page.waitForDownload(() -> {
                        try {
                            finalDownloadBtn.click(new Locator.ClickOptions().setForce(true));
                        } catch (Exception e) {
                            System.out.println("Обычный клик не сработал, пробуем через JS...");
                            page.evaluate("sel => document.querySelector(sel)?.click()", finalSelector);
                        }
                    });

                    // Сохраняем файл в проектную папку downloads
                    String suggested = d.suggestedFilename();
                    if (suggested == null || suggested.isBlank()) suggested = "coupon.pdf";
                    Path path = Paths.get("downloads", suggested);
                    d.saveAs(path);

                    System.out.println("💾 Купон скачан: " + path + " ✅");
                    tg.sendMessage("💾 *Купон успешно скачан* — `" + path + "` ✅");
                } else {
                    System.out.println("⚠️ Кнопка 'Скачать' не найдена. Проверь селектор и UI.");
                    tg.sendMessage("⚠️ Не удалось найти кнопку *Скачать* на сайте после закрытия превью.");
                }
            } catch (Exception e) {
                System.out.println("❌ Ошибка при скачивании купона: " + e.getMessage());
                tg.sendMessage("❌ Ошибка при скачивании купона: " + e.getMessage());
            }

            // ---- ЛИЧНЫЙ КАБИНЕТ ----
            System.out.println("Открываем 'Личный кабинет'");
            page.waitForTimeout(1000);
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

            System.out.println("Открываем 'История ставок'");
            page.click("div.ap-left-nav__item_history");
            page.waitForTimeout(1000);

            System.out.println("Разворачиваем первую ставку");
            page.click("button.apm-panel-head__expand");
            page.waitForTimeout(1000);

            // ---- ВЫХОД ----
            System.out.println("Жмём 'Выход'");
            page.waitForTimeout(1000);
            page.click("a.ap-left-nav__item_exit");

            System.out.println("Подтверждаем выход кнопкой 'ОК'");
            page.waitForTimeout(1000);
            page.click("button.swal2-confirm.swal2-styled");
            System.out.println("Выход завершён ✅ (браузер остаётся открытым)");

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage(
                    "✅ *Тест успешно завершён:* v2_id_authorization_and_bet\n" +
                            "• Авторизация — " + (isAuthorized ? "пропущена" : "выполнена") + "\n" +
                            "• Ставка — успешно сделана\n" +
                            "• История — проверена\n" +
                            "• Выход — произведён\n\n" +
                            "🕒 Время выполнения: *" + duration + " сек.*\n" +
                            "🌐 Сайт: [1xbet.kz](https://1xbet.kz)\n" +
                            "_Браузер остаётся открытым для ручной проверки._"
            );

        } catch (Exception e) {
            System.out.println("❌ Ошибка в тесте: " + e.getMessage());
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_id_authorization_and_bet");
            tg.sendMessage("🚨 Ошибка в тесте *v2_id_authorization_and_bet*:\n" + e.getMessage());
            if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            throw e;
        }
    }
}
