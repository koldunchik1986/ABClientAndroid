package com.neverlands.anlc.ui.login

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.neverlands.anlc.R // Assuming R.layout.dialog_password_input will be created

/**
 * Диалог для ввода пароля.
 * Используется для запроса пароля от пользователя.
 */
class PasswordInputDialog(private val onPasswordEntered: (String) -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_password_input, null)
        val passwordEditText = view.findViewById<EditText>(R.id.passwordEditText)

        builder.setView(view)
            .setTitle("Введите пароль")
            .setPositiveButton("ОК") { dialog, _ ->
                val password = passwordEditText.text.toString()
                onPasswordEntered(password)
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.cancel()
            }
        return builder.create()
    }
}