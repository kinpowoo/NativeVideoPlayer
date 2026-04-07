/******************************************************************
*
*	CyberHTTP for Java
*
*	Copyright (C) Satoshi Konno 2002-2004
*
*	File: HTTPConnection.java
*
*	Revision;
*
*	11/18/02
*		- first revision.
*	09/02/03
*		- Giordano Sassaroli <sassarol@cefriel.it>
*		- Problem : The API is unable to receive responses from the Microsoft UPnP stack
*		- Error : the Microsoft UPnP stack is based on ISAPI on IIS, and whenever IIS
*                 receives a post request, it answers with two responses: the first one has no 
*		          body and it is a code 100 (continue) response, which has to be ignored. The
*		          second response is the actual one and should be parsed as the response.
*	02/09/04
*		- Ralf G. R. Bergs" <Ralf@Ber.gs>
*		- Why do you strip leading and trailing white space from the response body?
*		- Disabled to trim the content string.
*	03/11/04
*		- Added some methods about InputStream content.
*		  setContentInputStream(), getContentInputStream() and hasContentInputStream().
*	03/16/04
*		- Thanks for Darrell Young
*		- Added setVersion() and getVersion();
*	03/17/04
*		- Added hasFirstLine();
*	05/26/04
*		- Jan Newmarch <jan.newmarch@infotech.monash.edu.au> (05/26/04)
*		- Changed setCacheControl() and getChcheControl();
*	08/25/04
*		- Added the following methods.
*		  hasContentRange(), setContentRange(), getContentRange(), 
*		  getContentRangeFirstPosition(), getContentRangeLastPosition() and getContentRangeInstanceLength()
*	08/26/04
*		- Added the following methods.
*		  hasConnection(), setConnection(), getConnection(), 
*		  isCloseConnection() and isKeepAliveConnection()
*	08/27/04
*		- Added a updateWithContentLength paramger to setContent().
*		- Changed to HTTPPacket::set() not to change the header of Content-Length.
*	08/28/04
*		- Added init() and read().
*	09/19/04
*		- Added a onlyHeaders parameter to set().
*	10/20/04 
*		- Brent Hills <bhills@openshores.com>
*		- Changed hasContentRange() to check Content-Range and Range header.
*		- Added support for Range header to getContentRange().
*	02/02/05
*		- Mark Retallack <mretallack@users.sourceforge.net>
*		- Fixed set() not to read over the content length when the stream is keep alive.
*	02/28/05
*		- Added the following methods for chunked stream support.
*		  hasTransferEncoding(), setTransferEncoding(), getTransferEncoding(), isChunked().
*	03/02/05
*		- Changed post() to suppot chunked stream.
*	06/11/05
*		- Added setHost().
*	07/07/05
*		- Lee Peik Feng <pflee@users.sourceforge.net>
*		- Andrey Ovchar <AOvchar@consultitnow.com>
*		- Fixed set() to parse the chunk size as a hex string.
*	11/02/05
*		- Changed set() to use BufferedInputStream instead of BufferedReader to
*		  get the content as a byte stream.
*	11/06/05
*		- Added getCharSet().
*		- Changed getContentString() to return the content string using the charset.
*
*******************************************************************/

package org.cybergarage.http;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Calendar;
import java.util.StringTokenizer;
import java.util.Vector;

import org.cybergarage.net.HostInterface;
import org.cybergarage.util.Debug;
import org.cybergarage.util.StringUtil;


/** HTTPPacket �������� 1:������, 2:��Ϣͷ, 3���� */
public class HTTPPacket 
{
	private final  String tag = "HTTPPacket";
	////////////////////////////////////////////////
	//	Constructor
	////////////////////////////////////////////////
	/** ����һ��HTTPPacket ���� ����version��ֵΪ1.1 */
	public HTTPPacket()
	{
		setVersion(HTTP.VERSION);
		setContentInputStream(null);
	}

	public HTTPPacket(HTTPPacket httpPacket)
	{
		setVersion(HTTP.VERSION);
		set(httpPacket);
		setContentInputStream(null);
	}

	public HTTPPacket(InputStream in)
	{
		setVersion(HTTP.VERSION);
		set(in);
		setContentInputStream(null);
	}

