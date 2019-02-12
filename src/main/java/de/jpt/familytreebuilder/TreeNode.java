package de.jpt.familytreebuilder;

import java.util.ArrayList;
import java.util.List;

public class TreeNode<T> {
	
    private T data;
    private TreeNode<T> parent;
    private List<TreeNode<T>> children = new ArrayList<TreeNode<T>>();
//    private static TreeNioComparator

    public TreeNode(T data) {
    	super();
    	this.data = data;
    	this.parent = null;
	}

    public TreeNode(T data, TreeNode<T> parent) {
    	super();
    	this.data = data;
    	this.parent = parent;
	}

    public TreeNode<T> getParent() {
		return parent;
	}

    public List<TreeNode<T>> getChildren() {
		return children;
	}
    
    public T getData() {
		return data;
	}
    
}
