/******************************************************************
 *
 *	CyberHTTP for Java
 *
 *	Copyright (C) Satoshi Konno 2002-2003
 *
 *	File: HostInterface.java
 *
 *	Revision;
 *
 *	05/12/03
 *		- first revision.
 *	05/13/03
 *		- Added support for IPv6 and loopback address.
 *	02/15/04
 *		- Added the following methods to set only a interface.
 *		- setInterface(), getInterfaces(), hasAssignedInterface()
 *	06/30/04
 *		- Moved the package from org.cybergarage.http to org.cybergarage.net.
 *	06/30/04
 *		- Theo Beisch <theo.beisch@gmx.de>
 *		- Changed isUseAddress() to isUsableAddress().
 *	
 ******************************************************************/

package org.cybergarage.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Vector;

import org.cybergarage.util.Debug;


/** �����ӿ� */
public class HostInterface
{
	private static final String TAG  = "org.cybergarage.net.HostInterface";
	// //////////////////////////////////////////////
	// Constants
	// //////////////////////////////////////////////

	public static boolean USE_LOOPBACK_ADDR = false;
	/** ����ʹ��ipv4��ַ */
	public static boolean USE_ONLY_IPV4_ADDR = false;
	public static boolean USE_ONLY_IPV6_ADDR = false;

	// //////////////////////////////////////////////
	// Network Interfaces
	// //////////////////////////////////////////////

	/** �ӿڵ�ַ */
	private static String ifAddress = "";
	public final static int IPV4_BITMASK = 0x0001;
	public final static int IPV6_BITMASK = 0x0010;
	public final static int LOCAL_BITMASK = 0x0100;

	/** ���ýӿڵ�ַ */
	public final static void setInterface(String ifaddr)
	{
		System.out.println("����=======================================");
		ifAddress = ifaddr;
	}

	/** ��ȡ��ַ */
	public final static String getInterface()
	{
		return ifAddress;
	}

	/** �ж��Ƿ� �ѷ���Ľӿ� */
	private final static boolean hasAssignedInterface()
	{
		return (0 < ifAddress.length()) ? true : false;
	}

	// //////////////////////////////////////////////
	// Network Interfaces
	// //////////////////////////////////////////////

	// Thanks for Theo Beisch (10/27/04)
	/** �ж��Ƿ���õĵ�ַ */
	private final static boolean isUsableAddress(InetAddress addr)
	{
		if (USE_LOOPBACK_ADDR == false)
		{
			// ��� InetAddress �Ƿ��ǻ��͵�ַ��ʵ�����г���
			if (addr.isLoopbackAddress() == true || addr.isLinkLocalAddress() == true)
			{
				return false;
			}
		}
		
		
		if (USE_ONLY_IPV4_ADDR == true)
		{
			if (addr instanceof Inet6Address)
			{
				return false;
			}
		}
		if (USE_ONLY_IPV6_ADDR == true)
		{
			if (addr instanceof Inet4Address)
			{
				return false;
			}
		}
		return true;
	}

	/** ��ȡ������ַ������ */
	public final static int getNHostAddresses()
	{

		// System.out.println("===============================���Ծ�̬����");
		// System.out.println("USE_LOOPBACK_ADDR="+USE_LOOPBACK_ADDR);
		// System.out.println("USE_ONLY_IPV4_ADDR="+USE_ONLY_IPV4_ADDR);
		// System.out.println("USE_ONLY_IPV6_ADDR="+USE_ONLY_IPV6_ADDR);
		// System.out.println("===============================");
		// System.out.println("getNHostAddresses");
		if (hasAssignedInterface() == true)
		{
			System.out.println("�Ѿ�����ӿ�");
			return 1;
		}

		int nHostAddrs = 0;
		try
		{
			// ���ش˻����ϵ����нӿڡ�
			Enumeration nis = NetworkInterface.getNetworkInterfaces();
			while (nis.hasMoreElements())
			{
				NetworkInterface ni = (NetworkInterface) nis.nextElement();
				// ����һ�����а󶨵�������ӿ�ȫ���򲿷� InetAddress �� Enumeration��
				Enumeration<InetAddress> addrs = ni.getInetAddresses();
				while (addrs.hasMoreElements())
				{
					InetAddress addr = addrs.nextElement(); 
					 
					
					if (isUsableAddress(addr) == false)
					{ 
						continue;
					}
					nHostAddrs++;
				}
			}
		}
		catch (Exception e)
		{
			Debug.warning(e);
		}
		;
		return nHostAddrs;
	}