	////////////////////////////////////////////////
	//	init
	////////////////////////////////////////////////
	
	/** ��ȡʱ�ĳ�ʼ�� */
	public void init()
	{
		//���õ�һ�е�ֵΪ���ַ���
		setFirstLine("");
		//���httpHeaderList����
		clearHeaders();
		//������ݵĽ�������
		setContent(new byte[0], false);
		setContentInputStream(null);
	}

	////////////////////////////////////////////////
	//	Version
	////////////////////////////////////////////////
	/** version httpЭ��汾 */
	private String version;
	
	/** ����version */
	public void setVersion(String ver)
	{
		version = ver;
	}
	
	/** ��ȡversion */
	public String getVersion()
	{
		return version;
	}
		
	////////////////////////////////////////////////
	//	set
	////////////////////////////////////////////////
	
	/** ��ȡһ�У����ظ��е��ַ�ת */
	private String readLine(BufferedInputStream in)
	{
		//����һ��ByteArrayOutputStream
		ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
		//����һ��byte ���� 
		byte readBuf[] = new byte[1];
		
 		try {
 			int	readLen = in.read(readBuf);
 			while (0 < readLen) {
 				//����ǻ��з��ͽ���ѭ��
 				if (readBuf[0] == HTTP.LF){
 					break;
 				}
 				//��������ڻس���д��
 				if (readBuf[0] != HTTP.CR){ 
 					lineBuf.write(readBuf[0]);
 				}
 	 			readLen = in.read(readBuf);
			}
 		}
 		catch (InterruptedIOException e) {
 			//Ignoring warning because it's a way to break the HTTP connecttion
 			//TODO Create a new level of Logging and log the event
		}
		catch (IOException e) {
			System.out.println("readLine Exception");
			Debug.warning(e);
		}

		return lineBuf.toString();
	}
	
