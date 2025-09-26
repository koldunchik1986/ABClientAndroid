# План портирования Misc.cs

Файл `Misc.cs` содержит утилиту для "глубокого" копирования объектов.

## Функциональность в C#

Класс содержит один статический метод `DeepClone(object obj)`.

*   **Назначение**: Создать полную, независимую копию объекта, включая все вложенные объекты.
*   **Алгоритм**: Метод использует стандартный для .NET трюк с сериализацией. Он сериализует объект в поток в памяти с помощью `BinaryFormatter`, а затем десериализует его обратно в новый экземпляр. Это работает для любых объектов, помеченных атрибутом `[Serializable]`.

## Решение для портирования на Android

**Прямое портирование этого метода невозможно**, так как в Java нет прямого аналога `BinaryFormatter`.

Задача глубокого копирования в Java/Android решается другими способами, и выбор зависит от конкретного класса, который нужно скопировать.

## План реализации (вместо портирования)

Когда в портируемом коде встретится вызов `Misc.DeepClone(someObject)`, необходимо будет выбрать один из следующих подходов для класса `someObject`:

### 1. Реализация интерфейса `Cloneable` (классический способ)

Это самый "правильный", но и самый трудоемкий способ.

```java
public class MyObject implements Cloneable {
    private String name;
    private InnerObject inner; // Вложенный объект

    // ... конструкторы, геттеры, сеттеры ...

    @Override
    public Object clone() throws CloneNotSupportedException {
        MyObject cloned = (MyObject) super.clone();
        // Для вложенных объектов также нужно вызывать clone()
        cloned.inner = (InnerObject) this.inner.clone(); 
        return cloned;
    }
}
```

### 2. Сериализация в JSON (современный и простой способ)

Если класс является простым POJO (Plain Old Java Object) без сложной логики, его можно легко скопировать с помощью библиотеки для работы с JSON, например, **Gson**.

1.  Добавить зависимость `implementation 'com.google.code.gson:gson:2.8.9'` в `build.gradle`.
2.  Создать утилитный метод:
    ```java
    import com.google.gson.Gson;

    public class MiscUtils {
        private static final Gson gson = new Gson();

        public static <T> T deepClone(T object, Class<T> classOfT) {
            String json = gson.toJson(object);
            return gson.fromJson(json, classOfT);
        }
    }
    ```
3.  Использование: `MyObject newObj = MiscUtils.deepClone(oldObj, MyObject.class);`

### 3. Конструктор копирования

В класс добавляется конструктор, который принимает другой объект того же типа.

```java
public class MyObject {
    private String name;

    public MyObject(String name) { this.name = name; }

    // Конструктор копирования
    public MyObject(MyObject other) {
        this.name = other.name;
    }
}

// Использование
MyObject newObj = new MyObject(oldObj);
```

**Рекомендация:** Для большинства случаев **второй способ (сериализация в JSON)** будет самым быстрым и удобным в реализации.
