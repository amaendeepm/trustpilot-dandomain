package com.trustpilot.support.dandomain.orderprocess;

import java.util.List;

public class Order {
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getCustomerName() {
		return customerName;
	}
	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}
	public String getCustomerEmail() {
		return customerEmail;
	}
	public void setCustomerEmail(String customerEmail) {
		this.customerEmail = customerEmail;
	}
	public List<Product> getProdList() {
		return prodList;
	}
	public void setProdList(List<Product> prodList) {
		this.prodList = prodList;
	}
	private String id;
	private String customerName;
	private String customerEmail;
	private List<Product> prodList;
	
	private String pril;
	
	public String getPril() {
		return pril;
	}
	
	public void setPril(String str) {
		pril =  str;
	}
	

}
