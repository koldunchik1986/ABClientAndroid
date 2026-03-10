package ru.neverlands.abclient.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.neverlands.abclient.manager.ChatUserList;
import ru.neverlands.abclient.model.ChatUser;

public class ChatFilter {
    // Извлечение лута из строк вида «...», включая вес ресурса в формате "(x.xx кг)".
    private static final Pattern LOOT_PATTERN = Pattern.compile(
            "\u00AB([^\u00BB]+)\u00BB(?:\\s*\\((\\d+(?:[\\.,]\\d+)?)\\s*[кК][гГ]\\))?");
    // Ники берём из <SPAN title/alt="..."> (для кликов/автоответа).
    private static final Pattern SPAN_NICK_PATTERN = Pattern.compile("<SPAN[^>]+(?:title|alt)=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public static String filter(String message) {
        if (message == null) return "";
        String result = message;

        // Парсинг боевого опыта и накопление статистики (аналог C# ChatFilter).
        String xpStr = HelperStrings.subString(
                result,
                "Получено <font color=#CC0000>боевого</font> опыта: <b><font color=#CC0000>",
                "</font></b>.");
        if (xpStr != null && !xpStr.isEmpty()) {
            try {
                long xp = Long.parseLong(xpStr);
                ChatStats.addXp(xp);
            } catch (NumberFormatException ignored) {
            }
        }

        String thingStr = HelperStrings.subString(result, "Результат обыска бота: <B>", "</B>.");
        // Парсинг лута (результат обыска бота) + привязка к времени строки.
        if (thingStr != null && !thingStr.isEmpty()) {
            String timeStr = HelperStrings.subString(
                    result,
                    "<font class=chattime>&nbsp;",
                    "&nbsp;</font> <font color=000000><B><font color=#CC0000>Внимание!</font> Системная информация.</B> Результат обыска бота: ");
            if (timeStr != null && !timeStr.isEmpty()) {
                List<String> items = new ArrayList<>();
                Matcher matcher = LOOT_PATTERN.matcher(thingStr);
                while (matcher.find()) {
                    String itemName = matcher.group(1);
                    String itemWeightKg = matcher.group(2);
                    if (itemName != null && !itemName.isEmpty()) {
                        String normalizedItem = itemName.trim();
                        if (itemWeightKg != null && !itemWeightKg.isEmpty()) {
                            normalizedItem = normalizedItem + " (" + itemWeightKg.replace(',', '.') + " кг)";
                        }
                        items.add(normalizedItem);
                    }
                }
                if (!items.isEmpty()) {
                    ChatStats.addLoot(timeStr, items);
                }
            }
        }

        if (result.toLowerCase(Locale.ROOT).contains("<font color=#000000><b>системная информация.</b></font> поединок завершён.")) {
            if (!AppVars.LastBoiLog.isEmpty()
                    && !AppVars.LastBoiSostav.isEmpty()
                    && !AppVars.LastBoiTravm.isEmpty()
                    && !AppVars.LastBoiUron.isEmpty()) {
                String lastBoiTravm = AppVars.LastBoiTravm == null ? "" : AppVars.LastBoiTravm;
                String newLog =
                        "Бой" +
                                lastBoiTravm +
                                " против " +
                                AppVars.LastBoiSostav +
                                " завершен (<a href=http://www.neverlands.ru/logs.fcg?fid=" +
                                AppVars.LastBoiLog +
                                " onclick=\"window.open(this.href);\">лог</a> боя). Нанесено урона: <FONT color=#339900><b>" +
                                AppVars.LastBoiUron +
                                "</b></FONT>";

                // Завершение боя: считаем бой и фиксируем последний лог.
                if (AppVars.LastBoiLog != null
                        && !AppVars.LastBoiLog.isEmpty()
                        && !AppVars.LastBoiLog.equals(AppVars.LastBoiEndLog)) {
                    ChatStats.addFight();
                    AppVars.LastBoiEndLog = AppVars.LastBoiLog;
                }

                result = result.replace("Поединок завершён", newLog);

                int pos = result.toLowerCase(Locale.ROOT).indexOf("получено <font color=#004bbb>магического");
                if (pos != -1) {
                    String se = "</font></b>.";
                    int spos = result.toLowerCase(Locale.ROOT).indexOf(se, pos);
                    if (spos != -1) {
                        result = result.substring(0, pos) + result.substring(spos + se.length());
                    }
                }

                AppLogger.writeTexLog("Бой против " + AppVars.LastBoiSostav + " завершен (" + AppVars.LastBoiLog + ")");
                AppVars.LastBoiLog = "";
                AppVars.LastBoiSostav = "";
            }
        } else {
            // Приват/клан/парный чат: автоответ и звук, если сообщение адресовано нам.
            String myNick = AppVars.Profile != null ? AppVars.Profile.UserNick : "";
            if (!myNick.isEmpty()) {
                String needle = "\">" + myNick + "</SPAN>";
                if (result.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) {
                    String fromNick = extractSpanNick(result);
                    if (fromNick != null) {
                        fromNick = fromNick.replaceFirst("^%+", "");
                        if (!fromNick.equalsIgnoreCase(myNick)) {
                            EventSounds.playSndMsg();
                            boolean isToClan = result.toLowerCase(Locale.ROOT).contains(" > clan: ");
                            boolean isToPair = result.toLowerCase(Locale.ROOT).contains(" > pair: ");
                            if (AppVars.Profile != null && AppVars.Profile.DoAutoAnswer) {
                                String answer = "%<" + fromNick + "> " + AutoAnswerMachine.getNextAnswer();
                                if (isToClan) answer = "%clan%" + answer;
                                else if (isToPair) answer = "%pair%" + answer;
                                Chat.addAnswer(answer);
                            }
                        }
                    }
                }
            }

            // Подстановка уровней и значков в нике (DoChatLevels).
            if (AppVars.Profile != null && AppVars.Profile.DoChatLevels) {
                int posSpanEnd = result.toLowerCase(Locale.ROOT).indexOf("</span>");
                if (posSpanEnd != -1) {
                    int posSpanTagEnd = result.lastIndexOf('>', posSpanEnd);
                    if (posSpanTagEnd != -1) {
                        String sayNick = result.substring(posSpanTagEnd + 1, posSpanEnd);
                        if (ChatUserList.hasUser(sayNick)) {
                            int posSpanTagStart = result.lastIndexOf('<', posSpanTagEnd);
                            if (posSpanTagStart != -1) {
                                ChatUser chatUser = ChatUserList.getUser(sayNick);
                                if (chatUser != null) {
                                    String level = chatUser.level != null ? chatUser.level : "";
                                    if (!level.isEmpty()) {
                                        String insert =
                                                "&nbsp;[" + level + "]<a href=\"http://www.neverlands.ru/pinfo.cgi?" +
                                                        chatUser.nick +
                                                        "\" onclick=\"window.open(this.href);\"><img src=http://image.neverlands.ru/chat/info.gif width=11 height=12 border=0 align=bottom></a>";
                                        result = result.substring(0, posSpanEnd + "</SPAN>".length())
                                                + insert + result.substring(posSpanEnd + "</SPAN>".length());
                                    }
                                    if (chatUser.sign != null && !chatUser.sign.isEmpty()) {
                                        StringBuilder sb = new StringBuilder();
                                        sb.append("<img src=http://image.neverlands.ru/signs/");
                                        sb.append(chatUser.sign);
                                        sb.append(" width=15 height=12 align=bottom title=\"");
                                        sb.append(chatUser.status != null ? chatUser.status : "");
                                        sb.append("\">&nbsp;");
                                        result = result.substring(0, posSpanTagStart)
                                                + sb + result.substring(posSpanTagStart);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Для корректной обработки кликов по нику в парном/клан‑чате.
        if (result.toLowerCase(Locale.ROOT).contains("pair:")) {
            result = result.replace("<SPAN title=\"%", "<SPAN title=\"%%%");
        } else if (result.toLowerCase(Locale.ROOT).contains("clan:")) {
            result = result.replace("<SPAN title=\"%", "<SPAN title=\"%%");
        }

        // Замена [[[logid]]] на ссылку лога боя.
        while (true) {
            int pos1 = result.indexOf("[[[");
            if (pos1 == -1) break;
            int pos2 = result.indexOf("]]]", pos1);
            if (pos2 == -1) break;
            String sorig = result.substring(pos1 + 3, pos2);
            String msg = "";
            if (!sorig.contains(":")) {
                msg = "<a href=http://www.neverlands.ru/logs.fcg?fid=" + sorig +
                        " onclick=\"window.open(this.href);\">лог</a> боя";
            }
            result = result.substring(0, pos1) + msg + result.substring(pos2 + 3);
        }

        Chat.addStringToChat(result);
        return result;
    }

    private static String extractSpanNick(String message) {
        if (message == null) return null;
        Matcher matcher = SPAN_NICK_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
