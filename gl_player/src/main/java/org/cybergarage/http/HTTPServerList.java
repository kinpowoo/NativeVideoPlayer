/******************************************************************
 *
 *	CyberUPnP for Java
 *
 *	Copyright (C) Satoshi Konno 2002-2003
 *
 *	File: HTTPServerList.java
 *
 *	Revision;
 *
 *	05/08/03
 *		- first revision.
 *	24/03/06
 *		- Stefano Lenzi:added debug information as request by Stephen More
 *
 ******************************************************************/

package org.cybergarage.http;

import java.net.InetAddress;
import java.util.Vector;

import org.cybergarage.net.HostInterface;

/** HTTPServerList �̳� Vector �������HTTPServer ���� */
public class HTTPServerList extends Vector
{
	
	
	// //////////////////////////////////////////////
	// Constructor
	// //////////////////////////////////////////////

	/** ��ַ */
	private InetAddress[] binds = null;
	/** �˿� */
	private int port = 4004;

	/** ����һ��Ĭ�ϵ�HTTPServerList */
	public HTTPServerList()
	{
	}

	/**
	 * ����һ�� HTTPServerList
	 * 
	 * @param list
	 *            ��ַ
	 * @param port
	 *            �˿�
	 * 
	 * */
	public HTTPServerList(InetAddress[] list, int port)
	{
		this.binds = list;
		this.port = port;
	}

	// //////////////////////////////////////////////
	// Methods
	// //////////////////////////////////////////////

	/** �������е�ÿ��HTTPServer�������HTTPRequestListener���� */
	public void addRequestListener(HTTPRequestListener listener)
	{
		int nServers = size();
		for (int n = 0; n < nServers; n++)
		{
			HTTPServer server = getHTTPServer(n);
			server.addRequestListener(listener);
		}
	}

	/** ����������ȡHTTPServer ���� */
	public HTTPServer getHTTPServer(int n)
	{
		return (HTTPServer) get(n);
	}

	// //////////////////////////////////////////////
	// open/close
	// //////////////////////////////////////////////

	/** ���ü���������HTTPServer��close����,�ر�HTTPServer��ServerSocket */
	public void close()
	{
		int nServers = size();
		for (int n = 0; n < nServers; n++)
		{
			HTTPServer server = getHTTPServer(n);
			server.close();
		}
	}

	/** ����HTTPServer������ӵ������У�������ӵ����� */
	public int open()
	{
		InetAddress[] binds = this.binds;
		String[] bindAddresses;
		
		if (binds != null)
		{
			bindAddresses = new String[binds.length];
			for (int i = 0; i < binds.length; i++)
			{
				bindAddresses[i] = binds[i].getHostAddress(); 
			}
		}
		else
		{
			int nHostAddrs = HostInterface.getNHostAddresses();

			bindAddresses = new String[nHostAddrs];
			for (int n = 0; n < nHostAddrs; n++)
			{
				bindAddresses[n] = HostInterface.getHostAddress(n);  
			}
		}
		// System.out.println("=======================================");
		// System.out.println("bindAddresses�ĳ���="+bindAddresses.length);
		//
		// for(int i=0;i<bindAddresses.length;i++){
		// System.out.println("bindAddresses["+i+"]="+bindAddresses[i]);
		// }

		int j = 0;
		for (int i = 0; i < bindAddresses.length; i++)
		{
			// ����һ��HTTPServer
			HTTPServer httpServer = new HTTPServer();
			if ((bindAddresses[i] == null)
					|| (httpServer.open(bindAddresses[i], port) == false))
			{
				close();
				clear();
			}
			else
			{
				add(httpServer);
				j++;
			}
		}
		return j;
	}

	/** ��ֵ������Ķ˿� */
	public boolean open(int port)
	{
		this.port = port;
		return open() != 0;
	}

	// //////////////////////////////////////////////
	// start/stop
	// //////////////////////////////////////////////

	/** ���ü���������HTTPServer��start���� */
	public void start()
	{
		int nServers = size();
		for (int n = 0; n < nServers; n++)
		{
			HTTPServer server = getHTTPServer(n);
			server.start();
		}
	}

	/** ���ü���������HTTPServer��stop���� �����߳�Ϊnull ֹͣѭ�� */
	public void stop()
	{
		int nServers = size();
		for (int n = 0; n < nServers; n++)
		{
			HTTPServer server = getHTTPServer(n);
			server.stop();
		}
	}

}