	/** ��ȡ���ݵķ��� */
	protected boolean set(InputStream in, boolean onlyHeaders)
	{
 		try {
 			//����һ��BufferedInputStream
 			BufferedInputStream reader = new BufferedInputStream(in);
 			Debug.message("setsetsetsetset = ");
 			//if(in.available() == 0)return false;
 			//��ȡ��һ��
			String firstLine = readLine(reader);
			if (firstLine == null || firstLine.length() <= 0){
				return false;
			}
			//���õ�һ�е�ֵ
			setFirstLine(firstLine);
			
			// Thanks for Giordano Sassaroli <sassarol@cefriel.it> (09/03/03)
			//����һ��HTTPStatus
	 
 
			HTTPStatus httpStatus = new HTTPStatus(firstLine);
			//��ȡ״̬��
			int statCode = httpStatus.getStatusCode();
			//״̬��Ϊ100�Ĳ��� ,������Ӧ������������� ���������ش˴����ʾ���յ�����ĵ�һ���֣����ڵȴ����ಿ�֡�
			if (statCode == HTTPStatus.CONTINUE){
				//ad hoc code for managing iis non-standard behaviour
				//iis sends 100 code response and a 200 code response in the same
				//stream, so the code should check the presence of the actual
				//response in the stream.
				//skip all header lines
				//ר������ģ�IIS���淶��Ϊ�ķǷ��뾳�ߵĴ��뷢��100�����Ӧ��200������ͬһ�����ķ�Ӧ�������Ĵ���Ӧ�ü���Ƿ����ʵ�ʵ���Ӧ���еġ�
				//�������б�����
				//��ȡû����Ϣͷ
				String headerLine = readLine(reader);
				while ((headerLine != null) && (0 < headerLine.length()) ) {
					//������Ϣͷ����
					HTTPHeader header = new HTTPHeader(headerLine);
					if (header.hasName() == true){
						//����httpHeaderList�е�header��ֵ
						setHeader(header);
					}
					headerLine = readLine(reader);
				}
				//look forward another first line
				//�ڴ�����һ����һ��
				String actualFirstLine = readLine(reader);
				if ((actualFirstLine != null) && (0 < actualFirstLine.length()) ) {
					//this is the actual first line
					//����ʵ�ʵĵ�һ��
					setFirstLine(actualFirstLine);
				}else{
					return true;
				}
			}
				
			//��ȡͷ
			String headerLine = readLine(reader);
			while ((headerLine != null) && (0 < headerLine.length()) ) {
				//����һ��HTTPHeader
				HTTPHeader header = new HTTPHeader(headerLine);
				if (header.hasName() == true){
					setHeader(header);
				}
				headerLine = readLine(reader);
			}
				
			if (onlyHeaders == true) {
				setContent("", false);
				return true;
			}
				
			boolean isChunkedRequest = isChunked();
				
			long contentLen = 0;
			if (isChunkedRequest == true) {
				try {
					String chunkSizeLine = readLine(reader);
					// Thanks for Lee Peik Feng <pflee@users.sourceforge.net> (07/07/05)
					//contentLen = Long.parseLong(new String(chunkSizeLine.getBytes(), 0, chunkSizeLine.length()-2), 16);
					contentLen = (chunkSizeLine != null) ? Long.parseLong(chunkSizeLine.trim(), 16) : 0;
				}
				catch (Exception e) {};
			}
			else{
				//��ȡ�ļ��ĳ���
				contentLen = getContentLength();
			}
			ByteArrayOutputStream contentBuf = new ByteArrayOutputStream();
			
			//�����Ƕ�ȡ����
			while (0 < contentLen) {
				//��ȡ���С
				int chunkSize = HTTP.getChunkSize();
				
				/* Thanks for Stephan Mehlhase (2010-10-26) */
				//�ж�byte����ĳ��� ���ݳ��ȴ��ڿ��С����Ϊ���С������Ϊ���ݳ���
				byte readBuf[] = new byte[(int) (contentLen > chunkSize ? chunkSize : contentLen)];
				
				long readCnt = 0;
				//��ȡ����д�����ڴ�
				while (readCnt < contentLen) {
					try {
						// Thanks for Mark Retallack (02/02/05)
						long bufReadLen = contentLen - readCnt;
						if (chunkSize < bufReadLen){
							bufReadLen = chunkSize;
						}
						int readLen = reader.read(readBuf, 0, (int)bufReadLen);
						if (readLen < 0){
							break;
						}
						//д�����ڴ�
						contentBuf.write(readBuf, 0, readLen);
						readCnt += readLen;
					}
					catch (Exception e)
					{
						Debug.warning(e);
						break;
					}
				}
				if (isChunkedRequest == true) {
					// skip CRLF
					// �����س�����
					long skipLen = 0;
					do {
						long skipCnt = reader.skip(HTTP.CRLF.length() - skipLen);
						if (skipCnt < 0){
							break;
						}
						skipLen += skipCnt;
					} while (skipLen < HTTP.CRLF.length());
					// read next chunk size
					// ��ȡ��һ�����ݿ��С
					try {
						String chunkSizeLine = readLine(reader);
						// Thanks for Lee Peik Feng <pflee@users.sourceforge.net> (07/07/05)
						contentLen = Long.parseLong(new String(chunkSizeLine.getBytes(), 0, chunkSizeLine.length()-2), 16);
					}
					catch (Exception e) {
						contentLen = 0;
					};
				}
				else{
					contentLen = 0;
				}
			}

			setContent(contentBuf.toByteArray(), false);
 		}
		catch (Exception e) {
			Debug.warning(e);
			return false;
		}
		
		return true;
	}

	/** ����set(InputStream in, boolean onlyHeaders) */
	protected boolean set(InputStream in)
	{
		return set(in, false);
	}
	
	/** ����set(InputStream in) */
	protected boolean set(HTTPSocket httpSock)
	{
		return set(httpSock.getInputStream());
	}

	/** ���ñ�������ݣ����ݲ��������ݸ�ֵ����������� */
	protected void set(HTTPPacket httpPacket)
	{
		setFirstLine(httpPacket.getFirstLine());
		
		clearHeaders();
		int nHeaders = httpPacket.getNHeaders();
		for (int n=0; n<nHeaders; n++) {
			HTTPHeader header = httpPacket.getHeader(n);
			addHeader(header);
		}
		setContent(httpPacket.getContent());
	}

