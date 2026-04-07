/******************************************************************
*
*	CyberHTTP for Java
*
*	Copyright (C) Satoshi Konno 2002-2003
*
*	File: HTTPResponse.java
*
*	Revision;
*
*	11/18/02
*		- first revision.
*	10/22/03
*		- Changed to initialize a content length header.
*	10/22/04
*		- Added isSuccessful().
*	
******************************************************************/

package org.cybergarage.http;

import java.io.InputStream;

/** HTTPResponse �̳� HTTPPacket   */
public class HTTPResponse extends HTTPPacket
{
	////////////////////////////////////////////////
	//	Constructor
	////////////////////////////////////////////////
	
	/** ����һ��Ĭ�ϵ�HTTPResponse ����
	 * Version = 1.1
	 */
	public HTTPResponse()
	{
		setVersion(HTTP.VERSION_11);
		//������������Ϊtext/html; charset="utf-8"
		setContentType(HTML.CONTENT_TYPE);
		//���÷��������
		setServer(HTTPServer.getName());
		setContent("");
	}

	/** ����HTTPResponse ���� */
	public HTTPResponse(HTTPResponse httpRes)
	{
		//������Ӧ
		set(httpRes);
	}

	public HTTPResponse(InputStream in)
	{
		super(in);
	}

	public HTTPResponse(HTTPSocket httpSock)
	{
		this(httpSock.getInputStream());
	}

	////////////////////////////////////////////////
	//	Status Line
	////////////////////////////////////////////////

	/** ״̬�� */
	private int statusCode = 0;
	
	/** ����״̬�� */
	public void setStatusCode(int code)
	{
		statusCode = code;
	}

	/** ��ȡ״̬�� */
	public int getStatusCode()
	{
		if (statusCode != 0)
			return statusCode;
		HTTPStatus httpStatus = new HTTPStatus(getFirstLine());
		return httpStatus.getStatusCode();
	}

	/** �ж�״̬�� �Ƿ���200-299֮�� �ж��Ƿ�ɹ����ɹ�����true�����򷵻�false */
	public boolean isSuccessful()
	{
		return HTTPStatus.isSuccessful(getStatusCode());
	}
	
	/** ��ȡ״̬�� */
	public String getStatusLineString()
	{
		return "HTTP/" + getVersion() + " " + getStatusCode() + " " + HTTPStatus.code2String(statusCode) + HTTP.CRLF;
	}
	
	////////////////////////////////////////////////
	//	getHeader
	////////////////////////////////////////////////
	
	/** ��ȡ״̬�кͶ����Ϣͷ */
	public String getHeader()
	{
		StringBuffer str = new StringBuffer();
	
		str.append(getStatusLineString());
		str.append(getHeaderString());
		
		return str.toString();
	}

	////////////////////////////////////////////////
	//	toString
	////////////////////////////////////////////////
	
	/** ����һ��������http��Ӧ */
	@Override
	public String toString()
	{
		StringBuffer str = new StringBuffer();

		str.append(getStatusLineString());
		str.append(getHeaderString());
		str.append(HTTP.CRLF);
		str.append(getContentString());
		
		return str.toString();
	}

	public void print()
	{
		System.out.println(toString());
	}
}
