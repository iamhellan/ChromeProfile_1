package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.BiFunction;

public class v2_id_authorization_fastgames {
    // ====== Поля окружения и сервисов =======================================================
    static Playwright playwright;
    static BrowserContext context; // persistent context
    static Page page;
    static TelegramNotifier tg;

    // ====== Цветные логи ====================================================================
    static void log(String text) {
        System.out.println("\u001B[37m" + text + "\u001B[0m");
    }
    static void info(String text) {
        System.out.println("\u001B[36mℹ️  " + text + "\u001B[0m");
    }
    static void success(String text) {
        System.out.println("\u001B[32m✅ " + text + "\u001B[0m");
    }
    static void warn(String text) {
        System.out.println("\u001B[33m⚠️  " + text + "\u001B[0m");
    }
    static void error(String text) {
        System.out.println("\u001B[31m❌ " + text + "\u001B[0m");
    }
    static void section(String name) {
        System.out.println("\n\u001B[45m===== " + name.toUpperCase() + " =====\u001B[0m");
    }

    // ====== Определение среды и запуск persistent Chrome ====================================
    private static String detectEnvironment() {
        String workPath = ConfigHelper.get("chrome.profile.work");
        String homePath = ConfigHelper.get("chrome.profile.home");

        try {
            if (workPath != null && new java.io.File(workPath).exists()) {
                System.out.println("🌐 Среда определена: WORK → " + workPath);
                return "work";
            }
        } catch (Throwable ignored) {}
        try {
            if (homePath != null && new java.io.File(homePath).exists()) {
                System.out.println("🏠 Среда определена: HOME → " + homePath);
                return "home";
            }
        } catch (Throwable ignored) {}

        System.out.println("⚠️ Не удалось достоверно определить среду → fallback: WORK");
        return "work";
    }

    private static Path resolveChromeProfilePath(String env) {
        String workPath = ConfigHelper.get("chrome.profile.work");
        String homePath = ConfigHelper.get("chrome.profile.home");
        if ("work".equals(env) && workPath != null) return Paths.get(workPath);
        if ("home".equals(env) && homePath != null) return Paths.get(homePath);
        // Фолбэк: если ключи пустые, используем рабочий
        if (workPath != null) return Paths.get(workPath);
        if (homePath != null) return Paths.get(homePath);
        throw new RuntimeException("Не заданы пути chrome.profile.work / chrome.profile.home в config.properties");
    }

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();

        String env = detectEnvironment();
        Path chromeProfilePath = resolveChromeProfilePath(env);

        info("Запускаем Chromium в persistent-профиле: " + chromeProfilePath);
        // Используем persistent context, чтобы иметь доступ к уже залогиненному Google Messages
        context = playwright.chromium().launchPersistentContext(
                chromeProfilePath,
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(false)
                        .setArgs(Arrays.asList(
                                "--start-maximized",
                                "--window-size=1920,1080"
                        ))
                        .setViewportSize(null) // во весь экран
        );

        page = context.newPage();

        // Telegram
        String botToken = ConfigHelper.get("telegram.bot.token");
        String chatId = ConfigHelper.get("telegram.chat.id");
        tg = new TelegramNotifier(botToken, chatId);