	////////////////////////////////////////////////
	//	read
	////////////////////////////////////////////////
	
	public boolean read(HTTPSocket httpSock)
	{
		init();
		return set(httpSock);
	}
	
	////////////////////////////////////////////////
	//	String
	////////////////////////////////////////////////

	/** firstLine ��һ��  */
	private String firstLine = "";
 	
	/** ����firstLine ��ֵ */
	private void setFirstLine(String value)
	{
			firstLine = value;
	}
	
	/** ��ȡfirstLine ��ֵ */
	protected String getFirstLine()
	{
		return firstLine;
	}

	protected String getFirstLineToken(int num)
	{
		StringTokenizer st = new StringTokenizer(firstLine, HTTP.REQEST_LINE_DELIM);
		String lastToken = "";
		for (int n=0; n<=num; n++) {
			if (st.hasMoreTokens() == false){
				return "";
			}
			lastToken = st.nextToken();
		}
		return lastToken;
     }
	
	/** �ж�firstLine�ĳ��ȣ��������0�ͷ���true�����򷵻�false */
	public boolean hasFirstLine()
	{
		return (0 < firstLine.length()) ? true : false;
	}
	
	////////////////////////////////////////////////
	//	Header
	////////////////////////////////////////////////

	/** httpHeaderList ���ϱ���HTTPHeader���� */
	private Vector httpHeaderList = new Vector();
	
	/** ��ȡhttpHeaderList �� httpHeader������*/
	public int getNHeaders()
	{
		return httpHeaderList.size();
	}

	/** httpHeaderList�� ��� HTTPHeaderͷ����*/
	public void addHeader(HTTPHeader header)
	{
		httpHeaderList.add(header);
	}

	/** ����HTTPHeader������ӵ�httpHeaderList���� */
	public void addHeader(String name, String value)
	{
		HTTPHeader header = new HTTPHeader(name, value);
		httpHeaderList.add(header);
	}

	/** ����������ȡhttpHeaderList�� Ԫ�أ�����HTTPHeader*/
	public HTTPHeader getHeader(int n)
	{
		return (HTTPHeader)httpHeaderList.get(n);
	}
	
	/** �������ֻ�ȡhttpHeaderList��Ԫ�أ������ httpHeaderListԪ���е�HTTPHeader��name�ֶ���ͬ,�����ִ�Сд�򷵻�HTTPHeader
	 *  ���򷵻�false
	 */
	public HTTPHeader getHeader(String name)
	{
		int nHeaders = getNHeaders();
		for (int n=0; n<nHeaders; n++) {
			HTTPHeader header = getHeader(n);
			String headerName = header.getName();
			if (headerName.equalsIgnoreCase(name) == true){
				return header;			
			}
		}
		return null;
	}

	/** ���httpHeaderList���� */
	public void clearHeaders()
	{
		httpHeaderList.clear();
		httpHeaderList = new Vector();
	}
	
	/** �ж�����Ϊname��ֵ����Ϣͷ�Ƿ���� �д���Ϣͷ����true�����򷵻�false */
	public boolean hasHeader(String name)
	{
		return (getHeader(name) != null) ? true : false;
	}

	/** ���httpHeaderList����HTTPHeader��������name��ͬ������value��ֵ
	 *���򴴽�һ���µ�HTTPHeader ��ӵ�httpHeaderList��
	 * 
	 * @param name ��ȡHTTPHeader��������HTTPHeader��name�ֶε�ֵ
	 * @param value ����HTTPHeader ��ֵ
	 *
	 **/
	public void setHeader(String name, String value)
	{
		HTTPHeader header = getHeader(name);
		if (header != null) {
			header.setValue(value);
			return;
		}
		addHeader(name, value);
	}

	public void setHeader(String name, int value)
	{
		setHeader(name, Integer.toString(value));
	}

	public void setHeader(String name, long value)
	{
		setHeader(name, Long.toString(value));
	}
	
	/** ���httpHeaderList�����а��� header ����Ϣͷ�����޸ĸ�ֵ��������ӵ�httpHeaderList��*/
	public void setHeader(HTTPHeader header)
	{
		setHeader(header.getName(), header.getValue());
	}

