/******************************************************************
 *
 *	CyberUtil for Java
 *
 *	Copyright (C) Satoshi Konno 2002
 *
 *	File: Debug.java
 *
 *	Revision;
 *
 *	11/18/02
 *		- first revision.
 *
 ******************************************************************/

package org.cybergarage.util;

import java.io.PrintStream;

/** ������ */
public final class Debug
{

	/** ��̬��Debug ���� */
	public static Debug debug = new Debug();

	/** ϵͳ�� PrintStream */
	private PrintStream out = System.out;

	/** Ĭ�ϵĹ��췽�� */
	public Debug()
	{

	}

	/** ��ȡ PrintStream ���󣬴�ͬ�������̰߳�ȫ�� */
	public synchronized PrintStream getOut()
	{
		return out;
	}

	/** ����PrintStream ���󣬴�ͬ�������̰߳�ȫ�� */
	public synchronized void setOut(PrintStream out)
	{
		this.out = out;
	}

	/** Debug�Ƿ�����trueΪ������falseΪ�ر� */
	public static boolean enabled = false;

	/** ��ȡDebug ���� */
	public static Debug getDebug()
	{
		return Debug.debug;
	}

	/** ����Debug ���Դ�ӡ��Ϣ */
	public static final void on()
	{
		enabled = true;
	}

	/** �ر�Debug ���ܴ�ӡ��Ϣ */
	public static final void off()
	{
		enabled = false;
	}

	/** �ж�Debug �Ƿ��� */
	public static boolean isOn()
	{
		return enabled;
	}

	/** ��ӡ��Ϣ */
	public static final void message(String s)
	{
		if (enabled == true)
			Debug.debug.getOut().println("CyberGarage message : " + s);
	}

	/** ��ӡ��Ϣ */
	public static final void message(String m1, String m2)
	{
		if (enabled == true)
			Debug.debug.getOut().println("CyberGarage message : ");
		Debug.debug.getOut().println(m1);
		Debug.debug.getOut().println(m2);
	}

	/** ��ӡ������Ϣ */
	public static final void warning(String s)
	{
		Debug.debug.getOut().println("CyberGarage warning : " + s);
	}

	/** ��ӡ������Ϣ */
	public static final void warning(String m, Exception e)
	{
		if (e.getMessage() == null)
		{
			Debug.debug.getOut().println(
					"CyberGarage warning : " + m + " START");
			e.printStackTrace(Debug.debug.getOut());
			Debug.debug.getOut().println("CyberGarage warning : " + m + " END");
		}
		else
		{
			Debug.debug.getOut().println(
					"CyberGarage warning : " + m + " (" + e.getMessage() + ")");
			e.printStackTrace(Debug.debug.getOut());
		}
	}

	/** ��ӡ�쳣 */
	public static final void warning(Exception e)
	{
		warning(e.getMessage());
		e.printStackTrace(Debug.debug.getOut());
	}
}
