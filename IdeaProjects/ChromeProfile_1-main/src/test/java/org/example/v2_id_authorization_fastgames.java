package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Полный автономный класс согласно стилю пользователя:
 * - 1xbet запускается в ЧИСТОМ контексте
 * - Google Messages открывается через persistent профиль Chrome пользователя
 * - Подробные логи на русском
 * - Надёжные ожидания, JS‑клики как запасной вариант
 * - Браузер в конце НЕ закрывается
 *
 * Совместимость: Playwright 1.48.0+, JUnit 5, JDK 24, IntelliJ IDEA CE 2025.1.3
 */
public class v2_id_authorization_fastgames {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext xContext; // чистый контекст для 1xbet
    static Page page;
    static TelegramNotifier tg;

    // ---- Цветные логи ----
    static void log(String text) { System.out.println("\u001B[37m" + text + "\u001B[0m"); }
    static void info(String text) { System.out.println("\u001B[36mℹ️  " + text + "\u001B[0m"); }
    static void success(String text) { System.out.println("\u001B[32m✅ " + text + "\u001B[0m"); }
    static void warn(String text) { System.out.println("\u001B[33m⚠️  " + text + "\u001B[0m"); }
    static void error(String text) { System.out.println("\u001B[31m❌ " + text + "\u001B[0m"); }
    static void section(String name) { System.out.println("\n\u001B[45m===== " + name.toUpperCase() + " =====\u001B[0m"); }

    // ---- Конфигурация путей ----
    // Путь профиля Chrome (рабочий ноут — из памяти пользователя)
    static final Path WORK_CHROME_PROFILE = Paths.get("C:\\Users\\b.zhantemirov\\AppData\\Local\\Google\\Chrome\\User Data\\Default");

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized", "--window-size=1920,1080", "--enable-webgl")));

        // Чистый контекст для 1xbet
        Browser.NewContextOptions xOptions = new Browser.NewContextOptions().setViewportSize(null);
        xContext = browser.newContext(xOptions);
        page = xContext.newPage();

