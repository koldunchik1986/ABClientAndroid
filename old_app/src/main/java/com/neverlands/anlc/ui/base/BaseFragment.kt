package com.neverlands.anlc.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment

/**
 * Базовый Fragment для всех Fragment в приложении.
 * Предоставляет общую функциональность, такую как показ Toast-сообщений.
 */
open class BaseFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Здесь можно добавить общую логику для всех Fragment
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    /**
     * Показывает короткое Toast-сообщение.
     * @param message Сообщение для отображения.
     */
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Показывает длинное Toast-сообщение.
     * @param message Сообщение для отображения.
     */
    fun showLongToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
