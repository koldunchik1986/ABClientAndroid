package ru.neverlands.abclient.model;

/**
 * Модель быстрой кнопки.
 * Хранит информацию о назначенной функции на конкретной позиции.
 */
public class QuickButton {
    private int position;
    private QuickActionType actionType;
    private String customName;

    public QuickButton(int position, QuickActionType actionType) {
        this.position = position;
        this.actionType = actionType;
        this.customName = null;
    }

    public QuickButton(int position, QuickActionType actionType, String customName) {
        this.position = position;
        this.actionType = actionType;
        this.customName = customName;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public QuickActionType getActionType() {
        return actionType;
    }

    public void setActionType(QuickActionType actionType) {
        this.actionType = actionType;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getDisplayName() {
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        return actionType != null ? actionType.getDisplayName() : "";
    }

    public boolean isEmpty() {
        return actionType == null || actionType == QuickActionType.NONE;
    }
}
