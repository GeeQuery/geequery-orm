package com.github.geequery.common.wrapper;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.github.geequery.common.AbstractMap;

/**
 * 将Properties封装成普通的Map
 * @author jiyi
 *
 */
public class PropertiesMap extends AbstractMap<String, String> implements Map<String, String>{
	private Properties prop;
	
	public PropertiesMap(Properties p){
		this.prop=p;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Set<java.util.Map.Entry<String, String>> entrySet() {
		return (Set)prop.entrySet();
	}

	@Override
	public int size() {
		return prop.size();
	}

	@Override
	public void clear() {
		prop.clear();		
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator entryIterator() {
		return prop.entrySet().iterator();
	}
}