	/** ����name��ȡHTTPHeader �����HTTPHeaderΪnull���ؿ��ַ��������򷵻�HTTPHeader��value��ֵ */
	public String getHeaderValue(String name)
	{
		HTTPHeader header = getHeader(name);
		if (header == null){
			return "";
		}
		return header.getValue();
	}

	////////////////////////////////////////////////
	// set*Value
	////////////////////////////////////////////////

	/** ����ͷ��ֵ���ж�ֵ�Ƿ���ǰ׺���׺��û�о����ǰ׺���׺ */
	public void setStringHeader(String name, String value, String startWidth, String endWidth)
	{
		String headerValue = value;
		if (headerValue.startsWith(startWidth) == false){
			headerValue = startWidth + headerValue;
		}
		if (headerValue.endsWith(endWidth) == false){
			headerValue = headerValue + endWidth;
		}
		setHeader(name, headerValue);
	}

	/** ������Ϣͷ,��Ϣͷ��ֵǰ׺��\��׺Ҳ��\
	 * @param name ��Ϣͷ������
	 * @param value ��Ϣͷ��ֵ
	 *  
	 *  */
	public void setStringHeader(String name, String value)
	{
		setStringHeader(name, value, "\"", "\"");
	}
	
	/** ��ȡͷ��ֵ��������û��ǰ׺�ͺ�׺�� */
	public String getStringHeaderValue(String name, String startWidth, String endWidth)
	{
		String headerValue = getHeaderValue(name);
		if (headerValue.startsWith(startWidth) == true){
			headerValue = headerValue.substring(1, headerValue.length());
		}
		if (headerValue.endsWith(endWidth) == true){
			headerValue = headerValue.substring(0, headerValue.length()-1);
		}
		return headerValue;
	}
	
	/** ��ȡͷ��ֵ��������û��ǰ׺ \ �� ��׺ \ ��  */
	public String getStringHeaderValue(String name)
	{
		return getStringHeaderValue(name, "\"", "\"");
	}

	public void setIntegerHeader(String name, int value)
	{
		setHeader(name, Integer.toString(value));
	}
	
	/** ���� */
	public void setLongHeader(String name, long value)
	{
		setHeader(name, Long.toString(value));
	}
	
	public int getIntegerHeaderValue(String name)
	{
		HTTPHeader header = getHeader(name);
		if (header == null)
			return 0;
		return StringUtil.toInteger(header.getValue());
	}

	/** �������ֻ�ȡHTTPHeader ,��HTTPHeader��value��ֵת��Ϊlong�� */
	public long getLongHeaderValue(String name)
	{
		//��ȡHTTPHeader
		HTTPHeader header = getHeader(name);
		if (header == null){
			return 0;
		}
		return StringUtil.toLong(header.getValue());
	}

	////////////////////////////////////////////////
	//	getHeader
	////////////////////////////////////////////////
	
	/** ��ȡ��Ϣͷ */
	public String getHeaderString()
	{
		StringBuffer str = new StringBuffer();
	
		int nHeaders = getNHeaders();
		for (int n=0; n<nHeaders; n++) {
			HTTPHeader header = getHeader(n);
			str.append(header.getName() + ": " + header.getValue() + HTTP.CRLF);
		}
		
		return str.toString();
	}

	////////////////////////////////////////////////
	//	Contents
	////////////////////////////////////////////////

	/** ���ݵ��ֽ� */
	private byte content[] = new byte[0];
	
	/** ���� content��ֵ ���updateWithContentLength Ϊtrue���Content-Length ��Ϣͷ��httpHeaderList������ */
	public void setContent(byte data[], boolean updateWithContentLength)
	{
		content = data;
		if (updateWithContentLength == true){
			setContentLength(data.length);
		}
	}

	public void setContent(byte data[])
	{
		setContent(data, true);
	}
	
	/** ����setContent(byte data[], boolean updateWithContentLength)
	 * data Ϊdata.getBytes()
	 * updateWithContentLengthΪupdateWithContentLength
	 */
	public void setContent(String data, boolean updateWithContentLength)
	{
		setContent(data.getBytes(), updateWithContentLength);
	}

