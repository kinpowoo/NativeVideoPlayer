/******************************************************************
*
*	CyberHTTP for Java
*
*	Copyright (C) Satoshi Konno 2002-2004
*
*	File: Parameter.java
*
*	Revision;
*
*	02/01/04
*		- first revision.
*
******************************************************************/

package org.cybergarage.http;

/** Parameter �������URI�еĲ���*/
public class Parameter 
{
	/** ���������� */
	private String name = new String(); 
	/** ������ֵ */
	private String value = new String(); 

	/** ����һ��Parameter */
	public Parameter() 
	{
	}

	/** ����һ��Parameter
	 * @param  name ����������
	 * @param  value ������ֵ
	 **/
	public Parameter(String name, String value) 
	{
		setName(name);
		setValue(value);
	}

	////////////////////////////////////////////////
	//	name
	////////////////////////////////////////////////

	/** ���ò��������� */
	public void setName(String name) 
	{
		this.name = name;
	}

	/** ��ȡ���������� */
	public String getName() 
	{
		return name;
	}

	////////////////////////////////////////////////
	//	value
	////////////////////////////////////////////////

	/** ���ò�����ֵ */
	public void setValue(String value) 
	{
		this.value = value;
	}

	/** ��ȡ������ֵ*/
	public String getValue() 
	{
		return value;
	}
}

