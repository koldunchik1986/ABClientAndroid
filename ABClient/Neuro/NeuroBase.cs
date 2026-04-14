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
        /*private const string FileBaseName = "abneuro.dat";*/

        private int ConstNumDigits = 5;
        private static readonly List<NeuroVector> listVectors = new List<NeuroVector>();
        private List<double[]> listMatrix;
        private double[] arrayDistances;
        /*private static readonly string[] arrayVotes = new string[5];*/
        private static readonly StringBuilder gyp = new StringBuilder();
        private static long elapsedTime;

        // Отладочные данные: расстояния до ближайшего вектора для каждой цифры
        private static double[] debugDistances;
        private static int debugNumDigits;
        private static int debugWidth;
        private static int debugHeight;
        private static int debugXLeft;
        private static int debugXRight;
        private static int debugRealWidth;

        // Данные последнего распознавания для обучения (Train)
        private static List<double[]> lastListMatrix = new List<double[]>();
        private static double[] lastArrayDistances = new double[0];
        private static int lastConstNumDigits;

        internal NeuroBase()
        {
            // ConstNumDigits определяется автоматически в Calculate()
            // по количеству групп чёрных пикселей на изображении капчи.
            // Ранее было захардкожено 5, но сервер может отдавать 4 или 6 цифр.
            // Мёртвый код Dice.Make(4,6) удалён — автоопределение надёжнее.

            listMatrix = new List<double[]>(ConstNumDigits);
            arrayDistances = new double[ConstNumDigits];
        }

        /// <summary>
        /// Отладочная информация о последнем распознавании.
        /// Формат: "RECOGNIZE_DEBUG: digits=N, w=W, h=H, xleft=XL, xright=XR, realwidth=RW, result=XXXXX, dist=[d0,d1,...]"
        /// </summary>
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

            return $"RECOGNIZE_DEBUG: digits={debugNumDigits}, w={debugWidth}, h={debugHeight}, " +
                   $"xleft={debugXLeft}, xright={debugXRight}, realwidth={debugRealWidth}, " +
                   $"result={gyp}, dist={distStr}";
        }

        /// <summary>
        /// Автоопределение количества цифр на капче — гибридный подход.
        /// Алгоритм:
        ///   1. Считаем группы чёрных пикселей (gap≥1 пустой столбец = разделитель)
        ///   2. Если группа шире 1.5x средней — в ней 2 цифры, разбиваем
        ///   3. Если стандартный размер капчи (ширина ≈132) → предполагаем 5 цифр
        ///   4. Если группа одна (все цифры слились) — разбиваем на 5 равных
        /// </summary>
        private static int CountDigitGroups(bool[] columnHasBlack, int xleft, int xright, int imageWidth)
        {
            // Для стандартной капчи neverlands (134x60, width=132) — предполагаем 5
            if (imageWidth >= 125 && imageWidth <= 140)
            {
                // Проверяем: если есть хотя бы 3 разделителя (gap≥1), считаем группы
                // Иначе (все цифры слиплись) — возвращаем 5
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

                // Все цифры слиплись или слишком много групп — равно 5
                return 5;
            }

            // Для нестандартных размеров — gap-based подсчёт
            var count = 0;
            var inGroup = false;
            var gapColumns = 0;
            var minGap = 1;

            // Сначала собираем группы и их ширину
            var groupStarts = new System.Collections.Generic.List<int>();
            var groupEnds = new System.Collections.Generic.List<int>();
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

            // Если 1 группа — все слиплись, разбиваем на 5
            if (count == 1)
                return 5;

            // Если какая-то группа явно шире остальных — в ней 2 цифры
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

        /// <summary>
        /// Адаптивный порог бинаризации (аналог метода Otsu).
        /// Находит порог, максимизирующий межклассовую дисперсию,
        /// что лучше разделяет цифры и фон чем простое среднее.
        /// </summary>
        private static int ComputeOtsuThreshold(Bitmap bitmapSource, int width, int height, int graymin, int graymax)
        {
            // Гистограмма: считаем сколько пикселей попадает в каждый bin
            var histogram = new int[256];
            for (var y = 0; y < height; y++)
            {
                for (var x = 0; x < width; x++)
                {
                    var color = bitmapSource.GetPixel(x + 1, y + 1);
                    var gray = (color.R + color.G + color.B) / 3;
                    if (gray >= 0 && gray < 256)
                        histogram[gray]++;
                }
            }

            var total = width * height;
            var sum = 0;
            for (var i = 0; i < 256; i++)
                sum += i * histogram[i];

            var sumB = 0;
            var wB = 0;
            var maxVariance = 0.0;
            var bestThreshold = (graymin + graymax) / 2; // fallback

            for (var t = 0; t < 256; t++)
            {
                wB += histogram[t];
                if (wB == 0)
                    continue;

                var wF = total - wB;
                if (wF == 0)
                    break;

                sumB += t * histogram[t];

                var mB = (double)sumB / wB;
                var mF = (double)(sum - sumB) / wF;

                var variance = wB * wF * (mB - mF) * (mB - mF);
                if (variance > maxVariance)
                {
                    maxVariance = variance;
                    bestThreshold = t * 3; // масштабируем обратно к R+G+B
                }
            }

            // Если порог слишком близко к краям — используем fallback
            if (bestThreshold < graymin + 30 || bestThreshold > graymax - 30)
                bestThreshold = (graymin + graymax) / 2;

            return bestThreshold;
        }

        internal static string Gyp()
        {
            return gyp.ToString();
        }

        internal static int NumNodes()
        {
            return listVectors.Count;
        }

        internal static long ElapsedTime()
        {
            return elapsedTime;
        }

        /*
        internal static string Votes()
        {
            var sb = new StringBuilder();
            for (var i = 0; i < 5; i++)
            {
                if (i > 0)
                {
                    sb.Append(", ");
                }

                sb.Append(arrayVotes[i]);
            }

            return sb.ToString();
        }
         */ 

        internal void Calculate(Bitmap bitmapSource)
        {
            var startProcess = DateTime.Now.Ticks;
            var graymin = 255 * 3;
            var graymax = 0;
            var width = bitmapSource.Size.Width - 2;
            var height = bitmapSource.Size.Height - 2;

            // Отладка: сохраняем размер исходной капчи
            debugWidth = width;
            debugHeight = height;

            for (var y = 0; y < height; y++)
            {
                for (var x = 0; x < width; x++)
                {
                    var color = bitmapSource.GetPixel(x + 1, y + 1);
                    var gray = color.R + color.G + color.B;
                    if (gray < graymin)
                    {
                        graymin = gray;
                    }

                    if (gray > graymax)
                    {
                        graymax = gray;
                    }
                }
            }

            if (graymin < graymax)
            {
                // Адаптивный порог бинаризации через гистограмму (аналог Otsu)
                // Считаем гистограмму яркости и ищем порог, максимизирующий межклассовую дисперсию
                var grayMiddle = ComputeOtsuThreshold(bitmapSource, width, height, graymin, graymax);

                var arrayTops = new int[width];
                var arrayBottoms = new int[width];
                // Отслеживаем какие столбцы содержат чёрные пиксели (для автоопределения кол-ва цифр)
                var columnHasBlack = new bool[width];
                for (var i = 0; i < width; i++)
                {
                    arrayTops[i] = -1;
                    arrayBottoms[i] = -1;
                    columnHasBlack[i] = false;
                }

                var xleft = -1;
                var xright = -1;

                using (var bitmapGray = new Bitmap(width, height, PixelFormat.Format24bppRgb))
                {
                    // Шаг 1: Нормализация серого с удаллением шумовых линий
                    // Капча neverlands содержит горизонтальные шумовые полосы (линии)
                    // Для каждого столбца вычисляем среднюю яркость и вычитаем её
                    var columnAvg = new int[width];
                    for (var x = 0; x < width; x++)
                    {
                        var colSum = 0;
                        var colCount = 0;
                        for (var y = 0; y < height; y++)
                        {
                            var color = bitmapSource.GetPixel(x + 1, y + 1);
                            colSum += color.R + color.G + color.B;
                            colCount++;
                        }
                        columnAvg[x] = colCount > 0 ? colSum / colCount : 0;
                    }

                    for (var x = 0; x < width; x++)
                    {
                        for (var y = 0; y < height; y++)
                        {
                            var color = bitmapSource.GetPixel(x + 1, y + 1);
                            var gray = color.R + color.G + color.B;

                            // Вычитаем фоновый шум столбца
                            var bgOffset = columnAvg[x] - (graymin + (graymax - graymin) / 2);
                            var grayClean = gray - (bgOffset > 0 ? bgOffset / 2 : 0);
                            if (grayClean < graymin) grayClean = graymin;
                            if (grayClean > graymax) grayClean = graymax;

                            var graynorm = (255 * (grayClean - graymin)) / (graymax - graymin);
                            bitmapGray.SetPixel(x, y, Color.FromArgb(graynorm, graynorm, graynorm));

                            var isBlack = grayClean < grayMiddle;
                            if (isBlack)
                            {
                                if (xleft == -1)
                                {
                                    xleft = x;
                                }

                                xright = x;
                                columnHasBlack[x] = true;

                                if (arrayTops[x] == -1)
                                {
                                    arrayTops[x] = y;
                                }

                                arrayBottoms[x] = y;
                            }
                        }
                    }

                    // Автоопределение количества цифр на капче
                    // Считаем группы чёрных пикселей вместо захардкоженного ConstNumDigits = 5
                    if (xleft != -1 && xright != -1)
                    {
                        ConstNumDigits = CountDigitGroups(columnHasBlack, xleft, xright, width);
                    }

                    // Отладка: сохраняем границы
                    debugXLeft = xleft;
                    debugXRight = xright;
                    debugNumDigits = ConstNumDigits;

                    listMatrix.Clear();
                    gyp.Length = 0;
                    arrayDistances = new double[ConstNumDigits];

                    var realwidth = xright - xleft;
                    debugRealWidth = realwidth;

                    for (var numchar = 0; numchar < ConstNumDigits; numchar++)
                    {
                        var xcleft = ((numchar * realwidth) / ConstNumDigits) + xleft;
                        var xcright = (((numchar + 1) * realwidth) / ConstNumDigits) + xleft;
                        var widthChar = xcright - xcleft;

                        var ybtop = -1;
                        var ybbottom = -1;
                        for (var xb = xcleft; xb < xcright; xb++)
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
                            using (
                                var bitmapChar = new Bitmap(section.Width, section.Height, PixelFormat.Format24bppRgb))
                            {
                                using (var graphChar = Graphics.FromImage(bitmapChar))
                                {
                                    graphChar.DrawImage(bitmapGray, 0, 0, section, GraphicsUnit.Pixel);
                                    var myCallback = new Image.GetThumbnailImageAbort(ThumbnailCallback);
                                    using (var image10x10 = bitmapChar.GetThumbnailImage(10, 10, myCallback, IntPtr.Zero))
                                    {
                                        using (var bitmap10x10 = new Bitmap(image10x10))
                                        {
                                            var arrayMatrix = new double[100];
                                            var index = 0;
                                            for (var y = 0; y < 10; y++)
                                            {
                                                for (var x = 0; x < 10; x++)
                                                {
                                                    var color = bitmap10x10.GetPixel(x, y);
                                                    arrayMatrix[index++] = (double) color.R / 255;
                                                }
                                            }

                                            listMatrix.Add(arrayMatrix);
                                            gyp.Append(FindVector(numchar, arrayMatrix));
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Отладка: сохраняем расстояния для диагностики качества распознавания
                    debugDistances = new double[ConstNumDigits];
                    Array.Copy(arrayDistances, debugDistances, ConstNumDigits);

                    // Сохраняем данные последнего распознавания для обучения (Train)
                    lastListMatrix = new List<double[]>(listMatrix);
                    lastArrayDistances = new double[arrayDistances.Length];
                    Array.Copy(arrayDistances, lastArrayDistances, arrayDistances.Length);
                    lastConstNumDigits = ConstNumDigits;
                }
            }

            elapsedTime = DateTime.Now.Ticks - startProcess;
        }

        /// <summary>
        /// Обучение нейросети: добавляет вектора из последнего распознавания в базу.
        /// train — строка правильных цифр (длина = lastConstNumDigits).
        /// Вектора с очень малым расстоянием (&lt;0.5) к существующему — пропускаются (дубликаты).
        /// </summary>
        internal static void Train(string train)
        {
            if (string.IsNullOrEmpty(train) || lastListMatrix.Count == 0)
                return;

            var count = Math.Min(train.Length, lastListMatrix.Count);
            for (var i = 0; i < count; i++)
            {
                if (i < lastArrayDistances.Length && listVectors.Count > 0 && lastArrayDistances[i] < 0.5)
                    continue;

                listVectors.Add(new NeuroVector(train[i], lastListMatrix[i]));
                AppLog.i("NeuroBase", "TRAIN: digit=" + train[i] + " dist=" + (i < lastArrayDistances.Length ? lastArrayDistances[i].ToString("F3") : "?") + " totalVectors=" + listVectors.Count);
            }

            SaveCustomBase();
        }

        /// <summary>
        /// Сохраняет кастомную базу векторов в файл abneuro.custom (рядом с exe).
        /// Формат: [int count] [char token + 100 bytes]* , gzip сжатый.
        /// </summary>
        internal static void SaveCustomBase()
        {
            try
            {
                var path = System.IO.Path.Combine(System.Windows.Forms.Application.StartupPath, "abneuro.custom");
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

                    var packed = PackArray(inner.ToArray());
                    File.WriteAllBytes(path, packed);
                    AppLog.i("NeuroBase", "SAVE_CUSTOM: path=" + path + " vectors=" + listVectors.Count + " bytes=" + packed.Length);
                }
            }
            catch (Exception ex)
            {
                AppLog.e("NeuroBase", "SAVE_CUSTOM_FAILED", ex);
            }
        }

        /// <summary>
        /// Загружает кастомную базу векторов из файла abneuro.custom.
        /// Если файл есть — загружается из него (перезаписывая встроенную базу).
        /// Если нет — из встроенного ресурса Resources.abneuro.
        /// </summary>
        internal static void LoadCustomBase()
        {
            var path = System.IO.Path.Combine(System.Windows.Forms.Application.StartupPath, "abneuro.custom");
            if (File.Exists(path))
            {
                try
                {
                    var arrayPacked = File.ReadAllBytes(path);
                    var arrayStream = UnpackArray(arrayPacked);
                    using (var inner = new MemoryStream(arrayStream))
                    {
                        using (var br = new BinaryReader(inner))
                        {
                            var count = br.ReadInt32();
                            for (var i = 0; i < count; i++)
                            {
                                listVectors.Add(new NeuroVector(br));
                            }
                        }
                    }
                    AppLog.i("NeuroBase", "LOAD_CUSTOM: vectors=" + listVectors.Count);
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
            var arrayStream = UnpackArray(arrayPacked);
            using (var inner = new MemoryStream(arrayStream))
            {
                using (var br = new BinaryReader(inner))
                {
                    var count = br.ReadInt32();
                    for (var i = 0; i < count; i++)
                    {
                        listVectors.Add(new NeuroVector(br));
                    }
                }
            }
        }

        private char FindVector(int index, double[] matrix)
        {
            if (listVectors.Count == 0)
            {
                return '?';
            }

            /*var listVotes = new List<NeuroVote>(10);*/
            var minDistance = double.MaxValue;
            var bestToken = '?';
            for (var i = 0; i < listVectors.Count; i++)
            {
                var distance = listVectors[i].Distance(matrix);
                /*
                if (listVotes.Count < 10 || distance < listVotes[listVotes.Count - 1].Distance)
                {
                    if (listVotes.Count == 10)
                    {
                        listVotes.RemoveAt(9);
                    }

                    var neuroVote = new NeuroVote { Token = listVectors[i].Token(), Distance = distance };
                    if (listVotes.Count == 0)
                    {
                        listVotes.Add(neuroVote);
                    }
                    else
                    {
                        var j = 0;
                        while (j < listVotes.Count)
                        {
                            if (distance < listVotes[j].Distance)
                            {
                                listVotes.Insert(j, neuroVote);
                                break;
                            }

                            j++;
                        }

                        if (j == listVotes.Count)
                        {
                            listVotes.Add(neuroVote);
                        }
                    }
                }
                */

                if (distance > minDistance)
                {
                    continue;
                }

                minDistance = distance;
                bestToken = listVectors[i].Token();
            }

            arrayDistances[index] = minDistance;
            return bestToken;

            /*
            var sb = new StringBuilder(16);
            for (var j = 0; j < listVotes.Count; j++)
            {
                sb.Append(listVotes[j].Token);
            }

            arrayVotes[index] = sb.ToString();
            var win 
            
            arrayDistances[index] = listVotes[0].Distance;
            return listVotes[0].Token;
             */ 
        }

        private static bool ThumbnailCallback()
        {
            return false;
        }

        private static byte[] PackArray(byte[] writeData)
        {
            using (var inner = new MemoryStream())
            {
                using (var stream2 = new GZipStream(inner, CompressionMode.Compress))
                {
                    stream2.Write(writeData, 0, writeData.Length);
                }

                return inner.ToArray();
            }
        }

        private static byte[] UnpackArray(byte[] compressedData)
        {
            using (var inner = new MemoryStream(compressedData))
            {
                using (var stream2 = new MemoryStream())
                {
                    using (var stream3 = new GZipStream(inner, CompressionMode.Decompress))
                    {
                        var buffer = new byte[0x8000];
                        int count;
                        while ((count = stream3.Read(buffer, 0, buffer.Length)) > 0)
                        {
                            stream2.Write(buffer, 0, count);
                        }
                    }

                    return stream2.ToArray();
                }
            }
        }
    }
}