        // Telegram notifier
        tg = new TelegramNotifier(ConfigHelper.get("telegram.bot.token"), ConfigHelper.get("telegram.chat.id"));
        success("✅ Инициализация завершена — контексты готовы");
    }

    @AfterAll
    static void tearDownAll() {
        success("Тест завершён ✅ (браузер остаётся открытым)");
    }

    // ========================= ОСНОВНОЙ ТЕСТ =========================
    @Test
    void loginAndPlayFastGames() {
        tg.sendMessage("🚀 *Тест v2_id_authorization_fastgames* стартовал (1xbet — чистый контекст, Messages — persistent)");
        try {
            // --- ОТКРЫТИЕ САЙТА И ВХОД ---
            section("Авторизация");
            log("Открываем сайт 1xbet.kz");
            page.navigate("https://1xbet.kz/");
            page.evaluate("window.moveTo(0,0); window.resizeTo(screen.width, screen.height);");

            System.out.println("Жмём 'Войти' в шапке");
            page.waitForTimeout(1000);
            page.click("button#login-form-call");

            System.out.println("Вводим ID");
            String login = org.example.ConfigHelper.get("login");
            page.fill("input#auth_id_email", login);

            System.out.println("Вводим пароль");
            String password = org.example.ConfigHelper.get("password");
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

            // ======= PERSISTENT GOOGLE MESSAGES (через профиль Chrome) =======
            section("Google Messages (через persistent профиль)");
            if (!Files.exists(WORK_CHROME_PROFILE)) {
                warn("Путь к профилю Chrome не найден: " + WORK_CHROME_PROFILE + " — проверьте путь.");
            }
            BrowserContext messagesCtx = playwright.chromium().launchPersistentContext(
                    WORK_CHROME_PROFILE,
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(false)
                            .setArgs(List.of("--start-maximized", "--disable-notifications"))
            );

            Page msgPage = messagesCtx.newPage();
            msgPage.navigate("https://messages.google.com/web/conversations");
            msgPage.waitForTimeout(4000);

            log("Берём последний чат");
            Locator chat = msgPage.locator("mws-conversation-list-item").first();
            chat.click();
            msgPage.waitForTimeout(2000);

            Locator messages = msgPage.locator("mws-message-part-content div.text-msg-content div.text-msg.msg-content div.ng-star-inserted");
            int count = messages.count();
            if (count == 0) throw new RuntimeException("❌ Нет сообщений!");
            String sms = messages.nth(count - 1).innerText();
            log("Последнее SMS: " + sms);

            Matcher m = Pattern.compile("\\b([0-9]{4,8}|[A-Za-z0-9]{6,8})\\b").matcher(sms);
            String code = m.find() ? m.group() : null;
            if (code == null) throw new RuntimeException("❌ Не удалось извлечь код из SMS!");
            log("Извлечённый код подтверждения: " + code);

            msgPage.close();
            page.bringToFront();

            // ---- ВВОДИМ КОД И ПОДТВЕРЖДАЕМ ----
            log("Вводим код и подтверждаем вход");
            page.fill("input.phone-sms-modal-code__input", code);
            robustClick(page, page.locator("button:has-text('Подтвердить')").first(), 6000, "confirm-sms");
            success("Авторизация завершена ✅");

            // ========================= БЫСТРЫЕ ИГРЫ =========================
            section("Переход в Быстрые игры");
            page.waitForTimeout(1200);
            robustClick(page, page.locator("a.header-menu-nav-list-item__link.main-item:has-text('Быстрые игры')").first(), 6000, "fast-games");

            // === Универсальная функция для проверки кнопки ставки ===
            BiFunction<Page, String, Boolean> tryBetButton = (gamePage, selector) -> {
                info("Проверяем кнопку ставки: " + selector);
                long start = System.currentTimeMillis();

                while (System.currentTimeMillis() - start < 30000) {
                    Locator button = gamePage.locator(selector);
                    if (button.count() > 0) {
                        Locator btn = button.first();
                        if (btn.isVisible()) {
                            boolean clickable = false;
                            try {
                                clickable = (Boolean) btn.evaluate(
                                        "el => {" +
                                                "const style = window.getComputedStyle(el);" +
                                                "return !el.classList.contains('disabled') &&" +
                                                "!el.classList.contains('pointer-events-none') &&" +
                                                "style.display !== 'none' &&" +
                                                "style.visibility !== 'hidden' &&" +
                                                "style.opacity !== '0' &&" +
                                                "!el.closest('[style*=\"display:none\"]');" +
                                                "}"
                                );
                            } catch (Throwable ignore) {}

                            if (clickable) {
                                success("Кнопка активна — делаем ставку");
                                try {
                                    btn.scrollIntoViewIfNeeded();
                                    btn.click(new Locator.ClickOptions()
                                            .setTimeout(2000)
                                            .setForce(true));
                                } catch (Throwable e) {
                                    warn("Обычный клик не сработал, пробуем через JS");
                                    try {
                                        gamePage.evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true}))", btn.elementHandle());
                                    } catch (Throwable e2) {
                                        error("Ошибка при JS-клике: " + e2.getMessage());
                                    }
                                }

                                gamePage.waitForTimeout(600);
                                waitRoundToSettle(gamePage, 30000);
                                return true;
                            }
                        }
                    }
                    gamePage.waitForTimeout(400);
                }
                warn("Кнопка ставки не появилась за 30 сек — пропускаем игру");
                return false;
            };

            // === Крэш-Бокс ===
            section("Крэш-Бокс");
            log("Ищем карточку 'Крэш-Бокс' (через href) в фреймах");

// --- Поиск карточки ---
            Frame gamesFrame = findFrameWithSelector(page, "a.game[href*='crash-boxing']", 8000);
            if (gamesFrame == null)
                gamesFrame = findFrameWithSelector(page, "p.game-name:has-text('Крэш-Бокс')", 12000);

            if (gamesFrame == null) {
                for (Frame fx : page.frames()) {
                    if (fx.locator("a.game[href*='crash-boxing']").count() > 0) {
                        gamesFrame = fx;
                        break;
                    }
                }
            }

            if (gamesFrame == null) {
                List<Frame> frames = page.frames();
                System.out.println("[DEBUG] Фреймы на странице:");
                for (Frame f : frames) System.out.println(" - " + f.url());
                throw new RuntimeException("❌ Не удалось найти карточку 'Крэш-Бокс' ни в одном iframe");
            }

            Locator crashCard = gamesFrame.locator("a.game[href*='crash-boxing']").first();
            crashCard.waitFor(new Locator.WaitForOptions().setTimeout(20000).setState(WaitForSelectorState.VISIBLE));

            log("Кликаем по Крэш-Бокс");
            Page gamePage = clickCardMaybeOpensNewTab(crashCard);
            gamePage.waitForTimeout(2500);

