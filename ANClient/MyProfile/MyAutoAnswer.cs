namespace ANClient.MyProfile
{
    using System.Xml;
    using System;
    using MyHelpers;

    internal class MyAutoAnswer
    {
        private int m_LastAutoAnswer;
        private int[] m_PrepAutoAnswers;
        private string[] m_Answers;

        internal string StrAnswers { get; private set; }

        internal bool Active { get; set; }

        internal MyAutoAnswer()
        {
            SetAnswers(
                "Это автоответ ANClient. Не ждали?" + Environment.NewLine +
                "Я все понимаю, но это автоответ ANClient" + Environment.NewLine +
                "Хватит! Автоответу ANClient это не нравится" + Environment.NewLine +
                "Это автоответ ANClient, не старайся мне что-то объяснить" + Environment.NewLine +
                "Хозяин отошел, но я ему это передам. Это автоответ ANClient" + Environment.NewLine +
                "Автоответ ANClient. Я ничего не читаю, что ты мне пишешь" + Environment.NewLine +
                "Серьезно? Это автоответ ANClient" + Environment.NewLine +
                "Что-то? Повтори. Автоответ ANClient плохо тебя понимает" + Environment.NewLine +
                "Перезагрузись, от тебя закорючки идут. Автоответ ANClient" + Environment.NewLine +
                "Автоответ ANClient советует тебе помолчать" + Environment.NewLine +
                "Это автоответ ANClient. Оставьте сообщение после длинного гудка" + Environment.NewLine +
                "Ты расстраиваешь автоответ ANClient своими глупостями" + Environment.NewLine +
                "Автоответ ANClient ненавидит спаммеров... Где моя нападалка?..." + Environment.NewLine +
                "А в рыло? Автоответ ANClient не понимает шуток" + Environment.NewLine +
                "Ну даешь! Нравится говорить с автоответом ANClient?" + Environment.NewLine +
                "Давай, давай. Ты только заводишь автоответ ANClient" + Environment.NewLine +
                "Автоответ ANClient думает, что это бред" + Environment.NewLine +
                "Как вы все меня утомили! Это автоответ ANClient" + Environment.NewLine +
                "Пиши еще. Автоответ ANClient питается твоими словами" + Environment.NewLine +
                "Я так и передам МСу. Это автоответ ANClient" + Environment.NewLine +
                "Не мешай автоответу ANClient медитировать" + Environment.NewLine +
                "Автоответ ANClient думает, просит не мешать" + Environment.NewLine +
                "Что-что? Пиши медленней, автоответ ANClient не успевает за тобой" + Environment.NewLine +
                "Я хоть и бот, но обидчивый. Автоответ ANClient может и боевую влепить" + Environment.NewLine +
                "Ты говоришь с автоответом ANClient, но не расстраивайся. Хозяин не намного умнее меня" +
                Environment.NewLine +
                "Ты даже автоответ ANClient сумел разозлить!" + Environment.NewLine +
                "Что за ерунду ты мне пишешь? Автоответ ANClient ничего не понимает!" + Environment.NewLine +
                "Я запишу и запомню. Автоответ ANClient ничего не забывает!");
        }

        internal void SetAnswers(string answers)
        {
            if (answers == null) throw new ArgumentNullException("answers");
            StrAnswers = answers;
            m_Answers = answers.Split(new[] { Environment.NewLine }, StringSplitOptions.RemoveEmptyEntries);
        }

        internal string GetNextAnswer()
        {
            if ((m_Answers == null) || (m_Answers.Length == 0))
            {
                return string.Empty;
            }

            if (m_PrepAutoAnswers == null || (m_PrepAutoAnswers.Length != m_Answers.Length))
            {
                m_PrepAutoAnswers = new int[m_Answers.Length];
                for (var i = 0; i < m_PrepAutoAnswers.Length; i++)
                {
                    m_PrepAutoAnswers[i] = i;
                }

                for (var i = 0; i < m_PrepAutoAnswers.Length; i++)
                {
                    var j = Helpers.Dice.Make(m_PrepAutoAnswers.Length);
                    var t = m_PrepAutoAnswers[i];
                    m_PrepAutoAnswers[i] = m_PrepAutoAnswers[j];
                    m_PrepAutoAnswers[j] = t;
                }

                m_LastAutoAnswer = -1;
            }

            m_LastAutoAnswer++;
            if (m_LastAutoAnswer == m_PrepAutoAnswers.Length)
            {
                m_LastAutoAnswer = 0;
            }

            return m_Answers[m_PrepAutoAnswers[m_LastAutoAnswer]];
        }

        internal void Write(XmlWriter writer)
        {
            writer.WriteStartElement("autoanswer");

            if (Active)
            {
                writer.WriteStartAttribute("active");
                writer.WriteValue(Active);
                writer.WriteEndAttribute();
            }

            writer.WriteStartAttribute("answers");
            writer.WriteString(StrAnswers.Replace(Environment.NewLine, "[BR]"));
            writer.WriteEndAttribute();

            writer.WriteEndElement();
        }

        internal void Read(XmlReader reader)
        {
            if (reader["active"] != null)
            {
                bool active;
                if (!bool.TryParse(reader["active"], out active))
                {
                    active = false;
                }

                Active = active;
            }

            if (reader["answers"] != null)
            {
                SetAnswers(reader["answers"].Replace("[BR]", Environment.NewLine));
            }
        }
    }
}