	/**
	 * 
	 * @param ipfilter
	 * @param interfaces
	 * @return
	 * @since 1.8.0
	 * @author Stefano "Kismet" Lenzi &lt;kismet.sl@gmail.com&gt;
	 */
	public final static InetAddress[] getInetAddress(int ipfilter,
			String[] interfaces)
	{
		Enumeration nis;
		if (interfaces != null)
		{
			Vector iflist = new Vector();
			for (int i = 0; i < interfaces.length; i++)
			{
				NetworkInterface ni;
				try
				{
					ni = NetworkInterface.getByName(interfaces[i]);
				}
				catch (SocketException e)
				{
					continue;
				}
				if (ni != null)
					iflist.add(ni);

			}
			nis = iflist.elements();
		}
		else
		{
			try
			{
				nis = NetworkInterface.getNetworkInterfaces();
			}
			catch (SocketException e)
			{
				return null;
			}
		}
		ArrayList addresses = new ArrayList();
		while (nis.hasMoreElements())
		{
			NetworkInterface ni = (NetworkInterface) nis.nextElement();
			Enumeration addrs = ni.getInetAddresses();
			while (addrs.hasMoreElements())
			{
				InetAddress addr = (InetAddress) addrs.nextElement();
				if (((ipfilter & LOCAL_BITMASK) == 0)
						&& addr.isLoopbackAddress())
					continue;

				if (((ipfilter & IPV4_BITMASK) != 0)
						&& addr instanceof Inet4Address)
				{
					addresses.add(addr);
				}
				else if (((ipfilter & IPV6_BITMASK) != 0)
						&& addr instanceof InetAddress)
				{
					addresses.add(addr);
				}
			}
		}
		return (InetAddress[]) addresses.toArray(new InetAddress[] {});
	}

	/** ��ȡ������ַ */
	public final static String getHostAddress(int n)
	{
		if (hasAssignedInterface() == true)
			return getInterface();

		int hostAddrCnt = 0;
		try
		{
			// ���ش˻����ϵ����нӿڡ�
			Enumeration<NetworkInterface> nis = NetworkInterface
					.getNetworkInterfaces();
			while (nis.hasMoreElements())
			{
				NetworkInterface ni = nis.nextElement();
				// һ����ݷ���������һ�����а󶨵�������ӿ�ȫ���򲿷� InetAddress �� Enumeration��
				Enumeration<InetAddress> addrs = ni.getInetAddresses();
				while (addrs.hasMoreElements())
				{
					InetAddress addr = addrs.nextElement();
					if (isUsableAddress(addr) == false)
						continue;
					if (hostAddrCnt < n)
					{
						hostAddrCnt++;
						continue;
					}
					String host = addr.getHostAddress();
					// System.out.println("========================================");
					// System.out.println("host="+host);
					// if (addr instanceof Inet6Address)
					// host = "[" + host + "]";
					return host;
				}
			}
		}
		catch (Exception e)
		{
		}
		;
		return "";
	}

	// //////////////////////////////////////////////
	// isIPv?Address
	// //////////////////////////////////////////////

	/** �ж�host�����IPv6 ��ַ����true�����򷵻�false */
	public final static boolean isIPv6Address(String host)
	{
		try
		{
			InetAddress addr = InetAddress.getByName(host);
			if (addr instanceof Inet6Address)
			{
				return true;
			}
			return false;
		}
		catch (Exception e)
		{
		}
		return false;
	}

	/** �ж�host�����IPv4 ��ַ����true�����򷵻�false */
	public final static boolean isIPv4Address(String host)
	{
		try
		{
			InetAddress addr = InetAddress.getByName(host);
			if (addr instanceof Inet4Address)
			{
				return true;
			}
			return false;
		}
		catch (Exception e)
		{
		}
		return false;
	}

	// //////////////////////////////////////////////
	// hasIPv?Interfaces
	// //////////////////////////////////////////////

	public final static boolean hasIPv4Addresses()
	{
		int addrCnt = getNHostAddresses();
		for (int n = 0; n < addrCnt; n++)
		{
			String addr = getHostAddress(n);
			if (isIPv4Address(addr) == true)
				return true;
		}
		return false;
	}

	public final static boolean hasIPv6Addresses()
	{
		int addrCnt = getNHostAddresses();
		for (int n = 0; n < addrCnt; n++)
		{
			String addr = getHostAddress(n);
			if (isIPv6Address(addr) == true)
				return true;
		}
		return false;
	}

	// //////////////////////////////////////////////
	// hasIPv?Interfaces
	// //////////////////////////////////////////////

	public final static String getIPv4Address()
	{
		int addrCnt = getNHostAddresses();
		for (int n = 0; n < addrCnt; n++)
		{
			String addr = getHostAddress(n);
			if (isIPv4Address(addr) == true)
				return addr;
		}
		return "";
	}

	public final static String getIPv6Address()
	{
		int addrCnt = getNHostAddresses();
		for (int n = 0; n < addrCnt; n++)
		{
			String addr = getHostAddress(n);
			if (isIPv6Address(addr) == true)
				return addr;
		}
		return "";
	}

	// //////////////////////////////////////////////
	// getHostURL
	// //////////////////////////////////////////////

	/** ��ȡURL */
	public final static String getHostURL(String host, int port, String uri)
	{
		String hostAddr = host;
		if (isIPv6Address(host) == true)
		{
			hostAddr = "[" + host + "]";
		}
		return "http://" + hostAddr + ":" + Integer.toString(port) + uri;
	}

}
