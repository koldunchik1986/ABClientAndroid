package com.neverlands.anlc.data.local.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue // Add this import

/**
 * Модель данных для профиля пользователя.
 * Хранит всю необходимую информацию для входа и конфигурации.
 * @param userNick Игровой ник.
 * @param encryptedPassword Зашифрованный пароль для входа в игру.
 * @param encryptedPasswordFlash Зашифрованный пароль для флеш-запросов (если отличается).
 * @param useAutoLogon Флаг автоматического входа.
 * @param useProxy Флаг использования прокси.
 * @param autoClearCookies Флаг автоматической очистки кук перед входом.
 * @param configPasswordHash MD5-хеш пароля от конфигурации (для проверки правильности пароля).
 */
@Parcelize // Add this annotation
data class Profile(
    val userNick: String,
    var encryptedPassword: String = "",
    var encryptedPasswordFlash: String = "",
    var useAutoLogon: Boolean = false,
    var useProxy: Boolean = true,
    var autoClearCookies: Boolean = true,
    var configPasswordHash: String? = null,
    var mapBigWidth: Int = 11,
    var mapBigHeight: Int = 11,
    var mapBigScale: Int = 100,
    var mapShowMiniMap: Boolean = true,
    var mapDrawRegion: Boolean = false,
    var fishStopOverWeight: Boolean = false,
    var fishAuto: Boolean = false,
    var doGuamod: Boolean = false,
    var lezDoAutoboi: Boolean = false,
    var fishEnabledPrims: Int = 0,
    var contacts: @RawValue MutableMap<String, Contact> = mutableMapOf(),
    var doShowFastAttack: Boolean = false,
    var doShowFastAttackBlood: Boolean = true,
    var doShowFastAttackUltimate: Boolean = true,
    var doShowFastAttackClosedUltimate: Boolean = true,
    var doShowFastAttackClosed: Boolean = true,
    var doShowFastAttackFist: Boolean = false,
    var doShowFastAttackClosedFist: Boolean = true,
    var doShowFastAttackOpenNevid: Boolean = true,
    var doShowFastAttackPoison: Boolean = true,
    var doShowFastAttackStrong: Boolean = true,
    var doShowFastAttackNevid: Boolean = true,
    var doShowFastAttackFog: Boolean = true,
    var doShowFastAttackZas: Boolean = true,
    var doShowFastAttackTotem: Boolean = true,
    var doShowFastAttackPortal: Boolean = true,
    var herbCells: @RawValue MutableMap<String, HerbCell> = mutableMapOf(),
    var mapLocation: String = "",
    var herbsAutoCut: MutableList<String> = mutableListOf(),
    var chatHeight: Int = 0,
    var chatDelay: Int = 0,
    var chatMode: Int = 0,
    var ChatKeepLog: Boolean = false,
    var persReady: Long = 0L,
    var showOverWarning: Boolean = false,
    var servDiff: Long = 0L,
    var doAutoAnswer: Boolean = false,
    var doChatLevels: Boolean = false
) : Parcelable // Implement Parcelable