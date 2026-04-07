/******************************************************************
 *
 *	CyberHTTP for Java
 *
 *	Copyright (C) Satoshi Konno 2002-2004
 *
 *	File: HTTPSocket.java
 *
 *	Revision;
 *
 *	12/12/02
 *		- first revision.
 *	03/11/04
 *		- Added the following methods about chunk size.
 *		  setChunkSize(), getChunkSize().
 *	08/26/04
 *		- Added a isOnlyHeader to post().
 *	03/02/05
 *		- Changed post() to suppot chunked stream.
 *	06/10/05
 *		- Changed post() to add a Date headedr to the HTTPResponse before the posting.
 *	07/07/05
 *		- Lee Peik Feng <pflee@users.sourceforge.net>
 *		- Fixed post() to output the chunk size as a hex string.
 *	
 ******************************************************************/

package org.cybergarage.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Calendar;


/** HTTPSocket ��������д�� */
public class HTTPSocket
{

	private static final String TAG = "org.cybergarage.http.HTTPSocket";

	// //////////////////////////////////////////////
	// Constructor
	// //////////////////////////////////////////////

	/**
	 * ����һ�� HTTPSocket ���� ��ֵSocket �� InputStream��OutputStream
	 * 
	 * @param socket
	 *            ��ֵ�������Socket����
	 **/
	public HTTPSocket(Socket socket)
	{
		setSocket(socket);
		open();
	}

	/**
	 * ����һ�� HTTPSocket ����ֵSocket �� InputStream��OutputStream
	 * 
	 * @param socket
	 *            ���Լ���Socket �� InputStream��OutputStream��ֵ��HTTPSocket
	 * 
	 */
	public HTTPSocket(HTTPSocket socket)
	{
		setSocket(socket.getSocket());
		setInputStream(socket.getInputStream());
		setOutputStream(socket.getOutputStream());
	}

	@Override
	public void finalize()
	{
		close();
	}

	// //////////////////////////////////////////////
	// Socket
	// //////////////////////////////////////////////

	/** Socket socket */
	private Socket socket = null;

	/** ���� socket */
	private void setSocket(Socket socket)
	{
		this.socket = socket;
	}

	/** ��ȡ socket */
	public Socket getSocket()
	{
		return socket;
	}

	// //////////////////////////////////////////////
	// local address/port
	// //////////////////////////////////////////////

	/** ��ȡ���ص�ַ */
	public String getLocalAddress()
	{
		return getSocket().getLocalAddress().getHostAddress();
	}

	/** ��ȡ���ض˿� */
	public int getLocalPort()
	{
		return getSocket().getLocalPort();
	}

	// //////////////////////////////////////////////
	// in/out
	// //////////////////////////////////////////////

	/** InputStream sockIn ������ */
	private InputStream sockIn = null;
	/** OutputStream sockOut ����� */
	private OutputStream sockOut = null;

	/** ���� sockIn */
	private void setInputStream(InputStream in)
	{
		sockIn = in;
	}

	/** ��ȡsockIn */
	public InputStream getInputStream()
	{
		return sockIn;
	}

	/** ���� sockOut */
	private void setOutputStream(OutputStream out)
	{
		sockOut = out;
	}

	/** ��ȡsockOut */
	private OutputStream getOutputStream()
	{
		return sockOut;
	}

	// //////////////////////////////////////////////
	// open/close
	// //////////////////////////////////////////////

	/** ��ȡSocket�е�InputStream ��OutputStream ,�����쳣����false��û���쳣����true */
	public boolean open()
	{
		// ��ȡsocket����
		Socket sock = getSocket();
		try
		{
			// ��ȡInputStream
			sockIn = sock.getInputStream();
			// ��ȡOutputStream
			sockOut = sock.getOutputStream();
		}
		catch (Exception e)
		{
			// TODO Add blacklistening of the UPnP Device
			return false;
		}
		return true;
	}

	/** �ر�sockIn,sockOut,socket�ķ�����û���쳣����true�����쳣����false */
	public boolean close()
	{
		try
		{
			if (sockIn != null)
			{
				sockIn.close();
			}
			if (sockOut != null)
			{
				sockOut.close();
			}
			getSocket().close();
		}
		catch (Exception e)
		{
			// Debug.warning(e);
			return false;
		}
		return true;
	}

	// //////////////////////////////////////////////
	// post
	// //////////////////////////////////////////////

