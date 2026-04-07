/******************************************************************
 *
 *	CyberHTTP for Java
 *
 *	Copyright (C) Satoshi Konno 2002-2003
 *
 *	File: HTTPServer.java
 *
 *	Revision;
 *
 *	12/12/02
 *		- first revision.
 *	10/20/03
 *		- Improved the HTTP server using multithreading.
 *	08/27/04
 *		- Changed accept() to set a default timeout, HTTP.DEFAULT_TIMEOUT, to the socket.
 *	
 ******************************************************************/

package org.cybergarage.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.cybergarage.util.Debug;
import org.cybergarage.util.ListenerList;

import android.util.Log;


/**
 * HTTPServer ʵ�� Runnable,��һ��HTTP������ This class identifies an HTTP over TCP
 * server<br>
 * The server must be initialized iether by the
 * {@link HTTPServer#open(InetAddress, int)} or the
 * {@link HTTPServer#open(String, int)} method.<br>
 * Optionally a set of {@link HTTPRequestListener} may be set<br>
 * The server then can be started or stopped by the method
 * {@link HTTPServer#start()} and {@link HTTPServer#stop()}
 * 
 * @author Satoshi "skonno" Konno
 * @author Stefano "Kismet" Lenzi
 * @version 1.8
 * 
 */
public class HTTPServer implements Runnable
{
	private final static String tag = "HTTPServer";
	// //////////////////////////////////////////////
	// Constants
	// //////////////////////////////////////////////

	public final static String NAME = "CyberHTTP";
	public final static String VERSION = "1.0";

	/** Ĭ�ϵĶ˿�Ϊ80 */
	public final static int DEFAULT_PORT = 80;

	/**
	 * Ĭ�����ӵĳ�ʱʱ�� Default timeout connection for HTTP comunication
	 * Ĭ�ϳ�ʱ����80*1000
	 * 
	 * @since 1.8
	 */
	public final static int DEFAULT_TIMEOUT = 15 * 1000;

	/** ��ȡHTTPServer������ */
	public static String getName()
	{
		// ��ȡϵͳ������
		String osName = System.getProperty("os.name");
		// ��ȡϵͳ�İ汾
		String osVer = System.getProperty("os.version");
		return osName + "/" + osVer + " " + NAME + "/" + VERSION;
	}

	// //////////////////////////////////////////////
	// Constructor
	// //////////////////////////////////////////////

	public HTTPServer()
	{
		serverSock = null;

	}

	// //////////////////////////////////////////////
	// ServerSocket
	// //////////////////////////////////////////////

	/** ServerSocket serverSock */
	private ServerSocket serverSock = null;
	/** InetAddress bindAddr ��ַ */
	private InetAddress bindAddr = null;
	/** �˿� */
	private int bindPort = 0;
	/**
	 * timeout tcp�ĳ�ʱʱ�� Ĭ��ֵΪ80��1000 Store the current TCP timeout value The
	 * variable should be accessed by getter and setter metho
	 */
	protected int timeout = DEFAULT_TIMEOUT;

	/** ��ȡserverSock */
	public ServerSocket getServerSock()
	{
		return serverSock;
	}

	/** ����ip��ַ���ַ�ת���bindAddrΪnull�򷵻ؿ��ַ��� */
	public String getBindAddress()
	{
		if (bindAddr == null)
		{
			return "";
		}
		return bindAddr.getHostAddress();
	}

	/** ��ȡ�˿� */
	public int getBindPort()
	{
		return bindPort;
	}

	// //////////////////////////////////////////////
	// open/close
	// //////////////////////////////////////////////

	/**
	 * ��ȡ��ʱʱ�䣬�÷�����ͬ������ס Get the current socket timeout
	 * 
	 * @since 1.8
	 */
	public synchronized int getTimeout()
	{
		return timeout;
	}

	/**
	 * ���ó�ʱʱ�䣬�÷�����ͬ������ס Set the current socket timeout
	 * 
	 * @param longout
	 *            new timeout
	 * @since 1.8
	 */
	public synchronized void setTimeout(int timeout)
	{
		this.timeout = timeout;
	}

