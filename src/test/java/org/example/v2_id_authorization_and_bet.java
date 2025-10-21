package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class v2_id_authorization_and_bet {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;
    static TelegramNotifier tg;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(false)
        );
        context = browser.newContext();
        page = context.newPage();

        // --- Инициализируем TelegramNotifier ---
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);
    }

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
            page.navigate("https://1xbet.kz/");

            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(1000);
            page.click("button#login-form-call");

            System.out.println("Вводим ID");
            page.waitForTimeout(1000);
            String login = ConfigHelper.get("login");
            page.fill("input#auth_id_email", login);

            System.out.println("Вводим пароль");
            page.waitForTimeout(1000);
            String password = ConfigHelper.get("password");
            page.fill("input#auth-form-password", password);

            System.out.println("Жмём 'Войти' в форме авторизации");
            page.waitForTimeout(1000);
            page.locator("button.auth-button:has-text('Войти')").click();

            System.out.println("Теперь решай капчу вручную — я жду появление кнопки 'Выслать код' (до 10 минут)...");
            try {
                page.waitForSelector("button.phone-sms-modal-content__send",
                        new Page.WaitForSelectorOptions()
                                .setTimeout(600_000)
                                .setState(WaitForSelectorState.VISIBLE)
                );
                System.out.println("Кнопка 'Выслать код' появилась ✅");
            } catch (PlaywrightException e) {
                throw new RuntimeException("Кнопка 'Выслать код' не появилась — капча не решена или что-то пошло не так!");
            }

            System.out.println("Ждём модальное окно SMS");
            page.waitForSelector("button:has-text('Выслать код')");

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

            // --- УНИВЕРСАЛЬНЫЙ ПОИСК СЕССИИ GOOGLE MESSAGES ---
            Path projectRoot = Paths.get(System.getProperty("user.dir"));
            Path[] possiblePaths = new Path[]{
                    projectRoot.resolve("resources/sessions/messages-session.json"),
                    projectRoot.resolve("src/test/resources/sessions/messages-session.json"),
                    projectRoot.resolve("src/test/java/org/example/resources/sessions/messages-session.json")
            };

            Path sessionPath = null;
            for (Path path : possiblePaths) {
                if (path.toFile().exists()) {
                    sessionPath = path;
                    break;
                }
            }

            if (sessionPath == null) {
                throw new RuntimeException("❌ Файл сессии Google Messages не найден ни в одном из стандартных путей!");
            }

            System.out.println("📁 Используем файл сессии: " + sessionPath.toAbsolutePath());

            // --- Открываем Google Messages с сохранённой авторизацией ---
            System.out.println("🔐 Открываем Google Messages с сохранённой сессией...");
            BrowserContext messagesContext = browser.newContext(
                    new Browser.NewContextOptions().setStorageStatePath(sessionPath)
            );
            Page messagesPage = messagesContext.newPage();
            messagesPage.navigate("https://messages.google.com/web/conversations");

            // 1. Кликаем по самому верхнему (новому) чату в списке:
            Locator chat = messagesPage.locator("mws-conversation-list-item").first();
            chat.click();
            messagesPage.waitForTimeout(1000); // даём загрузиться

            chat = messagesPage.locator("mws-conversation-list-item").first();
            int chatsCount = messagesPage.locator("mws-conversation-list-item").count();
            System.out.println("Чатов найдено: " + chatsCount);
            chat.click();
            messagesPage.waitForTimeout(1000);

            // Новый универсальный селектор — вытаскиваем все коды из всех сообщений!
            Locator messageNodes = messagesPage.locator(
                    "mws-message-part-content div.text-msg-content div.text-msg.msg-content div.ng-star-inserted"
            );
            int count = messageNodes.count();
            System.out.println("Сообщений найдено: " + count);
            for (int i = 0; i < count; i++) {
                System.out.println("[" + i + "] " + messageNodes.nth(i).innerText());
            }

            if (count == 0)
                throw new RuntimeException("Сообщения не найдены! (Проверь, что чат не пуст и аккаунт авторизован)");

            String smsText = messageNodes.nth(count - 1).innerText();
            System.out.println("Содержимое последнего SMS: " + smsText);

            String code = smsText.split("\\s+")[0].trim();
            System.out.println("Извлечённый код подтверждения: " + code);

            System.out.println("Возвращаемся на сайт 1xbet.kz");
            page.bringToFront();

            System.out.println("Вводим код подтверждения");
            page.fill("input.phone-sms-modal-code__input", code);

            System.out.println("Жмём 'Подтвердить'");
            page.click("button:has-text('Подтвердить')");

            System.out.println("Авторизация завершена ✅");

            // --- СТАВКА ---
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
            page.waitForSelector(makeBetBtn + ":not([disabled])");
            try {
                page.click(makeBetBtn);
                System.out.println("Кнопка 'Сделать ставку' нажата ✅");
            } catch (Exception e) {
                page.evaluate("document.querySelector(\"button.cpn-btn.cpn-btn--theme-accent\").click()");
                System.out.println("JS-клик по кнопке 'Сделать ставку'");
            }
            page.waitForTimeout(1000);

            // --- ПЕЧАТЬ И РАБОТА С НОВОЙ ВКЛАДКОЙ ---
            System.out.println("Жмём 'Печать' после ставки");
            String printBtn = "button.c-btn.c-btn--print";
            page.waitForSelector(printBtn);
            page.click(printBtn);
            page.waitForTimeout(1500);

            System.out.println("Ждём открытия вкладки 'Печать'");
            Page printPage = null;
            for (int i = 0; i < 10; i++) {
                List<Page> pages = context.pages();
                if (pages.size() > 1) {
                    printPage = pages.get(pages.size() - 1);
                    break;
                }
                page.waitForTimeout(500);
            }
            if (printPage == null) throw new RuntimeException("Не удалось найти вкладку печати!");

            printPage.bringToFront();

            boolean cancelClicked = false;
            try {
                printPage.waitForSelector("cr-button.cancel-button", new Page.WaitForSelectorOptions().setTimeout(3000));
                if (printPage.isVisible("cr-button.cancel-button")) {
                    printPage.click("cr-button.cancel-button");
                    System.out.println("Всплывающее окно: нажата 'Отмена'");
                    cancelClicked = true;
                    printPage.waitForTimeout(1000);
                }
            } catch (Exception ignored) {}

            if (!cancelClicked) {
                try {
                    printPage.waitForSelector("cr-icon-button#save", new Page.WaitForSelectorOptions().setTimeout(5000));
                    printPage.click("cr-icon-button#save");
                    System.out.println("Жмём 'Скачать' в окне печати");
                    printPage.waitForTimeout(1000);
                } catch (Exception e) {
                    System.out.println("Кнопка 'Скачать' не найдена или ошибка: " + e.getMessage());
                }
            }

            printPage.close();
            page.bringToFront();
            page.waitForTimeout(1000);

            // --- Проверка истории ---
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

            // --- Выход ---
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
                            "• Авторизация — выполнена\n" +
                            "• Код из Google Messages — получен\n" +
                            "• Личный кабинет — открыт и проверен\n" +
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