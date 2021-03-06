/*
 * JEF - Copyright 2009-2010 Jiyi (mr.jiyi@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.geequery.jsqlparser.expression.operators.relational;

import java.util.Arrays;
import java.util.List;

import com.github.geequery.jsqlparser.Util;
import com.github.geequery.jsqlparser.statement.select.PlainSelect;
import com.github.geequery.jsqlparser.visitor.Expression;
import com.github.geequery.jsqlparser.visitor.ExpressionVisitor;
import com.github.geequery.jsqlparser.visitor.ItemsList;

/**
 * A list of expressions, as in SELECT A FROM TAB WHERE B IN (expr1,expr2,expr3)
 */
public class ExpressionList implements ItemsList{

    private List<Expression> expressions;
    private String between=",";
    
	public ExpressionList() {
    }

	/**
	 * 获得多个参数之间的分隔符
	 * @return
	 */
    public String getBetween() {
		return between;
	}

	public ExpressionList setBetween(String between) {
		this.between = between;
		return this;
	}

	public int size(){
    	return expressions.size();
    }
    
    public boolean isEmpty(){
    	return expressions==null?true:expressions.isEmpty();
    }
    
    public ExpressionList(List<Expression> expressions) {
        this.expressions = expressions;
    }

    public ExpressionList(Expression... expression) {
        this.expressions = Arrays.asList(expression);
	}

	public List<Expression> getExpressions() {
        return expressions;
    }
	public Expression get(int i) {
		return expressions.get(i);
	}

    public void setExpressions(List<Expression> list) {
        expressions = list;
    }

    public String toString() {
    	StringBuilder sb=new StringBuilder();
    	Util.getStringList(sb,expressions, between, true);
        return sb.toString();
    }

	public void appendTo(StringBuilder sb) {
		Util.getStringList(sb,expressions, between, true);
	}

	public void accept(ExpressionVisitor itemsListVisitor) {
		itemsListVisitor.visit(this);
	}
}