	/** serverSock������null����true �����򴴽�ServerSocket ����û���쳣����true�������쳣����false */
	public boolean open(InetAddress addr, int port)
	{
		if (serverSock != null)
			return true;
		try
		{
			serverSock = new ServerSocket(bindPort, 0, bindAddr);


		}
		catch (IOException e)
		{
			return false;
		}
		return true;
	}

	/** serverSock������null����true �����򴴽�ServerSocket ����û���쳣����true�������쳣����false */
	public boolean open(String addr, int port)
	{
		if (serverSock != null)
		{
			return true;
		}
		try
		{
			bindAddr = InetAddress.getByName(addr);
			bindPort = port;
			serverSock = new ServerSocket(bindPort, 0, bindAddr);
	 


		}
		catch (IOException e)
		{
			return false;
		}
		return true;
	}

	/** �ر�socket�ķ������û���쳣����true�����쳣�ͷ���false */
	public boolean close()
	{
		if (serverSock == null)
		{
			return true;
		}
		try
		{
			serverSock.close();
			serverSock = null;
			bindAddr = null;
			bindPort = 0;
			
			Log.e(tag, "�ر�http�������� "+serverSock.getInetAddress().getHostAddress());
		}
		catch (Exception e)
		{
			Debug.warning(e);
			return false;
		}
		return true;
	}

	/** ��ʼ���������ó�ʱʱ�䣬���û�����쳣����Socket���󣬷��򷵻�null */
	public Socket accept()
	{
		if (serverSock == null)
			return null;
		try
		{
			// ��ʼ����
			Socket sock = serverSock.accept();
			// ���ó�ʱʱ��
			sock.setSoTimeout(getTimeout());
			return sock;
		}
		catch (Exception e)
		{
			return null;
		}
	}

	/** �ж��Ƿ񴴽� ServerSocket ���� */
	public boolean isOpened()
	{
		return (serverSock != null) ? true : false;
	}

	// //////////////////////////////////////////////
	// httpRequest
	// //////////////////////////////////////////////

	/** ListenerList httpRequestListenerList ���� HTTPRequestListener ���� */
	private ListenerList httpRequestListenerList = new ListenerList();

	/** ListenerList ����� HTTPRequestListener ���� */
	public void addRequestListener(HTTPRequestListener listener)
	{
		httpRequestListenerList.add(listener);
	}

	/** ListenerList ��ɾ�� HTTPRequestListener ���� */
	public void removeRequestListener(HTTPRequestListener listener)
	{
		httpRequestListenerList.remove(listener);
	}

	/** Ϊ�����е�ÿ��HTTPRequestListener ����ִ��httpRequestRecieved���� */
	public void performRequestListener(HTTPRequest httpReq)
	{
		int listenerSize = httpRequestListenerList.size();
		for (int n = 0; n < listenerSize; n++)
		{
			HTTPRequestListener listener = (HTTPRequestListener) httpRequestListenerList
					.get(n);
		 
			
			listener.httpRequestRecieved(httpReq);
		}
	}

	// //////////////////////////////////////////////
	// run
	// //////////////////////////////////////////////

	private Thread httpServerThread = null;

	@Override
	public void run()
	{
		// ���û�д��� ServerSocket ����return
		if (isOpened() == false)
		{
			return;
		}

		// ��ȡ��ǰ�߳�
		Thread thisThread = Thread.currentThread();

		while (httpServerThread == thisThread)
		{
			// �߳��ò����ص�׼������״̬
			Thread.yield();
			Socket sock;
			try
			{
				Debug.message("accept ...");
				
				sock = accept();
				if (sock != null)
				{
					Debug.message("sock = " + sock.getRemoteSocketAddress());
				}
			}
			catch (Exception e)
			{ 
				break;
			}
			 
			// ����һ��HTTPServerThread ����
			HTTPServerThread httpServThread = new HTTPServerThread(this, sock);
			httpServThread.start();
			Debug.message("httpServThread ...");
		}
	}

	public boolean start()
	{
		StringBuffer name = new StringBuffer("Cyber.HTTPServer/");
		name.append(serverSock.getLocalSocketAddress());
		httpServerThread = new Thread(this, name.toString());
		httpServerThread.start();
		return true;
	}

	/** httpServerThread ����Ϊnull */
	public boolean stop()
	{
		httpServerThread = null;
		return true;
	}
}
