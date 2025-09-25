package com.neverlands.anlc.data.local

import java.util.Date
import com.neverlands.anlc.data.local.model.AutoboiState

object AppVars {
    var fishNoCaptchaReady: Boolean = false
    var codeAddress: String = ""
    var fightLink: String = ""
    var autoFishMassa: String = ""
    var priSelected: Boolean = false
    var namePri: String = ""
    var valPri: Int = 0
    var neverTimer: Date? = null
    var lastBoiTimer: Date = Date()
    var doHerbAutoCut: Boolean = false
    var usersOnline: String = ""
    var autoboi: AutoboiState = AutoboiState.AutoboiOff
    var lastChatChanged: Date = Date()
    var chatCritical: Boolean = false
    var autoMoving: Boolean = false
    var autoMovingJumps: Int = 0
    var autoMovingDestination: String = ""
    var doSearchBox: Boolean = false
    var bulkSellThing: String = ""
    var bulkSellPrice: Int = 0
    var bulkSellSum: Int = 0
    var bulkDropThing: String = ""
    var bulkDropPrice: String = ""
    var bulkSellOldName: String = ""
    var bulkSellOldPrice: String = ""
    var bulkSellOldScript: String = ""
    // TODO: Add other global application variables as needed
}