	/** 
	 * ���� Content-Length ͷ��ֵ
	 * ���� setContent(String data, boolean updateWithContentLength)
	 * updateWithContentLength Ϊtrue
	 */
	public void setContent(String data)
	{
		setContent(data, true);
	}
	
	/** ��ȡ���ݵ��ֽ� */
	public  byte []getContent()
	{
		return content;
	}

	/** ��ȡ�����ַ��� */
	public  String getContentString()
	{
		//��ȡ�ַ���
		String charSet = getCharSet();
		if (charSet == null || charSet.length() <= 0){
			return new String(content);
		}
		try {
			return new String(content, charSet);
		}
		catch (Exception e) {
			Debug.warning(e);
		}
		return new String(content);
	}
	
	public boolean hasContent()
	{
		return (content.length > 0) ? true : false;
	}

	////////////////////////////////////////////////
	//	Contents (InputStream)
	////////////////////////////////////////////////

	/** ���ݵ������� */
	private InputStream contentInput = null;
	
	/** �������ݵ������� InputStream contentInput */
	public void setContentInputStream(InputStream in)
	{
		contentInput = in;
	}

	/** ��ȡ ���ݵ������� InputStream contentInput*/
	public InputStream getContentInputStream()
	{
		return contentInput;
	}

	/** �ж� contentInput �Ƿ�Ϊnull����Ϊnull����true��Ϊnull����false*/
	public boolean hasContentInputStream()
	{
		return (contentInput != null) ? true : false;
	}

	////////////////////////////////////////////////
	//	ContentType
	////////////////////////////////////////////////

	/** ����HTTPHeader ��nameΪ Content-Type ֵΪtype��ֵ*/
	public void setContentType(String type)
	{
		setHeader(HTTP.CONTENT_TYPE, type);
	}

	/** ��ȡ��Ϣͷ  Content-Type ��ֵ */
	public String getContentType()
	{
		return getHeaderValue(HTTP.CONTENT_TYPE);
	}

	////////////////////////////////////////////////
	//	Charset
	////////////////////////////////////////////////

	/** ��ȡ�ַ��� */
	public String getCharSet()
	{
		//��ȡContent-Type ��ֵ
		String contentType = getContentType();
		if (contentType == null){
			return "";
		}
		//�ĳ�Сд
		contentType = contentType.toLowerCase();
		int charSetIdx = contentType.indexOf(HTTP.CHARSET);
		if (charSetIdx < 0){
			return "";
		}
		int charSetEndIdx = charSetIdx + HTTP.CHARSET.length() + 1; 
		//��ȡcharSet��ֵ
		String charSet = new String(contentType.getBytes(), charSetEndIdx, (contentType.length() - charSetEndIdx));
		if (charSet.length() < 0){
			return "";
		}
		if (charSet.charAt(0) == '\"'){
			charSet = charSet.substring(1, (charSet.length() - 1));
		}
		if (charSet.length() < 0){
			return "";
		}
		if (charSet.charAt((charSet.length()-1)) == '\"'){
			charSet = charSet.substring(0, (charSet.length() - 1));
		}
		return charSet;
	}

	////////////////////////////////////////////////
	//	ContentLength
	////////////////////////////////////////////////

	
	/** ����HTTPHeader ��nameΪ Content-Length ֵΪlen��ֵ*/
	public void setContentLength(long len)
	{
		setLongHeader(HTTP.CONTENT_LENGTH, len);
	}

	/**  ��ȡ��ϢͷContent-Length��ֵ������һ��long�� */
	public long getContentLength()
	{
		return getLongHeaderValue(HTTP.CONTENT_LENGTH);
	}

	////////////////////////////////////////////////
	//	Connection
	////////////////////////////////////////////////

	/** �����Connectionͷ����true�����򷵻�false */
	public boolean hasConnection()
	{
		return hasHeader(HTTP.CONNECTION);
	}

	/** ����HTTPHeader ��nameΪConnection valueΪvalue��ֵ
	 * 	�����Ƿ񱣴����� 
	 */
	public void setConnection(String value)
	{
		setHeader(HTTP.CONNECTION, value);
	}

