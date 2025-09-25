Файл `MapManager.kt` содержит логику управления картой, включая загрузку данных карты из XML-файлов (`map.xml`, `abcells.xml`), хранение информации о ячейках карты и их свойствах, а также вспомогательные функции для работы с координатами и регионами. Этот файл является центральным для обработки картографических данных в приложении.

**Ключевые особенности:**

*   **`init(context: Context)`:** Инициализирует менеджер карты, загружая данные из `map.xml` и `abcells.xml`.
*   **`locations` (MutableMap<String, Position>):** Хранит информацию о позициях на карте, где ключ — это строка-координата (например, "999/997_999"), а значение — объект `Position`.
*   **`invLocations` (MutableMap<String, String>):** Обратное отображение для `locations`, где ключ — это `regNum` (например, "1-001"), а значение — строка-координата.
*   **`cells` (MutableMap<String, Cell>):** Хранит информацию о ячейках карты, где ключ — это `cellNumber` (например, "1-001"), а значение — объект `Cell`.
*   **`abcCells` (MutableMap<String, AbcCell>):** Хранит дополнительную информацию о ячейках карты, специфичную для приложения, где ключ — это `regNum`, а значение — объект `AbcCell`.
*   **`addRegions()`:** Инициализирует предопределенные регионы карты.
*   **`makeRegNum(reg: String, k: Int): String`:** Генерирует строковый идентификатор региона (например, "1-001").
*   **`makePosition(x: Int, y: Int): String`:** Генерирует строковое представление координат (например, "999/997_999").
*   **`loadMap(inputStream: InputStream)`:** Загружает данные карты из `map.xml`.
*   **`loadAbcMap(inputStream: InputStream)`:** Загружает дополнительные данные карты из `abcells.xml`.
*   **`isCellExists(x: Int, y: Int): Boolean`:** Проверяет, существует ли ячейка с заданными координатами.
*   **`makeVisit(x: Int, y: Int)`:** Отмечает ячейку как посещенную и сохраняет `abcCells`.
*   **`saveAbcMap()`:** Сохраняет данные `abcCells` в файл `abcells.xml`.
*   **`genMoveLink(x: Int, y: Int): String`:** Генерирует ссылку для перемещения на заданные координаты.
*   **`getCellAltText(x: Int, y: Int, scale: Int): String`:** Генерирует альтернативный текст для ячейки карты (используется для всплывающих подсказок).
*   **`isCellInPath(x: Int, y: Int): Boolean`:** **TODO:** Требует реализации логики, связанной с автоматическим перемещением.
*   **`getCellDivText(x: Int, y: Int, scale: Int, link: String, showmove: Boolean, isframe: Boolean): String`:** Генерирует HTML-код для отображения информации о ячейке карты.
*   **`getMapText(): String`:** **TODO:** Требует реализации логики для получения текста, связанного с картой.

**Зависимости:**

*   `android.content.Context`
*   `android.util.Xml`
*   `com.neverlands.anlc.data.model.map.AbcCell`
*   `com.neverlands.anlc.data.model.map.Cell`
*   `com.neverlands.anlc.data.model.map.MapBot`
*   `com.neverlands.anlc.data.model.map.Position`
*   `org.xmlpull.v1.XmlPullParser`
*   `java.io.InputStream`
*   `java.text.SimpleDateFormat`
*   `java.util.Date`
*   `java.util.Locale`

**TODOs:**

*   `isCellInPath(x: Int, y: Int): Boolean`: Реализовать логику, связанную с автоматическим перемещением.
*   `getCellDivText(x: Int, y: Int, scale: Int, link: String, showmove: Boolean, isframe: Boolean): String`: Добавить более сложную логику для рыбы, воды, ботов, трав, статуса посещения.
*   `getMapText(): String`: Реализовать логику для получения текста, связанного с картой.
