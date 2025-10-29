package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v2_MOBI_id_authorization_fastgames
 * Мобильный сценарий: авторизация (или пропуск, если уже залогинен) + раздел "Быстрые игры" (серия игр).
 * Блок Google Messages — ровно как в эталоне (не менять).
 *
 * Требования пользователя:
 * - Логи на русском
 * - Телеграм-уведомления tg.sendMessage / tg.sendPhoto
 * - Скриншоты при ошибках через ScreenshotHelper
 * - Не закрывать браузер в конце
 * - Полная совместимость с BaseTest, JUnit 5, Playwright 1.48+, JDK 24
 */
public class v2_MOBI_id_authorization_fastgames_ПРОЦЕСС extends BaseTest {

    // ========================== Х Е Л П Е Р Ы (SPA + iframe + вкладки) ==========================

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
                System.out.println("⚠️ Ошибка при ожидании загрузки: " + e.getMessage());
                page.reload();
                waited = 0;
            }
        }
    }

    static void tgStep(String message, boolean success) {
        String icon = success ? "✅" : "❌";
        System.out.println(icon + " " + message);
        try { tg.sendMessage(icon + " " + message); } catch (Exception ignore) {}
    }

    private static void robustClick(Page p, Locator loc, int timeoutMs, String debugName) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        RuntimeException lastErr = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                loc.first().scrollIntoViewIfNeeded();
                loc.first().click(new Locator.ClickOptions().setTimeout(2500));
                return;
            } catch (RuntimeException e1) {
                lastErr = e1;
                String msg = e1.getMessage();
                if (msg != null && (msg.contains("intercepts pointer events")
                        || msg.contains("not visible") || msg.contains("timeout"))) {
                    try {
                        loc.first().click(new Locator.ClickOptions().setTimeout(1500).setForce(true));
                        return;
                    } catch (RuntimeException e2) {
                        lastErr = e2;
                        try {
                            loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}))");
                            return;
                        } catch (RuntimeException e3) { lastErr = e3; }
                    }
                }
            }
            p.waitForTimeout(200);
        }
        if (lastErr != null) throw new RuntimeException("❌ Не удалось кликнуть: " + debugName + " — " + lastErr.getMessage(), lastErr);
        throw new RuntimeException("❌ Не удалось кликнуть: " + debugName);
    }

    /** Ищем селектор во всех страницах контекста и во всех iframe */
    private static Frame findFrameWithSelector(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Page pg : p.context().pages()) {
                for (Frame f : pg.frames()) {
                    try {
                        if (f.locator(selector).count() > 0) {
                            System.out.println("[DEBUG] Нашли селектор во фрейме: " + f.url());
                            return f;
                        }
                    } catch (Throwable ignore) {}
                }
            }
            p.waitForTimeout(250);
        }
        return null;
    }

    /** Сначала ищем в текущем page, иначе — автоматически прыгаем в нужный iframe */
    private static Locator smartLocator(Page p, String selector, int timeoutMs) {
        Locator direct = p.locator(selector);
        if (direct.count() > 0) return direct;
        Frame f = findFrameWithSelector(p, selector, timeoutMs);
        if (f != null) return f.locator(selector);
        throw new RuntimeException("Элемент не найден (даже во фреймах): " + selector);
    }

    /** Клик по карточке игры: ловим новую вкладку, если она открылась */
    private static Page clickCardMaybeOpensNewTab(Locator card) {
        int before = page.context().pages().size();
        Page maybeNew = null;
        try {
            // Ждём новую страницу, если она действительно откроется
            maybeNew = page.context().waitForPage(
                    () -> robustClick(page, card.first(), 15000, "Открываем карточку игры")
            );
        } catch (PlaywrightException ignore) {
            // Если новая вкладка не открылась — просто продолжим в текущей
        }
        page.waitForTimeout(600);
        int after = page.context().pages().size();
        if (maybeNew != null) {
            maybeNew.bringToFront();
            return maybeNew;
        } else if (after > before) {
            Page newPage = page.context().pages().get(after - 1);
            newPage.bringToFront();
            return newPage;
        }
        // Игра открылась в той же вкладке
        return page;
    }

    // ================================== Т Е С Т ==================================

    @Test
    void loginAndPlayFastGames() {
        long startTime = System.currentTimeMillis();
        tg.sendMessage("🚀 *Тест v2_MOBI_id_authorization_fastgames* стартовал (авторизация + быстрые игры)");

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

                // --- GOOGLE MESSAGES (эталонный блок — не трогать) ---
                System.out.println("📨 Открываем Google Messages с уже сохранённой сессией...");
                Page messagesPage = context.newPage();
                messagesPage.navigate("https://messages.google.com/web/conversations");
                messagesPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
                messagesPage.waitForTimeout(3000);

                if (messagesPage.url().contains("welcome")) {
                    throw new RuntimeException("⚠️ Не авторизованы в Google Messages! Нужно один раз вручную отсканировать QR.");
                }

                // ---- ЖДЁМ ЗАГРУЗКИ СПИСКА ЧАТОВ ----
                System.out.println("Теперь подожди — Google Messages загружается (до 10 минут)...");
                try {
                    messagesPage.waitForSelector("mws-conversation-list-item",
                            new Page.WaitForSelectorOptions()
                                    .setTimeout(600_000) // максимум 10 минут
                                    .setState(WaitForSelectorState.VISIBLE)
                    );
                    System.out.println("✅ Список чатов успешно загрузился");
                } catch (PlaywrightException e) {
                    throw new RuntimeException("❌ Чаты не появились в Google Messages — возможно, не авторизованы или сеть тормозит");
                }

                System.out.println("🔍 Ищем чат с 1xBet...");
                Locator chat = messagesPage.locator("mws-conversation-list-item:has-text('1xbet'), mws-conversation-list-item:has-text('1xbet-kz')");
                if (chat.count() == 0) {
                    System.out.println("⚠️ Чат 1xBet не найден, кликаем по первому в списке");
                    chat = messagesPage.locator("mws-conversation-list-item").first();
                }
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

                page.bringToFront();
                page.fill("input.phone-sms-modal-code__input", code);
                page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");
                page.waitForTimeout(3000);
                System.out.println("Авторизация завершена ✅");

                try {
                    tg.sendMessage("📬 Код из Google Messages: *" + code + "*");
                    System.out.println("📨 Код успешно отправлен в Telegram ✅");
                } catch (Exception tgErr) {
                    System.out.println("⚠️ Ошибка при отправке в Telegram: " + tgErr.getMessage());
                }
            }

            // --- ПЕРЕХОД К БЫСТРЫМ ИГРАМ ---
            System.out.println("🎯 Переходим в 'Быстрые игры'");
            page.click("button.header__hamburger.hamburger");
            page.waitForTimeout(1000);
            page.click("a[href*='fast-games']");

