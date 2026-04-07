/******************************************************************
 *
 *	CyberHTTP for Java
 *
 *	Copyright (C) Satoshi Konno 2002-2003
 *
 *	File: HTTPServerThread.java
 *
 *	Revision;
 *
 *	10/10/03
 *		- first revision.
 *	
 ******************************************************************/

package org.cybergarage.http;

import java.net.Socket;


/** HTTPServerThread �̳� Thread */
public class HTTPServerThread extends Thread
{
	private static final String tag = "HTTPServerThread";
	private HTTPServer httpServer;
	private Socket sock;

	// //////////////////////////////////////////////
	// Constructor
	// //////////////////////////////////////////////

	/**
	 * ����һ��HTTPServerThread ����
	 * 
	 * @param httpServer
	 *            ��ֵ�������HTTPServer����
	 * @param sock
	 *            ��ֵ�������Socket����
	 **/
	public HTTPServerThread(HTTPServer httpServer, Socket sock)
	{
		super("Cyber.HTTPServerThread");
		this.httpServer = httpServer;
		this.sock = sock;
	}

	// //////////////////////////////////////////////
	// run
	// //////////////////////////////////////////////

	@Override
	public void run()
	{
		// ����һ��HTTPSocket����
		HTTPSocket httpSock = new HTTPSocket(sock);
		if (httpSock.open() == false)
		{
			return;
		}
		
		
		// ����HTTPRequest����
		HTTPRequest httpReq = new HTTPRequest();
		httpReq.setSocket(httpSock);
 
		// ��ȡ
		while (httpReq.read() == true)
		{
			 
			httpServer.performRequestListener(httpReq);
			 
		 
			if (httpReq.isKeepAlive() == false)
			{
				break;
			}
		}
		httpSock.close();
	}
}
