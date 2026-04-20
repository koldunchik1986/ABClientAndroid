namespace ABClient.Neuro
{
    using System;
    using System.Collections.Generic;
    using System.Drawing;
    using System.Drawing.Imaging;
    using System.IO;
    using System.IO.Compression;
    using System.Text;

    internal class NeuroBase
    {
        private int ConstNumDigits = 5;
        private static readonly List<NeuroVector> listVectors = new List<NeuroVector>();
        private List<double[]> listMatrix;
        private List<double[]> listMatrixRaw;
        private double[] arrayDistances;
        private static readonly StringBuilder gyp = new StringBuilder();
        private static long elapsedTime;

        // Отладочные данные
        private static double[] debugDistances;
        private static int debugNumDigits;
        private static int debugWidth;
        private static int debugHeight;
        private static int debugXLeft;
        private static int debugXRight;
        private static int debugRealWidth;
        private bool[,] isDigitOutline; // Маска для хранения контуров цифр

        // Данные для обучения
        // lastListMatrixRaw — ненормализованные (0..1), для фильтрации мусора по blackRatio
        // lastListMatrix — нормализованные (sum=1), для добавления в базу
        private static List<double[]> lastListMatrix = new List<double[]>();
        private static List<double[]> lastListMatrixRaw = new List<double[]>();
        private static double[] lastArrayDistances = new double[0];
        private static int lastConstNumDigits;

        // ПАРАМЕТР РАСКЛЕИВАНИЯ: Насколько "утоньшать" группы пикселей при сегментации

        // ЦВЕТА
        // Основной цвет цифр и сетки на оригинале
        private static readonly Color ColorDigitOriginal = Color.FromArgb(157, 28, 36); // #9c1c24
        // Цвет, в который красим восстановленные контуры
        private static readonly Color ColorDigitTarget = Color.FromArgb(157, 28, 36); 
        // Белый фон
        private static readonly Color ColorWhite = Color.FromArgb(255, 255, 255);

        internal NeuroBase()
        {
            listMatrix = new List<double[]>(ConstNumDigits);
            listMatrixRaw = new List<double[]>(ConstNumDigits);
            arrayDistances = new double[ConstNumDigits];
        }

        internal static string DebugInfo()
        {
            if (debugDistances == null)
                return "RECOGNIZE_DEBUG: no data yet";

            var distStr = new StringBuilder("[");
            for (var i = 0; i < debugDistances.Length; i++)
            {
                if (i > 0) distStr.Append(", ");
                distStr.Append(debugDistances[i].ToString("F3"));
            }
            distStr.Append("]");

            return string.Format("RECOGNIZE_DEBUG: digits={0}, w={1}, h={2}, xleft={3}, xright={4}, realwidth={5}, result={6}, dist={7}",
                debugNumDigits, debugWidth, debugHeight, debugXLeft, debugXRight, debugRealWidth, gyp.ToString(), distStr.ToString());
        }

        /// <summary>
        /// Автоопределение количества цифр на капче.
        /// </summary>
        private static int CountDigitGroups(bool[] columnHasBlack, int xleft, int xright, int imageWidth)
        {
            if (imageWidth >= 125 && imageWidth <= 140)
            {
                var gapCount = 0;
                var inBlack = false;
                var emptyRun = 0;
                for (var x = xleft; x <= xright; x++)
                {
                    if (columnHasBlack[x])
                    {
                        if (!inBlack && emptyRun >= 1)
                            gapCount++;
                        inBlack = true;
                        emptyRun = 0;
                    }
                    else
                    {
                        emptyRun++;
                        inBlack = false;
                    }
                }

                var groups = gapCount + 1;
                if (groups >= 4 && groups <= 7)
                    return groups;

                return 5;
            }

            var count = 0;
            var inGroup = false;
            var gapColumns = 0;
            var minGap = 1;

            var groupStarts = new List<int>();
            var groupEnds = new List<int>();
            var currentStart = -1;

            for (var x = xleft; x <= xright; x++)
            {
                if (columnHasBlack[x])
                {
                    if (currentStart == -1)
                        currentStart = x;
                    gapColumns = 0;
                    if (!inGroup)
                    {
                        inGroup = true;
                    }
                }
                else
                {
                    if (inGroup) 
                    {
                        gapColumns++;
                        if (gapColumns >= minGap)
                        {
                            inGroup = false;
                            if (currentStart != -1)
                            {
                                groupStarts.Add(currentStart);
                                groupEnds.Add(x - gapColumns);
                                currentStart = -1;
                            }
                        } 
                    }
                }
            }

            if (currentStart != -1)
            {
                groupStarts.Add(currentStart);
                groupEnds.Add(xright);
            }

            count = groupStarts.Count;
            if (count == 0)
                return 5;

            if (count == 1)
                return 5;

            var totalWidth = 0;
            for (var i = 0; i < count; i++)
                totalWidth += groupEnds[i] - groupStarts[i] + 1;

            var avgWidth = totalWidth / count;
            var expandedCount = count;
            for (var i = 0; i < count; i++)
            {
                var gw = groupEnds[i] - groupStarts[i] + 1;
                if (gw > avgWidth * 1.5)
                    expandedCount++;
            }

            if (expandedCount < 4) expandedCount = 4;
            if (expandedCount > 7) expandedCount = 7;
            return expandedCount;
        }

        internal static string Gyp() { return gyp.ToString(); }
        internal static int NumNodes() { return listVectors.Count; }
        internal static long ElapsedTime() { return elapsedTime; }

        /// <summary>
        /// Идентифицирует пиксели, принадлежащие контурам цифр (анти-алиасинг по краям штрихов),
        /// и расширяет их на 1 пиксель влево/вправо чтобы заполнить однопиксельные горизонтальные разрывы.
        ///
        /// Почему работает:
        ///   Линии сетки нарисованы строго цветом #9c1c24 (R=157).
        ///   Края цифровых штрихов имеют ЧУТЬ БОЛЕЕ СВЕТЛЫЙ оттенок (анти-алиасинг):
        ///   R в диапазоне (157..220), G и B тоже немного выше базовых (28, 36).
        ///   Линии сетки анти-алиасинга НЕ ИМЕЮТ (они тонкие, 1px) — поэтому только
        ///   цифры дают пиксели в этом диапазоне.
        ///
        /// Горизонтальное расширение:
        ///   После удаления ровного цвета (#9c1c24) от штрихов остаётся только контур.
        ///   Добавляем соседей ±1px по горизонтали — это "утолщает" контур и заполняет
        ///   однопиксельные разрывы, которые иначе мешают определению групп столбцов.
        /// </summary>
        private void IdentifyDigitOutlines(Bitmap bitmapSource, int width, int height, int DR, int DG, int DB)
        {
            isDigitOutline = new bool[width, height];

            // Проход 1: определяем "сырые" анти-алиасинговые пиксели
            var raw = new bool[width, height];
            for (var y = 0; y < height; y++)
                for (var x = 0; x < width; x++)
                {
                    var c = bitmapSource.GetPixel(x, y);
                    // Анти-алиасинговый пиксель: чуть светлее базового цвета цифры по всем каналам
                    raw[x, y] = (c.R > DR && c.R < 220 &&
                                 c.G > DG && c.G < 220 &&
                                 c.B > DB && c.B < 220);
                }

            // Проход 2: расширяем на ±1px по горизонтали
            // Это заполняет разрывы и "утолщает" контур для надёжной работы
            // колоночного анализа (colScore)
            for (var y = 0; y < height; y++)
                for (var x = 0; x < width; x++)
                {
                    if (raw[x, y])
                    {
                        isDigitOutline[x, y] = true;
                        if (x > 0)           isDigitOutline[x - 1, y] = true; // сосед слева
                        if (x < width - 1)   isDigitOutline[x + 1, y] = true; // сосед справа
                    }
                }
        }

        internal void Calculate(Bitmap bitmapSource)
        {
            var startProcess = DateTime.Now.Ticks;

            // Полные размеры bitmap (без старого трюка -2)
            var width  = bitmapSource.Size.Width;
            var height = bitmapSource.Size.Height;

            debugWidth  = width;
            debugHeight = height;

            // Компоненты целевого цвета цифр (#9c1c24 = R:157, G:28, B:36)
            // Используем константы, чтобы не путаться и легко менять при необходимости
            const int DR = 157, DG = 28, DB = 36;

            // Рабочий буфер — все манипуляции с цветами происходят здесь,
            // оригинальный bitmapSource не трогаем
            using (var workBitmap = new Bitmap(width, height, PixelFormat.Format24bppRgb))
            {
            // ════════════════════════════════════════════════════════════════
            // ШАГ 0: ПРЕДВАРИТЕЛЬНАЯ ИДЕНТИФИКАЦИЯ КОНТУРОВ ЦИФР
            // Заполняет isDigitOutline для использования в следующих шагах.
            // ════════════════════════════════════════════════════════════════
            IdentifyDigitOutlines(bitmapSource, width, height, DR, DG, DB);

            // ════════════════════════════════════════════════════════════════
            // ШАГ 1: УДАЛЕНИЕ ЛИНИЙ СЕТКИ-РОМБОВ (#9c1c24 → белый)
                //
                // Линии ромбов нарисованы ровно одним цветом: R=157, G=28, B=36.
                // Это их отличительная черта — ТОЧНЫЙ постоянный цвет без вариаций.
                // Заменяем все такие пиксели на белый.
                //
                // ВАЖНО: тело цифровых штрихов тоже имеет этот цвет,
                // поэтому оно тоже исчезнет — но это нормально!
                // Края цифровых штрихов имеют ЧУТЬ БОЛЕЕ СВЕТЛЫЙ оттенок
                // (анти-алиасинг), и они будут восстановлены на шаге 2.
                // ════════════════════════════════════════════════════════════════
                for (var y = 0; y < height; y++)
                    for (var x = 0; x < width; x++)
                    {
                        var c = bitmapSource.GetPixel(x, y);
                        // Точное совпадение → это линия сетки или тело цифры → в белый
                        workBitmap.SetPixel(x, y,
                            (c.R == DR && c.G == DG && c.B == DB && !isDigitOutline[x, y]) ? Color.White : c);
                    }

                // ════════════════════════════════════════════════════════════════
                // ШАГ 3: УДАЛЕНИЕ СЕРЫХ АРТЕФАКТОВ (#dddddd → белый)
                //
                // После удаления сетки могут остаться серые точки — тени
                // пересечений ромбов, узор фона и т.п. Их цвет #dddddd:
                //   R=221, G=221, B=221
                // Убираем их в белый, чтобы они не мешали бинаризации.
                // ════════════════════════════════════════════════════════════════
                for (var y = 0; y < height; y++)
                    for (var x = 0; x < width; x++)
                    {
                        var c = workBitmap.GetPixel(x, y);
                        if (c.R == 221 && c.G == 221 && c.B == 221)
                            workBitmap.SetPixel(x, y, Color.White);
                    }

                // ════════════════════════════════════════════════════════════════
                // ШАГ 4: БИНАРИЗАЦИЯ — СТРОИМ ЧЁРНО-БЕЛУЮ МАСКУ
                //
                // После трёх шагов в workBitmap:
                //   • Пиксели цифровых контуров = #9c1c24 (R=157, G=28, B=36)
                //   • Всё остальное ≈ белый (фон + убранная сетка)
                //
                // Переводим в ч/б: #9c1c24 → чёрный, всё остальное → белый.
                //
                // Заодно собираем данные по столбцам для сегментации:
                //   columnHasBlack[x] — есть ли хоть один чёрный пиксель в столбце x
                //   arrayTops[x]      — y верхнего чёрного пикселя в столбце x
                //   arrayBottoms[x]   — y нижнего чёрного пикселя в столбце x
                // ════════════════════════════════════════════════════════════════
                var columnHasBlack = new bool[width];
                var arrayTops      = new int[width];
                var arrayBottoms   = new int[width];
                for (var i = 0; i < width; i++) { arrayTops[i] = -1; arrayBottoms[i] = -1; }

                using (var binaryBitmap = new Bitmap(width, height, PixelFormat.Format24bppRgb))
                {
                    // colScore[x] = количество контурных пикселей в столбце x.
                    // Это "вертикальная проекция" — основа для правильной сегментации.
                    // Там где цифра — высокое значение, там где пробел между цифрами — низкое (долина).
                    var colScore = new int[width];

                    for (var x = 0; x < width; x++)
                        for (var y = 0; y < height; y++)
                        {
                            var c = workBitmap.GetPixel(x, y);

                            // Пиксель цифры — только если он был идентифицирован как контур
                            var isDigit = isDigitOutline[x, y];

                            binaryBitmap.SetPixel(x, y, isDigit ? Color.Black : Color.White);

                            if (isDigit)
                            {
                                colScore[x]++;           // вертикальная проекция
                                columnHasBlack[x] = true;
                                if (arrayTops[x] == -1) arrayTops[x] = y;
                                arrayBottoms[x] = y;
                            }
                        }

                // ── ШАГ 3: ПОИСК ГРАНИЦ И СЕГМЕНТАЦИЯ ─────────────────────────
                int xleft = -1;
                int xright = -1;
                for (var x = 0; x < width; x++)
                {
                    if (columnHasBlack[x])
                    {
                        if (xleft == -1) xleft = x;
                        xright = x;
                    }
                }

                // Если ничего не нашли (пустая капча или полный сбой фильтрации)
                if (xleft == -1 || xright == -1)
                {
                    ConstNumDigits = 5;
                listMatrix.Clear();
                listMatrixRaw.Clear();
                    gyp.Length = 0;
                    arrayDistances = new double[5];
                    for(int i=0; i<5; i++) { listMatrix.Add(new double[100]); listMatrixRaw.Add(new double[100]); gyp.Append('?'); arrayDistances[i] = 999.0; }
                    debugDistances = new double[5];
                    Array.Copy(arrayDistances, debugDistances, 5);
                    elapsedTime = DateTime.Now.Ticks - startProcess;
                    return; // using workBitmap/binaryBitmap автоматически задиспозятся
                }

                // Автоопределение количества цифр
                ConstNumDigits = CountDigitGroups(columnHasBlack, xleft, xright, width);
                
                debugXLeft = xleft;
                debugXRight = xright;
                debugNumDigits = ConstNumDigits;

                listMatrix.Clear();
                gyp.Length = 0;
                arrayDistances = new double[ConstNumDigits];

                var realwidth = xright - xleft;
                debugRealWidth = realwidth;

                // Сегментация по проекционным долинам
                var segments = BuildDigitSegments(colScore, xleft, xright, ConstNumDigits);

                var charBitmapsList = new List<Bitmap>();
                var smallBitmapsList = new List<Bitmap>();

                for (var numchar = 0; numchar < ConstNumDigits; numchar++)
                {
                    // Защита от выхода за границы массива сегментов
                    if (numchar >= segments.Count)
                    {
                        charBitmapsList.Add(null);
                        smallBitmapsList.Add(null);
                        listMatrix.Add(new double[100]);
                        listMatrixRaw.Add(new double[100]);
                        gyp.Append('?');
                        arrayDistances[numchar] = 999.0;
                        continue;
                    }

                    var xcleft = segments[numchar].A;
                    var xcright = segments[numchar].B;

                    // Корректировка границ
                    if (xcleft < 0) xcleft = 0;
                    if (xcright >= width) xcright = width - 1;
                    if (xcleft > xcright)
                    {
                        charBitmapsList.Add(null);
                        smallBitmapsList.Add(null);
                        listMatrix.Add(new double[100]);
                        listMatrixRaw.Add(new double[100]);
                        gyp.Append('?');
                        arrayDistances[numchar] = 999.0;
                        continue;
                    }

                    var widthChar = xcright - xcleft + 1;

                    var ybtop = -1;
                    var ybbottom = -1;
                    for (var xb = xcleft; xb <= xcright; xb++)
                    {
                        if ((arrayTops[xb] != -1 && ybtop == -1) ||
                            (arrayTops[xb] != -1 && ybtop != -1 && arrayTops[xb] < ybtop))
                        {
                            ybtop = arrayTops[xb];
                        }

                        if ((arrayBottoms[xb] != -1 && ybbottom == -1) ||
                            (arrayBottoms[xb] != -1 && ybbottom != -1 && arrayBottoms[xb] > ybbottom))
                        {
                            ybbottom = arrayBottoms[xb];
                        }
                    }

                    if (ybtop != -1 && ybbottom != -1)
                    {
                        var section = new Rectangle(xcleft, ybtop, widthChar, ybbottom - ybtop + 1);
                        using (var bitmapChar = new Bitmap(section.Width, section.Height, PixelFormat.Format24bppRgb))
                        {
                            using (var graphChar = Graphics.FromImage(bitmapChar))
                            {
                                graphChar.DrawImage(binaryBitmap, 0, 0, section, GraphicsUnit.Pixel);
                            }

                            // Жёсткая бинаризация (на всякий случай, хотя binaryBitmap уже ч/б)
                            for (var by = 0; by < bitmapChar.Height; by++)
                            {
                                for (var bx = 0; bx < bitmapChar.Width; bx++)
                                {
                                    var c = bitmapChar.GetPixel(bx, by);
                                    var gray = (c.R + c.G + c.B) / 3;
                                    var binary = gray < 128 ? 0 : 255;
                                    bitmapChar.SetPixel(bx, by, Color.FromArgb(binary, binary, binary));
                                }
                            }

                            var charCopy = new Bitmap(bitmapChar);
                            charBitmapsList.Add(charCopy);

                            // Ресайз до 10x10
                            using (var bitmap10x10 = new Bitmap(10, 10, PixelFormat.Format24bppRgb))
                            {
                                using (var g = Graphics.FromImage(bitmap10x10))
                                {
                                    g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
                                    g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
                                    g.DrawImage(bitmapChar, 0, 0, 10, 10);
                                }

                                var smallCopy = new Bitmap(bitmap10x10);
                                smallBitmapsList.Add(smallCopy);

                                var arrayMatrix = new double[100];
                                var index = 0;
                                var matrixDump = new StringBuilder();
                                for (var y = 0; y < 10; y++)
                                {
                                    for (var x = 0; x < 10; x++)
                                    {
                                        var color = bitmap10x10.GetPixel(x, y);
                                        arrayMatrix[index++] = (double)color.R / 255;
                                        matrixDump.Append(color.R > 128 ? '.' : '#');
                                    }
                                    matrixDump.Append('|');
                                }

                                var normMatrix = NormalizeVector(arrayMatrix);
                                listMatrix.Add(normMatrix);
                                listMatrixRaw.Add((double[])arrayMatrix.Clone());
                                gyp.Append(FindVector(numchar, normMatrix));

                                var lastChar = gyp.Length > 0 ? gyp.ToString().Substring(gyp.Length - 1) : "?";
                                AppLog.d("NeuroBase", string.Format("DIGIT[{0}]={1} matrix={2} dist={3}",
                                    numchar, lastChar, matrixDump.ToString(),
                                    numchar < arrayDistances.Length ? arrayDistances[numchar].ToString("F1") : "?"));
                            }
                        }
                    }
                    else
                    {
                        charBitmapsList.Add(null);
                        smallBitmapsList.Add(null);
                        listMatrix.Add(new double[100]);
                        listMatrixRaw.Add(new double[100]);
                        gyp.Append('?');
                        arrayDistances[numchar] = 999.0;
                    }
                }

                debugDistances = new double[ConstNumDigits];
                Array.Copy(arrayDistances, debugDistances, ConstNumDigits);

                SaveDebugImages(binaryBitmap, xleft, xright, arrayTops, arrayBottoms, ConstNumDigits, realwidth, charBitmapsList, smallBitmapsList);
                
                foreach (var bmp in charBitmapsList) { if (bmp != null) bmp.Dispose(); }
                foreach (var bmp in smallBitmapsList) { if (bmp != null) bmp.Dispose(); }

                lastListMatrix = new List<double[]>(listMatrix);
                lastListMatrixRaw = new List<double[]>(listMatrixRaw);
                lastArrayDistances = new double[arrayDistances.Length];
                Array.Copy(arrayDistances, lastArrayDistances, arrayDistances.Length);
                lastConstNumDigits = ConstNumDigits;
                } // end using binaryBitmap
            } // end using workBitmap

            elapsedTime = DateTime.Now.Ticks - startProcess;
        }

        private struct IntPair
        {
            public int A, B;
            public IntPair(int a, int b) { A = a; B = b; }
        }

        /// <summary>
        /// Сегментация по вертикальной проекции: разрезаем в "долинах".
        ///
        /// ЗАЧЕМ этот метод лучше простого gap-detection:
        ///   Скрипт-цифры часто соединены друг с другом или слиплись.
        ///   Бинарный метод (есть/нет пиксель в столбце) видит всё как одну группу.
        ///   Проекционный метод видит НАСКОЛЬКО МНОГО пикселей в каждом столбце —
        ///   в месте между цифрами значение ниже, это и есть наилучшая точка разреза.
        ///
        /// Алгоритм:
        ///   1. Сглаживаем colScore скользящим средним (окно 5px) — убираем шум.
        ///   2. Делим диапазон [xleft..xright] на targetCount равных "зон".
        ///   3. На каждой границе зон ищем локальный минимум в пределах ±window.
        ///      window = треть ширины одной зоны (адаптивно).
        ///   4. Если нашли точку лучше центра зоны — ставим разрез там.
        ///      Иначе — ставим на ожидаемой позиции (graceful degradation).
        ///
        /// Пример: [xleft=5, xright=126], 5 цифр → 4 разреза.
        ///   Ожидаемые позиции разрезов: 29, 53, 77, 101
        ///   В каждом ±window ищем минимум colScore → реальный разрез.
        /// </summary>
        private static List<IntPair> BuildDigitSegments(int[] colScore, int xleft, int xright, int targetCount)
        {
            var result = new List<IntPair>();

            // Защита от вырожденных случаев
            if (xleft < 0 || xright <= xleft || targetCount <= 0 || colScore == null)
            {
                for (var i = 0; i < targetCount; i++) result.Add(new IntPair(0, 0));
                return result;
            }

            var rangeLen = xright - xleft + 1;

            // ── ШАГ 1: СГЛАЖИВАНИЕ ПРОЕКЦИИ (moving average, окно 5) ───────────
            // Убираем однопиксельные провалы и шум.
            // "Истинные" долины между цифрами — широкие, шум — узкий.
            var smooth = new double[rangeLen];
            const int halfWin = 2; // окно = 2*halfWin+1 = 5
            for (var i = 0; i < rangeLen; i++)
            {
                var sum = 0.0; var cnt = 0;
                for (var j = Math.Max(0, i - halfWin); j <= Math.Min(rangeLen - 1, i + halfWin); j++)
                {
                    sum += colScore[xleft + j]; cnt++;
                }
                smooth[i] = sum / cnt;
            }

            // Логируем минимум/максимум проекции для диагностики
            var projMin = double.MaxValue; var projMax = 0.0;
            for (var i = 0; i < rangeLen; i++) { if (smooth[i] < projMin) projMin = smooth[i]; if (smooth[i] > projMax) projMax = smooth[i]; }
            AppLog.d("NeuroBase", string.Format("PROJ: xleft={0} xright={1} rangeLen={2} projMin={3:F1} projMax={4:F1}", xleft, xright, rangeLen, projMin, projMax));

            // ── ШАГ 2: ПОИСК ТОЧЕК РАЗРЕЗА (N-1 долин) ──────────────────────────
            // Ширина одной зоны (ожидаемая ширина одной цифры)
            var zoneWidth = rangeLen / targetCount;
            // Окно поиска долины: треть ширины зоны, но минимум 3px и максимум треть зоны
            var window = Math.Max(3, zoneWidth / 3);

            // Массив точек разреза (абсолютные X-координаты)
            // splits[0] = xleft, splits[targetCount] = xright+1
            var splits = new int[targetCount + 1];
            splits[0] = xleft;
            splits[targetCount] = xright + 1;

            for (var seg = 1; seg < targetCount; seg++)
            {
                // Ожидаемая позиция разреза (равномерное деление)
                var expectedX = xleft + (seg * rangeLen) / targetCount;
                var expectedI = expectedX - xleft; // индекс в smooth[]

                // Окно поиска (ограничиваем границами диапазона)
                var searchL = Math.Max(0, expectedI - window);
                var searchR = Math.Min(rangeLen - 1, expectedI + window);

                // Ищем минимум сглаженной проекции в окне
                var minVal = double.MaxValue;
                var minI   = expectedI; // по умолчанию — ожидаемая позиция
                for (var i = searchL; i <= searchR; i++)
                {
                    if (smooth[i] < minVal)
                    {
                        minVal = smooth[i];
                        minI   = i;
                    }
                }

                splits[seg] = xleft + minI;
                AppLog.d("NeuroBase", string.Format("SPLIT[{0}]: expected={1} found={2} val={3:F1}", seg, expectedX, splits[seg], minVal));
            }

            // ── ШАГ 3: СТРОИМ СЕГМЕНТЫ ───────────────────────────────────────────
            for (var i = 0; i < targetCount; i++)
            {
                var l = splits[i];
                var r = splits[i + 1] - 1;
                // Гарантируем что сегмент не пустой
                if (l > r) r = l;
                result.Add(new IntPair(l, r));
            }

            return result;
        }

        internal static void Train(string train)
        {
            if (string.IsNullOrEmpty(train) || lastListMatrix.Count == 0)
                return;

            var count = Math.Min(train.Length, lastListMatrix.Count);
            var trained = 0;
            var skipped = 0;
            for (var i = 0; i < count; i++)
            {
                // Фильтр мусора: считаем blackRatio
                var blackCount = 0;
                for (var j = 0; j < 100; j++)
                {
                    // В матрице: 0.0 - чёрный, 1.0 - белый
                    if (lastListMatrixRaw[i][j] < 0.5)
                        blackCount++;
                }
                var blackRatio = (double)blackCount / 100.0;
                
                if (blackRatio < 0.02 || blackRatio > 0.80)
                {
                    skipped++;
                    AppLog.d("NeuroBase", "TRAIN_SKIP: digit=" + train[i] + " reason=GARBAGE blackRatio=" + blackRatio.ToString("F3"));
                    continue;
                }

                listVectors.Add(new NeuroVector(train[i], lastListMatrix[i]));
                trained++;
                AppLog.i("NeuroBase", "TRAIN: digit=" + train[i] + " blackRatio=" + blackRatio.ToString("F3") + " totalVectors=" + listVectors.Count);
            }

            AppLog.i("NeuroBase", string.Format("TRAIN_SUMMARY: trained={0} skipped={1} totalVectors={2}", trained, skipped, listVectors.Count));
            SaveCustomBase();
        }

        internal static void SaveCustomBase()
        {
            try
            {
                var path = Path.Combine(System.Windows.Forms.Application.StartupPath, "abneuro.custom");
                using (var inner = new MemoryStream())
                {
                    using (var bw = new BinaryWriter(inner))
                    {
                        bw.Write(listVectors.Count);
                        for (var i = 0; i < listVectors.Count; i++)
                        {
                            listVectors[i].SaveToStream(bw);
                        }
                    }
                    File.WriteAllBytes(path, PackArray(inner.ToArray()));
                }
            }
            catch (Exception ex)
            {
                AppLog.e("NeuroBase", "SAVE_CUSTOM_FAILED", ex);
            }
        }

        internal static void LoadCustomBase()
        {
            var path = Path.Combine(System.Windows.Forms.Application.StartupPath, "abneuro.custom");
            if (File.Exists(path))
            {
                try
                {
                    using (var inner = new MemoryStream(UnpackArray(File.ReadAllBytes(path))))
                    using (var br = new BinaryReader(inner))
                    {
                        var count = br.ReadInt32();
                        for (var i = 0; i < count; i++)
                        {
                            listVectors.Add(new NeuroVector(br));
                        }
                    }
                    AppLog.i("NeuroBase", string.Format("LOAD_CUSTOM: vectors={0}", listVectors.Count));
                }
                catch (Exception ex)
                {
                    AppLog.e("NeuroBase", "LOAD_CUSTOM_FAILED, fallback to resource", ex);
                    LoadFromArray(Properties.Resources.abneuro);
                }
            }
        }

        internal static void LoadFromArray(byte[] arrayPacked)
        {
            listVectors.Clear();
            try
            {
                using (var inner = new MemoryStream(UnpackArray(arrayPacked)))
                using (var br = new BinaryReader(inner))
                {
                    var count = br.ReadInt32();
                    for (var i = 0; i < count; i++)
                    {
                        listVectors.Add(new NeuroVector(br));
                    }
                }
                AppLog.i("NeuroBase", "LOADFromArray: vectors=" + listVectors.Count);
            }
            catch (EndOfStreamException ex)
            {
                AppLog.e("NeuroBase", "LOADFromArray: TRUNCATED DATA, loaded=" + listVectors.Count, ex);
            }
            catch (Exception ex)
            {
                AppLog.e("NeuroBase", "LOADFromArray: FAILED", ex);
            }
        }

        private char FindVector(int index, double[] matrix)
        {
            if (listVectors.Count == 0)
                return '?';

            var minDistance = double.MaxValue;
            var bestToken = '?';
            for (var i = 0; i < listVectors.Count; i++)
            {
                var distance = listVectors[i].Distance(matrix);
                if (distance < minDistance)
                {
                    minDistance = distance;
                    bestToken = listVectors[i].Token();
                }
            }

            arrayDistances[index] = minDistance;
            return bestToken;
        }

        private static double[] NormalizeVector(double[] vector)
        {
            var sum = 0.0;
            foreach (var v in vector) sum += v;
            if (sum == 0) return vector;
            
            var normalized = new double[vector.Length];
            for (var i = 0; i < vector.Length; i++)
                normalized[i] = vector[i] / sum;
            return normalized;
        }

        private static bool ThumbnailCallback() { return false; }

        private static int debugSaveCounter;

        private static void SaveDebugImages(Bitmap bitmapGray, int xleft, int xright, int[] arrayTops, int[] arrayBottoms, int ConstNumDigits, int realwidth, List<Bitmap> charBitmaps, List<Bitmap> smallBitmaps)
        {
            try
            {
                var dir = Path.Combine(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Logs"), "Captcha");
                if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);

                var n = debugSaveCounter++;

                if (bitmapGray != null)
                {
                    bitmapGray.Save(Path.Combine(dir, string.Format("captcha_gray_{0}.png", n)), ImageFormat.Png);
                }

                for (var m = 0; m < charBitmaps.Count && m < smallBitmaps.Count; m++)
                {
                    if (charBitmaps[m] != null)
                    {
                        charBitmaps[m].Save(Path.Combine(dir, string.Format("captcha_digit_{0}_{1}.png", n, m)), ImageFormat.Png);
                    }
                    if (smallBitmaps[m] != null)
                    {
                        var enlarged = new Bitmap(100, 100, PixelFormat.Format24bppRgb);
                        using (var g = Graphics.FromImage(enlarged))
                        {
                            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
                            g.DrawImage(smallBitmaps[m], 0, 0, 100, 100);
                        }
                        enlarged.Save(Path.Combine(dir, string.Format("captcha_10x10_{0}_{1}.png", n, m)), ImageFormat.Png);
                        enlarged.Dispose();
                    }
                }

                AppLog.d("NeuroBase", string.Format("DEBUG_IMAGES_SAVED: captcha #{0} digits={1}", n, ConstNumDigits));
            }
            catch (Exception ex)
            {
                AppLog.e("NeuroBase", "SAVE_DEBUG_IMAGES_FAILED", ex);
            }
        }

        private static byte[] PackArray(byte[] writeData)
        {
            using (var inner = new MemoryStream())
            using (var stream2 = new GZipStream(inner, CompressionMode.Compress))
            {
                stream2.Write(writeData, 0, writeData.Length);
                return inner.ToArray();
            }
        }

        private static byte[] UnpackArray(byte[] compressedData)
        {
            using (var inner = new MemoryStream(compressedData))
            using (var stream2 = new MemoryStream())
            using (var stream3 = new GZipStream(inner, CompressionMode.Decompress))
            {
                var buffer = new byte[0x8000];
                int count;
                while ((count = stream3.Read(buffer, 0, buffer.Length)) > 0)
                {
                    stream2.Write(buffer, 0, count);
                }
                return stream2.ToArray();
            }
        }
    }
}