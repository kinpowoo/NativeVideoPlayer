/******************************************************************
*
*	CyberUtil for Java
*
*	Copyright (C) Satoshi Konno 2002-2004
*
*	File: Mutex.java
*
*	Revision:
*
*	06/19/04
*		- first revision.
*
******************************************************************/

package org.cybergarage.util;

/** Mutex ���� ����*/
public class Mutex
{
	/** syncLock ͬ������Ĭ��Ϊfalse */
	private boolean syncLock;
	
	////////////////////////////////////////////////
	//	Constructor
	////////////////////////////////////////////////

	/** ����һ��Mutex����ͬ����Ĭ��Ϊfalse */
	public Mutex()
	{
		syncLock = false;
	}
	
	////////////////////////////////////////////////
	//	lock
	////////////////////////////////////////////////
	/**��ס�̵߳ķ��� �ø��߳̽���ȴ�����*/
	public synchronized void lock()
	{
		while(syncLock == true) {
			try {
				wait();
			}
			catch (Exception e) {
				Debug.warning(e);
			};
		}
		syncLock = true;
	}

	/** ���������̵߳ķ��� */
	public synchronized void unlock()
	{
		syncLock = false;
		notifyAll();
	}

}