// ждём реальные карточки, а не просто сеть
            page.waitForSelector("div.tile__cell img, div.tile__cell",
                    new Page.WaitForSelectorOptions().setTimeout(30_000).setState(WaitForSelectorState.VISIBLE));
            page.waitForTimeout(1200);
            System.out.println("✅ Раздел 'Быстрые игры' открыт — запускаем тест игр...");

// ====================== И Г Р Ы ======================

// 1) Крэш-Бокс
            {
                Page gamePage = openGameFromLobbyByName(page, "Crash boxing", "crash");
                passTutorialIfPresent(gamePage);
                setStake50(gamePage);
                clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes']", 30_000, "Crash: исход 1");
                clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes_2']", 30_000, "Crash: исход 2");
                waitRoundToSettle(gamePage, 25_000);
                System.out.println("🎮 Crash boxing завершён ✅");

                // пробуем сразу переключиться на Нарды
                if (!switchToGameByTitle(gamePage, "Нарды")) {
                    backToLobby(gamePage);
                    page.waitForTimeout(1500);
                }
            }

// 2) Нарды
            {
                Page gamePage = page;
                passTutorialIfPresent(gamePage);
                setStake50(gamePage);
                clickFirstEnabled(gamePage, "span[role='button'][data-market='dice'][data-outcome='blue']", 25_000, "Нарды: синий");
                waitRoundToSettle(gamePage, 25_000);
                System.out.println("🎯 Нарды завершены ✅");

                if (!switchToGameByTitle(gamePage, "Дартс")) {
                    backToLobby(gamePage);
                    page.waitForTimeout(1500);
                }
            }