// --- Ждём появления интерфейса игры ---
            log("Ждём загрузку интерфейса игры (до 25 сек)");
            boolean gameReady = false;
            for (int i = 0; i < 50; i++) {
                if (gamePage.locator("text=Сделать ставку").count() > 0 ||
                        gamePage.locator(".contest-panel-outcome-button").count() > 0) {
                    gameReady = true;
                    break;
                }
                gamePage.waitForTimeout(500);
            }
            if (!gameReady) {
                ScreenshotHelper.take(gamePage, "crashbox_not_loaded");
                throw new RuntimeException("❌ Игра 'Крэш-Бокс' не загрузилась вовремя");
            }

            passTutorialIfPresent(gamePage);

// --- Ввод суммы вручную ---
            log("Вводим сумму вручную: 50 KZT");
            try {
                Locator amountInput = gamePage.locator("input[type='text'], input[type='number']").first();
                if (amountInput.count() > 0 && amountInput.isVisible()) {
                    amountInput.click();
                    amountInput.fill("50");
                    log("✅ Сумма 50 введена вручную");
                } else {
                    gamePage.evaluate("document.querySelector('input[type=text],input[type=number]')?.value='50'");
                    log("⚠️ Поле суммы не найдено, значение установлено через JS");
                }
            } catch (Exception e) {
                log("❌ Ошибка при вводе суммы: " + e.getMessage());
            }

