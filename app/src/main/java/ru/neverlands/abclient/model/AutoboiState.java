package ru.neverlands.abclient.model;

/**
 * Состояния автобоя.
 * Портировано из AutoboiState.cs.
 */
public enum AutoboiState {
    /** Все отключено. */
    AutoboiOff,
    
    /** Автобой, нанесение ударов. */
    AutoboiOn,
    
    /** Восстановление перед кнопкой "завершить". */
    Restoring,
    
    /** Ожидание таймаута боя. */
    Timeout,
    
    /** Вычисление цифр. */
    Guamod
}
