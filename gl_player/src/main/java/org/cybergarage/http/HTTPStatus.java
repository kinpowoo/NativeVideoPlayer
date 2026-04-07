/******************************************************************
*
*	CyberHTTP for Java
*
*	Copyright (C) Satoshi Konno 2002
*
*	File: HTTPStatus.java
*
*	Revision;
*
*	12/17/02
*		- first revision.
*	09/03/03
*		- Added CONTINUE_STATUS.
*	10/20/04 
*		- Brent Hills <bhills@openshores.com>
*		- Added PARTIAL_CONTENT and INVALID_RANGE;
*	10/22/04
*		- Added isSuccessful().
*	10/29/04
*		- Fixed set() to set the version and the response code when the mothod is null.
*		- Fixed set() to read multi words of the response sring such as Not Found.
*	
******************************************************************/

package org.cybergarage.http;

import java.util.StringTokenizer;

import org.cybergarage.util.Debug;

/*** HTTPStatus http״̬ */
public class HTTPStatus 
{
	////////////////////////////////////////////////
	//	Code
	////////////////////////////////////////////////
	
	/**
	 * ������Ӧ������������󡣷��������ش˴�������ζ�ţ����������յ�������ĵ�һ���֣������ڵȴ��������ಿ�֡�
	 */
	public static final int CONTINUE = 100;
	
	
	/**
	 *  �������ѳɹ����������� 
	 */
	public static final int OK = 200;
	//	Thanks for Brent Hills (10/20/04)
	/**
	 * �������ɹ������˲��� GET ����
	 */
	public static final int PARTIAL_CONTENT = 206;
	
	/**
	 * �����������������﷨ 
	 */
	public static final int BAD_REQUEST = 400;
	
	
	/**
	 * �������Ҳ����������ҳ 
	 */
	public static final int NOT_FOUND = 404;
	
	/**
	 *������δ���������������������õ�����һ��ǰ�������� 
	 */
	public static final int PRECONDITION_FAILED = 412;
	//	Thanks for Brent Hills (10/20/04)
	
	/**
	 * ������ķ�Χ�޷����� 
	 */
	public static final int INVALID_RANGE = 416;
	
	
	/**
	 * ���������������޷�������� 
	 */
	public static final int INTERNAL_SERVER_ERROR = 500;

	/** ��״̬��ת������ʾ���ַ��� ,���û�д�״̬�뷵��һ�����ַ���*/
	public static final String code2String(int code)
	{
		switch (code) {
		case CONTINUE: return "Continue";
		case OK: return "OK";
		case PARTIAL_CONTENT: return "Partial Content";
		case BAD_REQUEST: return "Bad Request";
		case NOT_FOUND: return "Not Found";
		case PRECONDITION_FAILED: return "Precondition Failed";
		case INVALID_RANGE: return "Invalid Range";
		case INTERNAL_SERVER_ERROR: return "Internal Server Error";
		}
		 return "";
	}
 	
	////////////////////////////////////////////////
	//	Constructor
	////////////////////////////////////////////////

	public HTTPStatus()
	{
		setVersion("");
		setStatusCode(0);
		setReasonPhrase("");
	}
	
	public HTTPStatus(String ver, int code, String reason)
	{
		setVersion(ver);
		setStatusCode(code);
		setReasonPhrase(reason);
	}

	/** ����һ�� HTTPStatus
	 * @param
	 * ����һ��״̬�� ���� ״̬ */
	public HTTPStatus(String lineStr)
	{
		set(lineStr);
	}
	
	////////////////////////////////////////////////
	//	Member
	////////////////////////////////////////////////

	/** httpЭ��İ汾 */
	private String version = "";
	/** response��״̬�� */
	private int statusCode = 0;
	/** reasonPhrase ԭ����� */
	private String reasonPhrase = "";

	/** ����http�汾 */
	public void setVersion(String value)
	{
		version = value;
	}
	
	/** ����http״̬�� */
	public void setStatusCode(int value)
	{
		statusCode = value;
	}
	
	/** ����ԭ����� */
	public void setReasonPhrase(String value)
	{
		reasonPhrase = value;
	}
	
	/** ��ȡhttp�汾 */
	public String getVersion()
	{
		return version;
	}
	
	/** ��ȡhttp״̬�� */
	public int getStatusCode()
	{
		return statusCode;
	}
	
	/** ��ȡԭ����� */
	public String getReasonPhrase()
	{
		return reasonPhrase;
	}

	////////////////////////////////////////////////
	//	Status
	////////////////////////////////////////////////

	/** ���״̬����200-299֮�䷵��true ���򷵻�false
	 * 200-299 ���ڱ�ʾ����ɹ��� 
	 */
	final public static boolean isSuccessful(int statCode)
	{
		if (200 <= statCode && statCode < 300){
			return true;
		}
		return false;
	}
	
	public boolean isSuccessful()
	{
		return isSuccessful(getStatusCode());
	}

	////////////////////////////////////////////////
	//	set
	////////////////////////////////////////////////
	
	/**
	 *	����httpЭ��汾��״̬�룬ԭ�����
	 *	@param lineStr ״̬��
	 */
	public void set(String lineStr)
	{
		if (lineStr == null) {
			//����Э��汾
			setVersion(HTTP.VERSION);
			//����״̬��
			setStatusCode(INTERNAL_SERVER_ERROR);
			//����ԭ�����
			setReasonPhrase(code2String(INTERNAL_SERVER_ERROR));
			return;
		}

		try {
			StringTokenizer st = new StringTokenizer(lineStr, HTTP.STATUS_LINE_DELIM);
		
			if (st.hasMoreTokens() == false){
				return;
			}
			String ver = st.nextToken();
			//����http�汾
			setVersion(ver.trim());
			
			if (st.hasMoreTokens() == false){
				return;
			}
			String codeStr = st.nextToken();
			int code = 0;
			try {
				code = Integer.parseInt(codeStr);
			}
			catch (Exception e1) {}
			//����״̬��
			setStatusCode(code);
			
			String reason = "";
			while (st.hasMoreTokens() == true) {
				if (0 <= reason.length()){
					reason += " ";
				}
				reason += st.nextToken();
			}
			//����״̬����
			setReasonPhrase(reason.trim());
		}
		catch (Exception e) {
			Debug.warning(e);
		}

	}	
}