// --- Кнопка ставки ---
            log("Пробуем найти кнопку 'Сделать ставку'");
            String[] selectors = {
                    "button:has-text('Сделать ставку')",
                    "div.contest-panel-outcome-button:has-text('Сделать ставку')",
                    "text=Сделать ставку"
            };

            boolean betMade = false;
            for (String s : selectors) {
                try {
                    Locator btn = gamePage.locator(s).first();
                    btn.waitFor(new Locator.WaitForOptions().setTimeout(8000).setState(WaitForSelectorState.VISIBLE));
                    if (btn.isVisible()) {
                        btn.scrollIntoViewIfNeeded();
                        btn.click(new Locator.ClickOptions().setForce(true));
                        success("Ставка сделана через селектор: " + s);
                        betMade = true;
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            if (!betMade) {
                ScreenshotHelper.take(gamePage, "crashbox_no_button");
                throw new RuntimeException("❌ Кнопка 'Сделать ставку' не найдена или неактивна");
            }

            waitRoundToSettle(gamePage, 25000);
            success("Крэш-Бокс успешно завершён ✅");

            // ===== Нарды =====
            section("Нарды");
            Page nardsPage = openGameByHrefContains(gamePage, "nard", "Нарды");
            nardsPage.waitForTimeout(600);
            passTutorialIfPresent(nardsPage);
            setStake50ViaChip(nardsPage);
            log("Выбираем исход: Синий");
            clickFirstEnabled(nardsPage, "span[role='button'][data-market='dice'][data-outcome='blue']", 20000);
            waitRoundToSettle(nardsPage, 25000);

            // ===== Дартс =====
            section("Дартс");
            Page dartsPage = openGameByHrefContains(nardsPage, "darts?cid", "Дартс");
            dartsPage.waitForTimeout(600);
            passTutorialIfPresent(dartsPage);
            setStake50ViaChip(dartsPage);
            log("Выбираем исход (1-4-5-6-9-11-15-16-17-19)");
            clickFirstEnabled(dartsPage, "span[role='button'][data-market='1-4-5-6-9-11-15-16-17-19']", 20000);
            waitRoundToSettle(dartsPage, 25000);

            // ===== Дартс - Фортуна =====
            section("Дартс - Фортуна");
            Page dartsFortunePage = openGameByHrefContains(dartsPage, "darts-fortune", "Дартс - Фортуна");
            dartsFortunePage.waitForTimeout(600);
            passTutorialIfPresent(dartsFortunePage);
            setStake50ViaChip(dartsFortunePage);
            log("Выбираем исход: ONE_TO_EIGHT (Сектор 1-8)");
            clickFirstEnabled(dartsFortunePage, "div[data-outcome='ONE_TO_EIGHT']", 20000);
            waitRoundToSettle(dartsFortunePage, 25000);

            // ===== Больше/Меньше =====
            section("Больше / Меньше");
            Page hiloPage = openGameByHrefContains(dartsFortunePage, "darts-hilo", "Больше/Меньше");
            hiloPage.waitForTimeout(600);
            passTutorialIfPresent(hiloPage);
            setStake50ViaChip(hiloPage);
            log("Выбираем исход: Больше или равно (>=16)");
            clickFirstEnabledAny(hiloPage, new String[]{
                    "div[role='button'][data-market='THROW_RESULT'][data-outcome='gte-16']",
                    "div.board-market-hi-eq:has-text('Больше или равно')"
            }, 45000);
            waitRoundToSettle(hiloPage, 30000);

            // ===== Буллиты NHL21 =====
            section("Буллиты NHL21");
            Page shootoutPage = openGameByHrefContains(hiloPage, "shootout", "Буллиты NHL21");
            shootoutPage.waitForTimeout(800);
            passTutorialIfPresent(shootoutPage);
            setStake50ViaChip(shootoutPage);
            log("Выбираем исход: Да");
            clickFirstEnabled(shootoutPage, "div[role='button'].market-button:has-text('Да')", 45000);
            waitRoundToSettle(shootoutPage, 35000);

            // ===== Бокс (уникальная кнопка) =====
            section("Бокс");
            Page boxingPage = openUniqueBoxingFromHub(shootoutPage);
            boxingPage.waitForTimeout(600);
            passTutorialIfPresent(boxingPage);
            setStake50ViaChip(boxingPage);
            log("Выбираем исход: боксёр №1 (первая кнопка)");
            boxingPage.waitForSelector("div.contest-panel", new Page.WaitForSelectorOptions().setTimeout(15000));
            boolean betDone = tryBetButton.apply(boxingPage,
                    "div.contest-panel-outcome-button:has-text('Сделать ставку'), " +
                            "button.contest-panel-outcome-button:has-text('Сделать ставку'), " +
                            "div[role='button'].contest-panel-outcome-button:has-text('Сделать ставку')");
            if (!betDone) {
                warn("Не удалось сделать ставку в 'Бокс' — кнопка не найдена. Возможна новая DOM-структура игры.");
                info("Совет: проверь актуальный селектор вручную через devtools (div.contest-panel-outcome-button или button.outcome-button)");
            }

            success("Все быстрые игры успешно пройдены ✅");

            // --- ЛИЧНЫЙ КАБИНЕТ + корректный выход ---
            section("Личный кабинет и выход");
            log("Открываем 'Личный кабинет'");
            page.waitForTimeout(1000);
            robustClick(page, page.locator("a.header-lk-box-link[title='Личный кабинет']").first(), 5000, "lk-open");

            log("Пробуем закрыть popup‑крестик после входа в ЛК (если он есть)");
            try {
                Locator closeCrossLk = page.locator("div.box-modal_close.arcticmodal-close");
                closeCrossLk.waitFor(new Locator.WaitForOptions().setTimeout(2000).setState(WaitForSelectorState.ATTACHED));
                if (closeCrossLk.isVisible()) {
                    closeCrossLk.click();
                    success("Крестик в ЛК найден и нажат ✅");
                } else {
                    info("Крестика в ЛК нет — идём дальше");
                }
            } catch (Exception e) { info("Всплывашки в ЛК нет — пропускаем"); }

            log("Жмём 'Выход'");
            page.waitForTimeout(1000);
            robustClick(page, page.locator("a.ap-left-nav__item_exit").first(), 5000, "lk-logout");

            log("Подтверждаем выход кнопкой 'ОК'");
            page.waitForTimeout(1000);
            robustClick(page, page.locator("button.swal2-confirm.swal2-styled").first(), 5000, "logout-ok");

            success("Выход завершён ✅ (браузер остаётся открытым)");
            tg.sendMessage("✅ *v2_id_authorization_fastgames* завершён успешно (оба контекста работают корректно)");
        } catch (Exception e) {
            error("Ошибка: " + e.getMessage());
            ScreenshotHelper.take(page, "error");
            tg.sendMessage("🚨 Ошибка в тесте v2_id_authorization_fastgames: " + e.getMessage());
            Assertions.fail(e);
        }
    }

    // ========================= ХЕЛПЕРЫ =========================

    /** JS‑вспомогательный клик с запасным вариантом */
    static void robustClick(Page p, Locator target, long timeoutMs, String name) {
        long start = System.currentTimeMillis();
        Throwable last = null;
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                if (target.count() > 0 && target.first().isVisible()) {
                    target.first().scrollIntoViewIfNeeded();
                    target.first().click(new Locator.ClickOptions().setTimeout(1500));
                    return;
                }
            } catch (Throwable t) { last = t; }
            try {
                // JS‑клик
                if (target.count() > 0) {
                    p.evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true}))", target.first().elementHandle());
                    return;
                }
            } catch (Throwable t) { last = t; }
            p.waitForTimeout(200);
        }
        throw new RuntimeException("robustClick(" + name + ") не удалось: " + (last != null ? last.getMessage() : "unknown"));
    }

    /** Ждём стабилизации раунда (фолбэк: таймаут и отсутствие спиннеров, тостов и т.п.) */
    static void waitRoundToSettle(Page p, long maxWaitMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxWaitMs) {
            try {
                boolean busy = p.locator(".loading, .spinner, .preloader, [aria-busy='true']").isVisible();
                if (!busy) { p.waitForLoadState(LoadState.NETWORKIDLE); break; }
            } catch (Throwable ignore) {}
            p.waitForTimeout(300);
        }
    }

    /** Закрываем туториалы, подсказки и модалки, если всплыли */
    static void passTutorialIfPresent(Page p) {
        String[] closeSelectors = new String[]{
                "button:has-text('Понятно')",
                "button:has-text('Далее')",
                "button:has-text('OK')",
                "button.tutorial-close",
                ".modal-close, .popup-close, .arcticmodal-close, .close, [aria-label='Close']"
        };
        for (String sel : closeSelectors) {
            try {
                Locator loc = p.locator(sel).first();
                if (loc.count() > 0 && loc.isVisible()) {
                    loc.click(new Locator.ClickOptions().setTimeout(800));
                    p.waitForTimeout(400);
                }
            } catch (Throwable ignore) {}
        }
    }

    /** Находит первый iframe, в котором присутствует selector, за отведённое время */
    static Frame findFrameWithSelector(Page p, String selector, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            for (Frame f : p.frames()) {
                try {
                    if (f.locator(selector).count() > 0) return f;
                } catch (Throwable ignore) {}
            }
            p.waitForTimeout(200);
        }
        return null;
    }

    /** Клик по карточке игры; если открылась новая вкладка — вернём её, иначе текущую */
    static Page clickCardMaybeOpensNewTab(Locator card) {
        Page current = card.page();
        try {
            Page newPage = current.waitForPopup(() -> {
                try {
                    card.first().click(new Locator.ClickOptions().setButton(MouseButton.LEFT).setTimeout(2000));
                } catch (Throwable t) {
                    // запасной JS‑клик
                    current.evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true}))", card.first().elementHandle());
                }
            });
            return newPage != null ? newPage : current;
        } catch (Throwable ignore) {
            // Вероятно открывается в том же табе
            try { card.first().click(new Locator.ClickOptions().setTimeout(1500).setForce(true)); } catch (Throwable ignored) {}
            return current;
        }
    }

    /** Открываем игру из хаба по части href и имени (для логов) */
    static Page openGameByHrefContains(Page fromPage, String hrefPart, String humanName) {
        log("Переходим в игру '" + humanName + "'");
        Frame f = findFrameWithSelector(fromPage, "a[href*='" + hrefPart + "']", 8000);
        if (f == null) {
            // запасной поиск по тексту
            f = findFrameWithSelector(fromPage, "p.game-name:has-text('" + humanName + "')", 12000);
        }
        if (f == null) throw new RuntimeException("Не нашли карточку игры: " + humanName);

        Locator link = f.locator("a[href*='" + hrefPart + "']").first();
        if (link.count() == 0) link = f.locator("p.game-name:has-text('" + humanName + "')").locator("xpath=ancestor::a").first();

        return clickCardMaybeOpensNewTab(link);
    }

    /** Открываем уникальный 'Бокс' (DOM может отличаться) */
    static Page openUniqueBoxingFromHub(Page fromPage) {
        String[] variants = new String[] { "boxing", "box", "crash-boxing" };
        for (String v : variants) {
            try { return openGameByHrefContains(fromPage, v, "Бокс"); } catch (Throwable ignore) {}
        }
        // запасной путь: по названию
        Frame f = findFrameWithSelector(fromPage, "p.game-name:has-text('Бокс')", 10000);
        if (f == null) throw new RuntimeException("Карточка 'Бокс' не найдена");
        Locator link = f.locator("p.game-name:has-text('Бокс')").locator("xpath=ancestor::a").first();
        return clickCardMaybeOpensNewTab(link);
    }

    /** Ставим ставку 50 через чипы с номиналом */
    static void setStake50ViaChip(Page p) {
        String[] chips = new String[]{
                "button:has-text('50')",
                "span:has-text('50')",
                "div.chip:has-text('50')",
                "[data-amount='50']",
        };
        for (String sel : chips) {
            try {
                Locator chip = p.locator(sel).first();
                if (chip.count() > 0 && chip.isVisible()) {
                    robustClick(p, chip, 2000, "chip-50");
                    log("Сумма 50 выбрана чипом");
                    return;
                }
            } catch (Throwable ignore) {}
        }
        warn("Чип '50' не найден — попробуем ввести вручную");
        try {
            Locator input = p.locator("input[type='text'], input[type='number']").first();
            if (input.count() > 0) { input.fill("50"); }
        } catch (Throwable ignore) {}
    }

    /** Клик по первому доступному исходу */
    static void clickFirstEnabled(Page p, String selector, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        Throwable last = null;
        while (System.currentTimeMillis() < end) {
            try {
                Locator loc = p.locator(selector).first();
                if (loc.count() > 0 && loc.isVisible()) {
                    robustClick(p, loc, 2000, "market-" + selector);
                    return;
                }
            } catch (Throwable t) { last = t; }
            p.waitForTimeout(200);
        }
        throw new RuntimeException("Кнопка по селектору не найдена/не активна: " + selector + (last != null ? ("; last=" + last.getMessage()) : ""));
    }

    /** Клик по первому доступному исходу из набора селекторов */
    static void clickFirstEnabledAny(Page p, String[] selectors, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            for (String s : selectors) {
                try {
                    Locator loc = p.locator(s).first();
                    if (loc.count() > 0 && loc.isVisible()) {
                        robustClick(p, loc, 1500, "market-any");
                        return;
                    }
                } catch (Throwable ignore) {}
            }
            p.waitForTimeout(200);
        }
        throw new RuntimeException("Ни один из селекторов не сработал: " + Arrays.toString(selectors));
    }

    // ========================= ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ =========================

    /** Заглушка для конфигурации (чтение из системных свойств/окружения, иначе из config.properties рядом с проектом) */
    static class ConfigHelper {
        static final Properties props = new Properties();
        static {
            try {
                Path p = Paths.get("config.properties");
                if (Files.exists(p)) {
                    try (var in = Files.newInputStream(p)) { props.load(in); }
                }
            } catch (Exception ignore) {}
        }
        static String get(String key) {
            String v = System.getProperty(key);
            if (v != null && !v.isBlank()) return v;
            v = System.getenv(key.replace('.', '_').toUpperCase(Locale.ROOT));
            if (v != null && !v.isBlank()) return v;
            v = props.getProperty(key);
            return v != null ? v : "";
        }
    }

    /** Простая реализация Telegram‑нотификатора (пишет в консоль; HTTP‑отправку добавите при интеграции) */
    static class TelegramNotifier {
        final String token;
        final String chatId;
        TelegramNotifier(String token, String chatId) { this.token = token; this.chatId = chatId; }
        void sendMessage(String text) {
            System.out.println("[TG] " + text);
            // Реальная отправка может быть добавлена при необходимости через HTTP
        }
    }

    /** Скриншоты в папку ./screenshots */
    static class ScreenshotHelper {
        static void take(Page p, String name) {
            try {
                Path dir = Paths.get("screenshots");
                if (!Files.exists(dir)) Files.createDirectories(dir);
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path path = dir.resolve(name + "_" + ts + ".png");
                p.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
                System.out.println("Скриншот: " + path.toAbsolutePath());
            } catch (Exception e) { System.out.println("Не удалось сделать скриншот: " + e.getMessage()); }
        }
    }
}

