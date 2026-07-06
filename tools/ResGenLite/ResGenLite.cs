using System;
using System.Collections;
using System.Collections.Generic;
using System.ComponentModel.Design;
using System.IO;
using System.Resources;
using System.Text;
using System.Windows.Forms;

internal static class ResGenLite
{
    private static int Main(string[] args)
    {
        try
        {
            var expanded = ExpandArgs(args);
            var pairs = ParsePairs(expanded);
            if (pairs.Count == 0)
            {
                Console.Error.WriteLine("ResGenLite: no .resx inputs.");
                return 1;
            }

            foreach (var pair in pairs)
            {
                ConvertResx(pair.Input, pair.Output);
            }

            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("ResGenLite: " + ex);
            return 1;
        }
    }

    private static List<string> ExpandArgs(IEnumerable<string> args)
    {
        var result = new List<string>();
        foreach (var arg in args)
        {
            if (arg != null && arg.StartsWith("@", StringComparison.Ordinal) && File.Exists(arg.Substring(1)))
            {
                result.AddRange(SplitCommandLine(File.ReadAllText(arg.Substring(1))));
            }
            else
            {
                result.Add(arg);
            }
        }

        return result;
    }

    private static List<ResxPair> ParsePairs(IList<string> args)
    {
        var pairs = new List<ResxPair>();
        var positional = new List<string>();
        var compileMode = false;

        foreach (var raw in args)
        {
            var arg = (raw ?? string.Empty).Trim();
            if (arg.Length == 0)
            {
                continue;
            }

            if (IsOption(arg))
            {
                if (string.Equals(arg, "/compile", StringComparison.OrdinalIgnoreCase) ||
                    string.Equals(arg, "-compile", StringComparison.OrdinalIgnoreCase))
                {
                    compileMode = true;
                }

                continue;
            }

            if (compileMode && arg.IndexOf(',') >= 0)
            {
                var parts = arg.Split(new[] { ',' }, 2);
                pairs.Add(new ResxPair(parts[0], parts[1]));
            }
            else
            {
                positional.Add(arg);
            }
        }

        if (pairs.Count == 0 && positional.Count > 0)
        {
            for (var i = 0; i < positional.Count; i += 2)
            {
                var input = positional[i];
                if (!input.EndsWith(".resx", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                var output = i + 1 < positional.Count && !positional[i + 1].EndsWith(".resx", StringComparison.OrdinalIgnoreCase)
                    ? positional[i + 1]
                    : Path.ChangeExtension(input, ".resources");
                pairs.Add(new ResxPair(input, output));
            }
        }

        return pairs;
    }

    private static bool IsOption(string arg)
    {
        return arg.StartsWith("/", StringComparison.Ordinal) || arg.StartsWith("-", StringComparison.Ordinal);
    }

    private static void ConvertResx(string input, string output)
    {
        if (string.IsNullOrEmpty(input) || !File.Exists(input))
        {
            throw new FileNotFoundException("Input .resx not found.", input);
        }

        var outputDir = Path.GetDirectoryName(output);
        if (!string.IsNullOrEmpty(outputDir))
        {
            Directory.CreateDirectory(outputDir);
        }

        using (var reader = new ResXResourceReader(input))
        using (var writer = new ResourceWriter(output))
        {
            reader.UseResXDataNodes = true;
            reader.BasePath = Path.GetDirectoryName(Path.GetFullPath(input));
            foreach (DictionaryEntry entry in reader)
            {
                var name = (string)entry.Key;
                var node = entry.Value as ResXDataNode;
                var value = node == null ? entry.Value : node.GetValue((ITypeResolutionService)null);
                writer.AddResource(name, value);
            }
        }
    }

    private static List<string> SplitCommandLine(string value)
    {
        var result = new List<string>();
        var current = new StringBuilder();
        var quoted = false;
        for (var i = 0; i < value.Length; i++)
        {
            var ch = value[i];
            if (ch == '"')
            {
                quoted = !quoted;
                continue;
            }

            if (char.IsWhiteSpace(ch) && !quoted)
            {
                if (current.Length > 0)
                {
                    result.Add(current.ToString());
                    current.Length = 0;
                }
                continue;
            }

            current.Append(ch);
        }

        if (current.Length > 0)
        {
            result.Add(current.ToString());
        }

        return result;
    }

    private sealed class ResxPair
    {
        internal ResxPair(string input, string output)
        {
            Input = input;
            Output = output;
        }

        internal string Input { get; private set; }

        internal string Output { get; private set; }
    }
}
