
### 1. План портирования GroupCell.cs

Файл `GroupCell.cs` представляет собой класс для группировки ячеек карты по какому-либо признаку (например, по типу монстров или ресурсов).

### 2. Функциональность в C#

- **Назначение:** Хранить группу ячеек карты с общим именем и, возможно, уровнем.
- **Свойства:**
    - `Name`: Имя группы (например, "Крысы").
    - `Level`: Необязательный уровень, связанный с группой.
    - `Cells`: `SortedList` (отсортированный список) строковых идентификаторов ячеек, входящих в группу.
- **Логика:**
    - Реализует `IComparable` для возможности сортировки групп (сначала по имени, затем по уровню).
    - `AddCell()`: Добавляет ячейку в группу.
    - `GetCells()`: Возвращает все идентификаторы ячеек группы в виде одной строки, разделенной символом `|`.

### 3. Решение для портирования на Android

Это простая структура данных, которую легко портировать.

### 4. План реализации

1.  **Создать data-класс `GroupCell` в Kotlin:**
    - Класс будет реализовывать `Comparable<GroupCell>` для обеспечения сортировки.
    - `Cells` можно реализовать как `TreeSet<String>` для автоматической сортировки и уникальности элементов.
    - **Kotlin:**
      ```kotlin
      data class GroupCell(
          val name: String,
          val level: Int = -1,
          val cells: TreeSet<String> = TreeSet()
      ) : Comparable<GroupCell> {
          override fun compareTo(other: GroupCell): Int {
              val nameCompare = name.compareTo(other.name, ignoreCase = true)
              if (nameCompare != 0) {
                  return nameCompare
              }
              return level.compareTo(other.level)
          }

          fun getCellsString(): String {
              return cells.joinToString("|")
          }
      }
      ```

- [ ] Создать data-класс `GroupCell`, реализующий `Comparable`.
- [ ] Использовать `TreeSet` для хранения ячеек.
- [ ] Реализовать методы `addCell` и `getCellsString`.
