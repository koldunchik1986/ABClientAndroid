
### 1. План портирования MapPath.cs

Файл `MapPath.cs` содержит основную, используемую в проекте, реализацию алгоритма поиска пути. Этот алгоритм является вариацией **поиска в ширину (BFS)**, очень похожей на реализацию в `MapPath_0103.cs`.

### 2. Функциональность в C#

- **Назначение:** Найти оптимальный (в первую очередь, кратчайший по числу шагов) путь между двумя точками.
- **Алгоритм:** Поиск в ширину, который итеративно исследует "слои" карты, удаляющиеся от стартовой точки.
- **Отличия от `MapPath_0103.cs`:**
    - **Логика Острова:** Содержит жестко закодированный список ячеек острова (`_islandCells`). Если путь ведет с материка на остров, точка старта принудительно меняется на "11-398" (вероятно, причал).
    - **Отсечение пути:** Имеет дополнительную эвристику для прекращения поиска по ветке, если она становится на 10 шагов длиннее, чем уже найденный лучший путь.

### 3. Решение для портирования на Android

Рекомендации те же, что и для других файлов `MapPath_*.cs`. Следует использовать стандартную библиотеку для поиска пути, такую как **JGraphT**.

### 4. План реализации

### 4. План реализации

Поскольку в коде Android отсутствует реализация поиска пути, этот план описывает создание системы с нуля с использованием библиотеки JGraphT.

1.  **Интегрировать JGraphT:**
    - Добавить зависимость в `build.gradle`:
      ```groovy
      implementation 'org.jgrapht:jgrapht-core:1.5.1'
      ```

2.  **Создать класс `MapGraph`:**
    - Этот класс будет отвечать за построение графа.
      ```kotlin
      import org.jgrapht.graph.DefaultWeightedEdge
      import org.jgrapht.graph.SimpleDirectedWeightedGraph

      object MapGraph {
          val graph = SimpleDirectedWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)

          // Этот метод нужно будет вызвать один раз после загрузки данных из БД
          suspend fun build(mapRepository: MapRepository) {
              val allCells = mapRepository.getAllCells()
              for (cell in allCells) {
                  graph.addVertex(cell.cellNumber)
              }

              for (cell in allCells) {
                  // Добавить ребра к 8 соседям
                  val neighbors = mapRepository.getNeighbors(cell.cellNumber)
                  for (neighbor in neighbors) {
                      val edge = graph.addEdge(cell.cellNumber, neighbor.cellNumber)
                      graph.setEdgeWeight(edge, neighbor.cost.toDouble())
                  }

                  // Добавить ребра для врат и телепортов
                  // ...
              }
          }
      }
      ```

3.  **Создать класс `Pathfinder`:**
    - Будет использовать JGraphT для поиска пути.
      ```kotlin
      import org.jgrapht.alg.shortestpath.AStarShortestPath
      import org.jgrapht.alg.interfaces.AStarAdmissibleHeuristic

      class Pathfinder(private val graph: SimpleDirectedWeightedGraph<String, DefaultWeightedEdge>) {

          fun findPath(start: String, end: String): List<String>? {
              // Эвристика для A* (можно использовать манхэттенское расстояние)
              val heuristic = object : AStarAdmissibleHeuristic<String> {
                  override fun getCostEstimate(source: String, target: String): Double {
                      // TODO: Реализовать расчет расстояния между ячейками
                      return 0.0
                  }
              }

              val aStar = AStarShortestPath(graph, heuristic)
              val path = aStar.getPath(start, end)
              return path?.vertexList
          }
      }
      ```

4.  **Создать data-класс `MapPath`:**
    - Будет хранить результат и содержать логику навигации по нему.
      ```kotlin
      data class MapPath(val path: List<String>, val totalCost: Double) {
          var currentIndex: Int = 0
          val destination: String = path.last()

          fun getNextJump(): String? {
              if (currentIndex + 1 < path.size) {
                  return path[currentIndex + 1]
              }
              return null
          }
          // ... остальная логика из CanUseExistingPath
      }
      ```

- [ ] Добавить зависимость `JGraphT`.
- [ ] Реализовать `MapGraph` для построения полного графа карты, включая все типы переходов.
- [ ] Реализовать `Pathfinder` с использованием `AStarShortestPath`.
- [ ] Реализовать эвристику для A* (например, на основе координат).
- [ ] Создать итоговый класс `MapPath` для использования в навигаторе.
