package ru.neverlands.abclient.postfilter;

import ru.neverlands.abclient.utils.Russian;

/**
 * Класс для обработки содержимого файла msg.php.
 * Аналог MsgPhp.cs в оригинальном приложении.
 */
public class MsgPhp {

    /**
     * Обрабатывает HTML-содержимое msg.php, заменяя ссылки на смайлы
     * и добавляя обработчики для различных типов ссылок (персонажи, кланы, предметы и т.д.)
     * для взаимодействия с нативным кодом Android через window.external.
     * @param array Массив байт, содержащий HTML-содержимое msg.php.
     * @return Обработанный массив байт.
     */
    public static byte[] process(byte[] array) {
        // Преобразуем массив байт в строку, используя кодировку Windows-1251
        String html = Russian.getString(array);

        // Заменяем ссылки на смайлы на локальные ресурсы Android assets
        html = html.replace("http://image.neverlands.ru/chat/smiles/", "file:///android_asset/Icons/Smiles/");

        // Добавляем обработчики для различных типов ссылок, перенаправляя их на window.external
        // Это позволяет нативному Android-коду перехватывать и обрабатывать эти события.
        html = html.replace("show_info('", "window.external.ShowInfo('");
        html = html.replace("show_clan_info('", "window.external.ShowClanInfo('");
        html = html.replace("show_boi_info('", "window.external.ShowBoiInfo('");
        html = html.replace("show_item_info('", "window.external.ShowItemInfo('");
        html = html.replace("show_spell_info('", "window.external.ShowSpellInfo('");
        html = html.replace("show_loc_info('", "window.external.ShowLocInfo('");
        html = html.replace("show_torg_info('", "window.external.ShowTorgInfo('");
        html = html.replace("show_note_info('", "window.external.ShowNoteInfo('");
        html = html.replace("show_msg_info('", "window.external.ShowMsgInfo('");
        html = html.replace("show_inv_info('", "window.external.ShowInvInfo('");
        html = html.replace("show_bot_info('", "window.external.ShowBotInfo('");
        html = html.replace("show_guild_info('", "window.external.ShowGuildInfo('");
        html = html.replace("show_quest_info('", "window.external.ShowQuestInfo('");
        html = html.replace("show_event_info('", "window.external.ShowEventInfo('");
        html = html.replace("show_group_info('", "window.external.ShowGroupInfo('");
        html = html.replace("show_inv_item_info('", "window.external.ShowInvItemInfo('");
        html = html.replace("show_shop_item_info('", "window.external.ShowShopItemInfo('");
        html = html.replace("show_bag_item_info('", "window.external.ShowBagItemInfo('");
        html = html.replace("show_bank_item_info('", "window.external.ShowBankItemInfo('");
        html = html.replace("show_mail_item_info('", "window.external.ShowMailItemInfo('");
        html = html.replace("show_clan_storage_item_info('", "window.external.ShowClanStorageItemInfo('");
        html = html.replace("show_trade_item_info('", "window.external.ShowTradeItemInfo('");
        html = html.replace("show_auction_item_info('", "window.external.ShowAuctionItemInfo('");
        html = html.replace("show_clan_shop_item_info('", "window.external.ShowClanShopItemInfo('");
        html = html.replace("show_guild_shop_item_info('", "window.external.ShowGuildShopItemInfo('");
        html = html.replace("show_city_shop_item_info('", "window.external.ShowCityShopItemInfo('");
        html = html.replace("show_faction_shop_item_info('", "window.external.ShowFactionShopItemInfo('");
        html = html.replace("show_event_shop_item_info('", "window.external.ShowEventShopItemInfo('");
        html = html.replace("show_quest_shop_item_info('", "window.external.ShowQuestShopItemInfo('");

        // Преобразуем измененную строку обратно в массив байт в кодировке Windows-1251
        return Russian.getBytes(html);
    }
}