// 3) Дартс
            {
                Page gamePage = page;
                passTutorialIfPresent(gamePage);
                setStake50(gamePage);
                clickFirstEnabled(gamePage, "span[role='button'][data-market='1-4-5-6-9-11-15-16-17-19']", 25_000, "Дартс: комбо");
                waitRoundToSettle(gamePage, 25_000);
                System.out.println("🎯 Дартс завершён ✅");

                if (!switchToGameByTitle(gamePage, "Дартс - Фортуна")) {
                    backToLobby(gamePage);
                    page.waitForTimeout(1500);
                }
            }

// 4) Дартс - Фортуна
            {
                Page gamePage = page;
                passTutorialIfPresent(gamePage);
                setStake50(gamePage);
                clickFirstEnabled(gamePage, "div[data-outcome='ONE_TO_EIGHT']", 25_000, "Дартс-Фортуна: 1-8");
                waitRoundToSettle(gamePage, 25_000);
                System.out.println("🎯 Дартс-Фортуна завершена ✅");

                if (!switchToGameByTitle(gamePage, "Больше/Меньше")) {
                    backToLobby(gamePage);
                    page.waitForTimeout(1500);
                }
            }

// 5) Больше/Меньше
            {
                Page gamePage = page;
                passTutorialIfPresent(gamePage);
                setStake50(gamePage);
                clickFirstEnabled(gamePage,
                        "div[role='button'][data-market][data-outcome]:has-text('Больше'), button:has-text('Больше')",
                        45_000, "Hi/Lo: Больше");
                waitRoundToSettle(gamePage, 30_000);
                System.out.println("🎯 Больше/Меньше завершено ✅");

                if (!switchToGameByTitle(gamePage, "Буллиты NHL21")) {
                    backToLobby(gamePage);
                    page.waitForTimeout(1500);
                }
            }

// 6) Буллиты NHL21
            {
                Page gamePage = page;
                passTutorialIfPresent(gamePage);
                setStake50(gamePage);
                clickFirstEnabled(gamePage,
                        "div[role='button'].market-button:has-text('Да'), button.market-button:has-text('Да')",
                        45_000, "Буллиты: Да");
                waitRoundToSettle(gamePage, 35_000);
                System.out.println("🎯 Буллиты завершены ✅");

                if (!switchToGameByTitle(gamePage, "Бокс")) {
                    backToLobby(gamePage);
                    page.waitForTimeout(1500);
                }
            }