	/** ��ȡConnectionͷ��ֵ */
	public String getConnection()
	{
		return getHeaderValue(HTTP.CONNECTION);
	}

	public boolean isCloseConnection()
	{	
		if (hasConnection() == false)
			return false;
		String connection = getConnection();
		if (connection == null)
			return false;
		return connection.equalsIgnoreCase(HTTP.CLOSE);
	}

	public boolean isKeepAliveConnection()
	{	
		if (hasConnection() == false)
			return false;
		String connection = getConnection();
		if (connection == null)
			return false;
		return connection.equalsIgnoreCase(HTTP.KEEP_ALIVE);
	}
	
	////////////////////////////////////////////////
	//	ContentRange
	////////////////////////////////////////////////

	/** Content-Range || Range ���ھͷ���true */
	public boolean hasContentRange()
	{
		return (hasHeader(HTTP.CONTENT_RANGE) || hasHeader(HTTP.RANGE));
	}
	
	/** 
	 * ����ContentRange �������ָ�ʽ:Content-Range: bytes 0-800/801
	 * @param firstPos ��λ������0
	 * @param lastPos  ����λ������800
	 * @param length   ���ݵ��ܳ��� ����801
	 */ 
	public void setContentRange(long firstPos, long lastPos, long length)
	{
		String rangeStr = "";
		rangeStr += HTTP.CONTENT_RANGE_BYTES + " ";
		rangeStr += Long.toString(firstPos) + "-";
		rangeStr += Long.toString(lastPos) + "/";
		rangeStr += ((0 < length) ? Long.toString(length) : "*");
		setHeader(HTTP.CONTENT_RANGE, rangeStr);
	}

	/** ��ȡRange ����  long[]*/
	public long[] getContentRange()
	{
		long range[] = new long[3];
		range[0] = range[1] = range[2] = 0;
		if (hasContentRange() == false){
			return range;
		}
		//��ȡContent-Range��ֵ
		String rangeLine = getHeaderValue(HTTP.CONTENT_RANGE);
		// Thanks for Brent Hills (10/20/04)
		if (rangeLine.length() <= 0){
			//��ȡRange��ֵ
			rangeLine = getHeaderValue(HTTP.RANGE);
		}
		if (rangeLine.length() <= 0){
			return range;
		}
		
		try
		{
			String str[] = rangeLine.split(" |=|-|/");	
			
			if(2 <= str.length){
				range[0] = Long.parseLong(str[1]);
			}
			
			if(3 <= str.length){
				range[1] = Long.parseLong(str[2]);
			}
			
			if(4 <= str.length){
				range[2] = Long.parseLong(str[3]);
			}
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace();
		}
		return range;
//		// Thanks for Brent Hills (10/20/04)
//		StringTokenizer strToken = new StringTokenizer(rangeLine, " =");
//		Debug.message("strToken");
//		// Skip bytes
//		if (strToken.hasMoreTokens() == false){
//			return range;
//		}
//		String bytesStr = strToken.nextToken(" ");
//		// Get first-byte-pos
//		if (strToken.hasMoreTokens() == false)
//			return range;
//		String firstPosStr = strToken.nextToken(" -");
//		Debug.message("firstPosStr"+firstPosStr);
//		try {
//			range[0] = Long.parseLong(firstPosStr);
//		}
//		catch (NumberFormatException e) {};
//		if (strToken.hasMoreTokens() == false)
//			return range;
//		String lastPosStr = strToken.nextToken("-/");
//		Debug.message("lastPosStr"+lastPosStr);
//		try {
//			range[1] = Long.parseLong(lastPosStr);
//		}
//		catch (NumberFormatException e) {};
//		if (strToken.hasMoreTokens() == false)
//			return range;
//		String lengthStr = strToken.nextToken("/");
//		try {
//			range[2] = Long.parseLong(lengthStr);
//		}
//		catch (NumberFormatException e) {};
//		return range;
	}
	
	/** ��ȡ��λ�� */
	public long getContentRangeFirstPosition()
	{
		long range[] = getContentRange();
		return range[0];
	}

