package com.neverlands.anlc.ui.base

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Базовая Activity для всех Activity в приложении.
 * Предоставляет общую функциональность, такую как показ Toast-сообщений.
 */
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Здесь можно добавить общую логику для всех Activity
    }

    /**
     * Показывает короткое Toast-сообщение.
     * @param message Сообщение для отображения.
     */
    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Показывает длинное Toast-сообщение.
     * @param message Сообщение для отображения.
     */
    fun showLongToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