// 7) Бокс
            {
                Page gamePage = page;
                passTutorialIfPresent(gamePage);
                setStake50(gamePage);
                clickFirstEnabled(gamePage,
                        "div[role='button'].contest-panel-outcome-button, button.contest-panel-outcome-button",
                        25_000, "Бокс: исход");
                waitRoundToSettle(gamePage, 20_000);
                System.out.println("🎯 Бокс завершён ✅");
            }


            // ---------- ФИНАЛ ----------
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            tg.sendMessage("🎯 *Тест завершён успешно*\n" +
                    "• Авторизация: " + (isAuthorized ? "уже была" : "выполнена") + "\n" +
                    "• Все быстрые игры пройдены ✅\n" +
                    "🕒 Время выполнения: *" + duration + " сек.* ✅");

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            String screenshotPath = ScreenshotHelper.takeScreenshot(page, "v2_MOBI_id_authorization_fastgames_error");
            try {
                tg.sendMessage("🚨 Ошибка в *v2_MOBI_id_authorization_fastgames*:\n" + e.getMessage());
                if (screenshotPath != null) tg.sendPhoto(screenshotPath, "Скриншот ошибки");
            } catch (Exception ignore) {}
        }

        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    // --- ПЕРЕКЛЮЧЕНИЕ МЕЖДУ ИГРАМИ ЧЕРЕЗ ТОЧНОЕ СОВПАДЕНИЕ НАЗВАНИЯ ---
    private static boolean switchToGameByTitle(Page gamePage, String gameName) {
        try {
            Locator titleButton = gamePage.locator(
                    "span.w-100.text-hub-header-game-title"
            ).filter(new Locator.FilterOptions().setHasText(gameName));
            if (titleButton.count() > 0 && titleButton.first().isVisible()) {
                System.out.println("🔁 Переключаемся на игру: " + gameName + " через заголовок");
                titleButton.first().scrollIntoViewIfNeeded();
                titleButton.first().click();
                gamePage.waitForLoadState(LoadState.NETWORKIDLE);
                gamePage.waitForSelector("div.chip-text, div[role='button'], span[role='button']",
                        new Page.WaitForSelectorOptions().setTimeout(15_000).setState(WaitForSelectorState.VISIBLE));
                gamePage.waitForTimeout(1200);
                System.out.println("✅ Успешно переключились на игру: " + gameName);
                return true;
            } else {
                System.out.println("⚠️ Кнопка '" + gameName + "' не найдена — возвращаемся в лобби");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Не удалось переключиться на '" + gameName + "': " + e.getMessage());
        }
        return false;
    }

    // --- ОЖИДАНИЕ НАЧАЛА РАУНДА (если кнопки временно заблокированы) ---
    private static void waitForRoundStart(Page gamePage, int maxWaitMs) {
        System.out.println("⌛ Проверяем, начался ли новый раунд (до " + (maxWaitMs / 1000) + " сек)...");
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxWaitMs) {
            try {
                Locator anyBet = gamePage.locator(
                        "div[role='button'][data-market][data-outcome]:has-text('Сделать ставку'), " +
                                "button:has-text('Сделать ставку'), " +
                                "div.market-button:has-text('Да'), " +
                                "div[role='button'].contest-panel-outcome-button"
                );
                if (anyBet.count() > 0 && anyBet.first().isVisible()) {
                    boolean enabled = (Boolean) anyBet.first().evaluate(
                            "e => {" +
                                    "const s = getComputedStyle(e);" +
                                    "return !e.closest('[disabled]') && s.pointerEvents!=='none' && s.visibility!=='hidden';" +
                                    "}"
                    );
                    if (enabled) {
                        System.out.println("✅ Кнопки ставок активны — новый раунд начался!");
                        return;
                    }
                }
            } catch (Exception ignore) {}
            gamePage.waitForTimeout(1000);
        }
        System.out.println("⚠️ Не дождались начала нового раунда — продолжаем тест");
    }

    // --- УМНЫЙ КЛИК С ПОЛНОЙ ПРОВЕРКОЙ ДОСТУПНОСТИ ---
    private static void clickFirstEnabled(Page gamePage, String selector, int timeoutMs, String debugName) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                Locator elements = gamePage.locator(selector);
                int count = elements.count();

                for (int i = 0; i < count; i++) {
                    Locator el = elements.nth(i);

                    if (!el.isVisible()) continue;
                    if (!el.isEnabled()) continue;

                    boolean clickable = (Boolean) el.evaluate("e => {" +
                            "const s = getComputedStyle(e);" +
                            "return s.display!=='none' && s.visibility!=='hidden' && " +
                            "s.pointerEvents!=='none' && e.offsetParent !== null;" +
                            "}");

                    if (!clickable) continue;

                    gamePage.waitForTimeout(300);

                    try {
                        el.scrollIntoViewIfNeeded();
                        el.click(new Locator.ClickOptions().setTimeout(2500));
                        System.out.println("✅ Клик по '" + debugName + "' выполнен (обычный)");
                        return;
                    } catch (PlaywrightException e1) {
                        System.out.println("⚠️ Первый клик не сработал: " + e1.getMessage());
                        try {
                            el.click(new Locator.ClickOptions().setTimeout(1500).setForce(true));
                            System.out.println("✅ Клик по '" + debugName + "' выполнен через force()");
                            return;
                        } catch (Exception e2) {
                            el.evaluate("node => node.dispatchEvent(new MouseEvent('click', {bubbles:true}))");
                            System.out.println("✅ Клик по '" + debugName + "' выполнен через JS");
                            return;
                        }
                    }
                }
            } catch (Throwable ignore) {}
            gamePage.waitForTimeout(500);
        }
        throw new RuntimeException("❌ Не удалось кликнуть по '" + debugName + "' за " + timeoutMs + " мс");
    }

