using System.Net.Sockets;

namespace ANClient.ANProxy
{
    internal class ServerPipe
    {
        private readonly Socket _baseSocket;

        internal ServerPipe(Socket oSocket)
        {
            _baseSocket = oSocket;
            _baseSocket.NoDelay = true;
        }
    }
}