	/**
	 * ������д��
	 * 
	 * @param httpRes
	 * @param content
	 *            д��������
	 * @param contentOffset
	 *            ���������ƫ����
	 * @param contentLength
	 *            ���ݵĳ���
	 * @param isOnlyHeader
	 *            ����ʽ��HEAD Ϊtrue������false
	 * 
	 **/
	private boolean post(HTTPResponse httpRes, byte content[],
			long contentOffset, long contentLength, boolean isOnlyHeader)
	{
		// TODO Check for bad HTTP agents, this method may be list for
		// IOInteruptedException and for blacklistening
		// ��鲻�õ�HTTP�������ַ����������б�IOInteruptedException��blacklistening
		httpRes.setDate(Calendar.getInstance());

		// ��ȡOutputStream
		OutputStream out = getOutputStream();

		try
		{
			// �������ݳ���
			httpRes.setContentLength(contentLength);

			// д��״̬�кͶ����Ϣͷ
			out.write(httpRes.getHeader().getBytes());
			// д��CRLF
			out.write(HTTP.CRLF.getBytes());

			// ���������HEAD ִ��
			if (isOnlyHeader == true)
			{
				out.flush();
				return true;
			}

			// �ж��Ƿ���Transfer-Encoding: chunked
			boolean isChunkedResponse = httpRes.isChunked();

			if (isChunkedResponse == true)
			{
				// Thanks for Lee Peik Feng <pflee@users.sourceforge.net>
				// (07/07/05)
				// �����ݳ���ת����16����
				String chunSizeBuf = Long.toHexString(contentLength);
				// д�����ݳ���
				out.write(chunSizeBuf.getBytes());
				// д��CRLF
				out.write(HTTP.CRLF.getBytes());
			}

			// д������
			out.write(content, (int) contentOffset, (int) contentLength);

			if (isChunkedResponse == true)
			{
				// д��CRLF
				out.write(HTTP.CRLF.getBytes());
				// д���������
				out.write("0".getBytes());
				// д��CRLF
				out.write(HTTP.CRLF.getBytes());
			}
			// ˢ�»�����
			out.flush();
		}
		catch (Exception e)
		{
			// Debug.warning(e);
			return false;
		}

		return true;
	}

	/**
	 * �����ݱ߶���д��
	 * 
	 * @param httpRes
	 * @param in
	 *            ������
	 * @param contentOffset
	 *            ���ݵ���ʼλ��
	 * @param contentLength
	 *            ���ݵĳ���
	 * @param isOnlyHeader
	 *            ����ʽ��HEAD Ϊtrue������false
	 * @return
	 */
	private boolean post(HTTPResponse httpRes, InputStream in,
			long contentOffset, long contentLength, boolean isOnlyHeader)
	{
		// TODO Check for bad HTTP agents, this method may be list for
		// IOInteruptedException and for blacklistening
		// ��鲻�õ�HTTP�������ַ����������б�IOInteruptedException��blacklistening
		try
		{
			httpRes.setDate(Calendar.getInstance());

			// ��ȡoutputStream
			OutputStream out = getOutputStream();

			// �������ݳ���
			httpRes.setContentLength(contentLength);

			// д����Ӧ��״̬�кͶ����Ϣͷ

			out.write(httpRes.getHeader().getBytes());

			// д��CRLF
			out.write(HTTP.CRLF.getBytes());

			// ���������HEAD ִ��
			if (isOnlyHeader == true)
			{
				out.flush();
				return true;
			}

			// �ж��Ƿ���Transfer-Encoding: chunked
			boolean isChunkedResponse = httpRes.isChunked();

			if (0 < contentOffset)
			{
				// ����������ʼλ��
				in.skip(contentOffset);
			}

			// ��ȡ���С
			int chunkSize = HTTP.getChunkSize();
			// ����һ����ȡ�Ļ�������
			byte readBuf[] = new byte[chunkSize];
			long readCnt = 0;
			long readSize = (chunkSize < contentLength) ? chunkSize
					: contentLength;
			int readLen = in.read(readBuf, 0, (int) readSize);

			while (0 < readLen && readCnt < contentLength)
			{
				if (isChunkedResponse == true)
				{
					// Thanks for Lee Peik Feng <pflee@users.sourceforge.net>
					// (07/07/05)
					// ת����16����
					String chunSizeBuf = Long.toHexString(readLen);
					// ���ͳ���
					out.write(chunSizeBuf.getBytes());
					// ����CRLF

					out.write(HTTP.CRLF.getBytes());
				}
				// д���Ѷ�������
				out.write(readBuf, 0, readLen);

				if (isChunkedResponse == true)
				{
					// ����CRLF
					out.write(HTTP.CRLF.getBytes());
				}
				readCnt += readLen;
				readSize = (chunkSize < (contentLength - readCnt)) ? chunkSize
						: (contentLength - readCnt);
				readLen = in.read(readBuf, 0, (int) readSize);
			}

			// �������
			if (isChunkedResponse == true)
			{
				// ����0
				out.write("0".getBytes());
				// ����CRLF
				out.write(HTTP.CRLF.getBytes());
			}

			out.flush();


		}
		catch (IOException e)
		{ 
		}
		
		return true;
	}

	/**
	 * ������Ӧ����Ϣ
	 * 
	 * @param httpRes
	 *            HTTPResponse����
	 * @param contentOffset
	 *            ���ݵ���ʼλ��
	 * @param contentLength
	 *            ���ݵĳ���
	 * @param isOnlyHeader
	 * @return
	 */
	public boolean post(HTTPResponse httpRes, long contentOffset,
			long contentLength, boolean isOnlyHeader)
	{
		// TODO Close if Connection != keep-alive
		if (httpRes.hasContentInputStream() == true)
		{
			return post(httpRes, httpRes.getContentInputStream(),
					contentOffset, contentLength, isOnlyHeader);
		}
		return post(httpRes, httpRes.getContent(), contentOffset,
				contentLength, isOnlyHeader);
	}
}