// === ХЕЛПЕРЫ, КОТОРЫЕ ВЫЗЫВАЕТ ТЕСТ ===

    // Открыть игру по названию карточки; если не нашли — fallback по части href
    private static Page openGameFromLobbyByName(Page originPage, String gameName, String hrefContainsFallback) {
        System.out.println("🎮 Открываем игру: " + gameName);
        Locator card = originPage.locator("div.tile__cell img[alt*='" + gameName + "']").first();
        if (card.count() == 0)
            card = originPage.locator("div.tile__cell:has(:text('" + gameName + "'))").first();

        // прокрутка, если карточка вне экрана
        for (int i = 0; i < 12 && (card.count() == 0 || !card.isVisible()); i++) {
            originPage.evaluate("window.scrollBy(0, 600)");
            originPage.waitForTimeout(300);
        }

        // запасной вариант — по href
        if (card.count() == 0 && hrefContainsFallback != null) {
            Locator link = originPage.locator("a[href*='" + hrefContainsFallback + "']").first();
            if (link.count() > 0) return clickCardMaybeOpensNewTab(link.first());
        }
        if (card.count() == 0) throw new RuntimeException("Не нашли карточку игры: " + gameName);
        return clickCardMaybeOpensNewTab(card.first());
    }

    // Пройти возможный туториал в начале игры
    private static void passTutorialIfPresent(Page gamePage) {
        for (int i = 0; i < 6; i++) {
            try {
                Locator next = gamePage.locator("button:has-text('Далее'), div[role='button']:has-text('Далее')");
                if (next.count() == 0 || !next.first().isVisible()) break;
                next.first().scrollIntoViewIfNeeded();
                next.first().click(new Locator.ClickOptions().setTimeout(2000));
                gamePage.waitForTimeout(150);
            } catch (RuntimeException ignore) { break; }
        }
        try {
            Locator understood = gamePage.locator("button:has-text('Я всё понял'), div[role='button']:has-text('Я всё понял')");
            if (understood.count() > 0 && understood.first().isVisible()) {
                understood.first().click(new Locator.ClickOptions().setTimeout(2000));
            }
        } catch (RuntimeException ignore) {}
    }

    // Выставить ставку 50 (чип или инпут)
    private static void setStake50(Page gamePage) {
        try {
            Locator chip50 = gamePage.locator("div.chip-text:has-text('50'), button:has-text('50')");
            if (chip50.count() > 0 && chip50.first().isVisible()) {
                chip50.first().scrollIntoViewIfNeeded();
                chip50.first().click(new Locator.ClickOptions().setTimeout(3000));
                System.out.println("✅ Фишка 50 выбрана");
                return;
            }
        } catch (Throwable ignore) {}
        try {
            Locator input = gamePage.locator("input[type='text'], input[type='number']");
            if (input.count() > 0) {
                input.first().click();
                input.first().fill("50");
                System.out.println("✅ Ввели 50 вручную");
            }
        } catch (Throwable e) {
            System.out.println("⚠️ Не удалось выставить 50: " + e.getMessage());
        }
    }

    // Дождаться, когда раунд закончится и снова станет доступна кнопка ставки
    private static void waitRoundToSettle(Page gamePage, int maxMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxMs) {
            try {
                Locator again = gamePage.locator(
                        "button:has-text('Сделать ставку'), div:has-text('Сделать ставку'), div.market-button"
                );
                if (again.count() > 0 && again.first().isVisible()) {
                    boolean clickable = (Boolean) again.first().evaluate("el => {" +
                            "const s = getComputedStyle(el);" +
                            "return !el.closest('[disabled]') && s.visibility!=='hidden' && s.display!=='none' && s.pointerEvents!=='none';" +
                            "}");
                    if (clickable) {
                        System.out.println("[DEBUG] Новый раунд доступен ✅");
                        return;
                    }
                }
            } catch (Throwable ignore) {}
            gamePage.waitForTimeout(250);
        }
        System.out.println("⚠️ Раунд не завершился за " + maxMs + " мс — идём дальше.");
    }

    // Вернуться в лобби: если игра в новой вкладке — закрыть; если в той же — Назад/история
    private static void backToLobby(Page gamePage) {
        try {
            if (!gamePage.equals(page)) {
                System.out.println("⬅️ Закрываем вкладку игры");
                gamePage.close();
                page.bringToFront();
            } else {
                if (gamePage.locator("button:has-text('Назад')").count() > 0)
                    gamePage.locator("button:has-text('Назад')").first().click(new Locator.ClickOptions().setTimeout(2500));
                else
                    gamePage.evaluate("history.back()");
            }
        } catch (Throwable e) {
            System.out.println("⚠️ Ошибка при возврате: " + e.getMessage());
            try { gamePage.evaluate("history.back()"); } catch (Throwable ignore) {}
        }
        // убедимся, что карточки лобби появились
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForSelector("div.tile__cell img, div.tile__cell",
                new Page.WaitForSelectorOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
        page.waitForTimeout(800);
        System.out.println("⬅️ Вернулись в лобби — карточки игр появились ✅");
    }


    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }
}