	/** ��ȡ���λ�� */
	public long getContentRangeLastPosition()
	{
		long range[] = getContentRange();
		return range[1];
	}

	public long getContentRangeInstanceLength()
	{
		long range[] = getContentRange();
		return range[2];
	}
	
	////////////////////////////////////////////////
	//	CacheControl
	////////////////////////////////////////////////

	public void setCacheControl(String directive)
	{
		setHeader(HTTP.CACHE_CONTROL, directive);
	}
	
	public void setCacheControl(String directive, int value)
	{
		String strVal = directive + "=" + Integer.toString(value);
		setHeader(HTTP.CACHE_CONTROL, strVal);
	}
	
	public void setCacheControl(int value)
	{
		setCacheControl(HTTP.MAX_AGE, value);
	}

	public String getCacheControl()
	{
		return getHeaderValue(HTTP.CACHE_CONTROL);
	}

	////////////////////////////////////////////////
	//	Server
	////////////////////////////////////////////////

	/** ����HTTPHeader ��nameΪ Server ֵΪname��ֵ*/
	public void setServer(String name)
	{
		setHeader(HTTP.SERVER, name);
	}

	public String getServer()
	{
		return getHeaderValue(HTTP.SERVER);
	}

	////////////////////////////////////////////////
	//	Host
	////////////////////////////////////////////////

	/**
	 * ����HOSTͷ��ֵ
	 * ����HTTPHeader��name��value��ֵ name ��ֵΪHOST ��value��ֵΪ hostAddr + ":" + Integer.toString(port)
	 * @param host �����ĵ�ַ
	 * @param port �˿�
	 * 
	 */
	public void setHost(String host, int port)
	{
		String hostAddr = host;
		if (HostInterface.isIPv6Address(host) == true){
			hostAddr = "[" + host + "]";
		}
		setHeader(HTTP.HOST, hostAddr + ":" + Integer.toString(port));
	}

	/** ����������Host */
	public void setHost(String host)
	{
		String hostAddr = host;
		if (HostInterface.isIPv6Address(host) == true){
			hostAddr = "[" + host + "]";
		}
		setHeader(HTTP.HOST, hostAddr);
	}
	
	public String getHost()
	{
		return getHeaderValue(HTTP.HOST);
	}


	////////////////////////////////////////////////
	//	Date
	////////////////////////////////////////////////

	
	/** ����Date ����Ϣͷ*/
	public void setDate(Calendar cal)
	{
		Date date = new Date(cal);
		setHeader(HTTP.DATE, date.getDateString());
	}

	/** ��ȡ������Ϣͷ��ֵ */
	public String getDate()
	{
		return getHeaderValue(HTTP.DATE);
	}

	////////////////////////////////////////////////
	//	Connection
	////////////////////////////////////////////////
	/** Transfer-Encoding �ж��Ƿ��д���Ϣͷ���з���true��û�з���false */
	public boolean hasTransferEncoding()
	{
		return hasHeader(HTTP.TRANSFER_ENCODING);
	}

	public void setTransferEncoding(String value)
	{
		setHeader(HTTP.TRANSFER_ENCODING, value);
	}

	/** ��ȡTransfer-Encoding ��Ӧ��ֵ */
	public String getTransferEncoding()
	{
		return getHeaderValue(HTTP.TRANSFER_ENCODING);
	}

	/** ���û�� Transfer-Encoding ��Ϣͷ����false
	 * ����л�ȡTransfer-Encoding ��Ϣͷ�����ֵ�������Chunked��ͬ,�����ִ�Сд���ͷ���true�����򷵻�false
	 */
	public boolean isChunked()
	{	
		if (hasTransferEncoding() == false){
			return false;
		}
		String transEnc = getTransferEncoding();
		if (transEnc == null){
			return false;
		}
		return transEnc.equalsIgnoreCase(HTTP.CHUNKED);
	}
	
	////////////////////////////////////////////////
	//	set
	////////////////////////////////////////////////

/*
	public final static boolean parse(HTTPPacket httpPacket, InputStream in)
	{
 		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			return parse(httpPacket, reader);
		}
		catch (Exception e) {
			Debug.warning(e);
		}
		return false;
	}
*/
}