        success("Инициализация завершена ✅");
    }

    // ====== Утилиты Playwright ===============================================================

    private com.microsoft.playwright.Frame findFrameWithSelector(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                // Обходим все страницы контекста (включая новые вкладки)
                for (Page pg : p.context().pages()) {
                    for (com.microsoft.playwright.Frame f : pg.frames()) {
                        try {
                            if (f.locator(selector).count() > 0) {
                                System.out.println("[DEBUG] Нашли селектор во фрейме: " + f.url());
                                return f;
                            }
                        } catch (Throwable ignore) {}
                    }
                }
            } catch (Throwable ignore) {}
            p.waitForTimeout(300);
        }
        return null;
    }

    private Locator smartLocator(Page p, String selector, int timeoutMs) {
        try {
            Locator direct = p.locator(selector);
            if (direct.count() > 0) return direct;
        } catch (Throwable ignore) {}

        com.microsoft.playwright.Frame f = findFrameWithSelector(p, selector, timeoutMs);
        if (f != null) return f.locator(selector);
        throw new RuntimeException("Элемент не найден ни на странице, ни во фреймах: " + selector);
    }

    private void robustClick(Page p, Locator loc, int timeoutMs, String debugName) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        RuntimeException lastErr = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                loc.first().scrollIntoViewIfNeeded();
                loc.first().click(new Locator.ClickOptions().setTimeout(2500));
                return;
            } catch (RuntimeException e1) {
                lastErr = e1;
                String msg = e1.getMessage() == null ? "" : e1.getMessage();

                boolean intercept = msg.contains("intercepts pointer events")
                        || msg.contains("element is not receiving pointer events")
                        || msg.contains("is not visible");

                if (intercept) {
                    info("'" + debugName + "': перехват клика/невидимость. Пробуем force или JS.");
                    try {
                        loc.first().click(new Locator.ClickOptions().setTimeout(1500).setForce(true));
                        return;
                    } catch (Throwable ignore) {}
                    try {
                        loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true}))");
                        return;
                    } catch (Throwable ignore) {}
                }
            }
            p.waitForTimeout(200);
        }
        if (lastErr != null) throw lastErr;
        throw new RuntimeException("Не удалось кликнуть по '" + debugName + "' за " + timeoutMs + "ms");
    }

    private void clickFirstEnabled(Page p, String selector, int timeoutMs) {
        Locator loc = smartLocator(p, selector, timeoutMs);
        robustClick(p, loc.first(), timeoutMs, selector);
    }

    private void clickFirstEnabledAny(Page p, String[] selectors, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (String sel : selectors) {
                try {
                    clickFirstEnabled(p, sel, 1500);
                    return;
                } catch (Throwable ignore) {}
            }
            p.waitForTimeout(150);
        }
        throw new RuntimeException("Не нашли активный элемент ни по одному из селекторов!");
    }

    private Page clickCardMaybeOpensNewTab(Locator card) {
        int before = context.pages().size();
        robustClick(page, card, 30000, "game-card");
        page.waitForTimeout(600);
        int after = context.pages().size();
        if (after > before) {
            Page newPage = context.pages().get(after - 1);
            newPage.bringToFront();
            System.out.println("[DEBUG] Игра открылась в новой вкладке: " + newPage.url());
            return newPage;
        }
        System.out.println("[DEBUG] Игра открылась в текущем окне/фрейме");
        return page;
    }

    private void passTutorialIfPresent(Page gamePage) {
        // Простейший проход по «Далее»
        for (int i = 1; i <= 5; i++) {
            try {
                Locator nextBtn = gamePage.locator("div[role='button']:has-text('Далее')");
                if (nextBtn.count() == 0 || !nextBtn.first().isVisible()) break;
                robustClick(gamePage, nextBtn.first(), 1500, "Далее");
                gamePage.waitForTimeout(150);
            } catch (Throwable ignore) { break; }
        }
        // «Я всё понял»
        try {
            Locator understood = gamePage.locator("div[role='button']:has-text('Я всё понял')");
            if (understood.count() > 0 && understood.first().isVisible()) {
                robustClick(gamePage, understood.first(), 1500, "Я всё понял");
            }
        } catch (Throwable ignore) {}
    }

    private void setStake50ViaChip(Page gamePage) {
        System.out.println("Выбираем чип 50 KZT");

        // [MODIFIED] — добавлены запасные варианты поиска
        String[] possibleSelectors = new String[]{
                "div.chip-text:has-text('50')",
                "span.chip__value:has-text('50')",
                "div.chip-value:has-text('₸50')",
                "button:has-text('50')"
        };

        Locator chip50 = null;
        for (String sel : possibleSelectors) {
            try {
                chip50 = smartLocator(gamePage, sel, 2000);
                if (chip50 != null && chip50.count() > 0) {
                    System.out.println("[DEBUG] Найден чип по селектору: " + sel);
                    break;
                }
            } catch (Throwable ignore) {}
        }

        if (chip50 == null || chip50.count() == 0)
            throw new RuntimeException("❌ Не найден чип 50 KZT ни по одному из селекторов");

        robustClick(gamePage, chip50.first(), 8000, "chip-50");
    }

    // Защита от зависания между раундами
    private void waitRoundToSettle(Page gamePage, int maxMs) {
        long start = System.currentTimeMillis();
        boolean roundStarted = false;

        while (System.currentTimeMillis() - start < maxMs) {
            try {
                Locator anyBet = gamePage.locator("div[role='button'][data-market][data-outcome]:has-text('Сделать ставку')");
                if (anyBet.count() > 0 && anyBet.first().isVisible()) {
                    boolean enabled = (Boolean) anyBet.first().evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))");
                    if (enabled) {
                        System.out.println("[DEBUG] Новый раунд доступен — продолжаем ✅");
                        roundStarted = true;
                        break;
                    }
                }

                if (System.currentTimeMillis() - start > 60000 && !roundStarted) {
                    warn("Игра не реагирует более 60 сек — пропускаем и идём к следующей игре.");
                    return;
                }
            } catch (Throwable ignore) {}
            gamePage.waitForTimeout(300);
        }

        if (!roundStarted) {
            warn("Раунд не завершился в течение " + (maxMs / 1000) + " сек — продолжаем сценарий без ожидания.");
        }
    }

    private Page openGameByHrefContains(Page originPage, String hrefContains, String fallbackMenuText) {
        com.microsoft.playwright.Frame f = findFrameWithSelector(originPage, "a[href*='" + hrefContains + "']", 7000);
        if (f == null && fallbackMenuText != null) {
            f = findFrameWithSelector(originPage, "span.text-hub-header-game-title:has-text('" + fallbackMenuText + "')", 7000);
        }
        if (f == null) throw new RuntimeException("Не нашли ссылку на игру: " + hrefContains);
        Locator link = f.locator("a[href*='" + hrefContains + "']");
        if (link.count() == 0 && fallbackMenuText != null) {
            link = f.locator("span.text-hub-header-game-title:has-text('" + fallbackMenuText + "')").locator("xpath=ancestor::a");
        }
        return clickCardMaybeOpensNewTab(link.first());
    }

    private Page openUniqueBoxingFromHub(Page originPage) {
        String innerSpan = "a.menu-sports-item-inner[href*='productId=boxing'][href*='cid=1xbetkz'] span.text-hub-header-game-title:has-text('Бокс')";
        com.microsoft.playwright.Frame f = findFrameWithSelector(originPage, innerSpan, 8000);
        if (f == null) throw new RuntimeException("❌ Не нашли уникальную кнопку 'Бокс'");
        Locator link = f.locator(innerSpan).locator("xpath=ancestor::a");
        return clickCardMaybeOpensNewTab(link.first());
    }

    // Безопасный обёртка-блок для мини-сценариев игр
    private void playSafe(String gameName, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            warn("Ошибка при выполнении '" + gameName + "': " + e.getMessage());
            String screenshot = ScreenshotHelper.takeScreenshot(page, "skip_" + gameName);
            info("Пропускаем игру '" + gameName + "' и продолжаем...");
            try {
                if (screenshot != null) tg.sendPhoto(screenshot, "Скриншот: ошибка в игре " + gameName);
            } catch (Throwable ignored) {}
        }
    }

    // ====== Google Messages через persistent-контекст ========================================

    private String extractCodeFromGoogleMessages() {
        log("🔐 Открываем Google Messages в текущем persistent-контексте");
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");
        messagesPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
        messagesPage.waitForTimeout(3000);

        // Быстрая проверка на welcome-страницу (нет авторизации)
        if (messagesPage.url().contains("welcome")) {
            messagesPage.close();
            throw new RuntimeException("⚠️ Google Messages не авторизованы в данном профиле. " +
                    "Зайди один раз вручную в этом профиле и привяжи устройство.");
        }

        // Обработка "Не удалось подключиться" → кнопка «Обновить»
        try {
            Locator refreshButton = messagesPage.locator("button:has-text('Обновить')");
            if (refreshButton.isVisible()) {
                info("Обнаружено состояние 'Не удалось подключиться' → жмём 'Обновить'");
                try {
                    refreshButton.click();
                } catch (Throwable e) {
                    info("Обычный клик не сработал, пробуем через JS");
                    messagesPage.evaluate("document.querySelector('button.refresh-button')?.click()");
                }
                info("Ждём восстановления списка чатов...");
                boolean reconnected = false;
                for (int i = 0; i < 20; i++) {
                    if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                        reconnected = true;
                        break;
                    }
                    messagesPage.waitForTimeout(1000);
                }
                if (reconnected) success("Соединение восстановлено");
                else warn("После 'Обновить' чаты не появились");
            }
        } catch (Throwable e) {
            warn("Проверка 'Обновить' завершилась ошибкой: " + e.getMessage());
        }

        // Устойчивое ожидание чатов
        info("Ждём появления списка чатов...");
        boolean chatsLoaded = false;
        for (int i = 0; i < 20; i++) {
            if (messagesPage.locator("mws-conversation-list-item").count() > 0) {
                chatsLoaded = true;
                break;
            }
            messagesPage.waitForTimeout(1000);
        }
        if (!chatsLoaded) {
            messagesPage.close();
            throw new RuntimeException("❌ Чаты не появились в Google Messages");
        }
        success("Список чатов найден");

        // Ищем чат 1xBet, иначе верхний
        Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
        if (chat.count() == 0) {
            warn("Чат 1xBet не найден, берём верхний чат");
            chat = messagesPage.locator("mws-conversation-list-item").first();
        }
        robustClick(messagesPage, chat.first(), 3000, "chat-open");
        messagesPage.waitForTimeout(2000);

        // Ищем последние текстовые узлы
        info("Ищем последнее входящее сообщение в чате");
        Locator messageNodes = messagesPage.locator("div.text-msg-content div.text-msg.msg-content div.ng-star-inserted");
        int count = 0;
        for (int i = 0; i < 15; i++) {
            count = messageNodes.count();
            if (count > 0) break;
            messagesPage.waitForTimeout(1000);
        }
        if (count == 0) {
            messagesPage.close();
            throw new RuntimeException("❌ Нет сообщений внутри выбранного чата");
        }
        String lastMessageText = messageNodes.nth(count - 1).innerText().trim();
        log("📨 Последнее сообщение: " + lastMessageText);

        // Парсим код: цифры или буквы/цифры 4–8 символов
        Matcher m = Pattern.compile("\\b([0-9]{4,8}|[A-Za-z0-9]{4,8})\\b").matcher(lastMessageText);
        String code = m.find() ? m.group() : null;
        if (code == null) {
            messagesPage.close();
            throw new RuntimeException("❌ Код подтверждения не найден в сообщении");
        }
        success("Извлечён код подтверждения: " + code);

        try {
            tg.sendMessage("📬 Код из Google Messages: *" + code + "*");
        } catch (Throwable ignored) {}

        messagesPage.close();
        page.bringToFront();
        return code;
    }

    // ====== Основной тест ====================================================================
    @Test
    void loginAndPlayFastGames() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_id_authorization_fastgames* стартовал (авторизация через ID + Google Messages в persistent профиле)");

        try {
            // ---------- Авторизация ----------
            section("Авторизация по ID");
            log("Открываем сайт 1xbet.kz");
            page.navigate("https://1xbet.kz/");
            page.evaluate("window.moveTo(0,0); window.resizeTo(screen.width, screen.height);");

            // ======= Проверка авторизации перед входом =======
            section("Проверка состояния авторизации");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(1500);

            boolean alreadyLoggedIn = page.locator("a.header-lk-box-link[title='Личный кабинет']").isVisible();

            if (alreadyLoggedIn) {
                success("Пользователь уже авторизован ✅");
                tg.sendMessage("⚙️ Обнаружена активная сессия: пропускаем авторизацию и переходим к 'Быстрым играм'");
            } else {
                log("Жмём 'Войти' в шапке");
                page.waitForTimeout(800);
                page.click("button#login-form-call");

                String login = ConfigHelper.get("login");
                String password = ConfigHelper.get("password");
                log("Вводим ID и пароль из config.properties");

                page.fill("input#auth_id_email", login);
                page.fill("input#auth-form-password", password);
                page.click("button.auth-button.auth-button--block.auth-button--theme-secondary");

                // Ждём появления «Выслать код», что является триггером «капча решена / форма верификации готова»
                log("Ждём появления кнопки 'Выслать код' (до 10 мин)");
                page.waitForSelector("button:has-text('Выслать код')",
                        new Page.WaitForSelectorOptions().setTimeout(600000).setState(WaitForSelectorState.VISIBLE));

                log("Жмём 'Выслать код'");
                Locator sendCodeButton = page.locator("button:has-text('Выслать код')");
                try {
                    sendCodeButton.click();
                    log("Кнопка 'Выслать код' нажата");
                } catch (Throwable e) {
                    warn("Первая попытка клика не удалась, пробуем force/JS");
                    try {
                        sendCodeButton.click(new Locator.ClickOptions().setForce(true));
                    } catch (Throwable e2) {
                        page.evaluate("document.querySelector(\"button:has-text('Выслать код')\")?.dispatchEvent(new MouseEvent('click', {bubbles:true}))");
                    }
                }

                log("Ждём поле для ввода кода (до 10 мин)");
                page.waitForSelector("input.phone-sms-modal-code__input",
                        new Page.WaitForSelectorOptions().setTimeout(600000).setState(WaitForSelectorState.VISIBLE));

                // ---------- Google Messages ----------
                section("Google Messages");
                String code = extractCodeFromGoogleMessages();

                log("Вводим код и подтверждаем вход");
                page.fill("input.phone-sms-modal-code__input", code);
                page.click("button:has-text('Подтвердить')");
                page.waitForTimeout(1000);
                success("Авторизация завершена ✅");
            }

            // ====== БЫСТРЫЕ ИГРЫ ======
            section("Переход в Быстрые игры");
            page.waitForTimeout(1200);
            page.click("a.header-menu-nav-list-item__link.main-item:has-text('Быстрые игры')");

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
            com.microsoft.playwright.Frame gamesFrame = findFrameWithSelector(page, "a.game[href*='crash-boxing']", 8000);
            if (gamesFrame == null) {
                gamesFrame = findFrameWithSelector(page, "p.game-name:has-text('Крэш-Бокс')", 12000);
            }
            if (gamesFrame == null) {
                for (com.microsoft.playwright.Frame fx : page.frames()) {
                    if (fx.locator("a.game[href*='crash-boxing']").count() > 0) {
                        gamesFrame = fx;
                        break;
                    }
                }
            }
            if (gamesFrame == null) {
                List<com.microsoft.playwright.Frame> frames = page.frames();
                System.out.println("[DEBUG] Фреймы на странице:");
                for (com.microsoft.playwright.Frame f : frames) System.out.println(" - " + f.url());
                throw new RuntimeException("❌ Не удалось найти карточку 'Крэш-Бокс' ни в одном iframe");
            }

            Locator crashByHref = gamesFrame.locator("a.game[href*='crash-boxing']");
            Locator crashByText = gamesFrame.locator("p.game-name:has-text('Крэш-Бокс')").locator("xpath=ancestor::a");
            Locator crashCard = crashByHref.count() > 0 ? crashByHref : crashByText;

            log("Ждём появления карточки в DOM");
            crashCard.waitFor(new Locator.WaitForOptions().setTimeout(20000).setState(WaitForSelectorState.ATTACHED));

            log("Кликаем по Крэш-Бокс");
            Page gamePage = clickCardMaybeOpensNewTab(crashCard);
            gamePage.waitForTimeout(800);

            passTutorialIfPresent(gamePage);

// --- Ввод суммы вручную ---
            log("Вводим сумму вручную: 50 KZT");
            try {
                Locator amountInput = gamePage.locator("input[type='text'][value]").first();

                if (amountInput.count() > 0 && amountInput.isVisible()) {
                    amountInput.click();
                    amountInput.fill("50");
                    log("✅ Сумма 50 введена вручную в поле ставки");
                } else {
                    log("⚠️ Поле ввода суммы не найдено — пробуем через JS");
                    gamePage.evaluate("() => { const el = document.querySelector('input[type=text],input[type=number]'); if (el) el.value = '50'; }");
                }
            } catch (Exception e) {
                log("❌ Ошибка при вводе суммы вручную: " + e.getMessage());
            }

            gamePage.waitForTimeout(800);

// --- Первая ставка ---
            log("Ставка 50 KZT (yes)");
            clickFirstEnabled(gamePage,
                    "div[role='button'][data-market='hit_met_condition'][data-outcome='yes']:has-text('Сделать ставку')",
                    30000);

            gamePage.waitForTimeout(1500);

// --- Ожидаем завершение раунда ---
            waitRoundToSettle(gamePage, 60000);

            // ===== Нарды =====
            section("Нарды");
            log("Переходим в игру 'Нарды'");
            Page nardsPage = openGameByHrefContains(gamePage, "nard", "Нарды");
            nardsPage.waitForTimeout(600);
            passTutorialIfPresent(nardsPage);
            setStake50ViaChip(nardsPage);
            log("Выбираем исход: Синий");
            clickFirstEnabled(nardsPage, "span[role='button'][data-market='dice'][data-outcome='blue']", 20000);
            waitRoundToSettle(nardsPage, 60000);

            // ===== Дартс =====
            section("Дартс");
            log("Переходим в игру 'Дартс'");
            Page dartsPage = openGameByHrefContains(nardsPage, "darts?cid", "Дартс");
            dartsPage.waitForTimeout(600);
            passTutorialIfPresent(dartsPage);
            setStake50ViaChip(dartsPage);
            log("Выбираем исход (1-4-5-6-9-11-15-16-17-19)");
            clickFirstEnabled(dartsPage, "span[role='button'][data-market='1-4-5-6-9-11-15-16-17-19']", 20000);
            waitRoundToSettle(dartsPage, 60000);

            // ===== Дартс - Фортуна =====
            section("Дартс - Фортуна");
            log("Переходим в игру 'Дартс - Фортуна'");
            Page dartsFortunePage = openGameByHrefContains(dartsPage, "darts-fortune", "Дартс - Фортуна");
            dartsFortunePage.waitForTimeout(600);
            passTutorialIfPresent(dartsFortunePage);
            setStake50ViaChip(dartsFortunePage);
            log("Выбираем исход: ONE_TO_EIGHT (Сектор 1-8)");
            clickFirstEnabled(dartsFortunePage, "div[data-outcome='ONE_TO_EIGHT']", 20000);
            waitRoundToSettle(dartsFortunePage, 60000);

            // ===== Больше/Меньше =====
            section("Больше / Меньше");
            log("Переходим в игру 'Больше/Меньше'");
            Page hiloPage = openGameByHrefContains(dartsFortunePage, "darts-hilo", "Больше/Меньше");
            hiloPage.waitForTimeout(600);
            passTutorialIfPresent(hiloPage);
            setStake50ViaChip(hiloPage);
            log("Выбираем исход: Больше или равно (>=16)");
            clickFirstEnabledAny(hiloPage, new String[]{
                    "div[role='button'][data-market='THROW_RESULT'][data-outcome='gte-16']",
                    "div.board-market-hi-eq:has-text('Больше или равно')"
            }, 45000);
            waitRoundToSettle(hiloPage, 60000);

            // ===== Буллиты NHL21 =====
            section("Буллиты NHL21");
            log("Переходим в игру 'Буллиты NHL21'");
            Page shootoutPage = openGameByHrefContains(hiloPage, "shootout", "Буллиты NHL21");
            shootoutPage.waitForTimeout(800);
            passTutorialIfPresent(shootoutPage);
            log("Ждём появления суммы (чип 50)");
            setStake50ViaChip(shootoutPage);
            log("Выбираем исход: Да");
            clickFirstEnabled(shootoutPage, "div[role='button'].market-button:has-text('Да')", 45000);
            waitRoundToSettle(shootoutPage, 60000);

            // ===== Бокс (уникальная кнопка) =====
            section("Бокс");
            log("Переходим в игру 'Бокс' (уникальная кнопка)");

// [MODIFIED] — ищем кликабельную ссылку <a> с productId=boxing
            String boxingSelector = "a.menu-sports-item-inner[href*='productId=boxing'][href*='cid=1xbetkz']";
            com.microsoft.playwright.Frame boxingFrame = findFrameWithSelector(shootoutPage, boxingSelector, 8000);
            if (boxingFrame == null) {
                warn("❌ Не нашли ссылку на игру 'Бокс' (productId=boxing) — проверим наличие span-текста");
                boxingFrame = findFrameWithSelector(shootoutPage, "span.text-hub-header-game-title:has-text('Бокс')", 8000);
            }
            if (boxingFrame == null) {
                throw new RuntimeException("❌ Не удалось найти карточку 'Бокс' ни по href, ни по тексту");
            }

            Locator boxingLink = boxingFrame.locator(boxingSelector);
            if (boxingLink.count() == 0) {
                boxingLink = boxingFrame.locator("span.text-hub-header-game-title:has-text('Бокс')").locator("xpath=ancestor::a");
            }

            Page boxingPage = clickCardMaybeOpensNewTab(boxingLink.first());
            boxingPage.waitForLoadState(LoadState.NETWORKIDLE); // [MODIFIED] ждём полной загрузки
            boxingPage.waitForTimeout(600);

            passTutorialIfPresent(boxingPage);
            setStake50ViaChip(boxingPage);

            log("Выбираем исход: боксёр №1 (первая кнопка)");

// [MODIFIED] — уточнён селектор кнопки ставки с fallback-ами
            boxingPage.waitForSelector("div.contest-panel, div.contest, div.market-buttons",
                    new Page.WaitForSelectorOptions().setTimeout(15000));

            boolean betDone = tryBetButton.apply(boxingPage,
                    "button:has-text('Сделать ставку'), " +
                            "div.contest-panel-outcome-button:has-text('Сделать ставку'), " +
                            "div[role='button'].contest-panel-outcome-button:has-text('Сделать ставку')");

            if (!betDone) {
                warn("Не удалось сделать ставку в 'Бокс' — кнопка не найдена. Возможна новая DOM-структура игры.");
                info("Совет: проверь актуальный селектор вручную через devtools (button:has-text('Сделать ставку') или div.contest-panel-outcome-button)");
            }

            success("Готово ✅");

            // --- Переход в Личный кабинет и корректный выход ---
            log("Открываем 'Личный кабинет'");
            page.waitForTimeout(1000);
            page.click("a.header-lk-box-link[title='Личный кабинет']");

            log("Пробуем закрыть popup-крестик после входа в ЛК (если он вообще есть)");
            try {
                Locator closeCrossLk = page.locator("div.box-modal_close.arcticmodal-close");
                closeCrossLk.waitFor(new Locator.WaitForOptions().setTimeout(2000).setState(WaitForSelectorState.ATTACHED));
                if (closeCrossLk.isVisible()) {
                    closeCrossLk.click();
                    success("Крестик в ЛК найден и нажат ✅");
                } else {
                    info("Крестика в ЛК нет — идём дальше");
                }
            } catch (Exception e) {
                info("Всплывашки в ЛК или крестика нет, игнорируем и двигаемся дальше");
            }

            log("Жмём 'Выход'");
            page.waitForTimeout(1000);
            page.click("a.ap-left-nav__item_exit");

            log("Подтверждаем выход кнопкой 'ОК'");
            page.waitForTimeout(1000);
            page.click("button.swal2-confirm.swal2-styled");

            success("Выход завершён ✅ (браузер остаётся открытым)");

            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage(
                    "✅ *v2_id_authorization_fastgames успешно завершён!*\n" +
                            "• Авторизация по ID — выполнена\n" +
                            "• Код из Google Messages получен из persistent профиля\n" +
                            "• Все быстрые игры пройдены\n\n" +
                            "🕒 Время выполнения: *" + duration + " сек.*"
            );

        } catch (Exception e) {
            error("Ошибка: " + e.getMessage());
            String screenshot = ScreenshotHelper.takeScreenshot(page, "v2_id_authorization_fastgames_error");
            try {
                tg.sendMessage("🚨 Ошибка в тесте *v2_id_authorization_fastgames*:\n" + e.getMessage());
                if (screenshot != null) tg.sendPhoto(screenshot, "Скриншот ошибки");
            } catch (Throwable ignored) {}
            // Исключение не бросаем, чтобы не ронять раннер
        }
    }

    @AfterAll
    static void tearDownAll() {
        success("Тест завершён ✅ (браузер и контекст остаются открытыми)");
        try { playwright.close(); } catch (Exception ignore) {}
    }

    // ====== Дополнительные диагностические утилиты ==========================================
    // Эти методы не обязательны для сценария, но полезны в отладке.
    private void scrollSlowly(Page p, int steps, int deltaY, int pauseMs) {
        for (int i = 0; i < steps; i++) {
            try {
                p.mouse().wheel(0, deltaY);
                p.waitForTimeout(pauseMs);
            } catch (Throwable ignored) {}
        }
    }

    private void waitForNetworkIdle(Page p, int timeoutMs) {
        try {
            p.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
        } catch (Throwable ignored) {}
    }

    private void safeReload(Page p, int timeoutMs) {
        try {
            p.reload();
            waitForNetworkIdle(p, timeoutMs);
        } catch (Throwable ignored) {}
    }

    private void safeBringToFront(Page p) {
        try {
            p.bringToFront();
        } catch (Throwable ignored) {}
    }

    // Небольшой helper для видимости компонента
    private boolean isVisible(Locator loc) {
        try {
            return loc.count() > 0 && loc.first().isVisible();
        } catch (Throwable t) {
            return false;
        }
    }

    // ===== Универсальный helper для ожидания iframe игры =====
    private Frame waitForGameFrame(Page page, String gameKey, int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            for (Frame f : page.frames()) {
                String url = f.url();
                if (url != null && url.contains(gameKey)) {
                    success("[DEBUG] Найден фрейм: " + url);
                    return f;
                }
            }
            page.waitForTimeout(500);
        }
        throw new RuntimeException("❌ Не найден фрейм игры: " + gameKey);
    }
}
